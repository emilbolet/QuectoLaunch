package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dk.emilbolet.quectolaunch.R
import dk.emilbolet.quectolaunch.data.AppRepository
import dk.emilbolet.quectolaunch.data.FolderEntry
import dk.emilbolet.quectolaunch.data.HomeEntry
import dk.emilbolet.quectolaunch.data.Panel

private const val FOLDER_COLUMNS = 4

/**
 * An open folder: a card over a dimmed home screen with a title you can tap to rename.
 * Drawn inside the launcher's own layout (not a dialog window) so icons can be dragged out of it.
 */
@Composable
fun FolderPopup(
    folder: FolderEntry,
    repository: AppRepository,
    dragState: DragState,
    onRename: (String) -> Unit,
    onRemove: (HomeEntry) -> Unit,
    onClose: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = FOLDER_SCRIM))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                // Taps on the card itself shouldn't close the folder
                .clickable(interactionSource = noRipple, indication = null, onClick = {}),
            shape = RoundedCornerShape(28.dp),
            color = panelColor(Panel.Folder, MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(16.dp)) {
                FolderTitle(folder, onRename)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    folder.apps.chunked(FOLDER_COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                EntryCell(
                                    entry = entry,
                                    repository = repository,
                                    dragState = dragState,
                                    onClick = {
                                        onClose()
                                        repository.launch(entry.app)
                                    },
                                    onRemove = { onRemove(entry) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp),
                                    onWallpaper = panelNeedsLightText(Panel.Folder),
                                )
                            }
                            repeat(FOLDER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(folder: FolderEntry, onRename: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    // Kept locally while typing, so saving in the background can't move the cursor
    var title by remember(folder.id) { mutableStateOf(folder.title.orEmpty()) }
    val style = MaterialTheme.typography.titleLarge.onPanel(Panel.Folder).copy(
        color = panelContentColor(Panel.Folder),
        textAlign = TextAlign.Center,
    )

    BasicTextField(
        value = title,
        onValueChange = {
            title = it
            onRename(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.Center) {
                if (title.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                field()
            }
        },
    )
}
