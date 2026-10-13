package dk.emilbolet.quectolaunch.ui

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.CELL_STRIDE
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.WidgetEntry

private const val PREVIEW_COLUMNS = 2

/** Room under each preview for its number and remove button. */
private val PreviewFooter = 40.dp

/** A page move that has been dropped but isn't in the saved layout yet. */
private class PendingMove(val pages: List<Map<Int, HomeEntry>>, val from: Int, val to: Int)

/**
 * All home pages as small previews: tap one to go to it, hold and drag to reorder, ✕ to
 * remove, + to add an empty one. The last remaining page has no ✕. Opened from the
 * long-press menu on empty home space.
 */
@Composable
fun PageManager(
    pages: List<Map<Int, HomeEntry>>,
    currentPage: Int,
    columns: Int,
    rows: Int,
    /** Width divided by height of the home grid, so the previews get its shape. */
    aspectRatio: Float,
    repository: AppRepository,
    onShow: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onClose: () -> Unit,
) {
    // The page waiting for "Remove" to be confirmed, because it has things on it
    var confirmRemove by remember { mutableStateOf<Int?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.systemBarsPadding()) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.home_screens),
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                text = stringResource(R.string.home_screens_hint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val tileWidth = maxWidth / PREVIEW_COLUMNS
                val tileHeight = tileWidth / aspectRatio + PreviewFooter
                val tileWidthPx = with(LocalDensity.current) { tileWidth.toPx() }
                val tileHeightPx = with(LocalDensity.current) { tileHeight.toPx() }
                val haptics = LocalHapticFeedback.current
                val currentPages by rememberUpdatedState(pages)
                val currentOnMove by rememberUpdatedState(onMove)

                // Pages have no ID of their own, so a preview is recognised by what is on it.
                // That keeps it in place when the saved order catches up with a move.
                val keys = remember(pages) {
                    var empty = 0L
                    pages.map { page -> page.values.minOfOrNull { it.id } ?: --empty }
                }

                var dragged by remember { mutableStateOf<Int?>(null) }
                var dragSlot by remember { mutableIntStateOf(0) }
                var dragOffset by remember { mutableStateOf(Offset.Zero) }
                var pending by remember { mutableStateOf<PendingMove?>(null) }

                fun origin(slot: Int) = Offset(
                    x = (slot % PREVIEW_COLUMNS) * tileWidthPx,
                    y = (slot / PREVIEW_COLUMNS) * tileHeightPx,
                )

                // Where a page is shown: its own place, shifted by the move in progress
                fun slotOf(page: Int): Int {
                    val from: Int
                    val to: Int
                    val drag = dragged
                    val move = pending?.takeIf { it.pages === pages }
                    when {
                        drag != null -> { from = drag; to = dragSlot }
                        move != null -> { from = move.from; to = move.to }
                        else -> return page
                    }
                    return when {
                        page == from -> to
                        from < to && page in from + 1..to -> page - 1
                        from > to && page in to until from -> page + 1
                        else -> page
                    }
                }

                val slots = pages.size + 1 // the last one is the "add" tile
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(tileHeight * ((slots + PREVIEW_COLUMNS - 1) / PREVIEW_COLUMNS))
                ) {
                    pages.forEachIndexed { page, entries ->
                        key(keys[page]) {
                            val lifted = dragged == page
                            val settled by animateIntOffsetAsState(origin(slotOf(page)).round(), label = "page")
                            PagePreview(
                                entries = entries,
                                columns = columns,
                                rows = rows,
                                number = slotOf(page) + 1,
                                current = page == currentPage,
                                lifted = lifted,
                                repository = repository,
                                onClick = { onShow(page) },
                                onRemove = if (pages.size > 1) {
                                    { if (entries.isEmpty()) onRemove(page) else confirmRemove = page }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .zIndex(if (lifted) 1f else 0f)
                                    .offset { if (lifted) (origin(page) + dragOffset).round() else settled }
                                    .size(tileWidth, tileHeight)
                                    .pointerInput(keys[page], page, tileWidthPx, tileHeightPx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                pending = null
                                                dragSlot = page
                                                dragOffset = Offset.Zero
                                                dragged = page
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                // The slot under the middle of the dragged preview
                                                val centre = origin(page) + dragOffset +
                                                    Offset(tileWidthPx / 2, tileHeightPx / 2)
                                                val column = (centre.x / tileWidthPx).toInt()
                                                    .coerceIn(0, PREVIEW_COLUMNS - 1)
                                                val row = (centre.y / tileHeightPx).toInt().coerceAtLeast(0)
                                                dragSlot = (row * PREVIEW_COLUMNS + column)
                                                    .coerceIn(0, currentPages.lastIndex)
                                            },
                                            onDragEnd = {
                                                dragged = null
                                                if (dragSlot != page) {
                                                    pending = PendingMove(currentPages, page, dragSlot)
                                                    currentOnMove(page, dragSlot)
                                                }
                                            },
                                            onDragCancel = { dragged = null },
                                        )
                                    },
                            )
                        }
                    }

                    val addAt by animateIntOffsetAsState(origin(pages.size).round(), label = "add")
                    AddPageTile(
                        onClick = onAdd,
                        modifier = Modifier
                            .offset { addAt }
                            .size(tileWidth, tileHeight),
                    )
                }
            }
        }
    }

    val removing = confirmRemove
    val count = pages.getOrNull(removing ?: -1)?.size ?: 0
    if (removing != null && count > 0) {
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.remove_home_screen_title)) },
            text = { Text(pluralStringResource(R.plurals.remove_home_screen_text, count, count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        onRemove(removing)
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** One page in miniature: icons and folders in their cells, widgets as plain blocks. */
@Composable
private fun PagePreview(
    entries: Map<Int, HomeEntry>,
    columns: Int,
    rows: Int,
    number: Int,
    current: Boolean,
    lifted: Boolean,
    repository: AppRepository,
    onClick: () -> Unit,
    /** Null for the last remaining page, which can't be removed. */
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val description = stringResource(R.string.home_screen_number, number)

    Column(modifier.padding(horizontal = 6.dp)) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(if (lifted) 8.dp else 0.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (current) 2.dp else 1.dp,
                    color = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
        ) {
            val cellWidth = maxWidth / columns
            val cellHeight = maxHeight / rows
            val iconSize = minOf(cellWidth, cellHeight) * 0.7f

            entries.values.forEach { entry ->
                val row = entry.position / CELL_STRIDE
                val column = entry.position % CELL_STRIDE
                // Outside a grid that has been made smaller: hidden on the home screen too
                if (row >= rows || column >= columns) return@forEach

                if (entry is WidgetEntry) {
                    Box(
                        Modifier
                            .offset(cellWidth * column, cellHeight * row)
                            .size(
                                cellWidth * minOf(entry.spanX, columns - column),
                                cellHeight * minOf(entry.spanY, rows - row),
                            )
                            .padding(3.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                RoundedCornerShape(8.dp),
                            )
                    )
                } else {
                    Box(
                        Modifier
                            .offset(cellWidth * column, cellHeight * row)
                            .size(cellWidth, cellHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        EntryIcon(entry, repository, iconSize)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewFooter)
        ) {
            Text(
                text = number.toString(),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge,
                color = if (current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(PreviewFooter),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.remove_home_screen, number),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPageTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.padding(start = 6.dp, end = 6.dp, bottom = PreviewFooter)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.add_home_screen),
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
