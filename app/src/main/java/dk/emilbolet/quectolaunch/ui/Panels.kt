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

/**
 * How solid the launcher's panels are, from 0 (only the wallpaper) to 1 (as the theme draws
 * them). The panels are the app list, the dock, the folder popup, the long-press menus and
 * the search bar widget; the "Transparency" setting is 1 minus this. Provided at the root.
 */
val LocalPanelOpacity = compositionLocalOf { 1f }

/** See [panelsSeeThrough]. Provided at the root from [panelsNeedLightText]. */
val LocalPanelLightText = compositionLocalOf { false }

/** A panel's background color with the transparency setting applied. */
@Composable
fun panelColor(color: Color): Color = color.copy(alpha = color.alpha * LocalPanelOpacity.current)

/**
 * Whether panels are see-through enough, over a dark enough wallpaper, that the theme's text
 * color can't be read on them. Text then goes white with a shadow, as on the home screen.
 */
val panelsSeeThrough: Boolean
    @Composable get() = LocalPanelLightText.current

/** The color for text and icons on a panel. */
@Composable
fun panelContentColor(): Color =
    if (panelsSeeThrough) Color.White else MaterialTheme.colorScheme.onSurface

/** Adds the home screen's text shadow when the text is white because of the wallpaper. */
@Composable
fun TextStyle.onPanel(): TextStyle = if (panelsSeeThrough) copy(shadow = TextShadow) else this

/**
 * Works out [panelsSeeThrough]: mixes the wallpaper's main color with the theme's surface
 * color the way a panel at [opacity] does, and asks for white text if the result is dark.
 * Follows wallpaper changes. Without a known wallpaper color (Android 8.0) it assumes a dark one.
 */
@Composable
fun panelsNeedLightText(opacity: Float): Boolean {
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
    if (opacity >= 1f) return false
    val behindText = lerp(wallpaper, MaterialTheme.colorScheme.surface, opacity)
    return behindText.luminance() < LIGHT_TEXT_BELOW
}

/** Relative luminance under which white text reads better than dark text. */
private const val LIGHT_TEXT_BELOW = 0.4f

/**
 * A menu is never less than this solid: it opens on top of icons and labels, which would
 * make its own text unreadable if they showed through any more.
 */
private const val MIN_MENU_OPACITY = 0.75f

/** A long-press menu. All menus go through here so they follow the transparency setting. */
@Composable
fun LauncherMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val opacity = MIN_MENU_OPACITY + (1f - MIN_MENU_OPACITY) * LocalPanelOpacity.current
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
