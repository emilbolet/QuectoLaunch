package dk.emilbolet.quectolaunch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.BuiltInWidget
import dk.emilbolet.quectolaunch.data.DragSource
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.WidgetEntry
import dk.emilbolet.quectolaunch.data.WidgetHost
import kotlin.math.roundToInt

/**
 * The widgets of one home page, drawn over the grid of cells. Each widget sits at its
 * top-left cell and stretches over the cells it covers. Widgets that no longer fit in a
 * smaller grid are left out, like apps.
 */
@Composable
fun BoxScope.WidgetLayer(
    entries: Collection<HomeEntry>,
    columns: Int,
    rows: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    widgetHost: WidgetHost,
    dragState: DragState,
    resizingId: Long?,
    /** A tap on the built-in search bar. */
    onSearch: () -> Unit,
    onResizingChange: (Long?) -> Unit,
    onResize: (WidgetEntry, spanX: Int, spanY: Int) -> Unit,
    onRemove: (WidgetEntry) -> Unit,
) {
    val widgets = entries.filterIsInstance<WidgetEntry>()
        .filter { it.column + it.spanX <= columns && it.row + it.spanY <= rows }

    // While a widget is being resized, a tap anywhere else finishes it
    if (resizingId != null && widgets.any { it.id == resizingId }) {
        val noRipple = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null) { onResizingChange(null) }
        )
        BackHandler { onResizingChange(null) }
    }

    widgets.forEach { widget ->
        key(widget.id) {
            Box(
                Modifier
                    .offset(cellWidth * widget.column, cellHeight * widget.row)
                    .size(cellWidth * widget.spanX, cellHeight * widget.spanY)
            ) {
                WidgetCell(
                    widget = widget,
                    widgetHost = widgetHost,
                    dragState = dragState,
                    widthDp = (cellWidth * widget.spanX - WidgetPadding * 2).value,
                    heightDp = (cellHeight * widget.spanY - WidgetPadding * 2).value,
                    onSearch = onSearch,
                    onStartResize = { onResizingChange(widget.id) },
                    onRemove = { onRemove(widget) },
                )
                if (widget.id == resizingId) {
                    ResizeFrame(
                        widget = widget,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        maxSpanX = columns - widget.column,
                        maxSpanY = rows - widget.row,
                        onResize = { spanX, spanY -> onResize(widget, spanX, spanY) },
                    )
                }
            }
        }
    }
}

private val WidgetPadding = 4.dp

/**
 * One live widget, either an Android one or one of the launcher's own. Long-press for Resize
 * and Remove; keep holding and move to drag it.
 */
@Composable
private fun WidgetCell(
    widget: WidgetEntry,
    widgetHost: WidgetHost,
    dragState: DragState,
    widthDp: Float,
    heightDp: Float,
    onSearch: () -> Unit,
    onStartResize: () -> Unit,
    onRemove: () -> Unit,
) {
    // The view outlives changes to the entry (move, resize), so it must call the latest one
    val current by rememberUpdatedState(widget)
    val beingDragged = dragState.dragging &&
        (dragState.source as? DragSource.Placed)?.entry?.id == widget.id

    Box(
        Modifier
            .fillMaxSize()
            .padding(WidgetPadding)
            .alpha(if (beingDragged) 0.35f else 1f)
    ) {
        val builtIn = widget.builtIn
        val appWidgetId = widget.appWidgetId
        val info = widget.info
        if (builtIn != null) {
            val haptics = LocalHapticFeedback.current
            BuiltInWidgetContent(
                widget = builtIn,
                modifier = Modifier
                    .fillMaxSize()
                    // Same long-press entry point as icons and Android widgets
                    .pointerInput(builtIn) {
                        detectTapGestures(
                            onTap = { if (builtIn == BuiltInWidget.Search) onSearch() },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragState.longPress(DragSource.Placed(current), current.id)
                            },
                        )
                    },
            )
        } else if (appWidgetId != null && info != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
                factory = { context ->
                    widgetHost.createView(context, appWidgetId, info).apply {
                        onLongPress = { dragState.longPress(DragSource.Placed(current), current.id) }
                    }
                },
                update = { it.reportSize(widthDp, heightDp) },
            )
        }

        LauncherMenu(
            expanded = dragState.menuKey == widget.id,
            onDismissRequest = dragState::closeMenu,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.resize)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_resize),
                        contentDescription = null,
                        modifier = Modifier.size(MenuIconSize),
                    )
                },
                onClick = {
                    dragState.closeMenu()
                    onStartResize()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove)) },
                leadingIcon = { MenuIcon(Icons.Outlined.Close) },
                onClick = {
                    dragState.closeMenu()
                    onRemove()
                },
            )
        }
    }
}

/**
 * Outline with a handle on the right and bottom edge. Dragging a handle resizes the widget
 * one cell at a time; the size only changes if the cells it would grow into are free.
 */
@Composable
private fun BoxScope.ResizeFrame(
    widget: WidgetEntry,
    cellWidth: Dp,
    cellHeight: Dp,
    maxSpanX: Int,
    maxSpanY: Int,
    onResize: (spanX: Int, spanY: Int) -> Unit,
) {
    val current by rememberUpdatedState(widget)
    val currentOnResize by rememberUpdatedState(onResize)
    val color = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .padding(WidgetPadding)
            .border(2.dp, color, RoundedCornerShape(20.dp))
    )

    @Composable
    fun Handle(alignment: Alignment, horizontal: Boolean) {
        Box(
            Modifier
                .align(alignment)
                .size(40.dp) // generous touch area around the visible dot
                // A wide widget's handle is at the screen edge, where Android would
                // otherwise take the swipe as its Back gesture
                .systemGestureExclusion()
                .pointerInput(horizontal, cellWidth, cellHeight, maxSpanX, maxSpanY) {
                    var startSpan = 1
                    var dragged = 0f
                    detectDragGestures(
                        onDragStart = {
                            startSpan = if (horizontal) current.spanX else current.spanY
                            dragged = 0f
                        },
                    ) { change, amount ->
                        change.consume()
                        dragged += if (horizontal) amount.x else amount.y
                        val cell = if (horizontal) cellWidth.toPx() else cellHeight.toPx()
                        val span = startSpan + (dragged / cell).roundToInt()
                        if (horizontal) {
                            val spanX = span.coerceIn(1, maxSpanX)
                            if (spanX != current.spanX) currentOnResize(spanX, current.spanY)
                        } else {
                            val spanY = span.coerceIn(1, maxSpanY)
                            if (spanY != current.spanY) currentOnResize(current.spanX, spanY)
                        }
                    }
                },
            contentAlignment = alignment,
        ) {
            // The dot sits on the outline, half outside the touch area
            Box(
                Modifier
                    .offset(x = if (horizontal) 5.dp else 0.dp, y = if (horizontal) 0.dp else 5.dp)
                    .size(18.dp)
                    .background(color, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
            )
        }
    }

    Handle(Alignment.CenterEnd, horizontal = true)
    Handle(Alignment.BottomCenter, horizontal = false)
}
