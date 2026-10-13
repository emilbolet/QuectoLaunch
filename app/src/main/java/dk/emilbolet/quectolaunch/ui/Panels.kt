package dk.emilbolet.quectolaunch.ui

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.data.Panel

/**
 * How solid each of the launcher's panels is, from 0 (only the wallpaper) to 1 (as the theme
 * draws it); a panel that isn't in the map is solid. Each has its own "Transparency" slider
 * in settings, which shows 1 minus this. Provided at the root.
 */
val LocalPanelOpacity = compositionLocalOf<Map<Panel, Float>> { emptyMap() }

/** The wallpaper's main color, to judge what text on a see-through panel sits on. */
val LocalWallpaperColor = compositionLocalOf { Color.Black }

@Composable
fun panelOpacity(panel: Panel): Float = LocalPanelOpacity.current[panel] ?: 1f

/** A panel's background color with its transparency setting applied. */
@Composable
fun panelColor(panel: Panel, color: Color): Color = color.copy(alpha = color.alpha * panelOpacity(panel))

/**
 * Whether a panel is see-through enough, over a dark enough wallpaper, that the theme's text
 * color can't be read on it. Text then goes white with a shadow, as on the home screen.
 * Mixes the wallpaper's main color with the theme's surface color the way the panel does.
 */
@Composable
fun panelNeedsLightText(panel: Panel): Boolean {
    val opacity = panelOpacity(panel)
    if (opacity >= 1f) return false
    var behind = LocalWallpaperColor.current
    // An open folder dims everything behind it
    if (panel == Panel.Folder) behind = lerp(behind, Color.Black, FOLDER_SCRIM)
    return lerp(behind, MaterialTheme.colorScheme.surface, opacity).luminance() < LIGHT_TEXT_BELOW
}

/** The color for text and icons on a panel. */
@Composable
fun panelContentColor(panel: Panel): Color =
    if (panelNeedsLightText(panel)) Color.White else MaterialTheme.colorScheme.onSurface

/** Adds the home screen's text shadow when the text is white because of the wallpaper. */
@Composable
fun TextStyle.onPanel(panel: Panel): TextStyle =
    if (panelNeedsLightText(panel)) copy(shadow = TextShadow) else this

/**
 * The wallpaper's main color, followed as the wallpaper changes. Needs no permission.
 * Without a known color (Android 8.0) a dark wallpaper is assumed.
 */
@Composable
fun rememberWallpaperColor(): Color {
    val context = LocalContext.current
    var wallpaper by remember { mutableStateOf(Color.Black) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        DisposableEffect(context) {
            val manager = WallpaperManager.getInstance(context)
            fun read() {
                manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let {
                    wallpaper = Color(it.primaryColor.toArgb())
                }
            }
            val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) read()
            }
            read()
            manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            onDispose { manager.removeOnColorsChangedListener(listener) }
        }
    }
    return wallpaper
}

/** How dark the scrim behind an open folder is. */
const val FOLDER_SCRIM = 0.45f

/** Relative luminance under which white text reads better than dark text. */
private const val LIGHT_TEXT_BELOW = 0.4f

/**
 * A menu is never less than this solid: it opens on top of icons and labels, which would
 * make its own text unreadable if they showed through any more.
 */
private const val MIN_MENU_OPACITY = 0.75f

/** A long-press menu. All menus go through here so they follow their transparency setting. */
@Composable
fun LauncherMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val opacity = MIN_MENU_OPACITY + (1f - MIN_MENU_OPACITY) * panelOpacity(Panel.Menu)
    val solid = opacity >= 1f
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = MenuDefaults.containerColor.copy(alpha = opacity),
        // A shadow would show through a see-through menu as a grey smudge
        shadowElevation = if (solid) MenuDefaults.ShadowElevation else 0.dp,
        tonalElevation = if (solid) MenuDefaults.TonalElevation else 0.dp,
        content = content,
    )
}
