package com.vythera.vyxelapps.expressive.core.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP stack.
 *
 * A 60 MB on-disk cache does most of the heavy lifting for perceived speed: source
 * catalogs are re-requested on every app launch, and honouring their ETags turns
 * those into 304s instead of full re-downloads.
 */
object Net {

    const val USER_AGENT = "VyxelStore/1.0 (+https://github.com/NikhilKain)"

    @Volatile
    private var client: OkHttpClient? = null

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun init(context: Context) {
        if (client != null) return
        synchronized(this) {
            if (client != null) return
            val cacheDir = File(context.cacheDir, "http")
            client = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 60L * 1024 * 1024))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // Network-level, so a response served from the 60 MB disk cache never
                // republishes its stale rate-limit headers as a fresh reading.
                .addNetworkInterceptor(com.vythera.vyxelapps.api.GitHubRateLimit.interceptor)
                .addInterceptor { chain ->
                    // Deliberately does NOT set Accept-Encoding. OkHttp adds gzip
                    // itself and decompresses transparently; setting the header by
                    // hand opts out of that and hands back raw gzip bytes, which
                    // silently fails every JSON parse downstream.
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build()
                    )
                }
                .build()
        }
    }

    fun client(): OkHttpClient = client ?: synchronized(this) {
        client ?: OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build().also { client = it }
    }

    /**
     * GET returning the body as a string, or throws [HttpException] on failure.
     *
     * Gateway errors are retried with a short backoff: Codeberg and GitLab both
     * return sporadic 502/504s under load, and a single retry turns most of those
     * into a populated rail instead of an error chip.
     */
    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
        /**
         * Interactive paths pass 1: retrying a source that takes 20s to time out
         * turns a search into a minute-long wait for a result the user has already
         * given up on.
         */
        retries: Int = 3,
    ): String = withContext(Dispatchers.IO) {
        val attempts = retries.coerceAtLeast(1)
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                execute(url, headers).use { resp ->
                    if (resp.isSuccessful) return@withContext resp.body?.string().orEmpty()
                    if (resp.code !in TRANSIENT_CODES) throw HttpException(resp.code, url)
                    lastError = HttpException(resp.code, url)
                }
            } catch (e: java.io.IOException) {
                lastError = e
            }
            if (attempt < attempts - 1) kotlinx.coroutines.delay(backoffMillis(attempt))
        }

        // Last resort: whatever this URL returned the last time it worked.
        //
        // Codeberg is a small volunteer instance and sheds load with 503s several
        // times a day; GitLab does the same. Every attempt failing used to mean an
        // empty rail and a red error chip, even though a perfectly good copy of the
        // answer was sitting in the 60 MB disk cache from the previous launch. A
        // day-old list of Codeberg apps is worth far more to the user than an error,
        // and it stays honest — the source's own rail simply doesn't refresh.
        staleFromCache(url, headers)?.let { return@withContext it }

        throw lastError ?: HttpException(0, url)
    }

    /**
     * Reads a previously cached response, however old, without touching the network.
     *
     * `onlyIfCached` plus an unbounded `maxStale` is the documented way to ask OkHttp
     * for "the cached copy or nothing" — it answers 504 with an empty body when there
     * is no entry, which is why the result is null-checked rather than trusted.
     */
    private fun staleFromCache(url: String, headers: Map<String, String>): String? =
        runCatching {
            val req = Request.Builder()
                .url(url)
                .cacheControl(
                    okhttp3.CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(365, TimeUnit.DAYS)
                        .build()
                )
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()

    /**
     * Exponential backoff with jitter: 600ms, 1.2s, 2.4s, 4.8s, capped at 6s.
     *
     * The old flat 400ms ramp retried far too eagerly to help. A 503 from Codeberg or
     * GitLab means the instance is shedding load, and coming back a third of a second
     * later just adds to it — the retries reliably failed together and the source ended
     * up marked down. Backing off properly gives the far end time to recover.
     *
     * The jitter matters because every source starts at once on launch: without it all
     * of them retry on the same schedule and arrive as a burst each time.
     */
    private fun backoffMillis(attempt: Int): Long {
        val base = (600L shl attempt).coerceAtMost(6_000L)
        return base + (0..250).random()
    }

    private val TRANSIENT_CODES = setOf(429, 500, 502, 503, 504)

    /** POST a JSON body, returning the response as a string. */
    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        client().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code, url)
            resp.body?.string().orEmpty()
        }
    }

    @Volatile
    private var bulkClient: OkHttpClient? = null

    /**
     * Client for large file transfers — APK downloads, not catalog JSON.
     *
     * Shares the connection pool and dispatcher of the main client (so DNS, TLS
     * sessions and HTTP/2 connections are reused) but differs in two ways that
     * matter:
     *
     *  - **No response cache.** The shared client keeps a 60 MB on-disk cache whose
     *    entire job is turning catalog re-fetches into 304s. Streaming a 40 MB APK
     *    through it costs a second full write to disk *and* evicts most of the ETag
     *    entries, so every source index had to be re-downloaded in full on the next
     *    launch. The store got slower the more the user installed.
     *  - **No call timeout.** `callTimeout` bounds the whole call including the body,
     *    so the shared client's 180 s ceiling aborted any download that took longer
     *    than three minutes — a 60 MB APK on a slow connection, which is exactly the
     *    case where giving up is least welcome. The read timeout still applies, so a
     *    genuinely stalled transfer is still caught; only "large but progressing" is
     *    now allowed to finish.
     */
    fun downloadClient(): OkHttpClient = bulkClient ?: synchronized(this) {
        bulkClient ?: client().newBuilder()
            .cache(null)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
            .also { bulkClient = it }
    }

    /**
     * One byte range of [url], for segmented downloads.
     *
     * A 206 means the server honoured the range; a 200 means it ignored it and is
     * sending the whole file, which the caller has to detect rather than write the
     * complete body into a slice of the output.
     */
    fun openRange(url: String, first: Long, last: Long): Response {
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=$first-$last")
            .header("Accept-Encoding", "identity")
            .build()
        return downloadClient().newCall(req).execute()
    }

    /** Raw response for streaming consumers. Caller must close it. */
    fun execute(url: String, headers: Map<String, String> = emptyMap()): Response {
        val req = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return client().newCall(req).execute()
    }

    /** Decodes JSON, returning null rather than throwing on malformed payloads. */
    inline fun <reified T> decodeOrNull(text: String): T? = runCatching {
        json.decodeFromString<T>(text)
    }.getOrNull()
}

class HttpException(val code: Int, val url: String) :
    Exception("HTTP $code for $url")
