package com.simbuild.nuvora

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Night = Color(0xFF080D1A)
private val Panel = Color(0xFF121B2E)
private val Blue = Color(0xFF4D8DFF)
private val Cyan = Color(0xFF5CE1E6)
private val Muted = Color(0xFF9AA8C2)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue, secondary = Cyan, background = Night, surface = Panel,
                onBackground = Color(0xFFF4F7FF), onSurface = Color(0xFFF4F7FF)
            )) { NuvoraApp(player) }
        }
    }

    override fun onStop() {
        super.onStop()
        // Playback is currently activity-bound; a MediaSessionService is a follow-up milestone.
        if (::player.isInitialized && !isChangingConfigurations) player.pause()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}

@Composable
private fun NuvoraApp(player: ExoPlayer) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("electronic chill") }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var currentTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var fullPlayer by remember { mutableStateOf(false) }
    var savedIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(query) {
        delay(400)
        loading = true
        error = null
        try {
            tracks = AudiusRepository.searchTracks(query)
            if (tracks.isEmpty()) error = "No tracks found. Try another search."
        } catch (e: Exception) {
            error = "Couldn't load music. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, currentTrack?.id) {
        while (isActive && isPlaying) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    fun playTrack(track: MusicTrack) {
        currentTrack = track
        player.setMediaItem(MediaItem.fromUri(track.streamUrl))
        player.prepare()
        player.play()
        position = 0L
        fullPlayer = true
    }

    fun playRelative(offset: Int) {
        val list = tracks
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == currentTrack?.id }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + offset + list.size) % list.size
        playTrack(list[nextIndex])
    }

    Scaffold(
        containerColor = Night,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Night).padding(horizontal = 12.dp)) {
                currentTrack?.let { track ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF182641), Panel)))
                            .clickable { fullPlayer = true }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(track, Modifier.size(44.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = Muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                        }
                        IconButton(onClick = { playRelative(1) }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next track") }
                    }
                }
                NavigationBar(containerColor = Night, tonalElevation = 0.dp) {
                    val labels = listOf("Home", "Search", "Library")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Favorite)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index; fullPlayer = false },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    ) { insets ->
        if (fullPlayer && currentTrack != null) {
            NowPlaying(
                track = currentTrack!!, isPlaying = isPlaying, position = position, duration = duration,
                onBack = { fullPlayer = false },
                onToggle = { if (player.isPlaying) player.pause() else player.play() },
                onNext = { playRelative(1) }, onPrevious = { playRelative(-1) },
                onSeek = { player.seekTo(it); position = it },
                isSaved = currentTrack!!.id in savedIds,
                onSave = { track -> savedIds = if (track.id in savedIds) savedIds - track.id else savedIds + track.id },
                onOpenSource = { startExternal(context, currentTrack!!.permalink) }
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("NUVORA", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                    Text(if (tab == 0) "Find your frequency." else if (tab == 1) "Search music." else "Your library.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Powered by the Audius music catalogue", color = Muted, fontSize = 12.sp)
                }
                if (tab != 2) {
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs or artists...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
                            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                            shape = RoundedCornerShape(18.dp), singleLine = true
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (query.isBlank()) "Discover music" else "Search results", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
                        }
                    }
                    if (error != null) item {
                        Text(error!!, color = Muted)
                        TextButton(onClick = { query = query.trim() }) { Text("Try again") }
                    }
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track, isPlaying && currentTrack?.id == track.id, { playTrack(track) }, { savedIds = if (track.id in savedIds) savedIds - track.id else savedIds + track.id }, track.id in savedIds)
                    }
                    item {
                        Text("Music and playback are provided by Audius. Availability depends on each creator's settings.", color = Muted, fontSize = 11.sp)
                    }
                } else {
                    val savedTracks = tracks.filter { it.id in savedIds }
                    if (savedTracks.isEmpty()) item {
                        Text("Tap the heart on a track to save it here. Saved items currently stay in memory until the app is restarted.", color = Muted)
                    }
                    items(savedTracks, key = { it.id }) { track ->
                        TrackRow(track, isPlaying && currentTrack?.id == track.id, { playTrack(track) }, { savedIds = savedIds - track.id }, true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack, isPlaying: Boolean, onPlay: () -> Unit, onSave: () -> Unit, saved: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel.copy(alpha = 0.65f))
            .clickable(onClick = onPlay).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(track, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.durationSeconds > 0) Text(track.durationLabel, color = Muted, fontSize = 10.sp)
        }
        IconButton(onClick = onSave) { Icon(if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Save track", tint = if (saved) Cyan else Muted) }
        IconButton(onClick = onPlay) { Icon(if (isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.PlayArrow, contentDescription = "Play track", tint = Cyan) }
    }
}

@Composable
private fun TrackArtwork(track: MusicTrack, modifier: Modifier = Modifier) {
    if (track.artworkUrl != null) {
        AsyncImage(
            model = track.artworkUrl, contentDescription = "Artwork for ${track.title}",
            modifier = modifier.clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier.clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Blue, Panel, Color(0xFF1A2C4A)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NowPlaying(
    track: MusicTrack, isPlaying: Boolean, position: Long, duration: Long,
    onBack: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, isSaved: Boolean, onSave: () -> Unit, onOpenSource: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 24.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSource) { Icon(Icons.Rounded.OpenInNew, contentDescription = "Open Audius track") }
        }
        Spacer(Modifier.weight(1f))
        TrackArtwork(track, Modifier.fillMaxWidth().weight(1.5f))
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, fontSize = 14.sp)
            }
            IconButton(onClick = onSave) { Icon(if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Save track", tint = Cyan) }
        }
        val safeDuration = duration.coerceAtLeast(0L)
        Slider(
            value = if (safeDuration > 0) position.coerceIn(0L, safeDuration).toFloat() else 0f,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..safeDuration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Muted, fontSize = 11.sp)
            Text(formatTime(duration), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(32.dp)) }
            Surface(shape = CircleShape, color = Blue, modifier = Modifier.size(72.dp).clickable(onClick = onToggle)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.weight(1f))
        Text("Track provided by Audius · Open source page with ↗", color = Muted, fontSize = 11.sp)
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun startExternal(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
