package com.vythera.vyxelapps.updater

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class UpdateScanEngine(
    private val context: Context,
    private val githubToken: String = "",
    private val additionalGitHubApps: List<GHApp> = emptyList()
) {
    private val gson = Gson()

    // ── HTTP clients ──────────────────────────────────────────────────────────

    /**
     * Client for the F-Droid-protocol repos.
     *
     * `callTimeout` bounds the *whole* call, body download included. At 30s the
     * F-Droid and IzzyOnDroid index downloads (tens of MB) were cancelled mid-body on
     * every single scan — surfacing as `StreamResetException: stream was reset:
     * CANCEL` — which is why "Scan all sources" never returned anything from the two
     * most important sources. Connect/read timeouts still catch a dead server; only
     * the total-duration cap needed to grow.
     */
    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun githubClient() = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // A scan spends the same GitHub budget the search screen does, so its
        // responses have to feed the quota meter too — otherwise the number in
        // Settings would read "full" right after a scan had emptied it.
        .addNetworkInterceptor(com.vythera.vyxelapps.api.GitHubRateLimit.interceptor)
        .apply {
            if (githubToken.isNotEmpty()) {
                addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "token $githubToken")
                            .build()
                    )
                }
            }
        }
        .build()

    private fun aptoideClient() = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", AptoideScanRepository.UserAgent)
                    .build()
            )
        }
        .build()

    // ── Retrofit instances ────────────────────────────────────────────────────

    private fun <T> retrofit(baseUrl: String, client: OkHttpClient, cls: Class<T>): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(cls)

    private val fdroidMainSvc  by lazy {
        retrofit("https://f-droid.org/repo/",            baseClient(), FdroidScanService::class.java)
    }
    private val fdroidIzzySvc  by lazy {
        retrofit("https://apt.izzysoft.de/fdroid/repo/", baseClient(), FdroidScanService::class.java)
    }
    private val githubSvc by lazy {
        retrofit("https://api.github.com",               githubClient(), GitHubScanService::class.java)
    }
    private val gitlabSvc by lazy {
        retrofit("https://gitlab.com/",                  baseClient(), GitLabScanService::class.java)
    }
    private val aptoideSvc by lazy {
        retrofit("https://ws75.aptoide.com/api/7/",      aptoideClient(), AptoideScanService::class.java)
    }
    private val apkPureSvc by lazy {
        retrofit("https://tapi.pureapk.com/",            baseClient(), ApkPureScanService::class.java)
    }

    // ── Repositories ──────────────────────────────────────────────────────────

    private val appRepo       by lazy { AppScanRepository(context) }
    private val fdroidMain    by lazy { FdroidScanRepository(fdroidMainSvc,  "https://f-droid.org/repo/",            FdroidUpdaterSource) }
    private val fdroidIzzy    by lazy { FdroidScanRepository(fdroidIzzySvc,  "https://apt.izzysoft.de/fdroid/repo/", IzzyUpdaterSource) }
    private val githubRepo    by lazy { GitHubScanRepository(githubSvc) }
    private val gitlabRepo    by lazy { GitLabScanRepository(gitlabSvc) }
    private val aptoideRepo   by lazy { AptoideScanRepository(context, aptoideSvc) }

    /**
     * APKPure — the update path for apps that only exist on Google Play.
     *
     * Every other source here is open-source-only by construction: F-Droid and
     * IzzyOnDroid build from source, and the repo hosts have what their authors
     * pushed. So a scan found updates for the FOSS half of a phone and reported
     * nothing at all for the half that came from Play, which reads as the scan being
     * broken rather than the sources being narrow.
     *
     * It was written, tested against, and then never wired in — this repository has
     * been dead code. Its signature check is what makes it safe to use: an update is
     * only reported when the candidate's signing certificate matches the one already
     * installed, so a repackaged build under a real package name is dropped rather
     * than offered as an upgrade.
     */
    private val apkPureRepo   by lazy { ApkPureScanRepository(apkPureSvc, gson) }

    // ── Main scan function ────────────────────────────────────────────────────

    fun scan(): Flow<List<AppScanResult>> = flow {
        appRepo.getApps().collect { result ->
            result.onSuccess { apps ->
                val sources = mutableListOf<Flow<List<AppScanResult>>>(
                    fdroidMain.updates(apps).onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                    fdroidIzzy.updates(apps).onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                    githubRepo.updates(apps, additionalGitHubApps).onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                    gitlabRepo.updates(apps).onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                    aptoideRepo.updates(apps).onStart { emit(emptyList()) }.catch { emit(emptyList()) },
                    apkPureRepo.updates(apps).onStart { emit(emptyList()) }.catch { emit(emptyList()) }
                )
                sources.combineFlows { all ->
                    // Per package, keep only the entry with the highest newVersion so that
                    // a source reporting an update always wins over one reporting "up to date"
                    val deduped = all.flatMap { it }
                        .groupBy { it.packageName }
                        .values
                        .map { entries ->
                            entries.maxWithOrNull(Comparator { a, b ->
                                when {
                                    a.hasUpdate && !b.hasUpdate -> 1
                                    !a.hasUpdate && b.hasUpdate -> -1
                                    else -> {
                                        val av = filterVersionTag(a.newVersion)
                                        val bv = filterVersionTag(b.newVersion)
                                        when {
                                            isVersionNewer(av, bv) ->  1
                                            isVersionNewer(bv, av) -> -1
                                            else                   ->  0
                                        }
                                    }
                                }
                            }) ?: entries.first()
                        }
                    emit(deduped)
                }.collect()
            }.onFailure {
                Log.e("UpdateScanEngine", "Error getting installed apps", it)
                emit(emptyList())
            }
        }
    }.catch {
        Log.e("UpdateScanEngine", "Scan failed", it)
        emit(emptyList())
    }.flowOn(Dispatchers.IO)
}
