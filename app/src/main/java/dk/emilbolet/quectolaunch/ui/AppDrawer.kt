@file:OptIn(ExperimentalFoundationApi::class)

package dk.emilbolet.quectolaunch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppInfo
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.AppSection
import dk.emilbolet.quectolaunch.data.DragSource
import dk.emilbolet.quectolaunch.data.DrawerLayout
import dk.emilbolet.quectolaunch.data.Panel
import kotlinx.coroutines.launch

private val GridIconSize = 48.dp

/** The full-screen list of all apps, with search on top and the A–Z strip on the right. */
@Composable
fun AppDrawer(
    sections: List<AppSection>,
    query: String,
    onQueryChange: (String) -> Unit,
    focusSearch: Boolean,
    /** Which half of the list is shown when the phone has a work profile. */
    showWork: Boolean,
    onShowWorkChange: (Boolean) -> Unit,
    layout: DrawerLayout,
    /** Icons per row in the grid layout; the same as the home screen's columns. */
    columns: Int,
    repository: AppRepository,
    dragState: DragState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Search always looks through every app, whichever profile is showing
    val results = remember(query, sections) { searchApps(sections, query) }

    // Without a work profile there is no switch and nothing to filter
    val hasWork = remember(sections) { sections.any { section -> section.apps.any { it.isWorkProfile } } }
    // Page 0 is the personal apps, page 1 the work apps
    val halves = remember(sections) {
        listOf(false, true).map { work ->
            sections.mapNotNull { section ->
                val apps = section.apps.filter { it.isWorkProfile == work }
                if (apps.isEmpty()) null else section.copy(apps = apps)
            }
        }
    }

    // Swipe sideways between the halves, or tap the switch
    val pagerState = rememberPagerState(initialPage = if (showWork) 1 else 0) { 2 }
    val scope = rememberCoroutineScope()
    val currentOnShowWorkChange by rememberUpdatedState(onShowWorkChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnShowWorkChange(it == 1) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = panelColor(Panel.AppList, MaterialTheme.colorScheme.surface),
        contentColor = panelContentColor(Panel.AppList),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                focused = focusSearch,
                // Pressing Enter opens the top result, like Microsoft Launcher
                onSubmit = {
                    val top = results.firstOrNull()
                    when {
                        top != null -> repository.launch(top)
                        query.isNotBlank() -> repository.searchWeb(query.trim())
                    }
                },
            )
            if (hasWork && query.isBlank()) {
                // Follows the finger: flips once a swipe is past halfway
                ProfileSwitch(pagerState.currentPage == 1) { work ->
                    scope.launch { pagerState.animateScrollToPage(if (work) 1 else 0) }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(rememberPullDownToClose(onClose))
            ) {
                if (query.isBlank() && hasWork) {
                    // A page off screen is dropped, so each half starts at the top when shown
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        Box(Modifier.fillMaxSize()) {
                            AlphabeticalList(halves[page], layout, columns, repository, dragState)
                        }
                    }
                } else if (query.isBlank()) {
                    AlphabeticalList(sections, layout, columns, repository, dragState)
                } else {
                    SearchResults(results, query, repository, dragState)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AlphabeticalList(
    sections: List<AppSection>,
    layout: DrawerLayout,
    columns: Int,
    repository: AppRepository,
    dragState: DragState,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val letters = remember(sections) { sections.map { it.letter } }

    // In the grid each list item is a row of apps; in the list it is a single app
    val perItem = if (layout == DrawerLayout.Grid) columns else 1
    val rows = remember(sections, perItem) { sections.map { it.apps.chunked(perItem) } }

    // List position of each letter's header (each section = 1 header + its rows)
    val headerIndex = remember(sections, rows) {
        buildMap {
            var index = 0
            sections.forEachIndexed { i, section ->
                put(section.letter, index)
                index += 1 + rows[i].size
            }
        }
    }

    // The section at the top of the list, highlighted in the strip while scrolling normally
    val currentLetter by remember(sections, headerIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            letters.lastOrNull { (headerIndex[it] ?: 0) <= first }
        }
    }

    var touchedLetter by remember { mutableStateOf<String?>(null) }
    var bubbleLetter by remember { mutableStateOf("") }

    val solid = panelOpacity(Panel.AppList) >= 1f

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = ScrollerWidth, bottom = 16.dp),
    ) {
        sections.forEachIndexed { i, section ->
            // A sticky heading needs a solid background, or the apps show through it as they
            // scroll past; on a see-through list the headings scroll along instead
            if (solid) {
                stickyHeader(key = "header:${section.letter}", contentType = "header") {
                    SectionHeader(section.letter)
                }
            } else {
                item(key = "header:${section.letter}", contentType = "header") {
                    SectionHeader(section.letter)
                }
            }
            if (layout == DrawerLayout.Grid) {
                items(rows[i], key = { it.first().key }, contentType = { "row" }) { row ->
                    AppGridRow(row, columns, repository, dragState)
                }
            } else {
                items(section.apps, key = { it.key }, contentType = { "app" }) { app ->
                    AppRow(app, repository, dragState)
                }
            }
        }
    }

    AlphabetScroller(
        letters = letters,
        highlighted = currentLetter,
        onLetter = { letter ->
            touchedLetter = letter
            if (letter != null) {
                bubbleLetter = letter
                headerIndex[letter]?.let { index ->
                    // Jump instantly; animating while the finger drags feels laggy
                    scope.launch { listState.scrollToItem(index) }
                }
            }
        },
        modifier = Modifier.align(Alignment.CenterEnd),
    )

    LetterBubble(
        visible = touchedLetter != null,
        letter = bubbleLetter,
        modifier = Modifier.align(Alignment.Center),
    )
}

@Composable
private fun SearchResults(
    results: List<AppInfo>,
    query: String,
    repository: AppRepository,
    dragState: DragState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(results, key = { it.key }) { app -> AppRow(app, repository, dragState) }
        item(key = "web-search") {
            WebSearchRow(query.trim()) { repository.searchWeb(query.trim()) }
        }
    }
}

/** Apps whose name starts with the query come first, then word matches, then anything containing it. */
private fun searchApps(sections: List<AppSection>, query: String): List<AppInfo> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()

    fun rank(app: AppInfo): Int = when {
        app.label.startsWith(q, ignoreCase = true) -> 0
        app.label.split(' ', '-', '.', '_').any { it.startsWith(q, ignoreCase = true) } -> 1
        app.label.contains(q, ignoreCase = true) -> 2
        else -> -1
    }

    return sections
        .flatMap { it.apps }
        .map { it to rank(it) }
        .filter { it.second >= 0 }
        .sortedBy { it.second } // stable sort, so ties stay alphabetical
        .map { it.first }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focused: Boolean,
    onSubmit: () -> Unit,
) {
    // Opened from the home screen's search bar: bring the keyboard up straight away
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focused) focusRequester.requestFocus()
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_apps)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
    )
}

/** Personal or work apps; only shown on a phone with a work profile. */
@Composable
private fun ProfileSwitch(showWork: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
    ) {
        listOf(false, true).forEachIndexed { index, work ->
            SegmentedButton(
                selected = showWork == work,
                onClick = { onChange(work) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) {
                Text(stringResource(if (work) R.string.apps_work else R.string.apps_personal))
            }
        }
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (panelOpacity(Panel.AppList) >= 1f) MaterialTheme.colorScheme.surface else Color.Transparent)
            .padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall.onPanel(Panel.AppList),
        fontWeight = FontWeight.Bold,
        color = if (panelNeedsLightText(Panel.AppList)) Color.White else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AppRow(app: AppInfo, repository: AppRepository, dragState: DragState) {
    // Long-press opens the menu; moving the finger after that drags the app to the home screen
    val menuKey = "drawer:${app.key}"

    Box(Modifier.padding(horizontal = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { repository.launch(app) },
                    onLongClick = { dragState.longPress(DragSource.FromDrawer(app), menuKey) },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, repository, size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge.onPanel(Panel.AppList),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AppMenu(
            app = app,
            repository = repository,
            expanded = dragState.menuKey == menuKey,
            onDismiss = dragState::closeMenu,
        )
    }
}

/** One row of the grid layout; a short last row keeps its apps at the left, in column-sized cells. */
@Composable
private fun AppGridRow(apps: List<AppInfo>, columns: Int, repository: AppRepository, dragState: DragState) {
    Row(Modifier.padding(horizontal = 8.dp)) {
        apps.forEach { app -> AppCell(app, repository, dragState, Modifier.weight(1f)) }
        repeat(columns - apps.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun AppCell(app: AppInfo, repository: AppRepository, dragState: DragState, modifier: Modifier = Modifier) {
    val menuKey = "drawer:${app.key}"

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { repository.launch(app) },
                    onLongClick = { dragState.longPress(DragSource.FromDrawer(app), menuKey) },
                )
                .padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, repository, size = GridIconSize)
            Text(
                text = app.label,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium.onPanel(Panel.AppList),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        AppMenu(
            app = app,
            repository = repository,
            expanded = dragState.menuKey == menuKey,
            onDismiss = dragState::closeMenu,
        )
    }
}

@Composable
private fun WebSearchRow(query: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.search_web, query),
            style = MaterialTheme.typography.bodyLarge.onPanel(Panel.AppList),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The big letter shown in the middle of the screen while dragging along the A–Z strip. */
@Composable
private fun LetterBubble(visible: Boolean, letter: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut(),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Pulling down past the top of the list closes the drawer. */
@Composable
private fun rememberPullDownToClose(onClose: () -> Unit): NestedScrollConnection {
    val currentOnClose by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    return remember(threshold) {
        object : NestedScrollConnection {
            private var pulled = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f) {
                    pulled += available.y
                    if (pulled > threshold) {
                        pulled = 0f
                        currentOnClose()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}
