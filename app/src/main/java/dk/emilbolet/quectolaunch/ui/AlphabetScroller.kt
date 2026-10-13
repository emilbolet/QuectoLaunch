package dk.emilbolet.quectolaunch.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width of the letter strip; the list leaves this much room on the right. */
internal val ScrollerWidth = 28.dp

/**
 * The A–Z strip on the right edge of the app drawer.
 *
 * Touching or dragging along it calls [onLetter] each time the finger reaches a new
 * letter, then `onLetter(null)` when the finger lifts. [highlighted] is the section
 * currently at the top of the list, shown in the accent color.
 */
@Composable
fun AlphabetScroller(
    letters: List<String>,
    highlighted: String?,
    onLetter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val currentOnLetter by rememberUpdatedState(onLetter)
    val haptics = LocalHapticFeedback.current
    var heightPx by remember { mutableIntStateOf(1) }
    var touched by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .width(ScrollerWidth)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                // Each letter gets an equal slice of the strip's height
                fun pick(y: Float) {
                    val index = (y / heightPx * letters.size).toInt()
                        .coerceIn(0, letters.lastIndex)
                    val letter = letters[index]
                    if (letter != touched) {
                        touched = letter
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnLetter(letter)
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    drag(down.id) { change ->
                        change.consume()
                        pick(change.position.y)
                    }
                    touched = null
                    currentOnLetter(null)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val active = touched ?: highlighted
        letters.forEach { letter ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val isActive = letter == active
                Text(
                    text = letter,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        if (panelsSeeThrough) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
