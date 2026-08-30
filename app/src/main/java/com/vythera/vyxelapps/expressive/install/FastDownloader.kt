package com.vythera.vyxelapps.expressive.install

import com.vythera.vyxelapps.expressive.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Splits a file into the byte ranges each parallel worker will fetch.
 *
 * Separate and pure so it can be tested without a Context or a network: an off-by-one
 * here does not fail loudly, it writes an APK with a one-byte hole in the middle,
 * which then fails signature verification with no clue as to why.
 */
internal object DownloadSegments {

    /**
     * Contiguous, non-overlapping ranges covering exactly `0 until total`.
     *
     * The last range absorbs the remainder of an uneven division, so the ranges always
     * sum back to [total] however the numbers fall.
     */
    fun ranges(total: Long, maxSegments: Int, minSegment: Long): List<LongRange> {
        if (total <= 0) return emptyList()
        val count = (total / minSegment)
            .coerceIn(1L, maxSegments.toLong())
            .toInt()
        if (count <= 1) return listOf(0 until total)

        val chunk = total / count
        return (0 until count).map { index ->
            val first = index * chunk
            val last = if (index == count - 1) total - 1 else first + chunk - 1
            first..last
        }
    }
}

/**
 * The app's one file downloader, used by both shells.
 *
 * Large files are fetched over several parallel HTTP range requests and reassembled in
 * place. This matters more than it looks: F-Droid and IzzyOnDroid shape traffic *per
 * connection* rather than per client, so a single stream sits well under the link's
 * real capacity however fast the phone's connection is — which is why downloads felt
 * slow even on good Wi-Fi. Anything small, or any server that does not honour `Range`,
 * falls back to a single stream.
 *
 * Classic previously used Android's system `DownloadManager` for this, which is always
 * one connection and cannot be tuned. Both shells now share this instead, so an app
 * downloads at the same speed whichever skin is active.
 */
object FastDownloader {

    /**
     * Parallel connections for an ordinary APK.
     *
     * Four is the point of diminishing returns on the mirrors that matter here. Going
     * much beyond it mostly adds request overhead, and on small volunteer-run mirrors
     * it starts to look like abuse.
     */
    private const val SEGMENTS = 4

    /**
     * Segments for a large file.
     *
     * Above [LARGE_FILE_BYTES] the per-connection shaping dominates everything else
     * and the extra handshakes are amortised over enough bytes to be free.
     */
    private const val SEGMENTS_LARGE = 6
    private const val LARGE_FILE_BYTES = 40L * 1024 * 1024

    /**
     * Below this, one connection is already faster — segmenting costs a probe request
     * plus N connection setups, which for a small APK exceeds anything it saves.
     */
    const val MIN_SEGMENTED_BYTES = 3L * 1024 * 1024

    /** Never cut the file into slices smaller than this. */
    private const val MIN_SEGMENT_BYTES = 1536L * 1024

    private const val BUFFER_BYTES = 128 * 1024

    /**
     * Downloads [url] into [target], reporting `(bytesDone, totalBytes)` as it goes.
     *
     * [knownSize], when the catalogue published one, lets a small download skip the
     * range probe entirely — a whole round trip spent deciding whether to parallelise
     * something that was never going to be split.
     *
     * Progress is reported only when the whole percent changes; a 60 MB file otherwise
     * pushes tens of thousands of updates at whatever is drawing the progress bar.
     */
    suspend fun download(
        url: String,
        target: File,
        knownSize: Long = 0L,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val probe = if (knownSize in 1 until MIN_SEGMENTED_BYTES) null else probeRanges(url)
        if (probe != null && probe.total >= MIN_SEGMENTED_BYTES) {
            downloadSegmented(probe, target, onProgress)
        } else {
            downloadSingle(url, target, knownSize, onProgress)
        }

        if (target.length() == 0L) error("Downloaded file was empty")
    }

    /** What a one-byte range request revealed about the server. */
    private data class RangeSupport(
        /**
         * The URL the probe actually landed on.
         *
         * GitHub redirects release assets to a signed objects.githubusercontent.com
         * address. Reusing the resolved URL saves every segment repeating the same
         * redirect chain.
         */
        val url: String,
        val total: Long,
    )

    /**
     * Asks for a single byte to learn the file size and whether ranges work.
     *
     * A `HEAD` would be the textbook way, but several mirrors answer HEAD with 405 or
     * with no `Content-Length`, whereas a one-byte GET is universally handled and
     * costs the same.
     */
    private fun probeRanges(url: String): RangeSupport? = runCatching {
        Net.openRange(url, 0, 0).use { response ->
            if (response.code != 206) return@use null
            // "bytes 0-0/12345" — the size is what follows the slash.
            val total = response.header("Content-Range")
                ?.substringAfterLast('/')
                ?.trim()
                ?.toLongOrNull()
                ?: return@use null
            if (total <= 0) return@use null
            RangeSupport(url = response.request.url.toString(), total = total)
        }
    }.getOrNull()

    /** Single-connection download — the fallback, and the fast path for small files. */
    private suspend fun downloadSingle(
        url: String,
        target: File,
        knownSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        Net.downloadClient()
            .newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .header("Accept-Encoding", "identity")
                    .build()
            )
            .execute()
            .use { response ->
                if (!response.isSuccessful) error("Server returned ${response.code}")
                val body = response.body ?: error("Empty response")
                val total = body.contentLength().takeIf { it > 0 } ?: knownSize

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var read: Int
                        var written = 0L
                        var lastPercent = -1

                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            written += read

                            if (total > 0) {
                                val percent = ((written * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(written, total)
                                }
                            } else {
                                onProgress(written, 0L)
                            }
                        }
                    }
                }
            }
    }

    /**
     * Fetches several slices in parallel, each written straight to its own offset.
     *
     * The file is pre-sized once up front so every worker can seek within the same
     * handle without any of them growing the file underneath the others.
     */
    private suspend fun downloadSegmented(
        probe: RangeSupport,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        val total = probe.total
        val maxSegments = if (total >= LARGE_FILE_BYTES) SEGMENTS_LARGE else SEGMENTS
        val ranges = DownloadSegments.ranges(total, maxSegments, MIN_SEGMENT_BYTES)

        if (ranges.size <= 1) {
            downloadSingle(probe.url, target, total, onProgress)
            return
        }

        val written = AtomicLong(0)
        // Shared across the workers, so it has to be atomic rather than a local var.
        val lastPercent = AtomicInteger(-1)

        RandomAccessFile(target, "rw").use { file ->
            file.setLength(total)

            coroutineScope {
                ranges.map { range ->
                    val first = range.first
                    val last = range.last

                    async(Dispatchers.IO) {
                        Net.openRange(probe.url, first, last).use { response ->
                            if (response.code != 206) {
                                error("Server stopped honouring ranges (${response.code})")
                            }
                            val body = response.body ?: error("Empty segment response")

                            body.byteStream().use { input ->
                                val buffer = ByteArray(BUFFER_BYTES)
                                var offset = first
                                var read: Int

                                while (input.read(buffer).also { read = it } != -1) {
                                    coroutineContext.ensureActive()
                                    // One handle, many writers: the seek and the write
                                    // have to be atomic with respect to each other.
                                    synchronized(file) {
                                        file.seek(offset)
                                        file.write(buffer, 0, read)
                                    }
                                    offset += read

                                    val done = written.addAndGet(read.toLong())
                                    val percent = ((done * 100) / total).toInt()
                                    if (lastPercent.getAndSet(percent) != percent) {
                                        onProgress(done, total)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        // A short read in any worker leaves a zero-filled hole that would install as a
        // corrupt APK, so the size is the last thing checked before handing it back.
        if (written.get() != total) {
            target.delete()
            error("Download was incomplete (${written.get()} of $total bytes)")
        }
    }
}
