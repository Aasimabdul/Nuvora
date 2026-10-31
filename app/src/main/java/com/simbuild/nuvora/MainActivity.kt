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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

private data class LocalPlaylist(val id: String, val name: String, val trackIds: List<String>)

private fun YouTubeMusicResult.toMusicTrack(): MusicTrack {
    val duration = durationLabel.split(":").mapNotNull { it.toIntOrNull() }
        .fold(0) { total, part -> total * 60 + part }
    return MusicTrack(
        id = "youtube-$videoId",
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationSeconds = duration,
        permalink = watchUrl,
        // Metadata only: resolve a fresh public stream when playback starts.
        streamUrl = watchUrl
    )
}

private fun encodeMusicTracks(tracks: List<MusicTrack>): String = JSONArray().apply {
    tracks.forEach { track ->
        put(JSONObject()
            .put("id", track.id)
            .put("title", track.title)
            .put("artist", track.artist)
            .put("artworkUrl", track.artworkUrl ?: JSONObject.NULL)
            .put("durationSeconds", track.durationSeconds)
            .put("permalink", track.permalink)
            .put("streamUrl", track.streamUrl))
    }
}.toString()

private fun decodeMusicTracks(raw: String?): List<MusicTrack> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                add(MusicTrack(
                    id = id,
                    title = item.optString("title", "Untitled track"),
                    artist = item.optString("artist", "Unknown artist"),
                    artworkUrl = item.optString("artworkUrl").takeIf { it.startsWith("https://") },
                    durationSeconds = item.optInt("durationSeconds", 0),
                    permalink = item.optString("permalink", ""),
                    streamUrl = item.optString("streamUrl", "")
                ))
            }
        }
    }.getOrDefault(emptyList())
}

private fun encodePlaylists(playlists: List<LocalPlaylist>): String = JSONArray().apply {
    playlists.forEach { playlist ->
        put(JSONObject()
            .put("id", playlist.id)
            .put("name", playlist.name)
            .put("trackIds", JSONArray().apply { playlist.trackIds.forEach(::put) }))
    }
}.toString()

private fun decodePlaylists(raw: String?): List<LocalPlaylist> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                val ids = item.optJSONArray("trackIds")
                val trackIds = if (ids == null) emptyList() else
                    (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }
                add(LocalPlaylist(id, name, trackIds))
            }
        }
    }.getOrDefault(emptyList())
}

private fun decodePlayCounts(raw: String?): Map<String, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optInt(key, 0))
            }
        }
    }.getOrDefault(emptyMap())
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

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
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        this,
                        DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                            .setDefaultRequestProperties(mapOf(
                                "Accept" to "*/*",
                                "Accept-Language" to "en-US,en;q=0.9",
                                "Referer" to "https://www.youtube.com/",
                                "Origin" to "https://www.youtube.com"
                            ))
                            .setAllowCrossProtocolRedirects(true)
                    )
                )
            )
            .build()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuvoraApp(player: ExoPlayer, themeIndex: Int, onThemeChange: (Int) -> Unit, sharedYouTubeLink: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nuvora_settings", Context.MODE_PRIVATE) }
    var sourceUrl by remember { mutableStateOf(prefs.getString("source_url", "https://api.audius.co/v1") ?: "https://api.audius.co/v1") }
    var editingSource by remember { mutableStateOf(sourceUrl) }
    var selectedProvider by remember {
        val initialProvider = if (!prefs.getBoolean("default_provider_migrated_v041", false)) {
            prefs.edit()
                .putString("music_provider", "YouTube Music")
                .putBoolean("default_provider_migrated_v040", true)
                .apply()
            "YouTube Music"
        } else {
            prefs.getString("music_provider", "YouTube Music") ?: "YouTube Music"
        }
        mutableStateOf(initialProvider)
    }
    var showProviders by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showMoodSection by remember { mutableStateOf(prefs.getBoolean("home_moods", true)) }
    var showRecentSection by remember { mutableStateOf(prefs.getBoolean("home_recent", true)) }
    var showCarouselSection by remember { mutableStateOf(prefs.getBoolean("home_carousel", true)) }
    val providers = listOf("Audius", "YouTube Music", "JioSaavn", "Spotify", "SoundCloud", "Bandcamp")
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var playlistQuery by remember { mutableStateOf("") }
    var saavnPlaylists by remember { mutableStateOf<List<JioSaavnPlaylist>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    val knownTracks = remember {
        mutableStateListOf<MusicTrack>().apply {
            addAll(decodeMusicTracks(prefs.getString("known_tracks", null)))
        }
    }
    var youtubeResults by remember { mutableStateOf<List<YouTubeMusicResult>>(emptyList()) }
    var selectedYouTubeLink by remember { mutableStateOf(sharedYouTubeLink) }
    val recentlyPlayed = remember {
        mutableStateListOf<MusicTrack>().apply {
            val byId = knownTracks.associateBy { it.id }
            prefs.getString("recent_track_ids", "").orEmpty().split("|")
                .filter { it.isNotBlank() }.mapNotNull { byId[it] }.take(12).forEach(::add)
        }
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorite_ids", emptySet()).orEmpty().toSet())
    }
    val localPlaylists = remember {
        mutableStateListOf<LocalPlaylist>().apply {
            addAll(decodePlaylists(prefs.getString("local_playlists", null)))
        }
    }
    val selectedTrackIds = remember { mutableStateListOf<String>() }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }
    var playlistProvider by remember { mutableStateOf("YouTube Music") }
    var youtubePlaylists by remember { mutableStateOf<List<YouTubeMusicPlaylist>>(emptyList()) }
    val playCounts = remember {
        mutableStateMapOf<String, Int>().apply {
            putAll(decodePlayCounts(prefs.getString("play_counts", null)))
        }
    }
    var queueTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
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
            kotlinx.coroutines.delay(500)
        }
    }

    fun rememberTrack(track: MusicTrack) {
        val index = knownTracks.indexOfFirst { it.id == track.id }
        if (index >= 0) knownTracks[index] = track else knownTracks.add(track)
        prefs.edit().putString("known_tracks", encodeMusicTracks(knownTracks)).apply()
    }

    fun toggleFavorite(track: MusicTrack) {
        rememberTrack(track)
        favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
        prefs.edit().putStringSet("favorite_ids", favorites).apply()
    }

    fun toggleTrackSelection(track: MusicTrack) {
        rememberTrack(track)
        if (track.id in selectedTrackIds) selectedTrackIds.remove(track.id) else selectedTrackIds.add(track.id)
    }

    fun createLocalPlaylist(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val ids = selectedTrackIds.distinct().filter { id -> knownTracks.any { it.id == id } }
        val playlist = LocalPlaylist(
            id = "${System.currentTimeMillis()}-${localPlaylists.size}",
            name = cleanName,
            trackIds = ids
        )
        localPlaylists.add(0, playlist)
        prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
        selectedTrackIds.clear()
        expandedPlaylistId = playlist.id
        newPlaylistName = ""
        showCreatePlaylist = false
    }

    fun search(term: String) {
        if (selectedProvider == "YouTube Music") {
            tracks = emptyList()
            if (term.isBlank()) {
                youtubeResults = emptyList()
                loading = false
                error = "Enter a song or artist to search YouTube Music."
                return
            }
            loading = true
            error = null
            scope.launch {
                try {
                    youtubeResults = YouTubeMusicRepository.search(term)
                    queueTracks = youtubeResults.map { it.toMusicTrack() }
                    queueTracks.forEach(::rememberTrack)
                    error = if (youtubeResults.isEmpty()) "No YouTube Music results found." else null
                } catch (_: Exception) {
                    youtubeResults = emptyList()
                    error = "YouTube Music catalogue search failed. The unofficial search endpoint may be unavailable; try again later."
                } finally {
                    loading = false
                }
            }
            return
        }
        if (selectedProvider != "Audius" && selectedProvider != "JioSaavn") {
            tracks = emptyList()
            youtubeResults = emptyList()
            loading = false
            error = "$selectedProvider does not provide native audio playback in this build. Choose Audius or JioSaavn."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                tracks = if (selectedProvider == "JioSaavn") JioSaavnRepository.searchTracks(term) else AudiusRepository.searchTracks(term, baseUrl = sourceUrl.trim().trimEnd('/'))
                queueTracks = tracks
                tracks.forEach(::rememberTrack)
                if (tracks.isEmpty()) error = "No playable tracks found. Try another search."
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
                playerError = "Playback failed (${error.errorCodeName}). The source may have expired or refused the request; try again or choose another track."
                playing = false
                Toast.makeText(context, playerError, Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(sharedYouTubeLink) {
        selectedYouTubeLink = sharedYouTubeLink
        if (!sharedYouTubeLink.isNullOrBlank() && extractYouTubeVideoId(sharedYouTubeLink) != null) {
            selectedProvider = "YouTube Music"
            prefs.edit().putString("music_provider", selectedProvider).apply()
            tab = 1
        }
    }

    LaunchedEffect(Unit) { search("electronic chill") }

    fun play(track: MusicTrack) {
        rememberTrack(track)
        active = track
        recentlyPlayed.removeAll { it.id == track.id }
        recentlyPlayed.add(0, track)
        while (recentlyPlayed.size > 12) recentlyPlayed.removeAt(recentlyPlayed.lastIndex)
        prefs.edit().putString("recent_track_ids", recentlyPlayed.joinToString("|") { it.id }).apply()
        playCounts[track.id] = (playCounts[track.id] ?: 0) + 1
        val countsJson = JSONObject().apply { playCounts.forEach { (id, count) -> put(id, count) } }
        prefs.edit().putString("play_counts", countsJson.toString()).apply()
        playerError = null
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffleEnabled

        if (track.id.startsWith("youtube-")) {
            loading = true
            scope.launch {
                try {
                    val videoId = track.id.removePrefix("youtube-")
                    val streamUrl = YouTubeMusicPlaybackRepository.resolveAudioStream(videoId)
                    player.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(streamUrl).build())
                    player.prepare()
                    player.play()
                } catch (e: Exception) {
                    playerError = "Couldn't open this stream. YouTube may have blocked the request or the stream may have expired. Try another track or check your connection."
                    playing = false
                } finally {
                    loading = false
                }
            }
        } else {
            val queue = queueTracks.filter { !it.id.startsWith("youtube-") && it.streamUrl.startsWith("https://") }
            val startIndex = queue.indexOfFirst { it.id == track.id }
            if (queue.size > 1 && startIndex >= 0) {
                player.setMediaItems(queue.map { item ->
                    MediaItem.Builder().setMediaId(item.id).setUri(item.streamUrl).build()
                }, startIndex, 0L)
            } else {
                player.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(track.streamUrl).build())
            }
            player.prepare()
            player.play()
        }
    }

    fun playYouTubeResult(result: YouTubeMusicResult) {
        val track = result.toMusicTrack()
        rememberTrack(track)
        if (youtubeResults.isNotEmpty()) queueTracks = youtubeResults.map { it.toMusicTrack() }
        play(track)
    }

    fun skipNext() {
        val current = active ?: return
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            play(current)
            return
        }
        val queue = queueTracks.ifEmpty { listOf(current) }
        val index = queue.indexOfFirst { it.id == current.id }
        val next = if (shuffleEnabled && queue.size > 1) {
            queue.filterNot { it.id == current.id }.random()
        } else if (index in queue.indices && index < queue.lastIndex) {
            queue[index + 1]
        } else if (repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
            queue.first()
        } else null
        if (next != null) play(next)
    }

    fun skipPrevious() {
        val current = active ?: return
        if (player.currentPosition > 4_000L) {
            player.seekTo(0L)
            return
        }
        val queue = queueTracks.ifEmpty { listOf(current) }
        val index = queue.indexOfFirst { it.id == current.id }
        val previous = if (index > 0) queue[index - 1]
            else if (repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) queue.last()
            else null
        if (previous != null) play(previous) else player.seekTo(0L)
    }

    fun selectProvider(provider: String) {
        selectedProvider = provider
        prefs.edit().putString("music_provider", provider).apply()
        showProviders = false
        error = null
        tracks = emptyList()
        youtubeResults = emptyList()
        when (provider) {
            "YouTube Music" -> { tab = 1; search(query.ifBlank { "popular music" }) }
            "Audius", "JioSaavn" -> { tab = 1; search(query.ifBlank { "electronic chill" }) }
            else -> {
                val encoded = Uri.encode(query.ifBlank { "music" })
                val url = when (provider) {
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
                error = "$provider opens through its official app or website."
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 7.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab != 4) active?.let { track ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Panel.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Cyan.copy(alpha = 0.18f)),
                        tonalElevation = 4.dp,
                        shadowElevation = 14.dp,
                        modifier = Modifier.fillMaxWidth().clickable { tab = 4 }
                    ) {
                        Column(
                            Modifier.background(
                                Brush.verticalGradient(listOf(Panel.copy(alpha = 0.98f), Color(0xFF0D1528))),
                                RoundedCornerShape(22.dp)
                            ).padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(track.artworkUrl, Modifier.size(42.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                                }
                                IconButton(onClick = { skipNext() }) {
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
                    color = Panel.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Blue.copy(alpha = 0.18f)),
                    tonalElevation = 4.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(68.dp)
                    ) {
                        val labels = listOf("Home", "Search", "Playlists", "Settings")
                        val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.QueueMusic, Icons.Rounded.Settings)
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
                    OutlinedButton(
                        onClick = { showProviders = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.widthIn(max = 190.dp)
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectedProvider, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
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
                SearchBar(
                    query,
                    { query = it },
                    { tab = 1; search(query) },
                    { search(query.ifBlank { "popular music" }) },
                    placeholder = if (selectedProvider == "YouTube Music") "Search YouTube Music songs or artists…" else "Songs, artists, moods…"
                )
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
                    if (showRecentSection) {
                        val mostPlayed = knownTracks.filter { (playCounts[it.id] ?: 0) > 0 }
                            .sortedByDescending { playCounts[it.id] ?: 0 }.take(8)
                        if (mostPlayed.isNotEmpty()) item {
                            Text("Most played", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(mostPlayed, key = { "most-${it.id}" }) { track ->
                                    Column(Modifier.width(112.dp).clickable { queueTracks = mostPlayed; play(track) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        if (selectedProvider == "YouTube Music" && youtubeResults.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(youtubeResults.take(8), key = { "discover-youtube-${it.videoId}" }) { result ->
                                    val track = result.toMusicTrack()
                                    Column(Modifier.width(124.dp).clickable { queueTracks = youtubeResults.map { it.toMusicTrack() }; playYouTubeResult(result) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Cover(result.artworkUrl, Modifier.size(124.dp))
                                        Text(result.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(result.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        } else if (tracks.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(tracks.take(8), key = { "discover-${it.id}" }) { track ->
                                    Column(Modifier.width(124.dp).clickable { queueTracks = tracks; play(track) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Cover(track.artworkUrl, Modifier.size(124.dp))
                                        Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Trending tracks", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)) }
                    if (selectedProvider == "YouTube Music") {
                        items(youtubeResults, key = { "trending-youtube-${it.videoId}" }) { result ->
                            val track = result.toMusicTrack()
                            TrackRow(track, active?.id == track.id, { queueTracks = youtubeResults.map { it.toMusicTrack() }; playYouTubeResult(result) },
                                { toggleFavorite(track) }, track.id in favorites, {}, { toggleTrackSelection(track) }, track.id in selectedTrackIds)
                        }
                    } else {
                        items(tracks, key = { "trending-${it.id}" }) { track ->
                            TrackRow(track, active?.id == track.id, { queueTracks = tracks; play(track) },
                                { toggleFavorite(track) }, track.id in favorites, { downloadTrack(context, track) },
                                { toggleTrackSelection(track) }, track.id in selectedTrackIds)
                        }
                    }
                    item { Text("Catalogue by Audius · Track availability depends on creator settings.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
            1 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Search music", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp))
                SearchBar(
                    query,
                    { query = it },
                    { search(query) },
                    { search(query) },
                    placeholder = if (selectedProvider == "YouTube Music") "Search YouTube Music songs or artists…" else "Songs, artists, moods…"
                )
                StatusMessages(loading, error)
                if (selectedProvider == "YouTube Music") {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(youtubeResults, key = { it.videoId }) { result ->
                            val track = result.toMusicTrack()
                            TrackRow(
                                track = track,
                                isActive = active?.id == track.id,
                                onPlay = { queueTracks = youtubeResults.map { it.toMusicTrack() }; playYouTubeResult(result) },
                                onFavorite = { toggleFavorite(track) },
                                favorite = track.id in favorites,
                                onSelect = { toggleTrackSelection(track) },
                                selected = track.id in selectedTrackIds
                            )
                        }
                    }
                } else LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(track, active?.id == track.id, { queueTracks = tracks; play(track) },
                            { toggleFavorite(track) }, track.id in favorites, { downloadTrack(context, track) },
                            { toggleTrackSelection(track) }, track.id in selectedTrackIds)
                    }
                }
            }
            2 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Your library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Playlists, likes and listening history", color = Muted, fontSize = 12.sp)
                    }
                    Button(onClick = { showCreatePlaylist = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create")
                    }
                }
                if (selectedTrackIds.isNotEmpty()) {
                    Surface(
                        color = Blue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Blue.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Cyan)
                            Text("${selectedTrackIds.size} tracks selected", modifier = Modifier.weight(1f), color = Cyan)
                            TextButton(onClick = { selectedTrackIds.clear() }) { Text("Clear") }
                        }
                    }
                }
                val saved = knownTracks.filter { it.id in favorites }
                val mostPlayed = knownTracks.filter { (playCounts[it.id] ?: 0) > 0 }
                    .sortedByDescending { playCounts[it.id] ?: 0 }
                LazyColumn(
                    Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Liked songs", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (saved.isEmpty()) {
                        item { Text("Use the heart on any result to keep it here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                    } else {
                        items(saved, key = { "liked-${it.id}" }) { track ->
                            TrackRow(
                                track, active?.id == track.id, { queueTracks = saved; play(track) },
                                { toggleFavorite(track) }, true,
                                { if (!track.id.startsWith("youtube-")) downloadTrack(context, track) },
                                { toggleTrackSelection(track) }, track.id in selectedTrackIds
                            )
                        }
                    }
                    if (mostPlayed.isNotEmpty()) {
                        item { Text("Most played", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                        items(mostPlayed.take(10), key = { "mostplayed-${it.id}" }) { track ->
                            TrackRow(
                                track, active?.id == track.id, { queueTracks = mostPlayed; play(track) },
                                { toggleFavorite(track) }, track.id in favorites,
                                { if (!track.id.startsWith("youtube-")) downloadTrack(context, track) },
                                { toggleTrackSelection(track) }, track.id in selectedTrackIds
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("My playlists", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${localPlaylists.size}", color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (localPlaylists.isEmpty()) {
                        item {
                            Text(
                                "Create a playlist, then select tracks in Search with the playlist icon. You can make playlists from YouTube Music, Audius and JioSaavn results.",
                                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    items(localPlaylists, key = { "playlist-${it.id}" }) { playlist ->
                        val playlistTracks = playlist.trackIds.mapNotNull { id -> knownTracks.firstOrNull { it.id == id } }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)
                                .border(BorderStroke(1.dp, Blue.copy(alpha = 0.13f)), RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(13.dp), color = Blue.copy(alpha = 0.16f), modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Cyan, modifier = Modifier.size(23.dp))
                                    }
                                }
                                Column(
                                    Modifier.weight(1f).clickable {
                                        expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id
                                    }.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${playlistTracks.size} tracks", color = Muted, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id
                                }) {
                                    Icon(
                                        if (expandedPlaylistId == playlist.id) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = "Expand playlist",
                                        tint = Cyan
                                    )
                                }
                                IconButton(onClick = {
                                    localPlaylists.removeAll { it.id == playlist.id }
                                    prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
                                    if (expandedPlaylistId == playlist.id) expandedPlaylistId = null
                                }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist", tint = Muted) }
                            }
                            if (expandedPlaylistId == playlist.id) {
                                HorizontalDivider(color = Muted.copy(alpha = 0.14f))
                                if (selectedTrackIds.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            val index = localPlaylists.indexOfFirst { it.id == playlist.id }
                                            if (index >= 0) {
                                                val old = localPlaylists[index]
                                                localPlaylists[index] = old.copy(trackIds = (old.trackIds + selectedTrackIds).distinct())
                                                prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
                                            }
                                            selectedTrackIds.clear()
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Add selected tracks", color = Cyan) }
                                }
                                if (playlistTracks.isEmpty()) {
                                    Text("This playlist is empty. Search songs and select them, then tap Add selected tracks.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                } else {
                                    playlistTracks.forEach { track ->
                                        TrackRow(
                                            track, active?.id == track.id,
                                            { queueTracks = playlistTracks; play(track) },
                                            { toggleFavorite(track) }, track.id in favorites,
                                            { if (!track.id.startsWith("youtube-")) downloadTrack(context, track) },
                                            { toggleTrackSelection(track) }, track.id in selectedTrackIds
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Streaming playlists", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            FilterChip(
                                selected = playlistProvider == "YouTube Music",
                                onClick = { playlistProvider = "YouTube Music"; youtubePlaylists = emptyList() },
                                label = { Text("YouTube Music") },
                                leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = playlistProvider == "JioSaavn",
                                onClick = { playlistProvider = "JioSaavn"; saavnPlaylists = emptyList() },
                                label = { Text("JioSaavn") },
                                leadingIcon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = playlistQuery,
                                onValueChange = { playlistQuery = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Search playlist names") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    scope.launch {
                                        loading = true; error = null
                                        try {
                                            if (playlistProvider == "YouTube Music") {
                                                youtubePlaylists = YouTubeMusicPlaylistsRepository.searchPlaylists(playlistQuery)
                                                if (youtubePlaylists.isEmpty()) error = "No YouTube Music playlists found. Try a different search."
                                            } else {
                                                saavnPlaylists = JioSaavnRepository.searchPlaylists(playlistQuery)
                                                if (saavnPlaylists.isEmpty()) error = "No JioSaavn playlists found."
                                            }
                                        } catch (e: Exception) {
                                            error = "Playlist search failed. The provider's public endpoint may be unavailable."
                                        } finally { loading = false }
                                    }
                                })
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    loading = true; error = null
                                    try {
                                        if (playlistProvider == "YouTube Music") {
                                            youtubePlaylists = YouTubeMusicPlaylistsRepository.searchPlaylists(playlistQuery)
                                            if (youtubePlaylists.isEmpty()) error = "No YouTube Music playlists found. Try a different search."
                                        } else {
                                            saavnPlaylists = JioSaavnRepository.searchPlaylists(playlistQuery)
                                            if (saavnPlaylists.isEmpty()) error = "No JioSaavn playlists found."
                                        }
                                    } catch (e: Exception) {
                                        error = "Playlist search failed. The provider's public endpoint may be unavailable."
                                    } finally { loading = false }
                                }
                            }) { Icon(Icons.Rounded.Search, contentDescription = "Search playlists", tint = Cyan) }
                        }
                    }
                    if (playlistProvider == "YouTube Music") {
                        items(youtubePlaylists, key = { "ytplaylist-${it.id}" }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                                    .clickable {
                                        scope.launch {
                                            loading = true; error = null
                                            try {
                                                val imported = YouTubeMusicPlaylistsRepository.getPlaylistTracks(playlist.id)
                                                if (imported.isEmpty()) {
                                                    error = "This playlist did not return any public tracks."
                                                } else {
                                                    imported.forEach(::rememberTrack)
                                                    val local = LocalPlaylist(
                                                        id = "${System.currentTimeMillis()}-${localPlaylists.size}",
                                                        name = playlist.title,
                                                        trackIds = imported.map { it.id }
                                                    )
                                                    localPlaylists.add(0, local)
                                                    prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
                                                    queueTracks = imported
                                                    expandedPlaylistId = local.id
                                                    Toast.makeText(context, "Imported ${imported.size} tracks to My playlists", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                error = "Couldn't load that YouTube Music playlist. The unofficial endpoint may have changed."
                                            } finally { loading = false }
                                        }
                                    }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Cover(playlist.artworkUrl, Modifier.size(54.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(playlist.subtitle, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Tap to import into Nuvora", color = Cyan, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.DownloadDone, contentDescription = "Import playlist", tint = Cyan)
                            }
                        }
                    } else {
                        items(saavnPlaylists, key = { "saavnplaylist-${it.id}" }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                                    .clickable {
                                        scope.launch {
                                            loading = true; error = null
                                            try {
                                                val imported = JioSaavnRepository.getPlaylistTracks(playlist.id)
                                                if (imported.isEmpty()) error = "This JioSaavn playlist did not return playable tracks."
                                                else {
                                                    imported.forEach(::rememberTrack)
                                                    val local = LocalPlaylist(
                                                        id = "${System.currentTimeMillis()}-${localPlaylists.size}",
                                                        name = playlist.title,
                                                        trackIds = imported.map { it.id }
                                                    )
                                                    localPlaylists.add(0, local)
                                                    prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
                                                    queueTracks = imported
                                                    expandedPlaylistId = local.id
                                                }
                                            } catch (e: Exception) {
                                                error = "Couldn't load this JioSaavn playlist. Its community API may have changed."
                                            } finally { loading = false }
                                        }
                                    }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Cover(playlist.artworkUrl, Modifier.size(54.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(playlist.subtitle, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Tap to import into Nuvora", color = Cyan, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.DownloadDone, contentDescription = "Import playlist", tint = Cyan)
                            }
                        }
                    }
                    if (loading || error != null) item { StatusMessages(loading, error) }
                    item { Spacer(Modifier.height(18.dp)) }
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
                OutlinedButton(onClick = { showProviders = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                    Text("  Source: $selectedProvider", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
                Text(
                    when (selectedProvider) {
                        "Audius" -> "IN-APP PLAYBACK · Search and stream available Audius tracks inside Nuvora using Media3. Availability depends on creator settings."
                        "YouTube Music" -> "IN-APP AUDIO · Search results resolve through NewPipe Extractor and play with Media3 when YouTube provides a compatible public audio stream. Some videos may fail."
                        "JioSaavn" -> "IN-APP PLAYBACK · Uses a community API to search and stream available tracks. Endpoint availability may change."
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
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    placeholder: String = "Songs, artists, moods…"
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
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
