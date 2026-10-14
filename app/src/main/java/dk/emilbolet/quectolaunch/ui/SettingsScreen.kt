package dk.emilbolet.quectolaunch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.DrawerLayout
import dk.emilbolet.quectolaunch.data.IconPackInfo
import dk.emilbolet.quectolaunch.data.LauncherSettings
import dk.emilbolet.quectolaunch.data.Panel
import dk.emilbolet.quectolaunch.data.SystemControls
import dk.emilbolet.quectolaunch.data.ThemeMode
import dk.emilbolet.quectolaunch.ui.theme.wallpaperColorsAvailable
import kotlin.math.roundToInt

/** Theme, accent color, transparency (a page of its own), icon pack, grid size, app list layout and gestures. Opened by long-pressing empty space on the home screen. */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    iconPacks: List<IconPackInfo>,
    controls: SystemControls,
    onChange: (LauncherSettings) -> Unit,
    onClose: () -> Unit,
) {
    // The transparency sliders are a page of their own; Back returns from it to the main page
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }
    // Kept out here so the main page is still scrolled to where it was on the way back
    val mainScroll = rememberScrollState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings page",
        ) { shown ->
            when (shown) {
                SettingsPage.Main -> MainSettings(
                    settings = settings,
                    iconPacks = iconPacks,
                    controls = controls,
                    scrollState = mainScroll,
                    onChange = onChange,
                    onOpenTransparency = { page = SettingsPage.Transparency },
                    onClose = onClose,
                )
                SettingsPage.Transparency -> TransparencySettings(
                    settings = settings,
                    onChange = onChange,
                    onBack = { page = SettingsPage.Main },
                )
            }
        }
    }
}

private enum class SettingsPage { Main, Transparency }

@Composable
private fun MainSettings(
    settings: LauncherSettings,
    iconPacks: List<IconPackInfo>,
    controls: SystemControls,
    scrollState: ScrollState,
    onChange: (LauncherSettings) -> Unit,
    onOpenTransparency: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .systemBarsPadding()
            .verticalScroll(scrollState)
    ) {
        SettingsHeader(stringResource(R.string.launcher_settings), onBack = onClose)

        Section(stringResource(R.string.theme)) {
            Choices(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = {
                    stringResource(
                        when (it) {
                            ThemeMode.System -> R.string.theme_system
                            ThemeMode.Light -> R.string.theme_light
                            ThemeMode.Dark -> R.string.theme_dark
                        }
                    )
                },
                onSelect = { onChange(settings.copy(themeMode = it)) },
            )
        }

        Section(stringResource(R.string.accent_color)) {
            // Sliders for a color of one's own show once the rainbow swatch is tapped,
            // and from the start if such a color is already in use
            var customOpen by remember {
                mutableStateOf(settings.accent != null && settings.accent !in LauncherSettings.ACCENTS)
            }
            if (wallpaperColorsAvailable) {
                val useWallpaper = settings.accent == null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = useWallpaper,
                            role = Role.Switch,
                            onValueChange = { on ->
                                val accent = if (on) null else LauncherSettings.ACCENTS.first()
                                customOpen = false
                                onChange(settings.copy(accent = accent))
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.use_wallpaper_colors),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = useWallpaper, onCheckedChange = null)
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Before Android 12 there are no wallpaper colors, so the first one is the default
                val current = settings.accent
                    ?: LauncherSettings.ACCENTS.first().takeUnless { wallpaperColorsAvailable }
                // First, so it isn't scrolled out of sight behind the presets
                Swatch(
                    brush = Brush.sweepGradient(HueColors),
                    name = stringResource(R.string.accent_custom),
                    selected = customOpen,
                    onClick = {
                        customOpen = true
                        // Starts from the color in use; leaves "wallpaper colors" if that was on
                        if (settings.accent == null) {
                            onChange(settings.copy(accent = LauncherSettings.ACCENTS.first()))
                        }
                    },
                )
                LauncherSettings.ACCENTS.forEachIndexed { index, accent ->
                    Swatch(
                        color = Color(accent),
                        name = stringResource(R.string.accent_number, index + 1),
                        selected = accent == current && !customOpen,
                        onClick = {
                            customOpen = false
                            onChange(settings.copy(accent = accent))
                        },
                    )
                }
            }
            val custom = settings.accent
            if (customOpen && custom != null) {
                AccentSliders(color = custom, onChange = { onChange(settings.copy(accent = it)) })
            }
        }

        Section(stringResource(R.string.transparency)) {
            NavigationRow(label = transparencySummary(settings), onClick = onOpenTransparency)
        }

        Section(stringResource(R.string.icon_pack)) {
            // A pack that was chosen and then uninstalled counts as "default"
            val chosen = settings.iconPack?.takeIf { name -> iconPacks.any { it.packageName == name } }
            RadioRow(
                label = stringResource(R.string.icon_pack_default),
                selected = chosen == null,
                onClick = { onChange(settings.copy(iconPack = null)) },
            )
            iconPacks.forEach { pack ->
                RadioRow(
                    label = pack.label,
                    selected = chosen == pack.packageName,
                    onClick = { onChange(settings.copy(iconPack = pack.packageName)) },
                )
            }
            if (iconPacks.isEmpty()) {
                Text(
                    text = stringResource(R.string.icon_pack_none),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section(stringResource(R.string.grid_columns)) {
            Choices(
                options = LauncherSettings.COLUMN_CHOICES.toList(),
                selected = settings.columns,
                label = { it.toString() },
                onSelect = { onChange(settings.copy(columns = it)) },
            )
        }

        Section(stringResource(R.string.grid_rows)) {
            Choices(
                options = LauncherSettings.ROW_CHOICES.toList(),
                selected = settings.rows,
                label = { it.toString() },
                onSelect = { onChange(settings.copy(rows = it)) },
            )
        }

        Section(stringResource(R.string.dock_slots)) {
            Choices(
                options = LauncherSettings.DOCK_SLOT_CHOICES.toList(),
                selected = settings.dockSlots,
                label = { it.toString() },
                onSelect = { onChange(settings.copy(dockSlots = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.dock_labels),
                checked = settings.dockLabels,
                onCheckedChange = { onChange(settings.copy(dockLabels = it)) },
            )
            Text(
                text = stringResource(R.string.grid_size_note),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(stringResource(R.string.drawer_layout)) {
            Choices(
                options = DrawerLayout.entries,
                selected = settings.drawerLayout,
                label = {
                    stringResource(
                        when (it) {
                            DrawerLayout.Grid -> R.string.drawer_layout_grid
                            DrawerLayout.List -> R.string.drawer_layout_list
                        }
                    )
                },
                onSelect = { onChange(settings.copy(drawerLayout = it)) },
            )
            Text(
                text = stringResource(R.string.drawer_layout_note),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(stringResource(R.string.gestures)) {
            GestureSettings(
                enabled = settings.gestures,
                controls = controls,
                onEnabledChange = { onChange(settings.copy(gestures = it)) },
            )
        }
    }
}

/** Settings → Transparency: one slider per panel. */
@Composable
private fun TransparencySettings(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHeader(stringResource(R.string.transparency), onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Panel.entries.forEach { panel ->
                TransparencySlider(
                    label = panelLabel(panel),
                    opacity = settings.panelOpacity[panel] ?: 1f,
                    onChange = { onChange(settings.copy(panelOpacity = settings.panelOpacity + (panel to it))) },
                )
            }
            Text(
                text = stringResource(R.string.transparency_note),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the main page shows for transparency: "All solid", or each see-through panel with its value. */
@Composable
private fun transparencySummary(settings: LauncherSettings): String {
    val seeThrough = Panel.entries.mapNotNull { panel ->
        val transparency = ((1f - (settings.panelOpacity[panel] ?: 1f)) * 100).roundToInt()
        if (transparency == 0) null
        else panelLabel(panel) + " " + stringResource(R.string.percent, transparency)
    }
    return if (seeThrough.isEmpty()) stringResource(R.string.transparency_none)
    else seeThrough.joinToString(", ")
}

@Composable
private fun panelLabel(panel: Panel) = stringResource(
    when (panel) {
        Panel.AppList -> R.string.transparency_app_list
        Panel.Dock -> R.string.transparency_dock
        Panel.Folder -> R.string.transparency_folders
        Panel.Menu -> R.string.transparency_menus
        Panel.SearchBar -> R.string.transparency_search_bar
    }
)

/** A page's title with a back arrow in front. */
@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** A row that opens a page of its own: what is set there, and an arrow. */
@Composable
private fun NavigationRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The gestures switch. Gestures need an accessibility service, so before the user is sent
 * to system settings to turn it on, a dialog explains exactly what it is used for
 * (required by Google Play, and only fair).
 */
@Composable
private fun GestureSettings(
    enabled: Boolean,
    controls: SystemControls,
    onEnabledChange: (Boolean) -> Unit,
) {
    var showDisclosure by remember { mutableStateOf(false) }
    val hasAccess = controls.hasGestureAccess

    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { on ->
                    when {
                        !on -> onEnabledChange(false)
                        hasAccess -> onEnabledChange(true)
                        else -> showDisclosure = true
                    }
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.gestures_switch),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    if (controls.canLockScreen) R.string.gestures_summary
                    else R.string.gestures_summary_no_lock
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }

    // Switched on, but the service was never enabled or has been turned off again
    if (enabled && !hasAccess) {
        Text(
            text = stringResource(R.string.gestures_access_off),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDisclosure = true }
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.gestures_disclosure_title)) },
            text = { Text(stringResource(R.string.gestures_disclosure_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisclosure = false
                        onEnabledChange(true)
                        controls.openAccessibilitySettings()
                    },
                ) {
                    Text(stringResource(R.string.gestures_disclosure_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.gestures_disclosure_cancel))
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** A row of buttons where exactly one is selected. */
@Composable
private fun <T> Choices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(option))
            }
        }
    }
}

/** One panel's transparency: its name, a slider in steps of 5 % and the value. */
@Composable
private fun TransparencySlider(label: String, opacity: Float, onChange: (opacity: Float) -> Unit) {
    // Kept here while dragging; the saved value follows
    var transparency by remember { mutableFloatStateOf(1f - opacity) }
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = transparency,
            onValueChange = {
                transparency = it
                onChange(1f - it)
            },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            steps = 19,
        )
        Text(
            text = stringResource(R.string.percent, (transparency * 100).roundToInt()),
            modifier = Modifier
                .padding(start = 12.dp)
                .width(52.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
    }
}

/** Red through the rainbow and back to red, for the hue slider and the "custom" swatch. */
private val HueColors = (0..360 step 30).map { Color.hsl(it.toFloat(), 0.85f, 0.5f) }

/**
 * A color of one's own: its hue and how vivid it is. Lightness isn't offered because the
 * theme sets that itself for each surface (see accentColorScheme).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccentSliders(color: Int, onChange: (Int) -> Unit) {
    val hsl = remember(color) { FloatArray(3).also { ColorUtils.colorToHSL(color, it) } }
    // Kept here while dragging: converting to RGB and back would make the sliders jitter
    var hue by remember { mutableFloatStateOf(hsl[0]) }
    var vividness by remember { mutableFloatStateOf(hsl[1].coerceIn(MIN_VIVIDNESS, 1f)) }

    fun publish() = onChange(ColorUtils.HSLToColor(floatArrayOf(hue, vividness, 0.5f)))

    val hueLabel = stringResource(R.string.accent_hue)
    Text(
        text = hueLabel,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = hue,
        onValueChange = {
            hue = it
            publish()
        },
        modifier = Modifier.semantics { contentDescription = hueLabel },
        valueRange = 0f..359f,
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(HueColors))
            )
        },
    )

    val vividnessLabel = stringResource(R.string.accent_vividness)
    Text(text = vividnessLabel, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = vividness,
        onValueChange = {
            vividness = it
            publish()
        },
        modifier = Modifier.semantics { contentDescription = vividnessLabel },
        valueRange = MIN_VIVIDNESS..1f,
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.hsl(hue, MIN_VIVIDNESS, 0.5f), Color.hsl(hue, 1f, 0.5f))
                        )
                    )
            )
        },
    )
}

/** Below this a color is nearly grey and every hue looks the same. */
private const val MIN_VIVIDNESS = 0.15f

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) =
    Swatch(SolidColor(color), name, selected, onClick)

@Composable
private fun Swatch(brush: Brush, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(brush)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
    }
}
