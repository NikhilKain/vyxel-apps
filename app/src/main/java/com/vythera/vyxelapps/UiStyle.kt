package com.vythera.vyxelapps

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Which of the two shells the app renders.
 *
 * Classic is the original Vyxel Apps UI (Liquid Glass / Cyberpunk / NeonPunk themes).
 * Expressive is the Material 3 Expressive store.
 */
enum class UiStyle { Classic, Expressive }

/**
 * Single source of truth for the UI switch.
 *
 * This is an app-scoped observable rather than a plain preference read. The first
 * version kept the choice in two places — this preference *and* `AppSettings.uiStyle`
 * — and MainActivity synced Classic->Expressive from the settings copy. Switching
 * back then deadlocked: Expressive wrote Classic here, Classic rendered, the
 * still-stale `AppSettings.uiStyle` said "Expressive", and the sync immediately flipped
 * it back. One writable state that every screen reads and writes removes the loop.
 */
object UiStylePrefs {

    private const val PREFS = "vyxel_prefs"
    private const val KEY = "ui_style"

    @Volatile
    private var state: MutableState<UiStyle>? = null

    /** Observable current style; seeded from disk on first access. */
    fun state(context: Context): MutableState<UiStyle> {
        state?.let { return it }
        return synchronized(this) {
            state ?: mutableStateOf(read(context)).also { state = it }
        }
    }

    fun get(context: Context): UiStyle = state(context).value

    fun set(context: Context, style: UiStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, style.name)
            .apply()
        state(context).value = style
    }

    private fun read(context: Context): UiStyle {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, UiStyle.Classic.name)
        return runCatching { UiStyle.valueOf(raw ?: UiStyle.Classic.name) }
            .getOrDefault(UiStyle.Classic)
    }
}
