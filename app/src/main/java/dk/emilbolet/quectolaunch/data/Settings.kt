package dk.emilbolet.quectolaunch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

/** The parts of the launcher that can be made see-through, each with its own setting. */
enum class Panel { AppList, Dock, Folder, Menu, SearchBar }

/** How the app list is shown: icons in a grid under each letter, or one app per line. */
enum class DrawerLayout { Grid, List }

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Accent color as ARGB; null means the wallpaper's colors (Android 12+) or the default. */
    val accent: Int? = null,
    val columns: Int = 5,
    /** Rows of the home grid, including the ones the clock and search bar normally sit in. */
    val rows: Int = 7,
    /** Icons in each of the dock's rows. */
    val dockSlots: Int = 5,
    /**
     * How solid each panel is: 1 as the theme draws it, 0 only the wallpaper. A panel that
     * isn't in the map is solid. Shown in settings the other way round, as "Transparency".
     */
    val panelOpacity: Map<Panel, Float> = emptyMap(),
    /** App names under the dock's icons. */
    val dockLabels: Boolean = false,
    val drawerLayout: DrawerLayout = DrawerLayout.Grid,
    /** Package name of the chosen icon pack; null for the apps' own icons. */
    val iconPack: String? = null,
    /** Double-tap to lock and swipe down for notifications. Off unless the user asks for it. */
    val gestures: Boolean = false,
) {
    companion object {
        val COLUMN_CHOICES = 4..6
        val ROW_CHOICES = 4..9
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

private const val ICON_CHOICE_PREFIX = "icon_choice:"

/** The grid's rows before the clock and search bar moved onto it. */
private const val OLD_DEFAULT_ROWS = 5

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Launcher settings, kept in DataStore. */
class SettingsStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentKey = intPreferencesKey("accent")
    private val columnsKey = intPreferencesKey("columns")
    private val rowsKey = intPreferencesKey("rows")
    private val dockSlotsKey = intPreferencesKey("dock_slots")
    private val dockLabelsKey = booleanPreferencesKey("dock_labels")
    private fun panelOpacityKey(panel: Panel) = floatPreferencesKey("panel_opacity_" + panel.name)
    private val drawerLayoutKey = stringPreferencesKey("drawer_layout")
    private val iconPackKey = stringPreferencesKey("icon_pack")
    private val gesturesKey = booleanPreferencesKey("gestures")
    private val rowsForBuiltInsKey = booleanPreferencesKey("rows_for_built_ins")

    val settings: Flow<LauncherSettings> = flow {
        addRowsForBuiltIns()
        emitAll(savedSettings)
    }

    /**
     * One-time step when the clock and the search bar became widgets on the grid: the grid
     * takes over the space they had, as two more rows. See [HomeDao.placeBuiltIns].
     */
    private suspend fun addRowsForBuiltIns() {
        context.dataStore.edit { prefs ->
            if (prefs[rowsForBuiltInsKey] != true) {
                prefs[rowsKey] = ((prefs[rowsKey] ?: OLD_DEFAULT_ROWS) + 2)
                    .coerceIn(LauncherSettings.ROW_CHOICES)
                prefs[rowsForBuiltInsKey] = true
            }
        }
    }

    private val savedSettings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
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
            dockLabels = prefs[dockLabelsKey] ?: defaults.dockLabels,
            panelOpacity = Panel.entries.mapNotNull { panel ->
                prefs[panelOpacityKey(panel)]?.let { panel to it.coerceIn(0f, 1f) }
            }.toMap(),
            drawerLayout = DrawerLayout.entries.firstOrNull { it.name == prefs[drawerLayoutKey] }
                ?: defaults.drawerLayout,
            iconPack = prefs[iconPackKey],
            gestures = prefs[gesturesKey] ?: defaults.gestures,
        )
    }

    /**
     * Icons picked by hand, by [AppInfo.key]. Kept beside the settings, one entry per app,
     * as "pack/drawable".
     */
    val iconChoices: Flow<Map<String, IconChoice>> = context.dataStore.data.map { prefs ->
        buildMap {
            prefs.asMap().forEach { (key, value) ->
                if (!key.name.startsWith(ICON_CHOICE_PREFIX) || value !is String) return@forEach
                val pack = value.substringBefore('/')
                val drawable = value.substringAfter('/', "")
                if (drawable.isNotEmpty()) put(key.name.removePrefix(ICON_CHOICE_PREFIX), IconChoice(pack, drawable))
            }
        }
    }.distinctUntilChanged()

    /** Null goes back to the icon the app would have anyway. */
    suspend fun setIconChoice(appKey: String, choice: IconChoice?) {
        val key = stringPreferencesKey(ICON_CHOICE_PREFIX + appKey)
        context.dataStore.edit { prefs ->
            if (choice != null) prefs[key] = "${choice.pack}/${choice.drawable}" else prefs.remove(key)
        }
    }

    suspend fun save(settings: LauncherSettings) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = settings.themeMode.name
            if (settings.accent != null) prefs[accentKey] = settings.accent else prefs.remove(accentKey)
            prefs[columnsKey] = settings.columns
            prefs[rowsKey] = settings.rows
            prefs[dockSlotsKey] = settings.dockSlots
            prefs[dockLabelsKey] = settings.dockLabels
            Panel.entries.forEach { panel ->
                val opacity = settings.panelOpacity[panel]
                if (opacity != null) prefs[panelOpacityKey(panel)] = opacity else prefs.remove(panelOpacityKey(panel))
            }
            prefs[drawerLayoutKey] = settings.drawerLayout.name
            prefs[gesturesKey] = settings.gestures
            if (settings.iconPack != null) prefs[iconPackKey] = settings.iconPack else prefs.remove(iconPackKey)
        }
    }
}
