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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppInfo
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.AppSection
import dk.emilbolet.quectolaunch.data.DragSource
import kotlinx.coroutines.launch

/** The full-screen list of all apps, with search on top and the A–Z strip on the right. */
@Composable
fun AppDrawer(
    sections: List<AppSection>,
    query: String,
    onQueryChange: (String) -> Unit,
    focusSearch: Boolean,
    repository: AppRepository,
    dragState: DragState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = remember(query, sections) { searchApps(sections, query) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(rememberPullDownToClose(onClose))
            ) {
                if (query.isBlank()) {
                    AlphabeticalList(sections, repository, dragState)
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
    repository: AppRepository,
    dragState: DragState,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val letters = remember(sections) { sections.map { it.letter } }

    // List position of each letter's header (each section = 1 header + its apps)
    val headerIndex = remember(sections) {
        buildMap {
            var index = 0
            sections.forEach { section ->
                put(section.letter, index)
                index += 1 + section.apps.size
            }
        }
    }

    // The section at the top of the list, highlighted in the strip while scrolling normally
    val currentLetter by remember(sections) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            letters.lastOrNull { (headerIndex[it] ?: 0) <= first }
        }
    }

    var touchedLetter by remember { mutableStateOf<String?>(null) }
    var bubbleLetter by remember { mutableStateOf("") }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = ScrollerWidth, bottom = 16.dp),
    ) {
        sections.forEach { section ->
            stickyHeader(key = "header:${section.letter}", contentType = "header") {
                SectionHeader(section.letter)
            }
            items(section.apps, key = { it.key }, contentType = { "app" }) { app ->
                AppRow(app, repository, dragState)
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

@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
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
                style = MaterialTheme.typography.bodyLarge,
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
            style = MaterialTheme.typography.bodyLarge,
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
