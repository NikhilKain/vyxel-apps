package com.vythera.vyxelapps.expressive.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs a downloaded APK through [PackageInstaller].
 *
 * The session API is used rather than `ACTION_VIEW` on a content:// URI because it
 * reports a real result back to the app (via [InstallResultReceiver]) instead of
 * leaving the store guessing whether the user completed the system prompt.
 */
class ApkInstaller(private val context: Context) {

    private companion object {
        /**
         * Copy buffer for moving an APK into an install session.
         *
         * Kotlin's `copyTo` defaults to 8 KB, which is roughly eight thousand
         * round-trips through the session's stream for a 64 MB app — all of it after
         * the download has finished, so it lands as dead time on a progress bar that
         * already says 100%. A megabyte at a time makes that a few dozen.
         */
        const val COPY_BUFFER = 1024 * 1024
    }

    /**
     * Streams [apk] into a new install session and commits it. The system then shows
     * its own confirmation UI; the outcome arrives at [InstallResultReceiver].
     */
    /**
     * Installs silently through Shizuku when it is running and permitted.
     *
     * Classic has offered this since Shizuku support landed; Expressive went straight
     * to [PackageInstaller] and so always showed the system confirmation dialog, even
     * for users who had set Shizuku up precisely to avoid tapping through it on every
     * update. Falls through to the normal session install whenever Shizuku isn't
     * usable — not running, permission not granted, or the shell command failing —
     * so this can never make an install fail that would otherwise have worked.
     */
    private suspend fun installViaShizuku(apk: File): Boolean =
        withContext(Dispatchers.IO) {
            if (!com.vythera.vyxelapps.ShizukuInstaller.isAvailable()) return@withContext false
            if (!com.vythera.vyxelapps.ShizukuInstaller.hasPermission()) return@withContext false
            kotlin.coroutines.suspendCoroutine { cont ->
                var resumed = false
                com.vythera.vyxelapps.ShizukuInstaller.install(
                    context = context,
                    apkFile = apk,
                    onSuccess = { if (!resumed) { resumed = true; cont.resumeWith(Result.success(true)) } },
                    onFailure = { if (!resumed) { resumed = true; cont.resumeWith(Result.success(false)) } },
                )
            }
        }

    suspend fun install(apk: File, appId: String): Result<Unit> = withContext(Dispatchers.IO) {
        // An XAPK is a zip of split APKs, not an APK.
        //
        // Most large Play-store apps ship this way now — a base APK plus per-ABI and
        // per-density splits. Handing the zip to PackageInstaller fails with a parse
        // error that reads like a corrupt download, so bundles take a session that
        // writes every split instead. Detected by content rather than by file name,
        // because mirrors are inconsistent about the extension.
        val splits = runCatching { extractSplits(apk) }.getOrNull()
        if (!splits.isNullOrEmpty()) {
            return@withContext installBundle(splits, appId).also {
                // The extracted copies are only needed for the length of the session.
                splits.forEach { split -> runCatching { split.delete() } }
            }
        }

        if (installViaShizuku(apk)) {
            // Report the same outcome the session API would have broadcast.
            //
            // The Shizuku path returns straight from `pm install` and never creates a
            // PackageInstaller session, so nothing was ever delivered to
            // [InstallResultReceiver]. Everything downstream keys off that flow: the
            // button stayed on "Installing" forever, the installed-app list was never
            // re-read, and no install history was written. Silent installs looked like
            // hung ones for anyone who had set Shizuku up.
            InstallResultReceiver.emit(InstallOutcome.Success(appId))
            return@withContext Result.success(Unit)
        }
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
                }
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("vyxel", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output, COPY_BUFFER) }
                    session.fsync(output)
                }

                val intent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = InstallResultReceiver.ACTION_INSTALL_RESULT
                    putExtra(InstallResultReceiver.EXTRA_APP_ID, appId)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else 0

                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        }
    }

    /**
     * Unpacks the APKs out of an XAPK/APKS bundle, or null when [file] is a plain APK.
     *
     * An APK is itself a zip, so "is it a zip" is not the question — the question is
     * whether it *contains* APKs. A plain APK contains classes.dex and res/, never a
     * nested `.apk`, so the presence of one is an unambiguous signal.
     *
     * Only the APK entries are extracted. XAPKs also carry `manifest.json`, icons and
     * sometimes OBB data; OBB files need to land in Android/obb and are deliberately
     * out of scope here rather than silently dropped in the wrong place — an app that
     * needs one will say so on first launch.
     */
    private fun extractSplits(file: File): List<File>? {
        val entries = java.util.zip.ZipFile(file).use { zip ->
            // Cheap rejection first.
            //
            // A plain APK always has AndroidManifest.xml at its root and a bundle
            // never does, so one hash lookup answers the question for the common
            // case. Walking the central directory of every APK before every install
            // — tens of thousands of entries on a large app — cost real time to
            // conclude "this is an APK", which it almost always is.
            if (zip.getEntry("AndroidManifest.xml") != null) return null

            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .map { it.name }
                .toList()
        }
        if (entries.isEmpty()) return null

        val outDir = File(context.cacheDir, "splits/${file.nameWithoutExtension}").apply {
            deleteRecursively()
            mkdirs()
        }
        return java.util.zip.ZipFile(file).use { zip ->
            entries.map { name ->
                // Flattened: a zip entry can carry a path, and joining it onto a
                // directory unchecked is how an archive escapes the directory it is
                // supposed to unpack into.
                val target = File(outDir, name.substringAfterLast('/'))
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
                }
                target
            }
        }
    }

    /**
     * Installs a set of split APKs as one app, through a single session.
     *
     * Every split has to be written into the *same* session and committed together —
     * that is what makes the package manager treat them as one app rather than
     * rejecting each for being incomplete. Shizuku is skipped for bundles: its
     * `pm install -S` path takes exactly one APK, and the multi-stage
     * create/write/commit dance is not worth carrying a second implementation of.
     */
    private fun installBundle(splits: List<File>, appId: String): Result<Unit> = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setSize(splits.sumOf { it.length() })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            }
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            splits.forEachIndexed { index, split ->
                session.openWrite("split$index", 0, split.length()).use { output ->
                    split.inputStream().use { input -> input.copyTo(output, COPY_BUFFER) }
                    session.fsync(output)
                }
            }

            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_RESULT
                putExtra(InstallResultReceiver.EXTRA_APP_ID, appId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else 0

            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /**
     * Removes an installed package.
     *
     * Tries Shizuku first so users who set it up get the same no-dialog experience
     * they get on install, and falls back to the system uninstaller otherwise. Returns
     * true only when the package is actually gone; a false return means the system
     * prompt was launched and the caller should watch the package list instead of
     * assuming success.
     */
    suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (com.vythera.vyxelapps.ShizukuInstaller.isAvailable() &&
            com.vythera.vyxelapps.ShizukuInstaller.hasPermission()
        ) {
            val removed = kotlin.coroutines.suspendCoroutine { cont ->
                var resumed = false
                com.vythera.vyxelapps.ShizukuInstaller.uninstall(
                    packageName = packageName,
                    onSuccess = { if (!resumed) { resumed = true; cont.resumeWith(Result.success(true)) } },
                    onFailure = { if (!resumed) { resumed = true; cont.resumeWith(Result.success(false)) } },
                )
            }
            if (removed) return@withContext true
        }

        runCatching {
            val intent = Intent(Intent.ACTION_DELETE)
                .setData(android.net.Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        false
    }

    /** True when the user has granted "install unknown apps" to this app. */
    fun canRequestInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** Intent that takes the user to the permission screen for this app. */
    fun unknownSourcesIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
}
