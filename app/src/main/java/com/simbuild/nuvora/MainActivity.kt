package com.simbuild.nuvora

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

private fun scanDeviceAudio(context: Context, excluded: Set<String>): List<MusicTrack> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATA)
    val result = mutableListOf<MusicTrack>()
    context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val displayName = cursor.getString(nameCol).orEmpty()
            val filePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else displayName
            if (excluded.any { it.isNotBlank() && (filePath.contains(it, true) || displayName.contains(it, true)) }) continue
            val id = cursor.getLong(idCol)
            val uri = Uri.withAppendedPath(collection, id.toString()).toString()
            val albumId = cursor.getLong(albumCol)
            result += MusicTrack(
                id = "local:$id",
                title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: displayName,
                artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                artworkUrl = "content://media/external/audio/albumart/$albumId",
                durationSeconds = (cursor.getLong(durationCol) / 1000L).toInt(),
                permalink = uri,
                streamUrl = uri
            )
        }
    }
    return result
}

private fun clearAppCache(context: Context): Boolean = try {
    context.cacheDir.listFiles()?.forEach { file -> if (file.isDirectory) file.deleteRecursively() else file.delete() }
    true
} catch (_: Exception) { false }

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
    private var sharedYouTubeLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedYouTubeLink = youtubeLinkFromIntent(intent)
        player = ExoPlayer.Builder(this).build()
        player.setHandleAudioBecomingNoisy(true)
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
    var localTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var excludedFolders by remember { mutableStateOf(prefs.getString("excluded_folders", "") ?: "") }
    var showAbout by remember { mutableStateOf(false) }
    var resumeLast by remember { mutableStateOf(prefs.getBoolean("resume_last", true)) }
    var gapless by remember { mutableStateOf(prefs.getBoolean("gapless", true)) }
    var pauseOnDisconnect by remember { mutableStateOf(prefs.getBoolean("pause_disconnect", true)) }
    var normalizeVolume by remember { mutableStateOf(prefs.getBoolean("normalize_volume", false)) }
    var sleepRemaining by remember { mutableIntStateOf(0) }
    var sleepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val providers = listOf("Audius", "YouTube Music", "JioSaavn", "Spotify", "SoundCloud", "Bandcamp")
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    val recentlyPlayed = remember { mutableStateListOf<MusicTrack>() }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var active by remember { mutableStateOf<MusicTrack?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(player, active, playing) {
        while (active != null) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            if (resumeLast && active != null) prefs.edit().putLong("last_position", positionMs).apply()
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

    LaunchedEffect(Unit) {
        search("electronic chill")
        if (resumeLast) {
            val stream = prefs.getString("last_stream", null)
            val title = prefs.getString("last_title", null)
            if (!stream.isNullOrBlank() && !title.isNullOrBlank()) {
                val restored = MusicTrack(
                    id = prefs.getString("last_id", "restored") ?: "restored",
                    title = title,
                    artist = prefs.getString("last_artist", "Unknown artist") ?: "Unknown artist",
                    artworkUrl = prefs.getString("last_artwork", null),
                    durationSeconds = prefs.getInt("last_duration", 0),
                    permalink = prefs.getString("last_permalink", stream) ?: stream,
                    streamUrl = stream
                )
                active = restored
                player.setMediaItem(MediaItem.fromUri(stream))
                player.prepare()
                val savedPosition = prefs.getLong("last_position", 0L)
                if (savedPosition > 0L) player.seekTo(savedPosition)
                recentlyPlayed.add(restored)
            }
        }
    }

    fun play(track: MusicTrack) {
        active = track
        if (resumeLast) prefs.edit()
            .putString("last_id", track.id)
            .putString("last_title", track.title)
            .putString("last_artist", track.artist)
            .putString("last_artwork", track.artworkUrl)
            .putInt("last_duration", track.durationSeconds)
            .putString("last_stream", track.streamUrl)
            .putString("last_permalink", track.permalink)
            .putLong("last_position", 0L)
            .apply()
        recentlyPlayed.removeAll { it.id == track.id }
        recentlyPlayed.add(0, track)
        while (recentlyPlayed.size > 12) recentlyPlayed.removeAt(recentlyPlayed.lastIndex)
        playerError = null
        player.setMediaItem(MediaItem.fromUri(track.streamUrl))
        player.prepare()
        player.play()
    }

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
                                    thumb = { SliderDefaults.Thumb(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, colors = SliderDefaults.colors(thumbColor = Cyan)) },
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
                        in 5..11 -> "Good morning. Find your frequency."
                        in 12..16 -> "Good afternoon. Find your frequency."
                        in 17..21 -> "Good evening. Find your frequency."
                        else -> "Welcome back. Find your frequency."
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
                                    Column(Modifier.width(112.dp).clickable { play(track) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
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
                            favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
                        }, track.id in favorites, { downloadTrack(context, track) })
                    }
                }
            }
            2 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Library", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            localTracks = scanDeviceAudio(context, excludedFolders.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet())
                            scanMessage = "Scanned " + localTracks.size + " local audio tracks."
                        } else scanMessage = "Audio permission is needed to scan your music library."
                    }
                    OutlinedButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            localTracks = scanDeviceAudio(context, excludedFolders.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet())
                            scanMessage = "Scanned " + localTracks.size + " local audio tracks."
                        } else scanLauncher.launch(permission)
                    }) { Icon(Icons.Rounded.Refresh, contentDescription = null); Text(" Scan") }
                }
                if (scanMessage != null) Text(scanMessage!!, color = Muted, modifier = Modifier.padding(horizontal = 20.dp))
                if (localTracks.isNotEmpty()) {
                    Text("On this device · " + localTracks.size, color = Cyan, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(localTracks, key = { it.id }) { track ->
                            TrackRow(track, active?.id == track.id, { play(track) }, {
                                favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
                            }, track.id in favorites, { downloadTrack(context, track) })
                        }
                    }
                }
                Text("Your favourites", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
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
            3 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Discover", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
                SearchBar(query, { query = it }, { search(query) }, { search("electronic chill") })
                StatusMessages(loading, error)
                LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Trending discoveries", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(tracks, key = { "discover-tab-${it.id}" }) { track ->
                        TrackRow(track, active?.id == track.id, { play(track) }, {
                            favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
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
                Text("Playback & audio", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Resume last played song")
                        Text("Restore last track after reopening the app", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = resumeLast, onCheckedChange = { resumeLast = it; prefs.edit().putBoolean("resume_last", it).apply() })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gapless playback (where supported)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = gapless, onCheckedChange = { gapless = it; prefs.edit().putBoolean("gapless", it).apply(); player.setPauseAtEndOfMediaItems(!it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pause when headphones disconnect", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = pauseOnDisconnect, onCheckedChange = { pauseOnDisconnect = it; prefs.edit().putBoolean("pause_disconnect", it).apply(); player.setHandleAudioBecomingNoisy(it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume normalization (basic volume cap)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = normalizeVolume, onCheckedChange = { normalizeVolume = it; prefs.edit().putBoolean("normalize_volume", it).apply(); player.volume = if (it) 0.85f else 1f })
                }
                Text("Sleep timer", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(if (sleepRemaining > 0) "Timer active · " + sleepRemaining + " min remaining" else "Pause playback after a set time", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        OutlinedButton(onClick = {
                            sleepJob?.cancel()
                            sleepRemaining = minutes
                            sleepJob = scope.launch {
                                repeat(minutes * 60) {
                                    delay(1000)
                                    sleepRemaining = ((minutes * 60 - it - 1) / 60) + 1
                                }
                                player.pause()
                                sleepRemaining = 0
                                Toast.makeText(context, "Sleep timer finished", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(minutes.toString() + "m") }
                    }
                    TextButton(onClick = { sleepJob?.cancel(); sleepRemaining = 0 }) { Text("Off") }
                }
                Text("Excluded folders", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = excludedFolders,
                    onValueChange = { excludedFolders = it; prefs.edit().putString("excluded_folders", it).apply() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Folder names/paths, comma-separated") },
                    placeholder = { Text("WhatsApp, Podcasts, Recordings") },
                    minLines = 2
                )
                Text("Applied when scanning the local library.", color = Muted, fontSize = 11.sp)
                OutlinedButton(onClick = {
                    Toast.makeText(context, if (clearAppCache(context)) "App and artwork cache cleared" else "Could not clear cache", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null); Text("  Clear cache & artwork cache")
                }
                OutlinedButton(onClick = { showAbout = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Info, contentDescription = null); Text("  About Nuvora · Version 0.3.7 · Open-source credits")
                }
                if (showAbout) AlertDialog(
                    onDismissRequest = { showAbout = false },
                    title = { Text("About Nuvora") },
                    text = { Text("Nuvora 0.3.7\nBuilt with Kotlin, Jetpack Compose, AndroidX Media3 and Coil. Open-source project by Aasim.\n\nAudius streaming and local audio playback are in-app. YouTube uses official embedded video. Other streaming providers require authorized API integrations for native playback.") },
                    confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } }
                )
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
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YouTube Music", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Play an official YouTube video inside Nuvora. Find a song in YouTube Music, use Share → Copy link, then paste it here.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("YouTube / YouTube Music link") },
            placeholder = { Text("https://music.youtube.com/watch?v=…") },
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
                    modifier = Modifier.fillMaxWidth().height(240.dp),
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
