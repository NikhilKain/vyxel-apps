package com.vythera.vyxelapps

import android.content.Context
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuInstaller {

    private val main = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestPermission(requestCode: Int) {
        try { Shizuku.requestPermission(requestCode) } catch (_: Throwable) {}
    }

    /**
     * Silent install via Shizuku: streams the APK through stdin so pm never needs
     * to read the file directly — avoids permission errors on app-private storage paths.
     */
    fun install(
        @Suppress("UNUSED_PARAMETER") context: Context,
        apkFile: File,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!isAvailable()) { onFailure("Shizuku is not running"); return }
        if (!hasPermission()) { onFailure("Shizuku permission not granted"); return }
        Thread {
            try {
                val size    = apkFile.length()
                val process = ShizukuHelper.newProcess(
                    arrayOf("pm", "install", "-r", "--user", "0", "-S", size.toString())
                )
                // Pipe APK bytes to stdin — no file-path access needed by the shell user
                process.outputStream.buffered().use { sink ->
                    apkFile.inputStream().buffered().use { it.copyTo(sink) }
                }
                val exitCode = process.waitFor()
                val stdout   = process.inputStream.bufferedReader().readText().trim()
                val stderr   = process.errorStream.bufferedReader().readText().trim()
                val ok       = exitCode == 0 || stdout.contains("success", ignoreCase = true)
                if (ok) main.post { onSuccess() }
                else    main.post { onFailure(stderr.ifEmpty { stdout }.ifEmpty { "Exit $exitCode" }) }
            } catch (e: Exception) {
                main.post { onFailure("Shizuku install error: ${e.message}") }
            }
        }.start()
    }

    /**
     * Silent uninstall via Shizuku.
     *
     * The same privilege that lets Shizuku install without a confirmation dialog lets
     * it remove without one. Callers must still fall back to `ACTION_DELETE` when this
     * reports failure — Shizuku may be running but unable to touch a given package
     * (system apps, other users), and the user should still get the system uninstaller
     * rather than nothing happening.
     */
    fun uninstall(
        packageName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (!isAvailable()) { onFailure("Shizuku is not running"); return }
        if (!hasPermission()) { onFailure("Shizuku permission not granted"); return }
        Thread {
            try {
                val process = ShizukuHelper.newProcess(
                    arrayOf("pm", "uninstall", "--user", "0", packageName)
                )
                val exitCode = process.waitFor()
                val stdout = process.inputStream.bufferedReader().readText().trim()
                val stderr = process.errorStream.bufferedReader().readText().trim()
                val ok = exitCode == 0 || stdout.contains("success", ignoreCase = true)
                if (ok) main.post { onSuccess() }
                else main.post { onFailure(stderr.ifEmpty { stdout }.ifEmpty { "Exit $exitCode" }) }
            } catch (e: Exception) {
                main.post { onFailure("Shizuku uninstall error: ${e.message}") }
            }
        }.start()
    }
}
