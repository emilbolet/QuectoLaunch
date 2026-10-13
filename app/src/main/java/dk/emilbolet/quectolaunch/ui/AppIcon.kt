package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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

/**
 * Long-press menu for an app: its shortcuts (only while we're the default launcher),
 * then Remove (for apps placed on the home screen), App info and Uninstall.
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
                onClick = {
                    onDismiss()
                    onRemove()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.app_info)) },
            onClick = {
                onDismiss()
                repository.openAppInfo(app)
            },
        )
        if (!app.isSystemApp && !app.isWorkProfile) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uninstall)) },
                onClick = {
                    onDismiss()
                    repository.uninstall(app)
                },
            )
        }
    }
}
