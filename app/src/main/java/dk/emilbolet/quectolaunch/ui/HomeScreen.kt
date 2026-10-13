package dk.emilbolet.quectolaunch.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.LifecycleResumeEffect
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppEntry
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.CELL_STRIDE
import dk.emilbolet.quectolaunch.data.DragSource
import dk.emilbolet.quectolaunch.data.FolderEntry
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.HomeItem
import dk.emilbolet.quectolaunch.data.HomeLayout
import dk.emilbolet.quectolaunch.data.PageRequest
import dk.emilbolet.quectolaunch.data.SystemControls
import dk.emilbolet.quectolaunch.data.WidgetEntry
import dk.emilbolet.quectolaunch.data.WidgetHost
import dk.emilbolet.quectolaunch.data.cellPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val DefaultIconSize = 52.dp

/** How long a dragged icon has to be held at the screen edge before the page turns. */
private const val PAGE_TURN_HOLD_MS = 600L

/** Soft shadow so white text stays readable on any wallpaper. */
internal val TextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
)

/**
 * Wallpaper, the pages of pinned apps and widgets (swipe sideways) and the dock. The clock
 * and the search bar are widgets on the grid.
 * A swipe up that starts on the dock expands the dock; anywhere else it opens the drawer.
 * With gestures turned on in settings, a swipe down opens notifications and a double-tap
 * on empty space locks the phone.
 */
@Composable
fun HomeScreen(
    layout: HomeLayout,
    columns: Int,
    rows: Int,
    dockSlots: Int,
    pageRequest: PageRequest,
    gesturesEnabled: Boolean,
    repository: AppRepository,
    widgetHost: WidgetHost,
    dragState: DragState,
    dockState: DockState,
    controls: SystemControls,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onRemove: (HomeEntry) -> Unit,
    onResizeWidget: (WidgetEntry, spanX: Int, spanY: Int) -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val currentOnOpen by rememberUpdatedState(onOpenDrawer)
    val haptics = LocalHapticFeedback.current

    // Where the long-press menu for empty space is showing, if it is
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    // The widget that currently shows its resize handles
    var resizingId by remember { mutableStateOf<Long?>(null) }

    SideEffect {
        dragState.gridColumns = columns
        dragState.gridRows = rows
        dragState.dockSlots = dockSlots
    }

    @Composable
    fun Cell(entry: HomeEntry?, showLabel: Boolean, iconSize: Dp = DefaultIconSize) {
        // Widgets cover several cells and are drawn by WidgetLayer instead
        if (entry == null || entry is WidgetEntry) return
        EntryCell(
            entry = entry,
            repository = repository,
            dragState = dragState,
            onClick = {
                when (entry) {
                    is AppEntry -> repository.launch(entry.app)
                    is FolderEntry -> onOpenFolder(entry.id)
                    is WidgetEntry -> Unit
                }
            },
            onRemove = { onRemove(entry) },
            iconSize = iconSize,
            showLabel = showLabel,
        )
    }

    val scope = rememberCoroutineScope()

    // While an icon is being dragged there is one more, empty page at the end to drop it on.
    // It stays a moment after the drop, until the saved layout has caught up.
    var sparePage by remember { mutableStateOf(false) }
    LaunchedEffect(dragState.dragging) {
        if (dragState.dragging) {
            sparePage = true
        } else {
            delay(500)
            sparePage = false
        }
    }
    val pageCount = layout.pages.size + if (sparePage) 1 else 0
    val pagerState = rememberPagerState { pageCount }

    SideEffect { dragState.gridPage = pagerState.currentPage }

    // The Home button returns to the first page; a new widget shows the page it landed on
    LaunchedEffect(pageRequest) {
        if (pageRequest.serial > 0) {
            // A brand-new page only exists once the saved layout has caught up
            snapshotFlow { pagerState.pageCount }.first { it > pageRequest.page }
            pagerState.animateScrollToPage(pageRequest.page)
        }
    }
    // Resize handles go away when the page is turned
    LaunchedEffect(pagerState.currentPage) { resizingId = null }

    // Holding a dragged icon at the left or right edge of the screen turns the page
    val edgeWidth = with(LocalDensity.current) { 28.dp.toPx() }
    val screenWidth = dragState.gridBounds.right + dragState.gridBounds.left
    val edge = when {
        !dragState.dragging || screenWidth <= 0f -> 0
        dragState.position.x < edgeWidth -> -1
        dragState.position.x > screenWidth - edgeWidth -> 1
        else -> 0
    }
    LaunchedEffect(edge) {
        while (edge != 0) {
            delay(PAGE_TURN_HOLD_MS)
            val next = pagerState.currentPage + edge
            if (next !in 0 until pagerState.pageCount) break
            // In its own job: the finger moving on mustn't leave the pages stuck halfway
            scope.launch { pagerState.animateScrollToPage(next) }
        }
    }

    // Read through derivedStateOf so the screen isn't recomposed on every frame of the swipe
    val dockOpen by remember(dockState) { derivedStateOf { dockState.openFraction > 0f } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(gesturesEnabled) {
                val threshold = 48.dp.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        when {
                            dragged < -threshold -> currentOnOpen(false)
                            dragged > threshold && gesturesEnabled -> controls.openNotifications()
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            }
            // Long-press on empty space (icons handle their own long-press) opens a small menu
            .pointerInput(gesturesEnabled) {
                detectTapGestures(
                    onDoubleTap = if (gesturesEnabled) {
                        { controls.lockScreen() }
                    } else {
                        null
                    },
                    onLongPress = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        homeMenuAt = offset
                    },
                )
            }
    ) {
      // Shrink the dock's icons when many of them share a row (the dock has 8 dp side margins)
      val dockIconSize = minOf(DefaultIconSize, (maxWidth - 16.dp) / dockSlots - 14.dp)
          .coerceAtLeast(32.dp)

      Column(
          // Leaves room for the collapsed dock, which is drawn on top below
          Modifier
              .systemBarsPadding()
              .padding(bottom = DockRowHeight + 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        DefaultHomeButton()

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .onGloballyPositioned { dragState.gridBounds = it.boundsInRoot() }
        ) {
            // Shrink the icons when a dense grid leaves less room than the icon and its label need
            val iconSize = minOf(
                DefaultIconSize,
                maxWidth / columns - 14.dp,
                maxHeight / rows - 36.dp,
            ).coerceAtLeast(32.dp)
            val gridWidth = maxWidth
            val gridHeight = maxHeight

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                // The extra page shown while dragging has no entries yet
                val entries = layout.pages.getOrNull(page).orEmpty()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        repeat(rows) { row ->
                            Row(Modifier.weight(1f)) {
                                repeat(columns) { column ->
                                    val position = cellPosition(row, column)
                                    DropCell(dragState, HomeItem.HOME, position, Modifier.weight(1f), page) {
                                        Cell(entries[position], showLabel = true, iconSize = iconSize)
                                    }
                                }
                            }
                        }
                    }
                    WidgetLayer(
                        entries = entries.values,
                        columns = columns,
                        rows = rows,
                        cellWidth = gridWidth / columns,
                        cellHeight = gridHeight / rows,
                        widgetHost = widgetHost,
                        dragState = dragState,
                        resizingId = resizingId,
                        onSearch = { onOpenDrawer(true) },
                        onResizingChange = { resizingId = it },
                        onResize = onResizeWidget,
                        onRemove = onRemove,
                    )
                }
            }
            if (layout.isEmpty && !dragState.dragging) {
                Text(
                    text = stringResource(R.string.home_empty_hint),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = TextShadow),
                )
            }
        }

        PageDots(count = pageCount, current = pagerState.currentPage)

        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.apps),
            tint = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onOpenDrawer(false) }
                .padding(horizontal = 24.dp, vertical = 2.dp),
        )

      }

        // While the dock is open, the rest of the screen dims and a tap there closes it
        if (dockOpen) {
            val noRipple = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.3f * dockState.openFraction)) }
                    .clickable(interactionSource = noRipple, indication = null) {
                        scope.launch { dockState.animateTo(DockValue.Collapsed) }
                    }
            )
        }

        Dock(
            state = dockState,
            entries = layout.dock,
            slots = dockSlots,
            dragState = dragState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        ) { entry ->
            Cell(entry, showLabel = false, iconSize = dockIconSize)
        }

        // Menu for a long-press on empty space, shown where the finger was
        Box(Modifier.offset { homeMenuAt?.round() ?: IntOffset.Zero }) {
            DropdownMenu(expanded = homeMenuAt != null, onDismissRequest = { homeMenuAt = null }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widgets)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_widgets),
                            contentDescription = null,
                            modifier = Modifier.size(MenuIconSize),
                        )
                    },
                    onClick = {
                        homeMenuAt = null
                        onOpenWidgets()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_screens)) },
                    leadingIcon = { MenuIcon(Icons.Outlined.Home) },
                    onClick = {
                        homeMenuAt = null
                        onOpenPages()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.launcher_settings)) },
                    leadingIcon = { MenuIcon(Icons.Outlined.Settings) },
                    onClick = {
                        homeMenuAt = null
                        onOpenSettings()
                    },
                )
            }
        }
    }
}

/** Shows which home page you are on; hidden while there is only one. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count > 1) {
            repeat(count) { page ->
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Color.White.copy(alpha = if (page == current) 1f else 0.4f), CircleShape)
                )
            }
        }
    }
}

/** One grid or dock cell; lights up while a dragged icon hovers over it. */
@Composable
internal fun DropCell(
    dragState: DragState,
    container: Long,
    position: Int,
    modifier: Modifier = Modifier,
    page: Int = 0,
    content: @Composable () -> Unit,
) {
    val target = dragState.target
    val widget = dragState.heldWidget
    val hovered = target != null && target.container == container && target.page == page &&
        if (widget == null) {
            target.position == position
        } else {
            // A widget lights up every cell it would cover
            val row = position / CELL_STRIDE - target.position / CELL_STRIDE
            val column = position % CELL_STRIDE - target.position % CELL_STRIDE
            row in 0 until widget.spanY && column in 0 until widget.spanX
        }
    Box(
        modifier
            .fillMaxHeight()
            .padding(2.dp)
            .background(
                if (hovered) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * An app or folder icon with an optional label. Tap to open, long-press for the menu,
 * long-press and move to drag. Used on the home screen, in the dock and inside folders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCell(
    entry: HomeEntry,
    repository: AppRepository,
    dragState: DragState,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = DefaultIconSize,
    showLabel: Boolean = true,
    /** White text with a shadow for the wallpaper; false uses the theme's text color. */
    onWallpaper: Boolean = true,
) {
    val beingDragged = dragState.dragging &&
        (dragState.source as? DragSource.Placed)?.entry?.id == entry.id

    Box(modifier.alpha(if (beingDragged) 0f else 1f), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { dragState.longPress(DragSource.Placed(entry), entry.id) },
                )
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EntryIcon(entry, repository, iconSize)
            if (showLabel) {
                val label = when (entry) {
                    is AppEntry -> entry.app.label
                    is FolderEntry -> entry.title ?: stringResource(R.string.folder)
                    is WidgetEntry -> ""
                }
                Text(
                    text = label,
                    modifier = Modifier.padding(top = 4.dp),
                    color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = if (onWallpaper) TextShadow else null,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val menuOpen = dragState.menuKey == entry.id
        when (entry) {
            is AppEntry -> AppMenu(
                app = entry.app,
                repository = repository,
                expanded = menuOpen,
                onDismiss = dragState::closeMenu,
                onRemove = onRemove,
            )
            is FolderEntry -> DropdownMenu(expanded = menuOpen, onDismissRequest = dragState::closeMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove)) },
                    leadingIcon = { MenuIcon(Icons.Outlined.Close) },
                    onClick = {
                        dragState.closeMenu()
                        onRemove()
                    },
                )
            }
            is WidgetEntry -> Unit // widgets have their own cell and menu
        }
    }
}

/** Shows a "Set as default" button until the user picks this app as their home screen. */
@Composable
private fun DefaultHomeButton() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        isDefault = isDefaultHome(context)
        onPauseOrDispose { }
    }

    val roleRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultHome(context)
    }

    if (!isDefault) {
        FilledTonalButton(
            onClick = { requestDefaultHome(context) { roleRequest.launch(it) } },
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.set_default))
        }
    }
}

private fun isDefaultHome(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    @Suppress("DEPRECATION")
    val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == context.packageName
}

private fun requestDefaultHome(context: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            return
        }
    }
    // Android 9 and older, or devices without the role dialog: open the system setting
    try {
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

