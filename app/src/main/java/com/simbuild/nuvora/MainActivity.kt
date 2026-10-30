package com.simbuild.nuvora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

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

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun NuvoraApp(player: ExoPlayer) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var active by remember { mutableStateOf<MusicTrack?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun search(term: String) {
        loading = true
        error = null
        scope.launch {
            try {
                tracks = AudiusRepository.searchTracks(term)
                if (tracks.isEmpty()) error = "No tracks found. Try another search."
            } catch (e: Exception) {
                error = "Could not load music. Check your internet and try again."
            } finally {
                loading = false
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) {
                playerError = "This track could not play. Try another track."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) { search("electronic chill") }

    fun play(track: MusicTrack) {
        active = track
        playerError = null
        player.setMediaItem(MediaItem.fromUri(track.streamUrl))
        player.prepare()
        player.play()
    }

    Scaffold(
        containerColor = Night,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Night).padding(horizontal = 12.dp)) {
                active?.let { track ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF182641), Panel)))
                            .clickable { tab = 3 }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(track.artworkUrl, Modifier.size(44.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                        }
                        IconButton(onClick = { player.seekToNextMediaItem() }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                        }
                    }
                }
                NavigationBar(containerColor = Night, tonalElevation = 0.dp) {
                    val labels = listOf("Home", "Search", "Library")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Favorite)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    ) { insets ->
        when (tab) {
            0 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Header()
                SearchBar(query, { query = it }, { tab = 1; search(query) }, { search("electronic chill") })
                StatusMessages(loading, error)
                LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Discover music", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) }
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
                        }, track.id in favorites)
                    }
                    item { Text("Catalogue by Audius · Track availability depends on creator settings.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
            1 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Search music", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp))
                SearchBar(query, { query = it }, { search(query) }, { search(query) })
                StatusMessages(loading, error)
                LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
                        }, track.id in favorites)
                    }
                }
            }
            2 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Your favourites", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
                val saved = tracks.filter { it.id in favorites }
                if (saved.isEmpty()) {
                    Text("Tap the heart beside a track to save it here.", color = Muted, modifier = Modifier.padding(horizontal = 20.dp))
                } else LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp)) {
                    items(saved, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            favorites = favorites - track.id
                        }, true)
                    }
                }
            }
            else -> active?.let { track ->
                Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { tab = 0 }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                        Spacer(Modifier.weight(1f))
                        Text("NOW PLAYING", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.weight(1f))
                    Cover(track.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(24.dp))
                    Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Muted, fontSize = 15.sp)
                    if (playerError != null) Text(playerError!!, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { if (track.id in favorites) favorites = favorites - track.id else favorites = favorites + track.id }) {
                            Icon(if (track.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favourite", tint = Cyan)
                        }
                        IconButton(onClick = { player.seekBack() }) { Icon(Icons.Rounded.Replay10, contentDescription = "Back 10 seconds") }
                        Surface(shape = RoundedCornerShape(50), color = Blue, modifier = Modifier.size(72.dp).clickable { if (player.isPlaying) player.pause() else player.play() }) {
                            Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", tint = Color.White, modifier = Modifier.size(38.dp)) }
                        }
                        IconButton(onClick = { player.seekForward() }) { Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds") }
                    }
                    TextButton(onClick = { tab = 0 }) { Text("Back to discovery") }
                    Spacer(Modifier.weight(1f))
                }
            } ?: Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Choose a track to start listening.")
                Button(onClick = { tab = 0 }) { Text("Discover music") }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)) {
        Text("NUVORA", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Text("Find your frequency.", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Discover independent music.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.weight(1f),
            placeholder = { Text("Songs, artists, moods…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
        IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Load discovery") }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.ArrowForward, contentDescription = "Search") }
    }
}

@Composable
private fun StatusMessages(loading: Boolean, error: String?) {
    if (loading) Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Loading tracks…", color = Muted, modifier = Modifier.padding(start = 12.dp))
    }
    if (error != null) Text(error, color = Color(0xFFFFA6A6), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun Cover(url: String?, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = "Track artwork", modifier = modifier.clip(RoundedCornerShape(18.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Blue, Panel, Color(0xFF25234C)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun TrackRow(track: MusicTrack, isActive: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit, favorite: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (isActive) Color(0xFF1A2E4F) else Panel)
            .clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.artworkUrl, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.durationLabel, color = Cyan, fontSize = 10.sp)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favourite", tint = if (favorite) Cyan else Muted)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play track", tint = Color.White) }
    }
}
