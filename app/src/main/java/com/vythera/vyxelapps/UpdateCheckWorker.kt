package com.vythera.vyxelapps

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs   = PreferencesManager(applicationContext)
        val history = prefs.loadInstallHistory().distinctBy { it.repoId }
        val ignored = prefs.loadIgnoredVersions()
        val token   = prefs.loadSettings().githubToken
        if (token.isNotEmpty()) RetrofitClient.authToken = token

        // ── Updates for apps the user installed through Vyxel ────────────────
        val available = mutableListOf<UpdateInfo>()
        // Every entry this run actually reached the API for, whatever the verdict.
        // Anything in here has a fresh answer, so a stale saved entry for it is wrong
        // and must go — see the merge below.
        val verified = mutableSetOf<Long>()
        for (entry in history) {
            try {
                val release = RetrofitClient.service.getLatestRelease(entry.ownerLogin, entry.repoName)
                verified += entry.repoId
                val key = "${entry.repoId}:${release.tag_name}"
                // Compare versions, not strings.
                //
                // This used to be `release.tag_name != entry.tagName`, which flags an
                // update whenever the two tags merely *differ* — so "v1.0.1" against a
                // stored "1.0.1" reported an update forever, the notification fired,
                // and the Updates screen (which does compare properly) then showed
                // everything as current. Updating didn't help either: the newly stored
                // tag still didn't match the next tag string byte-for-byte, so it came
                // straight back. It also reported downgrades as updates.
                if (isVersionNewerThan(release.tag_name, entry.tagName) && key !in ignored) {
                    available.add(UpdateInfo(
                        repoId     = entry.repoId,
                        repoName   = entry.repoName,
                        currentTag = entry.tagName,
                        latestTag  = release.tag_name,
                        changelog  = release.body ?: ""
                    ))
                }
            } catch (_: Exception) {}
        }
        // Persist so the Updates UI and the home-screen widget show these immediately,
        // without waiting for the app's own foreground check.
        //
        // Entries this run didn't check are kept: the worker only covers GitHub-source
        // installs, so updates found from other sources must survive. But an entry it
        // *did* check and found current is dropped, which is what clears the phantom
        // updates left behind by the old string comparison. Without that, a bad saved
        // entry fell into the "keep the old one" branch on every run and survived
        // forever — the notification stopped, but the badge and the list did not.
        val stale = prefs.loadUpdates().filter { old -> old.repoId !in verified }
        val merged = available + stale
        if (merged != prefs.loadUpdates()) prefs.saveUpdates(merged)
        if (available.isNotEmpty()) {
            showAppsUpdateNotification(available.map { it.repoName to it.latestTag })
        }
        TodayWidgetProvider.refreshAll(applicationContext)

        // ── Check for a new Vyxel release itself ─────────────────────────────
        checkVyxelUpdate()

        return Result.success()
    }

    private suspend fun checkVyxelUpdate() {
        val rawPrefs  = applicationContext.getSharedPreferences("vyxel_prefs", Context.MODE_PRIVATE)
        // Notify at most once per 24 hours
        val lastCheck = rawPrefs.getLong("last_self_update_notif", 0L)
        if (System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L) return

        try {
            val release = RetrofitClient.service.getLatestRelease("NikhilKain", "vyxel-apps")
            val latest  = release.tag_name.trimStart('v', 'V')
            val current = BuildConfig.VERSION_NAME
            if (isNewerVersion(latest, current)) {
                showVyxelUpdateNotification(release.tag_name)
            }
            rawPrefs.edit().putLong("last_self_update_notif", System.currentTimeMillis()).apply()
        } catch (_: Exception) {}
    }

    private fun isNewerVersion(latest: String, current: String): Boolean =
        isVersionNewerThan(latest, current)

    private fun showAppsUpdateNotification(updates: List<Pair<String, String>>) {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = "${updates.size} app update${if (updates.size > 1) "s" else ""} available"
        val text  = updates.take(3).joinToString(", ") { "${it.first} → ${it.second}" }
        // Expanded view lists every app, one per line — a digest worth tapping
        val bigText = updates.joinToString("\n") { "• ${it.first}  ${it.second}" } +
            "\n\nOpen Vyxel and tap Update All."

        val notif = NotificationCompat.Builder(applicationContext, "vyxel_updates")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(2024, notif)
    }

    private fun showVyxelUpdateNotification(latestVersion: String) {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(applicationContext, "vyxel_updates")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Vyxel Apps update available")
            .setContentText("Version $latestVersion is ready — tap to open and update")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("A new version of Vyxel Apps ($latestVersion) is available. Open the app to download and install it."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(2025, notif)
    }
}
