package dk.emilbolet.quectolaunch.ui

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.emilbolet.quectolaunch.LauncherViewModel
import dk.emilbolet.quectolaunch.data.LauncherSettings
import dk.emilbolet.quectolaunch.data.PendingWidget
import dk.emilbolet.quectolaunch.ui.theme.isDark
import kotlinx.coroutines.launch

@Composable
fun LauncherApp(
    viewModel: LauncherViewModel,
    settings: LauncherSettings,
    onAddWidget: (PendingWidget) -> Unit,
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val drawerOpen = viewModel.drawerOpen
    val openFolder = viewModel.openFolderId?.let(layout::folder)
    val dragState = remember { DragState() }
    val dockState = rememberDockState(labels = settings.dockLabels)
    val scope = rememberCoroutineScope()

    // Home button, or coming back from another app: fold the dock away again
    LaunchedEffect(viewModel.homeResets) {
        dockState.animateTo(DockValue.Collapsed)
    }

    // Dark icons over the light drawer and settings; light icons over the wallpaper
    SystemBarIcons(
        darkIcons = (drawerOpen || viewModel.settingsOpen || viewModel.widgetPickerOpen ||
            viewModel.pageManagerOpen) &&
            !MaterialTheme.colorScheme.isDark,
    )

    // Back closes whatever is on top; on the plain home screen it does nothing
    BackHandler {
        when {
            viewModel.settingsOpen -> viewModel.closeSettings()
            viewModel.widgetPickerOpen -> viewModel.closeWidgetPicker()
            viewModel.pageManagerOpen -> viewModel.closePageManager()
            drawerOpen -> viewModel.closeDrawer()
            openFolder != null -> viewModel.closeFolder()
            else -> scope.launch { dockState.animateTo(DockValue.Collapsed) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(dragState) {
                with(dragState) {
                    trackDrag(
                        // Dragging always ends on the home screen, so get everything else out of the way.
                        // The dock stays as it is: an icon may be on its way to or from the second row.
                        onDragStart = {
                            viewModel.closeDrawer()
                            viewModel.closeFolder()
                        },
                        onDrop = viewModel::drop,
                    )
                }
            }
    ) {
        HomeScreen(
            layout = layout,
            columns = settings.columns,
            rows = settings.rows,
            dockSlots = settings.dockSlots,
            dockLabels = settings.dockLabels,
            pageRequest = viewModel.pageRequest,
            gesturesEnabled = settings.gestures,
            repository = viewModel.repository,
            widgetHost = viewModel.widgets,
            dragState = dragState,
            dockState = dockState,
            controls = viewModel.controls,
            onOpenDrawer = viewModel::openDrawer,
            onOpenFolder = viewModel::openFolder,
            onRemove = viewModel::remove,
            onResizeWidget = viewModel::resizeWidget,
            onOpenWidgets = viewModel::openWidgetPicker,
            onOpenPages = viewModel::openPageManager,
            onOpenSettings = viewModel::openSettings,
        )

        if (openFolder != null) {
            FolderPopup(
                folder = openFolder,
                repository = viewModel.repository,
                dragState = dragState,
                onRename = { viewModel.renameFolder(openFolder.id, it) },
                onRemove = viewModel::remove,
                onClose = viewModel::closeFolder,
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 4 } + fadeIn(),
            exit = slideOutVertically { it / 4 } + fadeOut(),
        ) {
            AppDrawer(
                sections = sections,
                query = viewModel.query,
                onQueryChange = viewModel::updateQuery,
                focusSearch = viewModel.drawerSearchFocused,
                showWork = viewModel.drawerShowsWork,
                onShowWorkChange = viewModel::showWorkApps,
                layout = settings.drawerLayout,
                columns = settings.columns,
                repository = viewModel.repository,
                dragState = dragState,
                onClose = viewModel::closeDrawer,
            )
        }

        AnimatedVisibility(visible = viewModel.settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
            SettingsScreen(
                settings = settings,
                iconPacks = iconPacks,
                controls = viewModel.controls,
                onChange = viewModel::updateSettings,
                onClose = viewModel::closeSettings,
            )
        }

        AnimatedVisibility(visible = viewModel.widgetPickerOpen, enter = fadeIn(), exit = fadeOut()) {
            val groups by viewModel.widgetGroups.collectAsStateWithLifecycle()
            // Size of one home grid cell, to work out how many cells a widget needs
            val spanOf = { info: AppWidgetProviderInfo ->
                viewModel.widgets.spanFor(
                    info = info,
                    cellWidthPx = dragState.gridBounds.width / settings.columns,
                    cellHeightPx = dragState.gridBounds.height / settings.rows,
                    columns = settings.columns,
                    rows = settings.rows,
                )
            }
            WidgetPicker(
                groups = groups,
                widgetHost = viewModel.widgets,
                spanOf = spanOf,
                columns = settings.columns,
                onPickBuiltIn = { widget ->
                    viewModel.closeWidgetPicker()
                    viewModel.addBuiltIn(widget, dragState.gridPage)
                },
                onPick = { info ->
                    val (spanX, spanY) = spanOf(info)
                    viewModel.closeWidgetPicker()
                    onAddWidget(
                        PendingWidget(
                            appWidgetId = viewModel.widgets.allocateId(),
                            info = info,
                            page = dragState.gridPage,
                            spanX = spanX,
                            spanY = spanY,
                        )
                    )
                },
                onClose = viewModel::closeWidgetPicker,
            )
        }

        AnimatedVisibility(visible = viewModel.pageManagerOpen, enter = fadeIn(), exit = fadeOut()) {
            val grid = dragState.gridBounds
            PageManager(
                pages = layout.pages,
                currentPage = dragState.gridPage,
                columns = settings.columns,
                rows = settings.rows,
                aspectRatio = if (grid.height > 0f) grid.width / grid.height else 0.8f,
                repository = viewModel.repository,
                onShow = { page ->
                    viewModel.showPage(page)
                    viewModel.closePageManager()
                },
                onMove = viewModel::movePage,
                onAdd = viewModel::addPage,
                onRemove = viewModel::removePage,
                onClose = viewModel::closePageManager,
            )
        }

        DragOverlay(dragState, viewModel.repository)
    }
}

/** Light status/nav bar icons over the wallpaper, dark ones over the light drawer. */
@Composable
private fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    LaunchedEffect(darkIcons) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
