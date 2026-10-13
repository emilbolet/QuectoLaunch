package dk.emilbolet.quectolaunch

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dk.emilbolet.quectolaunch.data.Alphabet
import dk.emilbolet.quectolaunch.data.AppChange
import dk.emilbolet.quectolaunch.data.AppEntry
import dk.emilbolet.quectolaunch.data.AppInfo
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.AppSection
import dk.emilbolet.quectolaunch.data.BuiltInWidget
import dk.emilbolet.quectolaunch.data.DragSource
import dk.emilbolet.quectolaunch.data.DropTarget
import dk.emilbolet.quectolaunch.data.FolderEntry
import dk.emilbolet.quectolaunch.data.HomeDatabase
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.HomeItem
import dk.emilbolet.quectolaunch.data.HomeLayout
import dk.emilbolet.quectolaunch.data.IconPackInfo
import dk.emilbolet.quectolaunch.data.LauncherSettings
import dk.emilbolet.quectolaunch.data.PageRequest
import dk.emilbolet.quectolaunch.data.PendingWidget
import dk.emilbolet.quectolaunch.data.SettingsStore
import dk.emilbolet.quectolaunch.data.SystemControls
import dk.emilbolet.quectolaunch.data.WidgetEntry
import dk.emilbolet.quectolaunch.data.WidgetGroup
import dk.emilbolet.quectolaunch.data.WidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    val controls = SystemControls(application)
    val widgets = WidgetHost(application)
    private val homeDao = HomeDatabase.create(application).homeDao()
    private val settingsStore = SettingsStore(application)

    /**
     * Null until the saved settings have been read. A change of icon pack is loaded before
     * the new settings are passed on, so at startup the first icons drawn are the right ones.
     */
    val settings: StateFlow<LauncherSettings?> = settingsStore.settings
        .scan(null as LauncherSettings?) { previous, current ->
            if (previous == null || previous.iconPack != current.iconPack) {
                withContext(Dispatchers.IO) { repository.setIconPack(current.iconPack) }
            }
            current
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    /** Installed icon packs; refreshed each time settings is opened. */
    val iconPacks: StateFlow<List<IconPackInfo>> = _iconPacks.asStateFlow()

    private val _sections = MutableStateFlow<List<AppSection>>(emptyList())
    /** All apps, sorted and grouped by letter. */
    val sections: StateFlow<List<AppSection>> = _sections.asStateFlow()

    /** The home grid and dock; items whose app is missing or disabled are left out. */
    val layout: StateFlow<HomeLayout> =
        flow {
            // First run after the clock and search bar became widgets (or first run ever)
            val grid = settings.filterNotNull().first()
            homeDao.placeBuiltIns(grid.columns, grid.rows)
            emitAll(
                combine(homeDao.observe(), homeDao.observeStoredPages(), _sections) { items, pages, sections ->
                    buildLayout(items, pages ?: 1, sections)
                }
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout())

    var drawerOpen by mutableStateOf(false)
        private set

    /** True when the drawer was opened from the search bar, so the keyboard should come up. */
    var drawerSearchFocused by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
        private set

    /** Whether the drawer shows the work profile's apps; kept while the launcher is running. */
    var drawerShowsWork by mutableStateOf(false)
        private set

    var openFolderId by mutableStateOf<Long?>(null)
        private set

    var settingsOpen by mutableStateOf(false)
        private set

    /** The page the home screen should scroll to: the first on a Home press, or where a new widget landed. */
    var pageRequest by mutableStateOf(PageRequest())
        private set

    var widgetPickerOpen by mutableStateOf(false)
        private set

    var pageManagerOpen by mutableStateOf(false)
        private set

    private val _widgetGroups = MutableStateFlow<List<WidgetGroup>>(emptyList())
    /** Installed widgets by app; refreshed each time the picker is opened. */
    val widgetGroups: StateFlow<List<WidgetGroup>> = _widgetGroups.asStateFlow()

    /** The widget being added, while Android's permission or setup screen is showing. */
    var pendingWidget: PendingWidget? = null

    /** Goes up each time the launcher returns to the plain home screen; the UI folds the dock on it. */
    var homeResets by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            repository.changes()
                .onStart { emit(AppChange()) } // initial load
                .collectLatest { change ->
                    val changed = change.packageName
                    if (changed != null) {
                        // The icon pack itself may have been updated or uninstalled
                        withContext(Dispatchers.IO) { repository.reloadIconPackIfChanged(changed) }
                    }
                    val removed = changed.takeIf { change.removed }
                    if (removed != null && change.user != null) {
                        // Separate job, so a burst of changes can't cancel the cleanup halfway
                        viewModelScope.launch {
                            homeDao.removeApps { key ->
                                AppInfo.packageOfKey(key) == removed &&
                                    AppInfo.keyHasUser(key, change.user)
                            }
                            removeWidgets { it.widgetProvider?.substringBefore('/') == removed }
                        }
                    }
                    repository.clearIconCache()
                    _sections.value = withContext(Dispatchers.Default) {
                        Alphabet.sections(Alphabet.sort(repository.loadApps()))
                    }
                }
        }
        // Apps uninstalled while the launcher wasn't running
        viewModelScope.launch(Dispatchers.IO) {
            homeDao.removeApps { key -> !repository.isInstalled(key) }
            removeWidgets { widgets.info(it.appWidgetId!!) == null }
        }
    }

    /** Removes widgets from the layout and gives their IDs back to Android. */
    private suspend fun removeWidgets(matches: (HomeItem) -> Boolean) {
        homeDao.widgets().filter(matches).forEach { item ->
            homeDao.remove(item.id)
            widgets.deleteId(item.appWidgetId!!)
        }
    }

    private fun buildLayout(items: List<HomeItem>, storedPages: Int, sections: List<AppSection>): HomeLayout {
        val apps = sections.asSequence().flatMap { it.apps }.associateBy { it.key }
        val byContainer = items.groupBy { it.container }

        fun appEntry(item: HomeItem): AppEntry? =
            apps[item.appKey]?.let { AppEntry(item.id, item.position, it) }

        fun entries(items: List<HomeItem>): Map<Int, HomeEntry> =
            items.mapNotNull { item ->
                when {
                    item.builtIn != null -> BuiltInWidget.entries.firstOrNull { it.key == item.builtIn }
                        ?.let { WidgetEntry(item.id, item.position, null, item.spanX, item.spanY, null, it) }
                    item.appWidgetId != null -> widgets.info(item.appWidgetId)?.let { info ->
                        WidgetEntry(item.id, item.position, item.appWidgetId, item.spanX, item.spanY, info)
                    }
                    item.isFolder -> {
                        val contents = byContainer[item.id].orEmpty().mapNotNull(::appEntry)
                        if (contents.isEmpty()) null
                        else FolderEntry(item.id, item.position, item.title, contents)
                    }
                    else -> appEntry(item)
                }
            }.associateBy { it.position }

        val home = byContainer[HomeItem.HOME].orEmpty().groupBy { it.page }
        // Pages stay until the user removes them, also when they are empty
        val pageCount = maxOf(storedPages, (home.keys.maxOrNull() ?: 0) + 1)
        return HomeLayout(
            pages = List(pageCount) { page -> entries(home[page].orEmpty()) },
            dock = entries(byContainer[HomeItem.DOCK].orEmpty()),
        )
    }

    fun openDrawer(focusSearch: Boolean = false) {
        drawerSearchFocused = focusSearch
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        query = ""
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun showWorkApps(work: Boolean) {
        drawerShowsWork = work
    }

    fun openFolder(id: Long) {
        openFolderId = id
    }

    fun closeFolder() {
        openFolderId = null
    }

    /**
     * Back to the plain home screen. [toFirstPage] is for the Home button, which also returns
     * to the first page; coming back from another app keeps the page you were on.
     */
    fun goHome(toFirstPage: Boolean = false) {
        closeDrawer()
        closeFolder()
        closeSettings()
        closeWidgetPicker()
        closePageManager()
        homeResets++
        if (toFirstPage) showPage(0)
    }

    fun showPage(page: Int) {
        pageRequest = PageRequest(page, pageRequest.serial + 1)
    }

    fun openPageManager() {
        pageManagerOpen = true
    }

    fun closePageManager() {
        pageManagerOpen = false
    }

    fun addPage() {
        viewModelScope.launch { homeDao.addPage() }
    }

    fun movePage(from: Int, to: Int) {
        viewModelScope.launch { homeDao.reorderPage(from, to) }
    }

    /** Removes a page with everything on it; the last remaining page is kept. */
    fun removePage(page: Int) {
        viewModelScope.launch { homeDao.removePage(page).forEach(widgets::deleteId) }
    }

    fun openWidgetPicker() {
        widgetPickerOpen = true
        viewModelScope.launch {
            _widgetGroups.value = withContext(Dispatchers.IO) { widgets.available() }
        }
    }

    fun closeWidgetPicker() {
        widgetPickerOpen = false
    }

    /** Last step of adding a widget: give it a place, starting with the page it was added from. */
    fun placePendingWidget() {
        val pending = pendingWidget ?: return
        pendingWidget = null
        val grid = settings.value ?: return
        viewModelScope.launch {
            val page = homeDao.placeWidget(
                appWidgetId = pending.appWidgetId,
                provider = pending.info.provider.flattenToString(),
                startPage = pending.page,
                columns = grid.columns,
                rows = grid.rows,
                spanX = pending.spanX.coerceAtMost(grid.columns),
                spanY = pending.spanY.coerceAtMost(grid.rows),
            )
            showPage(page)
        }
    }

    /** Adds the launcher's own clock or search bar: a full row, from the current page on. */
    fun addBuiltIn(widget: BuiltInWidget, startPage: Int) {
        val grid = settings.value ?: return
        viewModelScope.launch {
            val page = homeDao.placeWidget(
                appWidgetId = null,
                provider = null,
                startPage = startPage,
                columns = grid.columns,
                rows = grid.rows,
                spanX = grid.columns,
                spanY = 1,
                builtIn = widget.key,
            )
            showPage(page)
        }
    }

    /** The user backed out of the permission or setup screen. */
    fun cancelPendingWidget() {
        pendingWidget?.let { widgets.deleteId(it.appWidgetId) }
        pendingWidget = null
    }

    fun resizeWidget(entry: WidgetEntry, spanX: Int, spanY: Int) {
        viewModelScope.launch { homeDao.resizeWidget(entry.id, spanX, spanY) }
    }

    fun openSettings() {
        settingsOpen = true
        viewModelScope.launch {
            _iconPacks.value = withContext(Dispatchers.IO) { repository.iconPacks() }
        }
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun updateSettings(value: LauncherSettings) {
        viewModelScope.launch { settingsStore.save(value) }
    }

    fun drop(source: DragSource, target: DropTarget) {
        viewModelScope.launch {
            when (source) {
                is DragSource.FromDrawer ->
                    homeDao.drop(null, source.app.key, target.container, target.page, target.position)
                is DragSource.Placed -> if (source.entry is WidgetEntry) {
                    if (target.container == HomeItem.HOME) {
                        homeDao.moveWidget(source.entry.id, target.page, target.position)
                    }
                } else {
                    homeDao.drop(source.entry.id, null, target.container, target.page, target.position)
                }
            }
        }
    }

    fun remove(entry: HomeEntry) {
        viewModelScope.launch {
            homeDao.remove(entry.id)
            (entry as? WidgetEntry)?.appWidgetId?.let(widgets::deleteId)
        }
    }

    fun renameFolder(id: Long, title: String) {
        viewModelScope.launch { homeDao.rename(id, title.trim().ifEmpty { null }) }
    }
}
