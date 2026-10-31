package com.simbuild.nuvora

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch

private val Night = Color(0xFF080D1A)
private val Panel: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val Blue: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val Cyan: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)

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

private fun loadFavorites(prefs: SharedPreferences): List<MusicTrack> = try {
    val array = JSONArray(prefs.getString("favorite_tracks", "[]") ?: "[]")
    val loaded = mutableListOf<MusicTrack>()
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        loaded.add(
            MusicTrack(
                id = o.optString("id"),
                title = o.optString("title", "Untitled track"),
                artist = o.optString("artist", "Unknown artist"),
                artworkUrl = o.optString("artwork").takeIf { it.startsWith("https://") },
                durationSeconds = o.optInt("duration", 0),
                permalink = o.optString("permalink"),
                streamUrl = o.optString("stream")
            )
        )
    }
    loaded.filter { it.id.isNotBlank() && it.streamUrl.startsWith("https://") }
} catch (_: Exception) {
    emptyList()
}

private fun saveFavorites(prefs: SharedPreferences, tracks: List<MusicTrack>) {
    val array = JSONArray()
    tracks.forEach { t ->
        array.put(
            JSONObject()
                .put("id", t.id).put("title", t.title).put("artist", t.artist)
                .put("artwork", t.artworkUrl ?: "").put("duration", t.durationSeconds)
                .put("permalink", t.permalink).put("stream", t.streamUrl)
        )
    }
    prefs.edit().putString("favorite_tracks", array.toString()).apply()
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var sharedYouTubeLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedYouTubeLink = youtubeLinkFromIntent(intent)
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
            )) { NuvoraApp(player, themeIndex, { themeIndex = it; prefs.edit().putInt("theme_index", it).apply() }, sharedYouTubeLink) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedYouTubeLink = youtubeLinkFromIntent(intent)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun NuvoraApp(player: ExoPlayer, themeIndex: Int, onThemeChange: (Int) -> Unit, sharedYouTubeLink: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nuvora_settings", Context.MODE_PRIVATE) }
    var sourceUrl by remember { mutableStateOf(prefs.getString("source_url", "https://api.audius.co/v1") ?: "https://api.audius.co/v1") }
    var editingSource by remember { mutableStateOf(sourceUrl) }
    var selectedProvider by remember { mutableStateOf(prefs.getString("music_provider", "Audius") ?: "Audius") }
    var showProviders by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showMoodSection by remember { mutableStateOf(prefs.getBoolean("home_moods", true)) }
    var showRecentSection by remember { mutableStateOf(prefs.getBoolean("home_recent", true)) }
    var showCarouselSection by remember { mutableStateOf(prefs.getBoolean("home_carousel", true)) }
    val providers = listOf("Audius", "YouTube Music", "JioSaavn", "Spotify", "SoundCloud", "Bandcamp")
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    val recentlyPlayed = remember { mutableStateListOf<MusicTrack>() }
    val favoriteTracks = remember { mutableStateListOf<MusicTrack>().also { it.addAll(loadFavorites(prefs)) } }
    val favorites = favoriteTracks.map { it.id }.toSet()
    fun toggleFavorite(track: MusicTrack) {
        val existing = favoriteTracks.indexOfFirst { it.id == track.id }
        if (existing >= 0) favoriteTracks.removeAt(existing) else favoriteTracks.add(0, track)
        saveFavorites(prefs, favoriteTracks)
    }
    var active by remember { mutableStateOf<MusicTrack?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val queue = remember { mutableListOf<MusicTrack>() }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    fun markActive(track: MusicTrack) {
        active = track
        recentlyPlayed.removeAll { it.id == track.id }
        recentlyPlayed.add(0, track)
        while (recentlyPlayed.size > 12) recentlyPlayed.removeAt(recentlyPlayed.lastIndex)
        playerError = null
    }

    LaunchedEffect(player, active, playing) {
        while (active != null) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            kotlinx.coroutines.delay(500)
        }
    }

    fun search(term: String) {
        if (selectedProvider == "YouTube Music") {
            tracks = emptyList()
            loading = false
            error = "Paste a YouTube or YouTube Music video link below to play it in Nuvora's official embedded player."
            return
        }
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
                "Bandcamp" -> "https://bandcamp.com/search?q=$encoded"
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
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                queue.firstOrNull { it.id == id }?.let { markActive(it) }
            }
            override fun onPlayerError(error: PlaybackException) {
                playerError = "This track could not play. Try another track."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(sharedYouTubeLink) {
        if (!sharedYouTubeLink.isNullOrBlank() && extractYouTubeVideoId(sharedYouTubeLink) != null) {
            selectedProvider = "YouTube Music"
            prefs.edit().putString("music_provider", selectedProvider).apply()
            tab = 1
        }
    }

    LaunchedEffect(Unit) { search("electronic chill") }

    fun play(track: MusicTrack, list: List<MusicTrack> = tracks) {
        val items = if (list.any { it.id == track.id }) list else listOf(track)
        queue.clear()
        queue.addAll(items)
        val index = items.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        markActive(track)
        player.setMediaItems(items.map { MediaItem.Builder().setMediaId(it.id).setUri(it.streamUrl).build() }, index, 0L)
        player.prepare()
        player.play()
    }

    BackHandler(enabled = tab != 0) { tab = 0 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                active?.let { track ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Panel.copy(alpha = 0.98f),
                        tonalElevation = 3.dp,
                        shadowElevation = 10.dp,
                        modifier = Modifier.fillMaxWidth().clickable { tab = 5 }
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(track.artworkUrl, Modifier.size(42.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                                }
                                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                                }
                                IconButton(onClick = { player.seekToNextMediaItem() }) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                                }
                            }
                            if (durationMs > 0L) {
                                Slider(
                                    value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                                    onValueChange = { positionMs = (it * durationMs).toLong() },
                                    onValueChangeFinished = { player.seekTo(positionMs) },
                                    modifier = Modifier.fillMaxWidth().height(18.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Cyan,
                                        activeTrackColor = Cyan,
                                        inactiveTrackColor = Muted.copy(alpha = 0.25f)
                                    )
                                )
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Panel.copy(alpha = 0.98f),
                    tonalElevation = 3.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(64.dp)
                    ) {
                        val labels = listOf("Home", "Search", "Library", "Discover", "Settings")
                        val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Favorite, Icons.Rounded.Explore, Icons.Rounded.Settings)
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icons[index], contentDescription = label) },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Cyan,
                                    selectedTextColor = Cyan,
                                    indicatorColor = Blue.copy(alpha = 0.18f),
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { insets ->
        when (tab) {
            0 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("NUVORA", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("Find your frequency.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Box {
                        OutlinedButton(
                            onClick = { showProviders = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(selectedProvider, fontSize = 12.sp, maxLines = 1)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = showProviders, onDismissRequest = { showProviders = false }) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider + if (provider == "Audius") " · In-app" else if (provider == "YouTube Music") " · Embedded" else " · Official app") },
                                    leadingIcon = { if (provider == selectedProvider) Icon(Icons.Rounded.Check, contentDescription = null) },
                                    onClick = {
                                        selectedProvider = provider
                                        prefs.edit().putString("music_provider", provider).apply()
                                        showProviders = false
                                        error = null
                                        tracks = emptyList()
                                        if (provider == "Audius") {
                                            search(query.ifBlank { "electronic chill" })
                                        } else if (provider == "YouTube Music") {
                                            tab = 1
                                        } else {
                                            val encoded = Uri.encode(query.ifBlank { "music" })
                                            val url = when (provider) {
                                                "JioSaavn" -> "https://www.jiosaavn.com/search/$encoded"
                                                "Spotify" -> "https://open.spotify.com/search/$encoded"
                                                "SoundCloud" -> "https://soundcloud.com/search?q=$encoded"
                                                "Bandcamp" -> "https://bandcamp.com/search?q=$encoded"
                                                else -> null
                                            }
                                            if (url != null) try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Could not open $provider", Toast.LENGTH_SHORT).show()
                                            }
                                            error = "$provider opens through its official app or website in this version."
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Text(
                    when (java.time.LocalTime.now().hour) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        in 17..21 -> "Good evening"
                        else -> "Welcome back"
                    },
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                )
                SearchBar(query, { query = it }, { tab = 1; search(query) }, { search("electronic chill") })
                StatusMessages(loading, error)
                LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showMoodSection) item {
                        Text("Pick your mood", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(listOf("Chill", "Electronic", "Focus", "Energy", "Ambient", "Workout")) { mood ->
                                Surface(shape = RoundedCornerShape(18.dp), color = Panel, modifier = Modifier.clickable {
                                    query = mood.lowercase()
                                    search(query)
                                }) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            when (mood) {
                                                "Chill" -> Icons.Rounded.Waves
                                                "Electronic" -> Icons.Rounded.GraphicEq
                                                "Focus" -> Icons.Rounded.Headphones
                                                "Energy" -> Icons.Rounded.Bolt
                                                "Ambient" -> Icons.Rounded.Cloud
                                                else -> Icons.Rounded.FitnessCenter
                                            },
                                            contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(mood, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    if (showRecentSection && recentlyPlayed.isNotEmpty()) {
                        item {
                            Text("Recently played", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recentlyPlayed, key = { "recent-${it.id}" }) { track ->
                                    Column(Modifier.width(112.dp).clickable { play(track, recentlyPlayed.toList()) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Cover(track.artworkUrl, Modifier.size(112.dp))
                                        Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    if (showCarouselSection) item {
                        Text("Discover music", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                        if (tracks.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(tracks.take(8), key = { "discover-${it.id}" }) { track ->
                                    Column(Modifier.width(124.dp).clickable { play(track) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Cover(track.artworkUrl, Modifier.size(124.dp))
                                        Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    if (showCarouselSection && tracks.isNotEmpty()) {
                        item {
                            Text("Artists to explore", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(tracks.distinctBy { it.artist }.take(10), key = { "artist-${it.artist}" }) { artistTrack ->
                                    Column(
                                        Modifier.width(100.dp).clickable {
                                            query = artistTrack.artist
                                            search(query)
                                            tab = 1
                                        },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Cover(artistTrack.artworkUrl, Modifier.size(84.dp).clip(RoundedCornerShape(50)))
                                        Text(artistTrack.artist, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Trending tracks", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)) }
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            toggleFavorite(track)
                        }, track.id in favorites, { downloadTrack(context, track) })
                    }
                    item { Text("Catalogue by Audius · Track availability depends on creator settings.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
            1 -> if (selectedProvider == "YouTube Music") {
                Column(Modifier.fillMaxSize().padding(insets)) { YouTubeEmbedScreen(sharedYouTubeLink) }
            } else Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Search music", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp))
                SearchBar(query, { query = it }, { search(query) }, { search(query) })
                StatusMessages(loading, error)
                LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            toggleFavorite(track)
                        }, track.id in favorites, { downloadTrack(context, track) })
                    }
                }
            }
            2 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Your favourites", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
                val saved = favoriteTracks.toList()
                if (saved.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Muted, modifier = Modifier.size(56.dp))
                        Text("No favourites yet", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("Tap the heart beside a track to save it here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                } else LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp)) {
                    items(saved, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track, saved) }, {
                            toggleFavorite(track)
                        }, true, { downloadTrack(context, track) })
                    }
                }
            }
            3 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Discover", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
                SearchBar(query, { query = it }, { search(query) }, { search("electronic chill") })
                StatusMessages(loading, error)
                LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Trending discoveries", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(tracks, key = { "discover-tab-${it.id}" }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            toggleFavorite(track)
                        }, track.id in favorites, { downloadTrack(context, track) })
                    }
                }
            }
            4 -> Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Text("Home screen sections", color = Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mood cards", modifier = Modifier.weight(1f))
                    Switch(checked = showMoodSection, onCheckedChange = {
                        showMoodSection = it
                        prefs.edit().putBoolean("home_moods", it).apply()
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recently played", modifier = Modifier.weight(1f))
                    Switch(checked = showRecentSection, onCheckedChange = {
                        showRecentSection = it
                        prefs.edit().putBoolean("home_recent", it).apply()
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Horizontal music carousel", modifier = Modifier.weight(1f))
                    Switch(checked = showCarouselSection, onCheckedChange = {
                        showCarouselSection = it
                        prefs.edit().putBoolean("home_carousel", it).apply()
                    })
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
                        "YouTube Music" -> "IN-APP EMBEDDED VIDEO · Paste a YouTube/YouTube Music video link in Search to play it with the official YouTube embedded player. No audio extraction or background playback."
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
                    SeekBar(positionMs, durationMs, { positionMs = it }, { player.seekTo(positionMs) })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { shuffleOn = !shuffleOn; player.shuffleModeEnabled = shuffleOn }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffleOn) Cyan else Muted)
                        }
                        IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
                        }
                        Surface(shape = RoundedCornerShape(50), color = Blue, modifier = Modifier.size(72.dp).clickable { if (player.isPlaying) player.pause() else player.play() }) {
                            Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", tint = Color.White, modifier = Modifier.size(38.dp)) }
                        }
                        IconButton(onClick = { player.seekToNextMediaItem() }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = {
                            val next = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            repeatMode = next
                            player.repeatMode = next
                        }) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) Cyan else Muted
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { toggleFavorite(track) }) {
                            Icon(if (track.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
                            Text(if (track.id in favorites) "  Saved" else "  Save")
                        }
                        TextButton(onClick = { downloadTrack(context, track) }) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
                            Text("  Download")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            } ?: Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Choose a track to start listening.")
                Button(onClick = { tab = 0 }) { Text("Discover music") }
            }
        }
    }
}

private fun youtubeLinkFromIntent(intent: Intent?): String? {
    if (intent == null) return null
    val candidate = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }?.trim().orEmpty()
    return candidate.takeIf { extractYouTubeVideoId(it) != null }
}

private fun extractYouTubeVideoId(input: String): String? {
    val value = input.trim()
    val pattern = Regex("""(?:youtu\.be/|(?:www\.|music\.)?youtube\.com/(?:watch\?v=|embed/|shorts/))([A-Za-z0-9_-]{11})|^([A-Za-z0-9_-]{11})$""")
    val match = pattern.find(value) ?: return null
    return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
}

@Composable
private fun YouTubeEmbedScreen(sharedYouTubeLink: String? = null) {
    var videoUrl by remember(sharedYouTubeLink) { mutableStateOf(sharedYouTubeLink.orEmpty()) }
    var videoId by remember(sharedYouTubeLink) { mutableStateOf(sharedYouTubeLink?.let { extractYouTubeVideoId(it) }) }
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YouTube Music", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Play an official YouTube video inside Nuvora. Find a song in YouTube Music, use Share → Copy link, then paste it here.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("YouTube / YouTube Music link") },
            placeholder = { Text("https://music.youtube.com/watch?v=…") },
            trailingIcon = {
                IconButton(onClick = {
                    val pasted = clipboard.getText()?.text.orEmpty().trim()
                    if (pasted.isNotBlank()) {
                        videoUrl = pasted
                        videoId = extractYouTubeVideoId(pasted)
                    }
                }) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste link") }
            },
            singleLine = true
        )
        Button(
            onClick = { videoId = extractYouTubeVideoId(videoUrl) },
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null); Text("  Play in Nuvora") }
        if (videoId == null && videoUrl.isNotBlank()) {
            Text("Enter a valid YouTube video link or 11-character video ID.", color = Color(0xFFFFA6A6), fontSize = 12.sp)
        }
        Text("Official embedded playback · Internet required · YouTube controls and ads may appear. Audio-only and background playback are not provided.", color = Muted, fontSize = 12.sp)
        videoId?.let { id ->
            key(id) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webViewClient = WebViewClient()
                            val html = """
                                <!doctype html>
                                <html>
                                  <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                                    <style>
                                      html, body { margin:0; padding:0; width:100%; height:100%; background:#080D1A; overflow:hidden; }
                                      iframe { position:absolute; inset:0; width:100%; height:100%; border:0; }
                                    </style>
                                  </head>
                                  <body>
                                    <iframe
                                      src="https://www.youtube.com/embed/$id?playsinline=1&autoplay=0&rel=0"
                                      title="YouTube video player"
                                      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                                      referrerpolicy="strict-origin-when-cross-origin"
                                      allowfullscreen>
                                    </iframe>
                                  </body>
                                </html>
                            """.trimIndent()
                            loadDataWithBaseURL(
                                "https://www.youtube.com/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )
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
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
            },
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
            .background(if (isActive) Blue.copy(alpha = 0.2f) else Panel)
            .clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.artworkUrl, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, color = if (isActive) Cyan else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Now playing", tint = Cyan, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(track.durationLabel, color = Muted, fontSize = 10.sp)
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favourite", tint = if (favorite) Cyan else Muted)
        }
        IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Download track", tint = Cyan) }
        IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play track", tint = Color.White) }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onChange: (Long) -> Unit, onFinished: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { if (durationMs > 0L) onChange((it * durationMs).toLong()) },
            onValueChangeFinished = onFinished,
            enabled = durationMs > 0L,
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Muted.copy(alpha = 0.25f))
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), color = Muted, fontSize = 11.sp)
            Text(formatTime(durationMs), color = Muted, fontSize = 11.sp)
        }
    }
}
