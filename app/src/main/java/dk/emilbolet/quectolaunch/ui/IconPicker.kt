package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppInfo
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.IconChoice
import dk.emilbolet.quectolaunch.data.IconPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Choosing an icon for one app by hand, from any installed icon pack. Opened with
 * "Change icon" in an app's long-press menu. Icons whose name resembles the app's come first.
 */
@Composable
fun IconPicker(
    app: AppInfo,
    packs: List<IconPackInfo>,
    /** The pack in use for all icons, if any; it is the one shown first. */
    currentPack: String?,
    repository: AppRepository,
    onPick: (IconChoice?) -> Unit,
    onClose: () -> Unit,
) {
    var chosenPack by remember { mutableStateOf<String?>(null) }
    val pack = (chosenPack ?: currentPack)?.takeIf { name -> packs.any { it.packageName == name } }
        ?: packs.firstOrNull()?.packageName
    var search by remember { mutableStateOf("") }

    // Null while the pack is being read
    val names by produceState<List<String>?>(null, pack) {
        value = null
        value = if (pack == null) emptyList() else withContext(Dispatchers.IO) { repository.packIconNames(pack) }
    }
    val shown = remember(names, search, app.key) { names?.let { arrange(it, search, app) } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .systemBarsPadding()
                .imePadding()
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
                AppIcon(app, repository, size = 32.dp, modifier = Modifier.padding(start = 4.dp))
                Text(
                    text = stringResource(R.string.change_icon_title, app.label),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(onClick = { onPick(null) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.change_icon_reset))
            }

            if (packs.size > 1) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    packs.forEach { info ->
                        FilterChip(
                            selected = info.packageName == pack,
                            onClick = { chosenPack = info.packageName },
                            label = { Text(info.label) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.change_icon_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            when {
                shown == null -> Unit
                shown.isEmpty() && search.isBlank() -> Text(
                    text = stringResource(R.string.change_icon_none),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pack != null -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(72.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                ) {
                    items(shown, key = { it }) { name ->
                        PackIcon(pack, name, repository) { onPick(IconChoice(pack, name)) }
                    }
                }
            }
        }
    }
}

/** The icons matching [search]; without a search, the ones named like the app go first. */
private fun arrange(names: List<String>, search: String, app: AppInfo): List<String> {
    val query = search.trim().lowercase().replace(' ', '_')
    if (query.isNotEmpty()) return names.filter { it.lowercase().contains(query) }

    // "Google Maps" from com.google.android.apps.maps: "google", "maps"
    val words = (app.label.lowercase().split(' ', '-', '.', '_') + app.packageName.substringAfterLast('.'))
        .filter { it.length >= 3 }
        .toSet()
    if (words.isEmpty()) return names
    val (likely, rest) = names.partition { name -> words.any { name.lowercase().contains(it) } }
    return likely + rest
}

@Composable
private fun PackIcon(pack: String, name: String, repository: AppRepository, onClick: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, pack, name) {
        value = withContext(Dispatchers.IO) { repository.loadPackIcon(pack, name) }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            // Packs name their drawables after the app, which is the best description there is
            .semantics { contentDescription = name.replace('_', ' ') },
        contentAlignment = Alignment.Center,
    ) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
    }
}
