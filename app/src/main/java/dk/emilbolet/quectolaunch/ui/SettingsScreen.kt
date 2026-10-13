package dk.emilbolet.quectolaunch.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.DrawerLayout
import dk.emilbolet.quectolaunch.data.IconPackInfo
import dk.emilbolet.quectolaunch.data.LauncherSettings
import dk.emilbolet.quectolaunch.data.SystemControls
import dk.emilbolet.quectolaunch.data.ThemeMode
import dk.emilbolet.quectolaunch.ui.theme.wallpaperColorsAvailable

/** Theme, accent color, icon pack, grid size, app list layout and gestures. Opened by long-pressing empty space on the home screen. */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    iconPacks: List<IconPackInfo>,
    controls: SystemControls,
    onChange: (LauncherSettings) -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.launcher_settings),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

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
                    LauncherSettings.ACCENTS.forEachIndexed { index, accent ->
                        Swatch(
                            color = Color(accent),
                            name = stringResource(R.string.accent_number, index + 1),
                            selected = accent == current,
                            onClick = { onChange(settings.copy(accent = accent)) },
                        )
                    }
                }
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

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
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
