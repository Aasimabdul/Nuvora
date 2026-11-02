package com.simbuild.nuvora

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.QueueMusic
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
    val rowHeightPx = with(LocalDensity.current) { NuvoraTokens.QueueRowHeight.toPx() }
    val lastIndex by rememberUpdatedState(queue.lastIndex)
    val currentIndex = queue.indexOfFirst { it.id == currentId }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NuvoraTokens.SpaceXL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Up next", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (queue.isNotEmpty()) {
                    Text(
                        if (queue.size == 1) "1 track queued" else "${queue.size} tracks queued",
                        fontSize = 12.sp,
                        color = muted
                    )
                }
            }
            if (currentIndex in 0 until queue.lastIndex) {
                TextButton(onClick = onClearUpcoming) { Text("Clear upcoming") }
            }
        }
        if (queue.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(NuvoraTokens.SpaceXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(NuvoraTokens.SpaceL))
                Text("Queue is empty", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(NuvoraTokens.SpaceS))
                Text(
                    "Use the ⋮ menu on any track and choose Add to queue or Play next.",
                    color = muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = NuvoraTokens.SpaceXL)
                )
            }
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
                            Modifier.fillMaxWidth().height(NuvoraTokens.QueueRowHeight)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = NuvoraTokens.SpaceXL),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from queue", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    },
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            scaleX = if (dragging) 1.03f else 1f
                            scaleY = if (dragging) 1.03f else 1f
                            shadowElevation = if (dragging) 24f else 0f
                        }
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(NuvoraTokens.QueueRowHeight)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(NuvoraTokens.RadiusM)
                            )
                            .clickable { onPlayAt(rowIndex) }
                            .padding(horizontal = NuvoraTokens.SpaceL),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Cover(track.artworkUrl, Modifier.size(NuvoraTokens.CoverSize).clip(RoundedCornerShape(NuvoraTokens.RadiusM)))
                            if (isCurrent) {
                                Box(
                                    Modifier.size(NuvoraTokens.CoverSize)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                            RoundedCornerShape(NuvoraTokens.RadiusM)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NowPlayingBars()
                                }
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = NuvoraTokens.SpaceM)) {
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

/** Three pulsing bars shown over the artwork of the track currently playing. */
@Composable
private fun NowPlayingBars() {
    val transition = rememberInfiniteTransition(label = "now-playing-bars")
    val barHeights = (0 until 3).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, delayMillis = index * 140, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar-$index"
        )
    }
    Row(
        Modifier.size(width = 18.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEach { height ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((18 * height.value).dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimary,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
