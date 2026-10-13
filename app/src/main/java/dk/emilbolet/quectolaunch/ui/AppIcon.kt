package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppEntry
import dk.emilbolet.quectolaunch.data.AppInfo
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.AppShortcut
import dk.emilbolet.quectolaunch.data.FolderEntry
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.WidgetEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads the icon in the background the first time, then serves it from the cache. */
@Composable
fun AppIcon(app: AppInfo, repository: AppRepository, size: Dp, modifier: Modifier = Modifier) {
    // iconVersion is a key so that switching icon pack reloads icons already on screen
    val icon by produceState(repository.cachedIcon(app), app.key, repository.iconVersion) {
        value = repository.cachedIcon(app)
            ?: withContext(Dispatchers.IO) { repository.loadIcon(app) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Spacer(modifier.size(size))
    }
}

/** An app's icon, or for a folder a rounded tile with its first four apps. */
@Composable
fun EntryIcon(entry: HomeEntry, repository: AppRepository, size: Dp, modifier: Modifier = Modifier) {
    when (entry) {
        is AppEntry -> AppIcon(entry.app, repository, size, modifier)
        is FolderEntry -> Column(
            modifier
                .size(size)
                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(size * 0.28f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            entry.apps.take(4).chunked(2).forEach { pair ->
                Row(Modifier.size(width = size * 0.76f, height = size * 0.38f)) {
                    pair.forEach { AppIcon(it.app, repository, size * 0.38f) }
                }
            }
        }
        // Stand-in for a widget while it is dragged; the cells it will cover light up too
        is WidgetEntry -> Spacer(
            modifier
                .size(size)
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(size * 0.28f))
        )
    }
}

val MenuIconSize = 24.dp

/**
 * Opens the icon picker for an app. Provided at the root, so the app menu can offer
 * "Change icon" wherever it is shown without every screen passing the callback along.
 */
val LocalChangeIcon = staticCompositionLocalOf<(AppInfo) -> Unit> { {} }

/** The icon in front of a long-press menu item; the label next to it says what it does. */
@Composable
fun MenuIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(MenuIconSize))
}

/**
 * Long-press menu for an app: its shortcuts (only while we're the default launcher),
 * then Remove (for apps placed on the home screen), Change icon (if an icon pack is
 * installed), App info and Uninstall.
 */
@Composable
fun AppMenu(
    app: AppInfo,
    repository: AppRepository,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val shortcuts by produceState(emptyList<AppShortcut>(), app.key) {
            value = withContext(Dispatchers.IO) { repository.shortcuts(app) }
        }
        shortcuts.forEach { shortcut ->
            DropdownMenuItem(
                text = { Text(shortcut.label) },
                // The shortcut's own icon, in its own colors; the gap keeps the labels in line
                leadingIcon = {
                    val icon = shortcut.icon
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(MenuIconSize))
                    } else {
                        Spacer(Modifier.size(MenuIconSize))
                    }
                },
                onClick = {
                    onDismiss()
                    repository.startShortcut(shortcut)
                },
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()

        if (onRemove != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Close) },
                onClick = {
                    onDismiss()
                    onRemove()
                },
            )
        }
        if (repository.hasIconPacks) {
            val changeIcon = LocalChangeIcon.current
            DropdownMenuItem(
                text = { Text(stringResource(R.string.change_icon)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Edit) },
                onClick = {
                    onDismiss()
                    changeIcon(app)
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.app_info)) },
            leadingIcon = { MenuIcon(Icons.Outlined.Info) },
            onClick = {
                onDismiss()
                repository.openAppInfo(app)
            },
        )
        if (!app.isSystemApp && !app.isWorkProfile) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uninstall)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Delete) },
                onClick = {
                    onDismiss()
                    repository.uninstall(app)
                },
            )
        }
    }
}
