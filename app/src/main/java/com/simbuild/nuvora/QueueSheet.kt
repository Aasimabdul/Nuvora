package com.simbuild.nuvora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private val QueueRowHeight = 64.dp

/**
 * "Up next" sheet. Provider-agnostic: it only edits the UI queue through callbacks.
 * Tap a row to play it, long-press the handle and drag to reorder, swipe a row away to remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<MusicTrack>,
    currentId: String?,
    onPlayAt: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onDismiss: () -> Unit
) {
    val keys = remember(queue) {
        val seen = mutableMapOf<String, Int>()
        queue.map { track ->
            val n = seen.getOrDefault(track.id, 0)
            seen[track.id] = n + 1
            "${track.id}#$n"
        }
    }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    val lastIndex by rememberUpdatedState(queue.lastIndex)
    val currentIndex = queue.indexOfFirst { it.id == currentId }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Up next", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (currentIndex in 0 until queue.lastIndex) {
                TextButton(onClick = onClearUpcoming) { Text("Clear upcoming") }
            }
        }
        if (queue.isEmpty()) {
            Text(
                "Your queue is empty. Use the ⋮ menu on a track and choose Add to queue.",
                color = muted,
                modifier = Modifier.padding(20.dp)
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            itemsIndexed(queue, key = { index, _ -> keys.getOrElse(index) { "row-$index" } }) { index, track ->
                val rowIndex by rememberUpdatedState(index)
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            onRemove(rowIndex)
                            true
                        } else false
                    }
                )
                val dragging = dragIndex == index
                val isCurrent = track.id == currentId
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxWidth().height(QueueRowHeight)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from queue", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(QueueRowHeight)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onPlayAt(rowIndex) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(track.artworkUrl, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(track.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = muted)
                        }
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Hold and drag to reorder",
                            tint = muted,
                            modifier = Modifier.size(32.dp).pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragIndex = rowIndex; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                    onDragEnd = {
                                        val from = rowIndex
                                        val to = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, lastIndex)
                                        if (to != from) onMove(from, to)
                                        dragIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragIndex = -1; dragOffset = 0f }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
