package dk.emilbolet.quectolaunch.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.BuiltInWidget
import dk.emilbolet.quectolaunch.data.WidgetChoice
import dk.emilbolet.quectolaunch.data.WidgetGroup
import dk.emilbolet.quectolaunch.data.WidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher's own widgets (clock, search bar), then every installed widget, grouped by app. Tapping one adds it to the home page you came
 * from (or the next page with room). Opened from the long-press menu on empty home space.
 */
@Composable
fun WidgetPicker(
    groups: List<WidgetGroup>,
    widgetHost: WidgetHost,
    /** Size in cells the widget would get, for the "4 × 2" hint. */
    spanOf: (AppWidgetProviderInfo) -> Pair<Int, Int>,
    /** Home grid columns: the launcher's own widgets start a full row wide. */
    columns: Int,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickBuiltIn: (BuiltInWidget) -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.systemBarsPadding()) {
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
                    text = stringResource(R.string.widgets),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "built-in", contentType = "header") { GroupHeader(stringResource(R.string.app_name)) }
                items(BuiltInWidget.entries, key = { "built-in:${it.key}" }, contentType = { "built-in" }) { widget ->
                    BuiltInRow(widget, columns) { onPickBuiltIn(widget) }
                }
                groups.forEach { group ->
                    item(key = "app:${group.appLabel}", contentType = "header") {
                        GroupHeader(group.appLabel)
                    }
                    items(
                        group.widgets,
                        key = { "${it.info.provider.flattenToString()}#${it.info.profile.hashCode()}" },
                        contentType = { "widget" },
                    ) { widget ->
                        WidgetRow(widget, widgetHost, spanOf(widget.info)) { onPick(widget.info) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** One of the launcher's own widgets, with the real thing in miniature as its preview. */
@Composable
private fun BuiltInRow(widget: BuiltInWidget, columns: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dark behind it, like a wallpaper: the clock is white
        BuiltInWidgetContent(
            widget = widget,
            modifier = Modifier
                .size(width = 112.dp, height = 72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2B3040))
                .padding(4.dp),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(text = builtInWidgetLabel(widget), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.widget_size, columns, 1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetRow(
    widget: WidgetChoice,
    widgetHost: WidgetHost,
    span: Pair<Int, Int>,
    onClick: () -> Unit,
) {
    val preview by produceState<ImageBitmap?>(null, widget.info.provider) {
        value = withContext(Dispatchers.IO) { widgetHost.preview(widget.info) }
    }

    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 112.dp, height = 72.dp), contentAlignment = Alignment.Center) {
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(text = widget.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.widget_size, span.first, span.second),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
