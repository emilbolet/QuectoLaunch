package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.DOCK_ROWS
import dk.emilbolet.quectolaunch.data.DragSource
import dk.emilbolet.quectolaunch.data.DropTarget
import dk.emilbolet.quectolaunch.data.HomeItem
import dk.emilbolet.quectolaunch.data.WidgetEntry
import dk.emilbolet.quectolaunch.data.cellPosition

/**
 * Long-press menus and drag-and-drop for the whole launcher.
 *
 * An icon reports a long press with [longPress], which opens its menu. If the finger then
 * moves, the menu closes and the icon is dragged. The finger is followed by [trackDrag] on
 * the root of the screen rather than by the icon itself, so the drag carries on when the
 * icon's own screen (the drawer, a folder) closes underneath it.
 */
@Stable
class DragState {
    /** The item under the finger since the long press; dragged once [dragging] is true. */
    var source by mutableStateOf<DragSource?>(null)
        private set

    var dragging by mutableStateOf(false)
        private set

    /** Finger position while dragging, in root coordinates. */
    var position by mutableStateOf(Offset.Zero)
        private set

    /** Identifies the icon whose long-press menu is open. */
    var menuKey by mutableStateOf<Any?>(null)
        private set

    /** Where the home grid and the dock are on screen, reported by the home screen. */
    var gridBounds by mutableStateOf(Rect.Zero)
    var gridColumns by mutableIntStateOf(1)
    var gridRows by mutableIntStateOf(1)
    var dockSlots by mutableIntStateOf(1)

    /** The home page currently showing; drops on the grid land there. */
    var gridPage by mutableIntStateOf(0)
    var dockBounds by mutableStateOf(Rect.Zero)

    /** The visible part of each row of dock apps; empty for rows hidden while the dock is collapsed. */
    val dockRowBounds = mutableStateListOf(*Array(DOCK_ROWS) { Rect.Zero })

    /** The open folder, reported by FolderPopup; null when no folder is open. */
    var folderId by mutableStateOf<Long?>(null)

    /** The open folder's card; a drag that leaves it closes the folder. */
    var folderBounds by mutableStateOf(Rect.Zero)

    /** All rows of apps in the open folder, including any scrolled out of view. */
    var folderGridBounds by mutableStateOf(Rect.Zero)
    var folderColumns by mutableIntStateOf(1)
    var folderCount by mutableIntStateOf(0)

    /** The cell the dragged item would land on if released now. */
    val target: DropTarget?
        get() = if (dragging) targetAt(position) else null

    /** The widget being held, if that is what the finger picked up. */
    val heldWidget: WidgetEntry?
        get() = (source as? DragSource.Placed)?.entry as? WidgetEntry

    private var pointer = Offset.Zero
    private var pressedAt = Offset.Zero

    fun longPress(source: DragSource, menuKey: Any) {
        this.source = source
        this.menuKey = menuKey
        pressedAt = pointer
        position = pointer
    }

    fun closeMenu() {
        menuKey = null
    }

    private fun targetAt(point: Offset): DropTarget? {
        // Over an open folder: the place in the folder's order, counting the dragged app's own
        folderId?.let { id ->
            if (folderBounds.contains(point)) {
                if (folderCount == 0) return null
                val rows = (folderCount + folderColumns - 1) / folderColumns
                val column = cellIndex(point.x - folderGridBounds.left, folderGridBounds.width, folderColumns)
                val row = cellIndex(point.y - folderGridBounds.top, folderGridBounds.height, rows)
                return DropTarget(id, minOf(row * folderColumns + column, folderCount - 1))
            }
        }
        val widget = heldWidget
        // The open dock lies over the bottom of the grid, so it's checked first
        dockRowBounds.forEachIndexed { row, bounds ->
            if (bounds.contains(point)) {
                if (widget != null) return null // widgets don't go in the dock
                val slot = cellIndex(point.x - bounds.left, bounds.width, dockSlots)
                return DropTarget(HomeItem.DOCK, cellPosition(row, slot))
            }
        }
        if (dockBounds.contains(point) || !gridBounds.contains(point)) return null
        var column = cellIndex(point.x - gridBounds.left, gridBounds.width, gridColumns)
        var row = cellIndex(point.y - gridBounds.top, gridBounds.height, gridRows)
        if (widget != null) {
            // The finger holds the widget by its middle; keep all of it inside the grid
            column = (column - widget.spanX / 2).coerceIn(0, maxOf(0, gridColumns - widget.spanX))
            row = (row - widget.spanY / 2).coerceIn(0, maxOf(0, gridRows - widget.spanY))
        }
        return DropTarget(HomeItem.HOME, cellPosition(row, column), gridPage)
    }

    private fun cellIndex(offset: Float, length: Float, count: Int): Int =
        (offset / length * count).toInt().coerceIn(0, count - 1)

    /**
     * Follows the finger from the root of the screen. Runs in the Initial pass and consumes
     * movement after a long press, so lists underneath don't scroll while an icon is held.
     */
    suspend fun PointerInputScope.trackDrag(
        onDragStart: (DragSource) -> Unit,
        onDrop: (DragSource, DropTarget) -> Unit,
    ) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                pointer = change.position

                // A new touch: whatever was held before is over
                if (change.changedToDown()) {
                    source = null
                    dragging = false
                }
                val held = source ?: continue

                if (!change.pressed) {
                    val dropOn = target
                    source = null
                    dragging = false
                    if (dropOn != null) onDrop(held, dropOn)
                    continue
                }

                if (!dragging && (pointer - pressedAt).getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    menuKey = null
                    onDragStart(held)
                }
                if (dragging) position = pointer
                change.consume()
            }
        }
    }
}

/** The icon that follows the finger during a drag. */
@Composable
fun DragOverlay(state: DragState, repository: AppRepository) {
    if (!state.dragging) return
    val size = 64.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        Modifier.offset {
            // Lifted a little, so the icon isn't hidden under the finger
            IntOffset((state.position.x - half).toInt(), (state.position.y - half * 1.6f).toInt())
        }
    ) {
        when (val source = state.source) {
            is DragSource.FromDrawer -> AppIcon(source.app, repository, size)
            is DragSource.Placed -> EntryIcon(source.entry, repository, size)
            null -> Unit
        }
    }
}
