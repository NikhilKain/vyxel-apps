package com.vythera.vyxelapps.expressive.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Outcome of a PackageInstaller session, forwarded to the UI. */
sealed interface InstallOutcome {
    val appId: String
    data class Success(override val appId: String) : InstallOutcome
    data class Failure(override val appId: String, val message: String) : InstallOutcome
}

/**
 * Receives PackageInstaller session callbacks.
 *
 * The first callback is normally [PackageInstaller.STATUS_PENDING_USER_ACTION],
 * which carries the system confirmation dialog that the app must launch itself.
 */
class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_RESULT = "com.vythera.vyxelapps.expressive.INSTALL_RESULT"
        const val EXTRA_APP_ID = "app_id"

        private val _outcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 8)
        val outcomes: SharedFlow<InstallOutcome> = _outcomes

        fun emit(outcome: InstallOutcome) { _outcomes.tryEmit(outcome) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirm?.let { runCatching { context.startActivity(it) } }
            }

            PackageInstaller.STATUS_SUCCESS -> emit(InstallOutcome.Success(appId))

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Install failed"
                emit(InstallOutcome.Failure(appId, message))
            }
        }
    }
}
