@file:OptIn(ExperimentalFoundationApi::class)

package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.data.DOCK_ROWS
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.HomeItem
import dk.emilbolet.quectolaunch.data.cellPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DockValue { Collapsed, Expanded }

typealias DockState = AnchoredDraggableState<DockValue>

/** Height of one row of dock apps; also the height of the collapsed dock. */
val DockRowHeight = 80.dp

/** How long a dragged icon has to hover over the collapsed dock before it opens. */
private const val HOVER_TO_EXPAND_MS = 500L

/** The dock's position is how many pixels are revealed below its first row. */
@Composable
fun rememberDockState(): DockState {
    val hiddenPx = with(LocalDensity.current) {
        (DockRowHeight * (DOCK_ROWS - 1)).toPx()
    }
    return remember(hiddenPx) {
        AnchoredDraggableState(
            initialValue = DockValue.Collapsed,
            anchors = DraggableAnchors {
                DockValue.Collapsed at 0f
                DockValue.Expanded at hiddenPx
            },
        )
    }
}

/** 0 when collapsed, 1 when fully expanded. */
val DockState.openFraction: Float
    get() {
        val hidden = anchors.positionOf(DockValue.Expanded)
        return if (offset.isNaN() || hidden <= 0f) 0f else (offset / hidden).coerceIn(0f, 1f)
    }

/**
 * The swipe-up dock. Collapsed it shows one row of [slots] apps; swiping up on it reveals a second
 * row. It grows upwards, so the first row stays on top.
 */
@Composable
fun Dock(
    state: DockState,
    entries: Map<Int, HomeEntry>,
    slots: Int,
    dragState: DragState,
    modifier: Modifier = Modifier,
    cell: @Composable (HomeEntry?) -> Unit,
) {
    // Holding a dragged icon over the closed dock opens it, so the second row can be reached
    val hovered = dragState.target?.container == HomeItem.DOCK
    val scope = rememberCoroutineScope()
    LaunchedEffect(hovered) {
        if (hovered && state.currentValue == DockValue.Collapsed) {
            delay(HOVER_TO_EXPAND_MS)
            // In its own job: the finger moving on mustn't stop the dock half open
            scope.launch { state.animateTo(DockValue.Expanded) }
        }
    }

    val collapsedColor = Color.White.copy(alpha = 0.2f)
    val expandedColor = MaterialTheme.colorScheme.surfaceContainer
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { dragState.dockBounds = it.boundsInRoot() }
            .anchoredDraggable(state, reverseDirection = true, orientation = Orientation.Vertical)
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                val progress = state.openFraction
                // Solid once open, so the search bar underneath doesn't show through
                drawRect(lerp(collapsedColor, expandedColor, progress))
                // Small grab handle that fades in as the dock opens
                val handle = Size(32.dp.toPx(), 4.dp.toPx())
                drawRoundRect(
                    color = handleColor.copy(alpha = 0.5f * progress),
                    topLeft = Offset((size.width - handle.width) / 2, 6.dp.toPx()),
                    size = handle,
                    cornerRadius = CornerRadius(handle.height / 2),
                )
            }
            // Lay out everything, but only take up (and show) the revealed part
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                val revealed = if (state.offset.isNaN()) 0f else state.offset
                val height = (DockRowHeight.toPx() + revealed).roundToInt()
                    .coerceIn(0, placeable.height)
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
    ) {
        repeat(DOCK_ROWS) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DockRowHeight)
                    .onGloballyPositioned { dragState.dockRowBounds[row] = it.boundsInRoot() }
            ) {
                repeat(slots) { slot ->
                    val position = cellPosition(row, slot)
                    DropCell(dragState, HomeItem.DOCK, position, Modifier.weight(1f)) {
                        cell(entries[position])
                    }
                }
            }
        }
    }
}
