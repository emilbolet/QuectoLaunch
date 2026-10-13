package dk.emilbolet.quectolaunch.data

import android.appwidget.AppWidgetProviderInfo

/**
 * Home grid and dock positions are row * [CELL_STRIDE] + column, so they stay put when the
 * number of columns is changed in settings. Cells outside the current size are simply not shown.
 */
const val CELL_STRIDE = 100

fun cellPosition(row: Int, column: Int): Int = row * CELL_STRIDE + column

/** The first dock row is always visible; the rest show when the dock is swiped up. */
const val DOCK_ROWS = 2

/** Something placed on the home screen, in the dock, or inside a folder. */
sealed interface HomeEntry {
    val id: Long
    val position: Int
}

data class AppEntry(
    override val id: Long,
    override val position: Int,
    val app: AppInfo,
) : HomeEntry

data class FolderEntry(
    override val id: Long,
    override val position: Int,
    /** Null until the user names the folder. */
    val title: String?,
    val apps: List<AppEntry>,
) : HomeEntry

/** A widget on a home page. [position] is its top-left cell; it covers [spanX] × [spanY] cells. */
data class WidgetEntry(
    override val id: Long,
    override val position: Int,
    val appWidgetId: Int,
    val spanX: Int,
    val spanY: Int,
    val info: AppWidgetProviderInfo,
) : HomeEntry {
    val row: Int get() = position / CELL_STRIDE
    val column: Int get() = position % CELL_STRIDE
}

/** Asks the home screen to show a page; [serial] changes with every request. */
data class PageRequest(val page: Int = 0, val serial: Int = 0)

/**
 * The saved layout joined with the installed apps. [pages] are the home screen pages from
 * left to right (always at least one, and a page may be empty); each page and the dock are
 * keyed by cell position.
 */
data class HomeLayout(
    val pages: List<Map<Int, HomeEntry>> = listOf(emptyMap()),
    val dock: Map<Int, HomeEntry> = emptyMap(),
) {
    val isEmpty: Boolean get() = pages.all { it.isEmpty() } && dock.isEmpty()

    fun folder(id: Long): FolderEntry? =
        (pages.asSequence().flatMap { it.values } + dock.values.asSequence())
            .filterIsInstance<FolderEntry>()
            .firstOrNull { it.id == id }
}

/** What a drag started on. */
sealed interface DragSource {
    /** An app picked up in the drawer; dropping it adds a new item. */
    data class FromDrawer(val app: AppInfo) : DragSource

    /** An item that already has a place; dropping it moves it. */
    data class Placed(val entry: HomeEntry) : DragSource
}

/** A cell on the home grid ([HomeItem.HOME]) or in the dock ([HomeItem.DOCK]); see [cellPosition].
 * [page] is the home page, and always 0 for the dock. */
data class DropTarget(val container: Long, val position: Int, val page: Int = 0)
