package dk.emilbolet.quectolaunch.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One saved item: an app shortcut (has [appKey]), a widget (has [appWidgetId]) or a folder (neither). */
@Entity(tableName = "home_items")
data class HomeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [HOME], [DOCK], or the id of the folder this item is inside. */
    val container: Long,
    /** Home screen page, counted from 0. Always 0 in the dock and inside folders. */
    val page: Int = 0,
    /** Home and dock: see [cellPosition]. Folder: order. */
    val position: Int,
    /** [AppInfo.key], or null for a folder. */
    val appKey: String? = null,
    /** Folder name; null until the user sets one. */
    val title: String? = null,
    /** The ID Android gave the widget, or null if this isn't one. */
    val appWidgetId: Int? = null,
    /** The widget's provider (flattened component name), to notice when its app is uninstalled. */
    val widgetProvider: String? = null,
    /** How many cells the item covers; more than 1 only for widgets. */
    @ColumnInfo(defaultValue = "1") val spanX: Int = 1,
    @ColumnInfo(defaultValue = "1") val spanY: Int = 1,
) {
    val isWidget: Boolean get() = appWidgetId != null
    val isFolder: Boolean get() = appKey == null && appWidgetId == null

    val row: Int get() = position / CELL_STRIDE
    val column: Int get() = position % CELL_STRIDE

    /** Whether this item covers any cell of the given rectangle (same page assumed). */
    fun overlaps(row: Int, column: Int, spanX: Int, spanY: Int): Boolean =
        this.row < row + spanY && row < this.row + this.spanY &&
            this.column < column + spanX && column < this.column + this.spanX

    companion object {
        const val HOME = -1L
        const val DOCK = -2L
    }
}

/** A single row ([id] 0) with the number of home pages, so that a page can exist while it is empty. */
@Entity(tableName = "home_meta")
data class HomeMeta(
    @PrimaryKey val id: Int = 0,
    val pages: Int,
)

@Dao
abstract class HomeDao {

    /** Null until something has been saved; the real page count is never less than the pages in use. */
    @Query("SELECT pages FROM home_meta WHERE id = 0")
    abstract fun observeStoredPages(): Flow<Int?>

    @Query("SELECT pages FROM home_meta WHERE id = 0")
    protected abstract suspend fun storedPages(): Int?

    @Query("INSERT OR REPLACE INTO home_meta (id, pages) VALUES (0, :pages)")
    protected abstract suspend fun setPages(pages: Int)

    @Query(
        "DELETE FROM home_items WHERE container IN " +
            "(SELECT id FROM home_items WHERE container = ${HomeItem.HOME} AND page = :page)"
    )
    protected abstract suspend fun deleteFolderContentsOnPage(page: Int)

    @Query("DELETE FROM home_items WHERE container = ${HomeItem.HOME} AND page = :page")
    protected abstract suspend fun deletePage(page: Int)

    @Query(
        "UPDATE home_items SET page = page + :by " +
            "WHERE container = ${HomeItem.HOME} AND page BETWEEN :first AND :last"
    )
    protected abstract suspend fun shiftPages(first: Int, last: Int, by: Int)

    @Query("SELECT * FROM home_items ORDER BY position")
    abstract fun observe(): Flow<List<HomeItem>>

    @Query("SELECT * FROM home_items WHERE appKey IS NOT NULL")
    protected abstract suspend fun apps(): List<HomeItem>

    @Query("SELECT * FROM home_items WHERE id = :id")
    protected abstract suspend fun get(id: Long): HomeItem?

    @Query(
        "SELECT * FROM home_items " +
            "WHERE container = :container AND page = :page AND position = :position LIMIT 1"
    )
    protected abstract suspend fun at(container: Long, page: Int, position: Int): HomeItem?

    @Query("SELECT * FROM home_items WHERE container = ${HomeItem.HOME} AND page = :page")
    protected abstract suspend fun onPage(page: Int): List<HomeItem>

    @Query("SELECT MAX(page) FROM home_items WHERE container = ${HomeItem.HOME}")
    protected abstract suspend fun lastPage(): Int?

    @Query("SELECT * FROM home_items WHERE appWidgetId IS NOT NULL")
    abstract suspend fun widgets(): List<HomeItem>

    @Query("UPDATE home_items SET page = :to WHERE container = ${HomeItem.HOME} AND page = :from")
    protected abstract suspend fun movePage(from: Int, to: Int)

    @Query("SELECT * FROM home_items WHERE container = :container ORDER BY position")
    protected abstract suspend fun inContainer(container: Long): List<HomeItem>

    @Insert
    protected abstract suspend fun insert(item: HomeItem): Long

    @Update
    protected abstract suspend fun update(item: HomeItem)

    @Query("DELETE FROM home_items WHERE id = :id OR container = :id")
    protected abstract suspend fun deleteWithContents(id: Long)

    @Query("UPDATE home_items SET title = :title WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String?)

    /**
     * Puts an item on a home or dock cell. [itemId] is an existing item being moved;
     * otherwise a new item for [appKey] is created.
     *
     * Empty cell: the item goes there. App on app: the two become a folder.
     * App on folder: it joins the folder. A folder dropped on something swaps places with it.
     */
    @Transaction
    open suspend fun drop(itemId: Long?, appKey: String?, container: Long, page: Int, position: Int) {
        val moving = itemId?.let { get(it) }
        if (moving == null && appKey == null) return
        if (moving != null && moving.isWidget) return // widgets move with moveWidget
        // Nothing can be dropped onto a widget
        if (container == HomeItem.HOME) {
            val row = position / CELL_STRIDE
            val column = position % CELL_STRIDE
            if (onPage(page).any { it.isWidget && it.overlaps(row, column, 1, 1) }) return
        }
        val target = at(container, page, position)
        if (moving != null && target?.id == moving.id) return

        fun placed(newContainer: Long, newPosition: Int, newPage: Int = 0): HomeItem =
            moving?.copy(container = newContainer, page = newPage, position = newPosition)
                ?: HomeItem(container = newContainer, page = newPage, position = newPosition, appKey = appKey)

        suspend fun save(item: HomeItem) {
            if (item.id == 0L) insert(item) else update(item)
        }

        when {
            target == null -> save(placed(container, position, page))

            moving != null && moving.isFolder -> {
                // Folders can't go inside folders
                update(
                    target.copy(
                        container = moving.container,
                        page = moving.page,
                        position = moving.position,
                    )
                )
                update(moving.copy(container = container, page = page, position = position))
            }

            target.isFolder -> {
                val contents = inContainer(target.id)
                val key = moving?.appKey ?: appKey
                // Dragging an app from the drawer onto a folder that already has it does nothing
                if (moving != null || contents.none { it.appKey == key }) {
                    save(placed(target.id, (contents.maxOfOrNull { it.position } ?: -1) + 1))
                }
            }

            else -> {
                val folderId =
                    insert(HomeItem(container = container, page = page, position = position))
                update(target.copy(container = folderId, page = 0, position = 0))
                save(placed(folderId, 1))
            }
        }

        moving?.let { tidyFolder(it.container) }
        growPages()
    }

    @Transaction
    open suspend fun remove(id: Long) {
        val item = get(id) ?: return
        deleteWithContents(id)
        tidyFolder(item.container)
    }

    /** Removes every app item whose key matches, e.g. after an uninstall. */
    @Transaction
    open suspend fun removeApps(matches: (String) -> Boolean) {
        apps().filter { matches(it.appKey!!) }.forEach { remove(it.id) }
    }

    /**
     * Adds a widget at the first free spot big enough for it, starting on [startPage] and
     * going on to later pages (a new one if need be). Returns the page it landed on.
     */
    @Transaction
    open suspend fun placeWidget(
        appWidgetId: Int,
        provider: String,
        startPage: Int,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
    ): Int {
        val newPage = pageCount()
        for (page in startPage.coerceIn(0, newPage)..newPage) {
            val items = onPage(page)
            for (row in 0..rows - spanY) {
                for (column in 0..columns - spanX) {
                    if (items.none { it.overlaps(row, column, spanX, spanY) }) {
                        insert(
                            HomeItem(
                                container = HomeItem.HOME,
                                page = page,
                                position = cellPosition(row, column),
                                appWidgetId = appWidgetId,
                                widgetProvider = provider,
                                spanX = spanX,
                                spanY = spanY,
                            )
                        )
                        growPages()
                        return page
                    }
                }
            }
        }
        return newPage // not reached: an empty page always has room
    }

    /** Moves a widget, if the cells it would cover are free. */
    @Transaction
    open suspend fun moveWidget(id: Long, page: Int, position: Int) {
        val widget = get(id) ?: return
        val row = position / CELL_STRIDE
        val column = position % CELL_STRIDE
        val blocked = onPage(page).any {
            it.id != id && it.overlaps(row, column, widget.spanX, widget.spanY)
        }
        if (!blocked) {
            update(widget.copy(page = page, position = position))
            growPages()
        }
    }

    /** Changes a widget's size, if the cells it would cover are free. */
    @Transaction
    open suspend fun resizeWidget(id: Long, spanX: Int, spanY: Int) {
        val widget = get(id) ?: return
        val blocked = onPage(widget.page).any {
            it.id != id && it.overlaps(widget.row, widget.column, spanX, spanY)
        }
        if (!blocked) update(widget.copy(spanX = spanX, spanY = spanY))
    }

    private suspend fun pageCount(): Int = maxOf(storedPages() ?: 1, (lastPage() ?: 0) + 1)

    /** After something was put on the spare page shown while dragging, that page is a real one. */
    private suspend fun growPages() {
        val count = pageCount()
        if (count != storedPages()) setPages(count)
    }

    /** Adds an empty home page at the end. */
    @Transaction
    open suspend fun addPage() {
        setPages(pageCount() + 1)
    }

    /**
     * Removes a home page and everything on it; later pages move one step left. The last
     * remaining page can't be removed. Returns the Android IDs of the widgets that were on it.
     */
    @Transaction
    open suspend fun removePage(page: Int): List<Int> {
        val count = pageCount()
        if (count <= 1 || page !in 0 until count) return emptyList()
        val widgetIds = onPage(page).mapNotNull { it.appWidgetId }
        deleteFolderContentsOnPage(page)
        deletePage(page)
        shiftPages(first = page + 1, last = count - 1, by = -1)
        setPages(count - 1)
        return widgetIds
    }

    /** Moves a home page to another place in the order; the pages in between shift by one. */
    @Transaction
    open suspend fun reorderPage(from: Int, to: Int) {
        val count = pageCount()
        if (from == to || from !in 0 until count || to !in 0 until count) return
        movePage(from, to = PAGE_IN_TRANSIT)
        if (from < to) {
            shiftPages(first = from + 1, last = to, by = -1)
        } else {
            shiftPages(first = to, last = from - 1, by = 1)
        }
        movePage(from = PAGE_IN_TRANSIT, to)
        // An empty page that ended up last must not drop off the end
        setPages(count)
    }

    /** A folder left with one app turns back into that app; an empty one disappears. */
    private suspend fun tidyFolder(folderId: Long) {
        if (folderId <= 0) return
        val folder = get(folderId) ?: return
        val contents = inContainer(folderId)
        if (contents.size > 1) return
        contents.firstOrNull()?.let {
            update(
                it.copy(container = folder.container, page = folder.page, position = folder.position)
            )
        }
        deleteWithContents(folderId)
    }
}

/** Where a page's items are parked while the pages around it are renumbered. */
private const val PAGE_IN_TRANSIT = -1

@Database(entities = [HomeItem::class, HomeMeta::class], version = 5, exportSchema = false)
abstract class HomeDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao

    companion object {
        fun create(context: Context): HomeDatabase =
            Room.databaseBuilder(context, HomeDatabase::class.java, "home.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** Version 1 numbered home cells as row * 5 + column, which broke when the grid changed size. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE home_items SET position = (position / 5) * $CELL_STRIDE + (position % 5) " +
                        "WHERE container = ${HomeItem.HOME}"
                )
            }
        }

        /** Same again for the dock, when the number of icons per dock row became a setting. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE home_items SET position = (position / 5) * $CELL_STRIDE + (position % 5) " +
                        "WHERE container = ${HomeItem.DOCK}"
                )
            }
        }

        /** Widgets: their Android ID, provider and size in cells. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_items ADD COLUMN appWidgetId INTEGER")
                db.execSQL("ALTER TABLE home_items ADD COLUMN widgetProvider TEXT")
                db.execSQL("ALTER TABLE home_items ADD COLUMN spanX INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE home_items ADD COLUMN spanY INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** The number of home pages is saved, so pages can be added and stay while empty. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `home_meta` " +
                        "(`id` INTEGER NOT NULL, `pages` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO home_meta (id, pages) " +
                        "SELECT 0, COALESCE(MAX(page), 0) + 1 FROM home_items WHERE container = ${HomeItem.HOME}"
                )
            }
        }
    }
}
