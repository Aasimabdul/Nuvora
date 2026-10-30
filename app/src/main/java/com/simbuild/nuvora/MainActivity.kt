package com.simbuild.nuvora

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
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

private data class NuvoraTheme(val label: String, val background: Color, val surface: Color, val primary: Color, val secondary: Color)
private val themes = listOf(
    NuvoraTheme("Neon Blue", Color(0xFF080D1A), Color(0xFF121B2E), Color(0xFF4D8DFF), Color(0xFF5CE1E6)),
    NuvoraTheme("AMOLED Black", Color(0xFF000000), Color(0xFF101010), Color(0xFF4D8DFF), Color(0xFF5CE1E6)),
    NuvoraTheme("Purple Night", Color(0xFF10091D), Color(0xFF21152F), Color(0xFFB18CFF), Color(0xFFE0B8FF)),
    NuvoraTheme("Forest", Color(0xFF071510), Color(0xFF10251D), Color(0xFF48C78E), Color(0xFF9DE8C0)),
    NuvoraTheme("Light", Color(0xFFF4F7FC), Color(0xFFFFFFFF), Color(0xFF245DCE), Color(0xFF007F91))
)

private fun downloadTrack(context: Context, track: MusicTrack) {
    try {
        val safeTitle = track.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(70).ifBlank { "Nuvora track" }
        val request = DownloadManager.Request(Uri.parse(track.streamUrl))
            .setTitle(safeTitle)
            .setDescription("Downloading with Nuvora")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Nuvora/$safeTitle.mp3")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Download started: $safeTitle", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            val prefs = getSharedPreferences("nuvora_settings", MODE_PRIVATE)
            var themeIndex by remember { mutableIntStateOf(prefs.getInt("theme_index", 0).coerceIn(themes.indices)) }
            val chosenTheme = themes[themeIndex]
            MaterialTheme(colorScheme = if (themeIndex == 4) lightColorScheme(
                primary = chosenTheme.primary, secondary = chosenTheme.secondary, background = chosenTheme.background,
                surface = chosenTheme.surface, onBackground = Color(0xFF111827), onSurface = Color(0xFF111827)
            ) else darkColorScheme(
                primary = chosenTheme.primary, secondary = chosenTheme.secondary, background = chosenTheme.background,
                surface = chosenTheme.surface, onBackground = Color(0xFFF4F7FF), onSurface = Color(0xFFF4F7FF)
            )) { NuvoraApp(player, themeIndex, { themeIndex = it; prefs.edit().putInt("theme_index", it).apply() }) }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun NuvoraApp(player: ExoPlayer, themeIndex: Int, onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nuvora_settings", Context.MODE_PRIVATE) }
    var sourceUrl by remember { mutableStateOf(prefs.getString("source_url", "https://api.audius.co/v1") ?: "https://api.audius.co/v1") }
    var editingSource by remember { mutableStateOf(sourceUrl) }
    var selectedProvider by remember { mutableStateOf(prefs.getString("music_provider", "Audius") ?: "Audius") }
    var showProviders by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    val providers = listOf("Audius", "YouTube Music", "JioSaavn", "Spotify", "SoundCloud")
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
        if (selectedProvider != "Audius") {
            tracks = emptyList()
            loading = false
            error = "$selectedProvider is an external handoff in this release; Nuvora cannot play this provider's streams directly."
            val encoded = Uri.encode(term.ifBlank { "music" })
            val targetUrl = when (selectedProvider) {
                "YouTube Music" -> "https://music.youtube.com/search?q=$encoded"
                "JioSaavn" -> "https://www.jiosaavn.com/search/$encoded"
                "Spotify" -> "https://open.spotify.com/search/$encoded"
                "SoundCloud" -> "https://soundcloud.com/search?q=$encoded"
                else -> null
            }
            if (targetUrl != null) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open $selectedProvider", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                tracks = AudiusRepository.searchTracks(term, baseUrl = sourceUrl.trim().trimEnd('/'))
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp)) {
                active?.let { track ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF182641), Panel)))
                            .clickable { tab = 4 }.padding(9.dp),
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    val labels = listOf("Home", "Search", "Library", "Settings")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Favorite, Icons.Rounded.Settings)
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
                        }, track.id in favorites, { downloadTrack(context, track) })
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
                        }, track.id in favorites, { downloadTrack(context, track) })
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
                        }, true, { downloadTrack(context, track) })
                    }
                }
            }
            3 -> Column(Modifier.fillMaxSize().padding(insets).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Appearance", color = Muted, fontSize = 13.sp)
                Box {
                    OutlinedButton(onClick = { showThemes = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Palette, contentDescription = null)
                        Text("  Theme: ${themes[themeIndex].label}", modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showThemes, onDismissRequest = { showThemes = false }) {
                        themes.forEachIndexed { index, theme ->
                            DropdownMenuItem(
                                text = { Text(theme.label) },
                                leadingIcon = { if (index == themeIndex) Icon(Icons.Rounded.Check, contentDescription = null) },
                                onClick = { onThemeChange(index); showThemes = false }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Text("Music source", color = Muted, fontSize = 13.sp)
                Box {
                    OutlinedButton(onClick = { showProviders = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                        Text("  Source: $selectedProvider", modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showProviders, onDismissRequest = { showProviders = false }) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider + if (provider == "Audius") " · In-app playback" else " · Open official service") },
                                leadingIcon = { if (provider == selectedProvider) Icon(Icons.Rounded.Check, contentDescription = null) },
                                onClick = {
                                    selectedProvider = provider
                                    prefs.edit().putString("music_provider", provider).apply()
                                    if (provider != "Audius") {
                                        tracks = emptyList()
                                        loading = false
                                        error = null
                                    }
                                    showProviders = false
                                    Toast.makeText(context, if (provider == "Audius") "Audius in-app playback selected" else "$provider opens its official app or website", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
                Text(
                    when (selectedProvider) {
                        "Audius" -> "IN-APP PLAYBACK · Search and stream available Audius tracks inside Nuvora using Media3. Availability depends on creator settings."
                        "YouTube Music" -> "EXTERNAL ONLY · Opens the official YouTube Music app or website. Audio-only extraction and background playback are not implemented."
                        "JioSaavn" -> "EXTERNAL ONLY · Opens the official JioSaavn service. No authorized direct-stream integration is configured."
                        "Spotify" -> "EXTERNAL ONLY · Opens Spotify. Its Android SDK can control the official Spotify app; it does not expose Spotify audio streams to Nuvora."
                        "SoundCloud" -> "EXTERNAL ONLY · Opens SoundCloud. In-app playback requires approved API credentials and compliance with SoundCloud's playback and attribution rules."
                        else -> "External service handoff."
                    },
                    fontSize = 13.sp, color = Muted
                )
                if (selectedProvider == "Audius") {
                OutlinedTextField(
                    value = editingSource,
                    onValueChange = { editingSource = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API base URL") },
                    placeholder = { Text("https://api.audius.co/v1") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        val candidate = editingSource.trim().trimEnd('/')
                        if (candidate.startsWith("https://")) {
                            sourceUrl = candidate
                            prefs.edit().putString("source_url", candidate).apply()
                            Toast.makeText(context, "Music source saved. Run a search to load from it.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter a valid HTTPS URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Rounded.Save, contentDescription = null); Text("  Save source") }
                }
                HorizontalDivider()
                Text("Downloads", color = Muted, fontSize = 13.sp)
                Text("Tap the download icon beside a track. Files are saved in Music/Nuvora and the Android download notification shows progress.", fontSize = 13.sp)
                Text("Note: downloads depend on the selected provider's stream and usage permissions.", color = Muted, fontSize = 12.sp)
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
private fun TrackRow(track: MusicTrack, isActive: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit, favorite: Boolean, onDownload: () -> Unit = {}) {
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
        IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Download track", tint = Cyan) }
        IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play track", tint = Color.White) }
    }
}
