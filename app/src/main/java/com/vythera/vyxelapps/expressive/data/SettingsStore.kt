package com.vythera.vyxelapps.expressive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vythera.vyxelapps.expressive.data.model.SourceId
import com.vythera.vyxelapps.expressive.ui.theme.MotionIntensity
import com.vythera.vyxelapps.expressive.ui.theme.ThemeMode
import com.vythera.vyxelapps.expressive.ui.theme.VyxelSkin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vyxel_settings")

/** How the "installed via Vyxel" list is ordered. */
enum class InstalledSort { Recent, Name }

data class Settings(
    /** Premium look. [VyxelSkin.Default] leaves [themeMode] and dynamic colour in charge. */
    val skin: VyxelSkin = VyxelSkin.Default,
    val themeMode: ThemeMode = ThemeMode.System,
    // Off by default: wallpaper-derived palettes frequently land on muddy
    // low-chroma browns that bury the source brand colours. Opt-in from Settings.
    val dynamicColor: Boolean = false,
    val motionIntensity: MotionIntensity = MotionIntensity.Balanced,
    val enabledSources: Set<SourceId> = SourceId.entries.toSet(),
    val githubToken: String = "",
    val showDesktopSources: Boolean = true,
    /**
     * Packages the user has hidden, suppressed everywhere at once.
     *
     * Keyed by package name rather than by catalog id on purpose: the same app is
     * usually carried by three or four sources, and hiding it in search only for it to
     * reappear under a different badge on the home screen is not "hidden" in any sense
     * the user means. One entry here removes it from every source's results.
     */
    val hiddenPackages: Set<String> = emptySet(),
    val installedSort: InstalledSort = InstalledSort.Recent,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val SKIN = stringPreferencesKey("skin")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val MOTION = stringPreferencesKey("motion_intensity")
        val SOURCES = stringSetPreferencesKey("enabled_sources")
        val GH_TOKEN = stringPreferencesKey("github_token")
        val DESKTOP = booleanPreferencesKey("show_desktop_sources")
        val HIDDEN = stringSetPreferencesKey("hidden_packages")
        val INSTALLED_SORT = stringPreferencesKey("installed_sort")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            skin = prefs[Keys.SKIN]?.let { runCatching { VyxelSkin.valueOf(it) }.getOrNull() }
                ?: VyxelSkin.Default,
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            dynamicColor = prefs[Keys.DYNAMIC] ?: false,
            motionIntensity = prefs[Keys.MOTION]
                ?.let { runCatching { MotionIntensity.valueOf(it) }.getOrNull() }
                ?: MotionIntensity.Balanced,
            enabledSources = prefs[Keys.SOURCES]
                ?.mapNotNull { name -> runCatching { SourceId.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?: SourceId.entries.toSet(),
            githubToken = prefs[Keys.GH_TOKEN].orEmpty(),
            showDesktopSources = prefs[Keys.DESKTOP] ?: true,
            hiddenPackages = prefs[Keys.HIDDEN].orEmpty(),
            installedSort = prefs[Keys.INSTALLED_SORT]
                ?.let { runCatching { InstalledSort.valueOf(it) }.getOrNull() }
                ?: InstalledSort.Recent,
        )
    }

    suspend fun setInstalledSort(sort: InstalledSort) =
        context.dataStore.edit { it[Keys.INSTALLED_SORT] = sort.name }.let { }

    /** Hides or restores one package across every source. */
    suspend fun setHidden(packageName: String, hidden: Boolean) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN]?.toMutableSet() ?: mutableSetOf()
            if (hidden) current.add(packageName) else current.remove(packageName)
            prefs[Keys.HIDDEN] = current
        }
    }

    suspend fun clearHidden() =
        context.dataStore.edit { it[Keys.HIDDEN] = emptySet() }.let { }

    suspend fun setSkin(skin: VyxelSkin) =
        context.dataStore.edit { it[Keys.SKIN] = skin.name }.let { }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME] = mode.name }.let { }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC] = enabled }.let { }

    suspend fun setMotionIntensity(intensity: MotionIntensity) =
        context.dataStore.edit { it[Keys.MOTION] = intensity.name }.let { }

    suspend fun setGithubToken(token: String) =
        context.dataStore.edit { it[Keys.GH_TOKEN] = token }.let { }

    suspend fun setShowDesktopSources(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DESKTOP] = enabled }.let { }

    suspend fun toggleSource(source: SourceId, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SOURCES]?.toMutableSet()
                ?: SourceId.entries.map { it.name }.toMutableSet()
            if (enabled) current.add(source.name) else current.remove(source.name)
            prefs[Keys.SOURCES] = current
        }
    }
}
