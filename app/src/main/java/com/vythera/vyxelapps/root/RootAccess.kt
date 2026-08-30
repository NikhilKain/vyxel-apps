package com.vythera.vyxelapps.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File

/**
 * The root manager that owns `/data/adb/modules` on this device.
 *
 * All three install the same module zips through different binaries, and running the
 * wrong one fails in a way that reads like the module is broken rather than the
 * command being wrong.
 */
sealed interface RootManager {
    val label: String
    val version: String

    data object None : RootManager {
        override val label = "None"
        override val version = ""
    }

    data class Magisk(override val version: String) : RootManager {
        override val label = "Magisk"
    }

    data class KernelSU(override val version: String) : RootManager {
        override val label = "KernelSU"
    }

    data class APatch(override val version: String) : RootManager {
        override val label = "APatch"
    }

    val available: Boolean get() = this !is None

    /** Single-quoted so paths with spaces survive the trip through `su`. */
    fun installCommand(zipPath: String): String? = when (this) {
        is Magisk -> "magisk --install-module '$zipPath'"
        is KernelSU -> "ksud module install '$zipPath'"
        is APatch -> "apd module install '$zipPath'"
        None -> null
    }
}

/** One installed module, read straight off the device. */
data class InstalledModule(
    val moduleId: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val enabled: Boolean,
)

/**
 * Root access, used only when the user asks for it.
 *
 * Vyxel can catalogue modules without root — it can describe them and save their
 * zips — but flashing one needs the device's own root manager. Nothing here prompts
 * on its own: the `su` request happens when the user taps Install or opens the root
 * card in Settings, never on launch.
 *
 * Modelled on Modex's root layer, which is the same job against the same three
 * managers; the shape is deliberately identical so a module behaves the same in
 * either app.
 */
object RootAccess {

    private const val TAG = "VyxelRoot"
    private const val MODULE_DIR = "/data/adb/modules"
    private const val TIMEOUT_MS = 8_000L

    /**
     * Whether a root shell can actually be obtained.
     *
     * Runs a trivial command rather than looking for an `su` binary on disk: several
     * managers hide the binary from unprivileged lookups, so only asking gives a
     * truthful answer.
     */
    suspend fun isAvailable(): Boolean = runShell("id -u")?.trim()?.endsWith("0") == true

    /**
     * Which root manager is installed, and therefore how modules get installed.
     *
     * The three take the same zip but disagree on the command, and guessing wrong
     * produces a confusing failure rather than a clean one.
     */
    suspend fun detectManager(): RootManager {
        val probe = runShell(
            """
            if command -v magisk >/dev/null 2>&1; then echo "magisk:${'$'}(magisk -c 2>/dev/null)"; fi
            if command -v ksud >/dev/null 2>&1; then echo "ksud:${'$'}(ksud -V 2>/dev/null)"; fi
            if command -v apd >/dev/null 2>&1; then echo "apd:${'$'}(apd -V 2>/dev/null)"; fi
            """.trimIndent(),
        ) ?: return RootManager.None

        // A device can carry more than one binary; the first match wins, and the
        // order matches which one usually owns /data/adb/modules.
        probe.lineSequence().forEach { line ->
            val version = line.substringAfter(':', "").trim()
            when {
                line.startsWith("magisk:") -> return RootManager.Magisk(version)
                line.startsWith("ksud:") -> return RootManager.KernelSU(version)
                line.startsWith("apd:") -> return RootManager.APatch(version)
            }
        }
        return RootManager.None
    }

    /** Every module already on the device, enabled or not. */
    suspend fun listInstalledModules(): List<InstalledModule> {
        val script = """
            for d in $MODULE_DIR/*/; do
              [ -d "${'$'}d" ] || continue
              echo "###MODULE"
              if [ -f "${'$'}d/disable" ]; then echo "###DISABLED"; fi
              cat "${'$'}d/module.prop" 2>/dev/null
            done
        """.trimIndent()

        val output = runShell(script) ?: return emptyList()
        val modules = mutableListOf<InstalledModule>()
        var pending = StringBuilder()
        var disabled = false
        var seenHeader = false

        fun flush() {
            if (!seenHeader) return
            val props = pending.toString().lineSequence().mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith('#')) return@mapNotNull null
                val split = trimmed.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                trimmed.take(split).trim() to trimmed.substring(split + 1).trim()
            }.toMap()
            val id = props["id"] ?: return
            modules += InstalledModule(
                moduleId = id,
                name = props["name"].orEmpty().ifBlank { id },
                version = props["version"].orEmpty(),
                versionCode = props["versionCode"]?.toLongOrNull() ?: 0L,
                enabled = !disabled,
            )
        }

        output.lineSequence().forEach { line ->
            when {
                line.startsWith("###MODULE") -> {
                    flush(); pending = StringBuilder(); disabled = false; seenHeader = true
                }
                line.startsWith("###DISABLED") -> disabled = true
                else -> pending.appendLine(line)
            }
        }
        flush()
        return modules.sortedBy { it.name.lowercase() }
    }

    /**
     * Installs a module zip, streaming the manager's own output back.
     *
     * The output is the point. Module installers print their compatibility checks and
     * their reasons for refusing to stdout, and a spinner-and-verdict UI throws all of
     * that away — turning "your kernel is too old" into a silent failure. [onLine]
     * receives each line as it arrives so the console can show it live.
     */
    suspend fun installModule(
        zip: File,
        manager: RootManager,
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val command = manager.installCommand(zip.absolutePath) ?: run {
            onLine("No supported root manager found.")
            return@withContext false
        }

        onLine("$ $command")
        var process: Process? = null
        try {
            process = ProcessBuilder("su").redirectErrorStream(true).start()
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(command)
                writer.newLine()
                writer.write("exit ${'$'}?")
                writer.newLine()
            }
            process.inputStream.bufferedReader().forEachLine { onLine(it) }
            val code = process.waitFor()
            if (code != 0) onLine("Installer exited with code $code")
            code == 0
        } catch (e: Exception) {
            onLine("Install failed: ${e.message}")
            false
        } finally {
            runCatching { process?.destroy() }
        }
    }

    /**
     * Reboots the device.
     *
     * `svc power reboot` asks the framework to shut down cleanly; the bare `reboot`
     * fallback covers the ROMs where svc is missing. Callers must confirm with the
     * user first — nothing here asks.
     */
    suspend fun reboot(): Boolean = runShell("svc power reboot || reboot") != null

    /**
     * Runs [script] through `su`, returning stdout, or null if root was denied,
     * unavailable, or took too long.
     *
     * The script goes over stdin rather than through `su -c`: multi-line scripts get
     * mangled differently by every su implementation, while stdin behaves the same
     * everywhere.
     */
    private suspend fun runShell(
        script: String,
        timeoutMs: Long = TIMEOUT_MS,
    ): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            var process: Process? = null
            try {
                process = ProcessBuilder("su").redirectErrorStream(false).start()
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(script)
                    writer.newLine()
                    writer.write("exit")
                    writer.newLine()
                }
                val out = process.inputStream.bufferedReader().use(BufferedReader::readText)
                if (process.waitFor() == 0) out else null
            } catch (e: Exception) {
                // No su binary, a denial, or a manager that kills the request — all
                // of them mean the same thing to the caller.
                Log.d(TAG, "root unavailable: ${e.message}")
                null
            } finally {
                runCatching { process?.destroy() }
            }
        }
    }
}
