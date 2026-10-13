package dk.emilbolet.quectolaunch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import dk.emilbolet.quectolaunch.data.LauncherSettings
import dk.emilbolet.quectolaunch.data.ThemeMode

/** True when the device can take its colors from the wallpaper (Android 12+). */
val wallpaperColorsAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Light or dark as chosen in settings, colored from the wallpaper (Android 12+) unless
 * the user picked an accent color.
 */
@Composable
fun LauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val accent = settings.accent
    val colors = when {
        accent == null && wallpaperColorsAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(accent, dark) {
            accentColorScheme(accent ?: LauncherSettings.ACCENTS.first(), dark)
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Whether the current theme is a dark one, whatever the system setting says. */
val ColorScheme.isDark: Boolean
    get() = surface.luminance() < 0.5f

/**
 * A color scheme built around one accent color. Every color keeps the accent's hue;
 * only how vivid and how light it is changes (a simple stand-in for Material's tonal palettes).
 */
private fun accentColorScheme(accent: Int, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent, hsl)
    val hue = hsl[0]
    val saturation = hsl[1]

    fun tone(lightness: Float, vividness: Float = 1f): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation * vividness, lightness)))

    fun accentTone(lightness: Float) = tone(lightness)
    fun mutedTone(lightness: Float) = tone(lightness, 0.45f)
    fun neutralTone(lightness: Float) = tone(lightness, 0.12f)

    return if (dark) {
        darkColorScheme(
            primary = accentTone(0.78f),
            onPrimary = accentTone(0.18f),
            primaryContainer = accentTone(0.30f),
            onPrimaryContainer = accentTone(0.90f),
            inversePrimary = accentTone(0.40f),
            secondary = mutedTone(0.78f),
            onSecondary = mutedTone(0.18f),
            secondaryContainer = mutedTone(0.28f),
            onSecondaryContainer = mutedTone(0.90f),
            tertiary = mutedTone(0.78f),
            onTertiary = mutedTone(0.18f),
            tertiaryContainer = mutedTone(0.28f),
            onTertiaryContainer = mutedTone(0.90f),
            background = neutralTone(0.07f),
            onBackground = neutralTone(0.90f),
            surface = neutralTone(0.07f),
            onSurface = neutralTone(0.90f),
            surfaceVariant = neutralTone(0.28f),
            onSurfaceVariant = neutralTone(0.78f),
            surfaceTint = accentTone(0.78f),
            inverseSurface = neutralTone(0.90f),
            inverseOnSurface = neutralTone(0.18f),
            outline = neutralTone(0.58f),
            outlineVariant = neutralTone(0.28f),
            surfaceBright = neutralTone(0.24f),
            surfaceDim = neutralTone(0.07f),
            surfaceContainerLowest = neutralTone(0.04f),
            surfaceContainerLow = neutralTone(0.10f),
            surfaceContainer = neutralTone(0.13f),
            surfaceContainerHigh = neutralTone(0.17f),
            surfaceContainerHighest = neutralTone(0.22f),
        )
    } else {
        lightColorScheme(
            primary = accentTone(0.40f),
            onPrimary = Color.White,
            primaryContainer = accentTone(0.88f),
            onPrimaryContainer = accentTone(0.14f),
            inversePrimary = accentTone(0.78f),
            secondary = mutedTone(0.40f),
            onSecondary = Color.White,
            secondaryContainer = mutedTone(0.88f),
            onSecondaryContainer = mutedTone(0.14f),
            tertiary = mutedTone(0.40f),
            onTertiary = Color.White,
            tertiaryContainer = mutedTone(0.88f),
            onTertiaryContainer = mutedTone(0.14f),
            background = neutralTone(0.98f),
            onBackground = neutralTone(0.10f),
            surface = neutralTone(0.98f),
            onSurface = neutralTone(0.10f),
            surfaceVariant = neutralTone(0.90f),
            onSurfaceVariant = neutralTone(0.30f),
            surfaceTint = accentTone(0.40f),
            inverseSurface = neutralTone(0.18f),
            inverseOnSurface = neutralTone(0.95f),
            outline = neutralTone(0.50f),
            outlineVariant = neutralTone(0.80f),
            surfaceBright = neutralTone(0.98f),
            surfaceDim = neutralTone(0.86f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutralTone(0.96f),
            surfaceContainer = neutralTone(0.94f),
            surfaceContainerHigh = neutralTone(0.92f),
            surfaceContainerHighest = neutralTone(0.89f),
        )
    }
}
