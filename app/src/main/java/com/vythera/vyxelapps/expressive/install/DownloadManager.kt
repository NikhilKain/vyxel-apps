package com.vythera.vyxelapps.expressive.install

import android.content.Context
import com.vythera.vyxelapps.expressive.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/** Lifecycle of one app's download + install. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data object Connecting : DownloadState
    data class Downloading(
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : DownloadState
    data class Ready(val file: File) : DownloadState
    data object Installing : DownloadState
    data object Installed : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Per-item download state for the Expressive UI.
 *
 * The transfer itself is [FastDownloader], shared with Classic so both shells download
 * at the same speed. This class is only the bookkeeping: state is keyed by
 * [AppItem.id] so the same button can be rendered anywhere in the UI — rail card,
 * detail screen, updates list — and stay in sync.
 */
class DownloadManager(private val context: Context) {

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }

    fun stateOf(id: String): DownloadState = _states.value[id] ?: DownloadState.Idle

    // `update` rather than an assignment on `value`: progress arrives from one
    // coroutine per download, and a read-modify-write lets concurrent downloads drop
    // each other's updates — a second download would freeze the first one's bar.
    fun setState(id: String, state: DownloadState) {
        _states.update { current -> current + (id to state) }
    }

    fun clear(id: String) {
        _states.update { current -> current - id }
    }

    suspend fun download(item: AppItem): Result<File> {
        val url = item.downloadUrl
            ?: return Result.failure(IllegalStateException("No download URL"))

        setState(item.id, DownloadState.Connecting)

        return withContext(Dispatchers.IO) {
            runCatching {
                val target = File(downloadDir, "${item.packageName ?: item.id.hashCode()}.apk")
                FastDownloader.download(url, target, item.sizeBytes) { done, total ->
                    setState(
                        item.id,
                        DownloadState.Downloading(
                            progress = if (total > 0) done.toFloat() / total else -1f,
                            bytesRead = done,
                            totalBytes = total,
                        ),
                    )
                }
                setState(item.id, DownloadState.Ready(target))
                target
            }.onFailure { error ->
                setState(item.id, DownloadState.Failed(error.message ?: "Download failed"))
            }
        }
    }

    /** Drops cached APKs; called from Settings. */
    fun clearCache(): Long {
        var freed = 0L
        downloadDir.listFiles()?.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }

    fun cacheSize(): Long = downloadDir.listFiles()?.sumOf { it.length() } ?: 0L
}
