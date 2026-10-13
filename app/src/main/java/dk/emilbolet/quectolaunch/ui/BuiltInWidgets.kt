package dk.emilbolet.quectolaunch.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.BuiltInWidget
import kotlinx.coroutines.delay
import java.util.Date

/**
 * The launcher's own widgets. They are placed, moved, resized and removed like Android
 * widgets (see WidgetLayer) but are plain composables, so they need no widget host.
 */
@Composable
fun BuiltInWidgetContent(widget: BuiltInWidget, modifier: Modifier = Modifier) {
    when (widget) {
        BuiltInWidget.Clock -> ClockWidget(modifier)
        BuiltInWidget.Search -> SearchWidget(modifier)
    }
}

@Composable
fun builtInWidgetLabel(widget: BuiltInWidget): String = stringResource(
    when (widget) {
        BuiltInWidget.Clock -> R.string.widget_clock
        BuiltInWidget.Search -> R.string.widget_search
    }
)

/** Time and date in white over the wallpaper; the text follows the size of the widget. */
@Composable
private fun ClockWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick once a minute while visible; restarts on resume so it's right after the screen wakes
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000)
            }
        }
    }

    // Respects the phone's 12/24-hour setting and language
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) {
        DateUtils.formatDateTime(
            context,
            now,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
        )
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // As big as the widget allows, up to the size the clock always had
        val timeSize = with(LocalDensity.current) {
            minOf(maxHeight * 0.55f, maxWidth * 0.22f, 60.dp).toSp()
        }
        // Too low for two lines: the time alone
        val showDate = maxHeight >= 72.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = timeSize,
                    lineHeight = timeSize * 1.1f,
                    shadow = TextShadow,
                ),
            )
            if (showDate) {
                Text(
                    text = date,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(shadow = TextShadow),
                )
            }
        }
    }
}

/** Looks like a search field; a tap opens the app list with the keyboard up (see WidgetLayer). */
@Composable
private fun SearchWidget(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val height = minOf(48.dp, maxHeight)
        Row(
            Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.search_apps),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
