package dk.emilbolet.quectolaunch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

/** How the app list is shown: icons in a grid under each letter, or one app per line. */
enum class DrawerLayout { Grid, List }

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Accent color as ARGB; null means the wallpaper's colors (Android 12+) or the default. */
    val accent: Int? = null,
    val columns: Int = 5,
    val rows: Int = 5,
    /** Icons in each of the dock's rows. */
    val dockSlots: Int = 5,
    val drawerLayout: DrawerLayout = DrawerLayout.Grid,
    /** Package name of the chosen icon pack; null for the apps' own icons. */
    val iconPack: String? = null,
    /** Double-tap to lock and swipe down for notifications. Off unless the user asks for it. */
    val gestures: Boolean = false,
) {
    companion object {
        val COLUMN_CHOICES = 4..6
        val ROW_CHOICES = 4..7
        val DOCK_SLOT_CHOICES = 4..7

        /** Accent colors offered in settings; the first is also the fallback before Android 12. */
        val ACCENTS = listOf(
            0xFF3B6FD9.toInt(), // blue
            0xFF00897B.toInt(), // teal
            0xFF43A047.toInt(), // green
            0xFFF9A825.toInt(), // amber
            0xFFEF6C00.toInt(), // orange
            0xFFD32F2F.toInt(), // red
            0xFFD81B60.toInt(), // pink
            0xFF8E24AA.toInt(), // purple
        )
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Launcher settings, kept in DataStore. */
class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentKey = intPreferencesKey("accent")
    private val columnsKey = intPreferencesKey("columns")
    private val rowsKey = intPreferencesKey("rows")
    private val dockSlotsKey = intPreferencesKey("dock_slots")
    private val drawerLayoutKey = stringPreferencesKey("drawer_layout")
    private val iconPackKey = stringPreferencesKey("icon_pack")
    private val gesturesKey = booleanPreferencesKey("gestures")

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        val defaults = LauncherSettings()
        LauncherSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] }
                ?: defaults.themeMode,
            accent = prefs[accentKey],
            columns = (prefs[columnsKey] ?: defaults.columns)
                .coerceIn(LauncherSettings.COLUMN_CHOICES),
            rows = (prefs[rowsKey] ?: defaults.rows).coerceIn(LauncherSettings.ROW_CHOICES),
            dockSlots = (prefs[dockSlotsKey] ?: defaults.dockSlots)
                .coerceIn(LauncherSettings.DOCK_SLOT_CHOICES),
            drawerLayout = DrawerLayout.entries.firstOrNull { it.name == prefs[drawerLayoutKey] }
                ?: defaults.drawerLayout,
            iconPack = prefs[iconPackKey],
            gestures = prefs[gesturesKey] ?: defaults.gestures,
        )
    }

    suspend fun save(settings: LauncherSettings) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = settings.themeMode.name
            if (settings.accent != null) prefs[accentKey] = settings.accent else prefs.remove(accentKey)
            prefs[columnsKey] = settings.columns
            prefs[rowsKey] = settings.rows
            prefs[dockSlotsKey] = settings.dockSlots
            prefs[drawerLayoutKey] = settings.drawerLayout.name
            prefs[gesturesKey] = settings.gestures
            if (settings.iconPack != null) prefs[iconPackKey] = settings.iconPack else prefs.remove(iconPackKey)
        }
    }
}
