package com.simbuild.nuvora

import android.Manifest
import android.app.DownloadManager
import android.media.audiofx.Equalizer
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


private data class NuvoraTheme(
    val label: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val isLight: Boolean = false,
    val dynamic: Boolean = false
)

/** Existing themes are kept first so saved theme indices remain compatible. */
private val themes = listOf(
    NuvoraTheme("Neon Blue", Color(0xFF080D1A), Color(0xFF121B2E), Color(0xFF4D8DFF), Color(0xFF5CE1E6)),
    NuvoraTheme("AMOLED Black", Color(0xFF000000), Color(0xFF101010), Color(0xFF4D8DFF), Color(0xFF5CE1E6)),
    NuvoraTheme("Purple Night", Color(0xFF10091D), Color(0xFF21152F), Color(0xFFB18CFF), Color(0xFFE0B8FF)),
    NuvoraTheme("Forest", Color(0xFF071510), Color(0xFF10251D), Color(0xFF48C78E), Color(0xFF9DE8C0)),
    NuvoraTheme("Light", Color(0xFFF4F7FC), Color(0xFFFFFFFF), Color(0xFF245DCE), Color(0xFF007F91), isLight = true),
    NuvoraTheme("Midnight Navy", Color(0xFF07111F), Color(0xFF12233A), Color(0xFF4F9BFF), Color(0xFF86D7FF)),
    NuvoraTheme("Pixel Blue", Color(0xFF0B1424), Color(0xFF18283E), Color(0xFF8AB4F8), Color(0xFFD3E3FD)),
    NuvoraTheme("Liquid Glass", Color(0xFF091322), Color(0xB8203048), Color(0xFF72B7FF), Color(0xFF9BE7F5)),
    NuvoraTheme("Neo Blue", Color(0xFF111B2C), Color(0xFF17243A), Color(0xFF65A7FF), Color(0xFF8BC8FF)),
    NuvoraTheme("Arctic Cyan", Color(0xFF07171D), Color(0xFF112A33), Color(0xFF4DE1E6), Color(0xFFB2FAF4)),
    NuvoraTheme("Aurora", Color(0xFF100F25), Color(0xFF211D40), Color(0xFF9D91FF), Color(0xFF68D8F2)),
    NuvoraTheme("Sunset Coral", Color(0xFF21121A), Color(0xFF35202A), Color(0xFFFF8A80), Color(0xFFFFC2A1)),
    NuvoraTheme("Pearl Light", Color(0xFFF5F4F8), Color(0xFFFFFFFF), Color(0xFF6555A5), Color(0xFFB44C73), isLight = true),
    NuvoraTheme("Material You Dynamic", Color(0xFF101318), Color(0xFF20242B), Color(0xFF9DBDFF), Color(0xFFC4C6D0), dynamic = true),
    NuvoraTheme("Graphite Silver", Color(0xFF101113), Color(0xFF222428), Color(0xFFC2C7D0), Color(0xFF8E98A8)),
    NuvoraTheme("Ocean Teal", Color(0xFF061B20), Color(0xFF103036), Color(0xFF4DD0C8), Color(0xFF9AECE1))
)

private data class LocalPlaylist(val id: String, val name: String, val trackIds: List<String>)
private data class EqPreset(val name: String, val levels: List<Int>)

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
                    artworkUrl = normalizeArtworkUrl(item.optString("artworkUrl").takeIf { it != "null" })
                        ?: id.removePrefix("youtube-").takeIf { id.startsWith("youtube-") && it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                            ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
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

private const val DISCOVERY_TERM = "electronic chill"

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun explainPlaybackFailure(error: PlaybackException): String {
    val causes = generateSequence(error as Throwable?) { it.cause }
        .take(5)
        .mapNotNull { throwable ->
            throwable.message?.takeIf { it.isNotBlank() }
                ?: throwable.javaClass.simpleName.takeIf { it.isNotBlank() }
        }
        .distinct()
        .joinToString(" → ")
    return when {
        causes.contains("UnknownHostException", ignoreCase = true) ->
            "Could not resolve the audio host. Check mobile data/Wi-Fi or private DNS, then retry so Nuvora can fetch a fresh stream."
        causes.contains("SocketTimeoutException", ignoreCase = true) ->
            "The audio server timed out. Check your connection and retry."
        causes.contains("403", ignoreCase = true) || causes.contains("404", ignoreCase = true) ->
            "The stream URL was refused or expired. Retry to request a fresh stream, or open the track in YouTube Music."
        else -> "Playback failed (${error.errorCodeName}). " +
            causes.takeIf { it.isNotBlank() }?.take(220).orEmpty()
    }
}

private fun downloadTrack(context: Context, track: MusicTrack) {
    try {
        val safeTitle = track.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(70).ifBlank { "Nuvora track" }
        val audioUri = Uri.parse(track.streamUrl)
        require(audioUri.scheme.equals("https", ignoreCase = true) && !audioUri.host.isNullOrBlank()) { "No downloadable audio URL" }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(audioUri)
            .setTitle(safeTitle)
            .setDescription("Downloading with Nuvora")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Nuvora/$safeTitle.mp3")
        manager.enqueue(request)
        val artwork = normalizeArtworkUrl(track.artworkUrl)
        if (!artwork.isNullOrBlank() && artwork.startsWith("https://", ignoreCase = true)) runCatching {
            val artworkRequest = DownloadManager.Request(Uri.parse(artwork))
                .setTitle("$safeTitle artwork")
                .setDescription("Saving Nuvora artwork")
                .setMimeType("image/jpeg")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Nuvora/Artwork/$safeTitle.jpg")
            manager.enqueue(artworkRequest)
        }
        Toast.makeText(context, "Downloading audio and available artwork: $safeTitle", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
    }
}

/** Only official, API-backed sources (Audius) are played through the background MediaSession service. */
private fun isOfficialTrack(track: MusicTrack): Boolean =
    !track.id.startsWith("youtube-") && !track.id.startsWith("jiosaavn-")

private fun MusicTrack.toServiceMediaItem(): MediaItem {
    val uri = Uri.parse(streamUrl)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setDurationMs(durationSeconds * 1000L)
                .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                .build()
        )
        .build()
}

private fun speedLabel(v: Float): String = if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"

private class QueueActions(
    val playNext: (MusicTrack) -> Unit,
    val addToQueue: (MusicTrack) -> Unit,
    val download: (MusicTrack) -> Unit,
    val addToPlaylist: (MusicTrack) -> Unit,
    val openEqualizer: () -> Unit
)

private val LocalQueueActions = compositionLocalOf<QueueActions?> { null }

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var localEqualizer: Equalizer? = null
    private var localEqualizerSessionId: Int = 0
    private var localEqEnabled: Boolean = false
    private var localEqLevels: List<Short> = List(5) { 0.toShort() }
    private var sharedYouTubeLink by mutableStateOf<String?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var servicePlayer by mutableStateOf<MediaController?>(null)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
                                "Accept-Language" to "en-US,en;q=0.9"
                            ))
                            .setAllowCrossProtocolRedirects(true)
                    )
                )
            )
            .build()
        PlaybackBridge.applyLocalEqualizer = { enabled, levels -> applyLocalEqualizer(enabled, levels) }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) applyLocalEqualizer(localEqEnabled, localEqLevels)
            }
        })
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            servicePlayer = try { future.get() } catch (_: Exception) { null }
        }, ContextCompat.getMainExecutor(this))
        setContent {
            val prefs = getSharedPreferences("nuvora_settings", MODE_PRIVATE)
            var themeIndex by remember { mutableIntStateOf(prefs.getInt("theme_index", 0).coerceIn(themes.indices)) }
            val chosenTheme = themes[themeIndex]
            val systemDark = isSystemInDarkTheme()
            val colorScheme = when {
                chosenTheme.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (systemDark) dynamicDarkColorScheme(this@MainActivity) else dynamicLightColorScheme(this@MainActivity)
                chosenTheme.isLight -> lightColorScheme(
                    primary = chosenTheme.primary, secondary = chosenTheme.secondary, background = chosenTheme.background,
                    surface = chosenTheme.surface, onBackground = Color(0xFF111827), onSurface = Color(0xFF111827)
                )
                else -> darkColorScheme(
                    primary = chosenTheme.primary, secondary = chosenTheme.secondary, background = chosenTheme.background,
                    surface = chosenTheme.surface, onBackground = Color(0xFFF4F7FF), onSurface = Color(0xFFF4F7FF)
                )
            }
            MaterialTheme(colorScheme = colorScheme) {
                NuvoraApp(player, servicePlayer, themeIndex, { themeIndex = it; prefs.edit().putInt("theme_index", it).apply() }, sharedYouTubeLink)
            }
        }
    }

    private fun applyLocalEqualizer(enabled: Boolean, levels: List<Short>) {
        localEqEnabled = enabled
        localEqLevels = levels.toList()
        val sessionId = player.audioSessionId
        // The audio session may be unset until the first track is ready.
        if (sessionId <= 0) return
        if (localEqualizer == null || localEqualizerSessionId != sessionId) {
            runCatching { localEqualizer?.release() }
            localEqualizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
            localEqualizerSessionId = if (localEqualizer != null) sessionId else 0
        }
        val effect = localEqualizer ?: return
        runCatching {
            val range = effect.bandLevelRange
            val count = effect.numberOfBands.toInt()
            for (band in 0 until count) {
                val index = if (count <= 1) 0 else band * (levels.size - 1) / (count - 1)
                effect.setBandLevel(band.toShort(), levels.getOrElse(index) { 0 }.coerceIn(range[0], range[1]))
            }
            effect.enabled = enabled && count > 0
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedYouTubeLink = youtubeLinkFromIntent(intent)
    }

    override fun onDestroy() {
        PlaybackBridge.onNext = null
        PlaybackBridge.onPrevious = null
        PlaybackBridge.applyLocalEqualizer = null
        runCatching { localEqualizer?.enabled = false }
        runCatching { localEqualizer?.release() }
        localEqualizer = null
        localEqualizerSessionId = 0
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuvoraApp(localPlayer: ExoPlayer, servicePlayer: Player?, themeIndex: Int, onThemeChange: (Int) -> Unit, sharedYouTubeLink: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nuvora_settings", Context.MODE_PRIVATE) }
    var useService by remember { mutableStateOf(false) }
    val player: Player = if (useService && servicePlayer != null) servicePlayer else localPlayer
    var sourceUrl by remember { mutableStateOf(prefs.getString("source_url", "https://api.audius.co/v1") ?: "https://api.audius.co/v1") }
    var editingSource by remember { mutableStateOf(sourceUrl) }
    var selectedProvider by remember {
        val initialProvider = if (!prefs.getBoolean("default_provider_migrated_v041", false)) {
            prefs.edit()
                .putString("music_provider", "YouTube Music")
                .putBoolean("default_provider_migrated_v041", true)
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
    // Back from any non-home screen returns to Home instead of immediately exiting.
    BackHandler(enabled = tab != 0) { tab = 0 }
    var query by remember { mutableStateOf("") }
    var playlistQuery by remember { mutableStateOf("") }
    var searchResultTab by remember { mutableIntStateOf(0) } // 0 = Songs, 1 = Playlists
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
    var trackForPlaylist by remember { mutableStateOf<MusicTrack?>(null) }
    var playlistNameForTrack by remember { mutableStateOf("") }
    var showEqualizer by remember { mutableStateOf(false) }
    var showNowPlayingMenu by remember { mutableStateOf(false) }
    var customSleepMinutes by remember { mutableStateOf("30") }
    var eqEnabled by remember { mutableStateOf(prefs.getBoolean("eq_enabled", false)) }
    val eqLevels = remember {
        mutableStateListOf<Int>().apply {
            val stored = prefs.getString("eq_levels", null).orEmpty().split(",").mapNotNull { it.toIntOrNull() }
            addAll((0 until 5).map { stored.getOrNull(it)?.coerceIn(-12, 12) ?: 0 })
        }
    }
    var eqPresetName by remember { mutableStateOf("") }
    val customEqPresets = remember {
        mutableStateListOf<EqPreset>().apply {
            val raw = prefs.getString("eq_custom_presets", null)
            if (!raw.isNullOrBlank()) runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val bands = item.optJSONArray("levels") ?: continue
                    add(EqPreset(name, (0 until 5).map { bands.optInt(it, 0).coerceIn(-12, 12) }))
                }
            }
        }
    }
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
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var audiusFeedTerm by remember { mutableStateOf(DISCOVERY_TERM) }
    var canLoadMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
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

    LaunchedEffect(eqEnabled, eqLevels.toList(), servicePlayer, localPlayer) {
        val requestedLevels = eqLevels.map { (it * 100).toShort() }
        PlaybackBridge.applyEqualizer?.invoke(eqEnabled, requestedLevels)
        PlaybackBridge.applyLocalEqualizer?.invoke(eqEnabled, requestedLevels)
    }

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            kotlinx.coroutines.delay(sleepTimerMinutes * 60_000L)
            localPlayer.pause()
            servicePlayer?.pause()
            sleepTimerMinutes = 0
            Toast.makeText(context, "Sleep timer finished", Toast.LENGTH_SHORT).show()
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

    fun addTrackToPlaylist(track: MusicTrack, playlistId: String) {
        rememberTrack(track)
        val index = localPlaylists.indexOfFirst { it.id == playlistId }
        if (index < 0) return
        val playlist = localPlaylists[index]
        localPlaylists[index] = playlist.copy(trackIds = (playlist.trackIds + track.id).distinct())
        prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
        trackForPlaylist = null
        Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
    }

    fun applyEqPreset(levels: List<Int>) {
        for (i in 0 until 5) eqLevels[i] = levels.getOrNull(i)?.coerceIn(-12, 12) ?: 0
        eqEnabled = true
        prefs.edit().putString("eq_levels", eqLevels.joinToString(",")).putBoolean("eq_enabled", true).apply()
    }

    fun saveEqPreset(name: String) {
        val clean = name.trim().take(30)
        if (clean.isBlank()) return
        customEqPresets.removeAll { it.name.equals(clean, ignoreCase = true) }
        customEqPresets.add(0, EqPreset(clean, eqLevels.toList()))
        val json = JSONArray().apply {
            customEqPresets.forEach { preset -> put(JSONObject().put("name", preset.name).put("levels", JSONArray(preset.levels))) }
        }
        prefs.edit().putString("eq_custom_presets", json.toString()).apply()
        eqPresetName = ""
        Toast.makeText(context, "Saved equalizer preset: $clean", Toast.LENGTH_SHORT).show()
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
                val base = sourceUrl.trim().trimEnd('/')
                tracks = if (selectedProvider == "JioSaavn") JioSaavnRepository.searchTracks(term)
                else if (term.isBlank() || term == DISCOVERY_TERM) AudiusRepository.trendingTracks(baseUrl = base)
                else AudiusRepository.searchTracks(term, baseUrl = base)
                audiusFeedTerm = term
                canLoadMore = selectedProvider == "Audius" && tracks.size >= 25
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

    fun loadMore() {
        if (selectedProvider != "Audius" || loadingMore || loading || !canLoadMore || tracks.isEmpty()) return
        loadingMore = true
        val term = audiusFeedTerm
        val before = tracks
        scope.launch {
            try {
                val base = sourceUrl.trim().trimEnd('/')
                val page = if (term.isBlank() || term == DISCOVERY_TERM) AudiusRepository.trendingTracks(baseUrl = base, offset = before.size)
                else AudiusRepository.searchTracks(term, baseUrl = base, offset = before.size)
                if (audiusFeedTerm != term || tracks !== before) return@launch
                val known = before.map { it.id }.toSet()
                val fresh = page.filter { it.id !in known }
                fresh.forEach(::rememberTrack)
                val merged = before + fresh
                if (queueTracks == before) queueTracks = merged
                tracks = merged
                canLoadMore = page.size >= 25 && fresh.isNotEmpty()
            } catch (_: Exception) {
                canLoadMore = false
            } finally {
                loadingMore = false
            }
        }
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
        val viaService = servicePlayer != null
        val target: Player = if (viaService && servicePlayer != null) servicePlayer else localPlayer
        (if (viaService) localPlayer else servicePlayer)?.pause()
        useService = viaService
        if (viaService) {
            // The UI owns the queue for every provider; MediaSession exposes system notification controls.
            target.repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            target.shuffleModeEnabled = false
        } else {
            target.repeatMode = repeatMode
            target.shuffleModeEnabled = shuffleEnabled
        }
        target.setPlaybackSpeed(speed)

        if (track.id.startsWith("youtube-")) {
            loading = true
            scope.launch {
                try {
                    val videoId = track.id.removePrefix("youtube-")
                    val streamUrl = YouTubeMusicPlaybackRepository.resolveAudioStream(videoId)
                    val resolvedTrack = track.copy(streamUrl = streamUrl)
                    target.setMediaItem(resolvedTrack.toServiceMediaItem())
                    target.prepare()
                    target.play()
                } catch (e: Exception) {
                    playerError = "Couldn't open this stream. YouTube may have blocked the request or the stream may have expired. Try another track or check your connection."
                    playing = false
                } finally {
                    loading = false
                }
            }
        } else if (viaService) {
            target.setMediaItem(track.toServiceMediaItem())
            target.prepare()
            target.play()
        } else {
            val queue = queueTracks.filter { !it.id.startsWith("youtube-") && it.streamUrl.startsWith("https://") }
            val startIndex = queue.indexOfFirst { it.id == track.id }
            if (queue.size > 1 && startIndex >= 0) {
                localPlayer.setMediaItems(queue.map { item ->
                    MediaItem.Builder().setMediaId(item.id).setUri(item.streamUrl).build()
                }, startIndex, 0L)
            } else {
                localPlayer.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(track.streamUrl).build())
            }
            localPlayer.prepare()
            localPlayer.play()
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

    fun applyModes() {
        if (player === localPlayer) {
            player.repeatMode = repeatMode
            player.shuffleModeEnabled = shuffleEnabled
        } else {
            player.repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.shuffleModeEnabled = false
        }
    }

    fun playNext(track: MusicTrack) {
        rememberTrack(track)
        val current = active
        if (current == null) {
            queueTracks = listOf(track)
            play(track)
            return
        }
        val q = queueTracks.filterNot { it.id == track.id }.toMutableList()
        val index = q.indexOfFirst { it.id == current.id }
        q.add(if (index >= 0) index + 1 else q.size, track)
        queueTracks = q
        Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(track: MusicTrack) {
        rememberTrack(track)
        if (active == null) {
            queueTracks = listOf(track)
            play(track)
            return
        }
        queueTracks = queueTracks.filterNot { it.id == track.id } + track
        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
    }

    val onTrackEnded by rememberUpdatedState<() -> Unit>({ skipNext() })
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && useService) onTrackEnded()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                queueTracks.firstOrNull { it.id == id }?.let { if (active?.id != it.id) active = it }
            }
            override fun onPlayerError(error: PlaybackException) {
                playerError = explainPlaybackFailure(error)
                playing = false
                Toast.makeText(context, playerError, Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    SideEffect {
        PlaybackBridge.onNext = { skipNext() }
        PlaybackBridge.onPrevious = { skipPrevious() }
    }

    fun selectProvider(provider: String) {
        selectedProvider = provider
        prefs.edit().putString("music_provider", provider).apply()
        showProviders = false
        error = null
        tracks = emptyList()
        youtubeResults = emptyList()
        when (provider) {
            "YouTube Music" -> { search(query.ifBlank { "popular music" }) }
            "Audius", "JioSaavn" -> { search(query.ifBlank { "electronic chill" }) }
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

    CompositionLocalProvider(LocalQueueActions provides QueueActions(
        { playNext(it) }, { addToQueue(it) },
        { track -> if (track.id.startsWith("youtube-")) Toast.makeText(context, "Direct downloads unavailable for this YouTube Music item.", Toast.LENGTH_SHORT).show() else downloadTrack(context, track) },
        { track -> trackForPlaylist = track; playlistNameForTrack = "" },
        { showEqualizer = true }
    )) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 7.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab != 4) active?.let { track ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                        tonalElevation = 4.dp,
                        shadowElevation = 14.dp,
                        modifier = Modifier.fillMaxWidth().clickable { tab = 4 }
                    ) {
                        Column(
                            Modifier.background(
                                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)),
                                RoundedCornerShape(22.dp)
                            ).padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(track.artworkUrl, Modifier.size(42.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    thumb = { SliderDefaults.Thumb(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary)) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.secondary,
                                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                    )
                                )
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                    tonalElevation = 2.dp,
                    shadowElevation = 18.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0.dp),
                        modifier = Modifier.heightIn(min = 76.dp)
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
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { insets ->
        Crossfade(targetState = tab, animationSpec = tween(durationMillis = 220), label = "screen-fade") { screen ->
        when (screen) {
            0 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("NUVORA", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                )
                SearchBar(
                    query,
                    { query = it },
                    { tab = 1; search(query) },
                    { search(query.ifBlank { "popular music" }) },
                    placeholder = if (selectedProvider == "YouTube Music") "Search YouTube Music songs or artists…" else "Songs, artists, moods…"
                )
                StatusMessages(loading, error)
                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { search(query.ifBlank { if (selectedProvider == "YouTube Music") "popular music" else DISCOVERY_TERM }) },
                    modifier = Modifier.weight(1f)
                ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showMoodSection) item {
                        Text("Pick your mood", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(listOf("Chill", "Electronic", "Focus", "Energy", "Ambient", "Workout")) { mood ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.clickable {
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
                                            contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)
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
                                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                        Text(result.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        } else if (tracks.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(tracks.take(8), key = { "discover-${it.id}" }) { track ->
                                    Column(Modifier.width(124.dp).clickable { queueTracks = tracks; play(track) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Cover(track.artworkUrl, Modifier.size(124.dp))
                                        Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { Text("Trending tracks", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)) }
                    if (loading && tracks.isEmpty() && youtubeResults.isEmpty()) item(key = "skeleton-home") { SkeletonRows() }
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
                        if (selectedProvider == "Audius" && canLoadMore && tracks.isNotEmpty()) item(key = "load-more-home") {
                            LaunchedEffect(tracks.size) { loadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    item {
                        Text(
                            when (selectedProvider) {
                                "YouTube Music" -> "YouTube Music catalogue · Public stream availability can vary."
                                "JioSaavn" -> "JioSaavn catalogue · Community API and stream availability can change."
                                else -> "Catalogue by Audius · Track availability depends on creator settings."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
                }
            }
            1 -> Column(Modifier.fillMaxSize().padding(insets)) {
                Text("Search music", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = searchResultTab == 0, onClick = { searchResultTab = 0 }, label = { Text("Songs") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = searchResultTab == 1, onClick = { searchResultTab = 1; playlistQuery = query }, label = { Text("Playlists") }, leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp)) })
                }
                if (searchResultTab == 0) {
                    SearchBar(query, { query = it }, { search(query) }, { search(query) },
                        placeholder = if (selectedProvider == "YouTube Music") "Search YouTube Music songs or artists…" else "Songs, artists, moods…")
                    StatusMessages(loading, error)
                    if (selectedProvider == "YouTube Music") {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(youtubeResults, key = { it.videoId }) { result ->
                                val track = result.toMusicTrack()
                                TrackRow(track, active?.id == track.id, { queueTracks = youtubeResults.map { it.toMusicTrack() }; playYouTubeResult(result) },
                                    { toggleFavorite(track) }, track.id in favorites,
                                    { Toast.makeText(context, "Direct download is unavailable for YouTube Music tracks.", Toast.LENGTH_SHORT).show() },
                                    { toggleTrackSelection(track) }, track.id in selectedTrackIds)
                            }
                        }
                    } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (loading && tracks.isEmpty()) item(key = "skeleton-search") { SkeletonRows() }
                        items(tracks, key = { it.id }) { track ->
                            TrackRow(track, active?.id == track.id, { queueTracks = tracks; play(track) },
                                { toggleFavorite(track) }, track.id in favorites, { downloadTrack(context, track) },
                                { toggleTrackSelection(track) }, track.id in selectedTrackIds)
                        }
                        if (selectedProvider == "Audius" && canLoadMore && tracks.isNotEmpty()) item(key = "load-more-search") {
                            LaunchedEffect(tracks.size) { loadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = playlistProvider == "YouTube Music", onClick = { playlistProvider = "YouTube Music" }, label = { Text("YouTube Music") })
                        FilterChip(selected = playlistProvider == "JioSaavn", onClick = { playlistProvider = "JioSaavn" }, label = { Text("JioSaavn") })
                    }
                    SearchBar(
                        query = query, onQuery = { query = it; playlistQuery = it },
                        onSearch = {
                            playlistQuery = query
                            scope.launch {
                                loading = true; error = null
                                try {
                                    if (playlistProvider == "YouTube Music") {
                                        youtubePlaylists = YouTubeMusicPlaylistsRepository.searchPlaylists(query)
                                        if (youtubePlaylists.isEmpty()) error = "No public YouTube Music playlists found."
                                    } else {
                                        saavnPlaylists = JioSaavnRepository.searchPlaylists(query)
                                        if (saavnPlaylists.isEmpty()) error = "No JioSaavn playlists found."
                                    }
                                } catch (_: Exception) { error = "Playlist search failed. The provider may be unavailable." }
                                finally { loading = false }
                            }
                        },
                        onRefresh = {
                            scope.launch {
                                loading = true; error = null
                                try {
                                    if (playlistProvider == "YouTube Music") youtubePlaylists = YouTubeMusicPlaylistsRepository.searchPlaylists(query)
                                    else saavnPlaylists = JioSaavnRepository.searchPlaylists(query)
                                } catch (_: Exception) { error = "Playlist search failed. Try again." }
                                finally { loading = false }
                            }
                        }, placeholder = "Search playlist names…")
                    StatusMessages(loading, error)
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playlistProvider == "YouTube Music") items(youtubePlaylists, key = { "search-ytplaylist-${it.id}" }) { playlist ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        loading = true; error = null
                                        try {
                                            val imported = YouTubeMusicPlaylistsRepository.getPlaylistTracks(playlist.id)
                                            imported.forEach(::rememberTrack)
                                            if (imported.isEmpty()) error = "This playlist has no accessible public tracks."
                                            else { queueTracks = imported; play(imported.first()); tab = 4 }
                                        } catch (_: Exception) { error = "Could not import this playlist." }
                                        finally { loading = false }
                                    }
                                }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(34.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("YouTube Music playlist · Tap to open", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                        if (playlistProvider == "JioSaavn") items(saavnPlaylists, key = { "search-saavn-${it.id}" }) { playlist ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        loading = true; error = null
                                        try {
                                            val imported = JioSaavnRepository.getPlaylistTracks(playlist.id)
                                            imported.forEach(::rememberTrack)
                                            if (imported.isEmpty()) error = "This playlist has no accessible tracks."
                                            else { queueTracks = imported; play(imported.first()); tab = 4 }
                                        } catch (_: Exception) { error = "Could not import this playlist." }
                                        finally { loading = false }
                                    }
                                }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(34.dp))
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("JioSaavn playlist · Tap to open", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                }
                            }
                        }
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
                        Text("Playlists, likes and listening history", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Button(onClick = { showCreatePlaylist = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create")
                    }
                }
                if (selectedTrackIds.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("${selectedTrackIds.size} tracks selected", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
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
                        item { Text("Use the heart on any result to keep it here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                    } else {
                        items(saved, key = { "liked-${it.id}" }) { track ->
                            TrackRow(
                                track, active?.id == track.id, { queueTracks = saved; play(track) },
                                { toggleFavorite(track) }, true,
                                { if (track.id.startsWith("youtube-")) Toast.makeText(context, "Direct download is unavailable for YouTube Music tracks.", Toast.LENGTH_SHORT).show() else downloadTrack(context, track) },
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
                                { if (track.id.startsWith("youtube-")) Toast.makeText(context, "Direct download is unavailable for YouTube Music tracks.", Toast.LENGTH_SHORT).show() else downloadTrack(context, track) },
                                { toggleTrackSelection(track) }, track.id in selectedTrackIds
                            )
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("My playlists", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${localPlaylists.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    if (localPlaylists.isEmpty()) {
                        item {
                            Text(
                                "Create a playlist, then select tracks in Search with the playlist icon. You can make playlists from YouTube Music, Audius and JioSaavn results.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    items(localPlaylists, key = { "playlist-${it.id}" }) { playlist ->
                        val playlistTracks = playlist.trackIds.mapNotNull { id -> knownTracks.firstOrNull { it.id == id } }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)), RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(23.dp))
                                    }
                                }
                                Column(
                                    Modifier.weight(1f).clickable {
                                        expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id
                                    }.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${playlistTracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id
                                }) {
                                    Icon(
                                        if (expandedPlaylistId == playlist.id) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = "Expand playlist",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(onClick = {
                                    localPlaylists.removeAll { it.id == playlist.id }
                                    prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
                                    if (expandedPlaylistId == playlist.id) expandedPlaylistId = null
                                }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (expandedPlaylistId == playlist.id) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
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
                                    ) { Text("Add selected tracks", color = MaterialTheme.colorScheme.secondary) }
                                }
                                if (playlistTracks.isEmpty()) {
                                    Text("This playlist is empty. Search songs and select them, then tap Add selected tracks.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                } else {
                                    playlistTracks.forEach { track ->
                                        TrackRow(
                                            track, active?.id == track.id,
                                            { queueTracks = playlistTracks; play(track) },
                                            { toggleFavorite(track) }, track.id in favorites,
                                            { if (track.id.startsWith("youtube-")) Toast.makeText(context, "Direct download is unavailable for YouTube Music tracks.", Toast.LENGTH_SHORT).show() else downloadTrack(context, track) },
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
                        Text("Find playlists from Search → Playlists.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    if (playlistProvider == "YouTube Music") {
                        items(youtubePlaylists, key = { "ytplaylist-${it.id}" }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
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
                                    Text(playlist.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Tap to import into Nuvora", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.DownloadDone, contentDescription = "Import playlist", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    } else {
                        items(saavnPlaylists, key = { "saavnplaylist-${it.id}" }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
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
                                    Text(playlist.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Tap to import into Nuvora", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.DownloadDone, contentDescription = "Import playlist", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    if (loading || error != null) item { StatusMessages(loading, error) }
                    item { Spacer(Modifier.height(18.dp)) }
                }
            }
            3 -> Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Appearance", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Box {
                    OutlinedButton(onClick = { showThemes = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Palette, contentDescription = null)
                        Text("  Theme: ${themes[themeIndex].label}", modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Theme preview", fontWeight = FontWeight.SemiBold)
                                Text("Liquid surfaces • soft depth • readable contrast", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
                            ) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = "Theme accent preview", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Nuvora", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Now playing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("Audius", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                                    Text("Accent sample", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.QueueMusic, Icons.Rounded.Settings).forEachIndexed { index, icon ->
                                Icon(
                                    icon,
                                    contentDescription = listOf("Home preview", "Search preview", "Playlists preview", "Settings preview")[index],
                                    tint = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Home screen sections", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mood cards", modifier = Modifier.weight(1f))
                    Switch(checked = showMoodSection, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, uncheckedThumbColor = MaterialTheme.colorScheme.secondary, checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant), onCheckedChange = {
                        showMoodSection = it
                        prefs.edit().putBoolean("home_moods", it).apply()
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recently played", modifier = Modifier.weight(1f))
                    Switch(checked = showRecentSection, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, uncheckedThumbColor = MaterialTheme.colorScheme.secondary, checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant), onCheckedChange = {
                        showRecentSection = it
                        prefs.edit().putBoolean("home_recent", it).apply()
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Horizontal music carousel", modifier = Modifier.weight(1f))
                    Switch(checked = showCarouselSection, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, uncheckedThumbColor = MaterialTheme.colorScheme.secondary, checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant), onCheckedChange = {
                        showCarouselSection = it
                        prefs.edit().putBoolean("home_carousel", it).apply()
                    })
                }
                HorizontalDivider()
                Text("Music source", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("Downloads", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Use More ⋮ on a track to download audio and available artwork. Audio is saved to Music/Nuvora; artwork is stored separately in Pictures/Nuvora/Artwork.", fontSize = 13.sp)
                Text("Note: downloads depend on the selected provider's stream and usage permissions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            else -> active?.let { track ->
                val ambientShift by rememberInfiniteTransition(label = "player-ambient").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(9000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                    label = "ambient-shift"
                )
                Column(
                    Modifier.fillMaxSize().padding(insets)
                        .background(Brush.linearGradient(
                            colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), MaterialTheme.colorScheme.background),
                            start = Offset(ambientShift * 460f, ambientShift * 160f),
                            end = Offset(900f - ambientShift * 260f, 1700f)
                        ))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { tab = 0 }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NOW PLAYING", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("NUVORA PLAYER", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.4.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { toggleFavorite(track) }) {
                            Icon(if (track.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favourite", tint = if (track.id in favorites) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { showNowPlayingMenu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More player options") }
                            // Player actions are provided by the modern action sheet below the screen content.
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                        shadowElevation = 18.dp,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.02f).heightIn(max = 310.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Cover(track.artworkUrl, Modifier.fillMaxSize())
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x66070B16)))
                                )
                            )
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Slider(
                        value = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { if (durationMs > 0L) positionMs = (it * durationMs).toLong() },
                        onValueChangeFinished = { if (durationMs > 0L) player.seekTo(positionMs) },
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(if (durationMs > 0L) formatTime(durationMs) else track.durationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    if (playerError != null) {
                        Surface(
                            color = Color(0x26FF8A80),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0x66FF8A80)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.Start) {
                                Text("Playback couldn't start", color = Color(0xFFFFA6A0), fontWeight = FontWeight.SemiBold)
                                Text(playerError!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { play(track) }) { Text("Retry stream", color = MaterialTheme.colorScheme.secondary) }
                                    if (track.id.startsWith("youtube-")) {
                                        TextButton(onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.permalink)))
                                            }.onFailure {
                                                Toast.makeText(context, "Could not open YouTube Music", Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text("Open YouTube Music", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)), shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)))).padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { shuffleEnabled = !shuffleEnabled; applyModes() }) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffleEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { skipPrevious() }) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(30.dp)) }
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)), shadowElevation = 16.dp,
                                modifier = Modifier.size(70.dp).clickable { if (player.isPlaying) player.pause() else player.play() }) {
                                Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp)) }
                            }
                            IconButton(onClick = { skipNext() }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", modifier = Modifier.size(30.dp)) }
                            IconButton(onClick = {
                                repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                                applyModes()
                            }) {
                                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Repeat mode", tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat queue"
                            else -> if (shuffleEnabled) "Shuffle on" else "Now playing"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                    TextButton(onClick = { showSleepTimer = true }) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (sleepTimerMinutes > 0) "Sleep timer: $sleepTimerMinutes min" else "Sleep timer")
                    }
                    TextButton(onClick = { showQueue = true }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Up next \u00B7 ${queueTracks.size.coerceAtLeast(if (active == null) 0 else 1)} tracks")
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                            FilterChip(
                                selected = speed == v,
                                onClick = {
                                    speed = v
                                    localPlayer.setPlaybackSpeed(v)
                                    servicePlayer?.setPlaybackSpeed(v)
                                },
                                label = { Text(speedLabel(v), fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            } ?: Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Choose a track to start listening.")
                Button(onClick = { tab = 0 }) { Text("Discover music") }
            }
        }
        }
    }
    }
    if (showThemes) {
        ModalBottomSheet(onDismissRequest = { showThemes = false }, containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 20.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose your look", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("16 palettes · changes apply instantly", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(themes.size) { index ->
                        val theme = themes[index]
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = if (index == themeIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                            border = BorderStroke(1.dp, if (index == themeIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth().clickable { onThemeChange(index); showThemes = false }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(theme.background, theme.surface, theme.primary, theme.secondary).forEach { swatch ->
                                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(swatch).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(6.dp)))
                                    }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(theme.label, fontWeight = if (index == themeIndex) FontWeight.Bold else FontWeight.Medium)
                                    Text(if (theme.dynamic) "System wallpaper colours" else if (theme.isLight) "Light palette" else "Dark palette",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                if (index == themeIndex) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected theme", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showNowPlayingMenu) {
        active?.let { track ->
            ModalBottomSheet(
                onDismissRequest = { showNowPlayingMenu = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 20.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("PLAYER CONTROLS", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(track.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    MenuSheetAction("Add to queue", Icons.Rounded.PlaylistAdd, "Keep it in Up next") { showNowPlayingMenu = false; addToQueue(track) }
                    MenuSheetAction("Add to playlist", Icons.Rounded.QueueMusic, "Save it to a Nuvora playlist") { showNowPlayingMenu = false; trackForPlaylist = track; playlistNameForTrack = "" }
                    MenuSheetAction("Equalizer", Icons.Rounded.GraphicEq, "Tune bass, mids and treble") { showNowPlayingMenu = false; showEqualizer = true }
                    MenuSheetAction("Sleep timer", Icons.Rounded.Timer, if (sleepTimerMinutes > 0) "Active · $sleepTimerMinutes minutes" else "Set a playback timer") { showNowPlayingMenu = false; customSleepMinutes = "30"; showSleepTimer = true }
                    MenuSheetAction("Show queue", Icons.Rounded.QueueMusic, "Manage upcoming tracks") { showNowPlayingMenu = false; showQueue = true }
                    MenuSheetAction("Download audio", Icons.Rounded.Download, "Save eligible audio and artwork") {
                        showNowPlayingMenu = false
                        if (track.id.startsWith("youtube-")) Toast.makeText(context, "Direct downloads unavailable for this YouTube Music item.", Toast.LENGTH_SHORT).show() else downloadTrack(context, track)
                    }
                }
            }
        }
    }
    if (showProviders) {
        ModalBottomSheet(
            onDismissRequest = { showProviders = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 18.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Choose your source", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Pick a catalogue. In-app playback availability is shown clearly for each source.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                providers.forEach { provider ->
                    val description = when (provider) {
                        "YouTube Music" -> "In-app audio · Best-effort public streams"
                        "Audius" -> "In-app audio · Public creator streams"
                        "JioSaavn" -> "In-app audio · Community API"
                        "Spotify" -> "External app · No direct audio stream"
                        "SoundCloud" -> "External app · Approved integration required"
                        else -> "External app or website"
                    }
                    val isNative = provider in listOf("YouTube Music", "Audius", "JioSaavn")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (provider == selectedProvider) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0x66182134),
                        border = BorderStroke(
                            1.dp,
                            if (provider == selectedProvider) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { selectProvider(provider) }
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = if (provider == selectedProvider) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface, modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isNative) Icons.Rounded.LibraryMusic else Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = if (provider == selectedProvider) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(provider, fontWeight = FontWeight.SemiBold)
                                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            if (provider == selectedProvider) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.secondary)
                            } else if (isNative) {
                                Text("IN APP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            } else {
                                Text("WEB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showQueue) {
        QueueSheet(
            queue = queueTracks,
            currentId = active?.id,
            onPlayAt = { index -> queueTracks.getOrNull(index)?.let { play(it) } },
            onMove = { from, to -> queueTracks = queueTracks.toMutableList().apply { add(to, removeAt(from)) } },
            onRemove = { index -> queueTracks = queueTracks.toMutableList().apply { if (index in indices) removeAt(index) } },
            onClearUpcoming = {
                val index = queueTracks.indexOfFirst { it.id == active?.id }
                if (index >= 0) queueTracks = queueTracks.take(index + 1)
            },
            onDismiss = { showQueue = false }
        )
    }
    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Sleep timer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pause playback automatically after:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    OutlinedTextField(
                        value = customSleepMinutes,
                        onValueChange = { value -> customSleepMinutes = value.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Custom minutes (1–720)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true
                    )
                    Text("Choose a quick preset or enter your own time.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        Surface(
                            color = if (sleepTimerMinutes == minutes) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (sleepTimerMinutes == minutes) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth().clickable { sleepTimerMinutes = minutes; showSleepTimer = false }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$minutes minutes", modifier = Modifier.weight(1f))
                                if (sleepTimerMinutes == minutes) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    TextButton(onClick = { sleepTimerMinutes = 0; showSleepTimer = false }) { Text("Cancel active timer") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val minutes = customSleepMinutes.toIntOrNull()
                    if (minutes != null && minutes in 1..720) {
                        sleepTimerMinutes = minutes
                        showSleepTimer = false
                        Toast.makeText(context, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Enter a time between 1 and 720 minutes", Toast.LENGTH_SHORT).show()
                }) { Text("Start timer") }
            },
            dismissButton = { TextButton(onClick = { showSleepTimer = false }) { Text("Cancel") } }
        )
    }
    if (trackForPlaylist != null) {
        val track = trackForPlaylist!!
        AlertDialog(
            onDismissRequest = { trackForPlaylist = null }, containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Add to playlist", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(track.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (localPlaylists.isEmpty()) Text("No local playlists yet. Create one below.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    localPlaylists.forEach { playlist ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable { addTrackToPlaylist(track, playlist.id) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, fontWeight = FontWeight.SemiBold)
                                    Text("${playlist.trackIds.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.Add, null)
                            }
                        }
                    }
                    OutlinedTextField(value = playlistNameForTrack, onValueChange = { playlistNameForTrack = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Or create a playlist") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = playlistNameForTrack.isNotBlank(), onClick = {
                    val name = playlistNameForTrack.trim()
                    val existing = localPlaylists.firstOrNull { it.name.equals(name, true) }
                    if (existing != null) addTrackToPlaylist(track, existing.id) else {
                        rememberTrack(track)
                        val created = LocalPlaylist("${System.currentTimeMillis()}-${localPlaylists.size}", name, listOf(track.id))
                        localPlaylists.add(0, created)
                        prefs.edit().putString("local_playlists", encodePlaylists(localPlaylists)).apply()
                        trackForPlaylist = null
                        Toast.makeText(context, "Created playlist: $name", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Create & add") }
            },
            dismissButton = { TextButton(onClick = { trackForPlaylist = null }) { Text("Cancel") } }
        )
    }
    if (showEqualizer) {
        EqualizerDialog(
            enabled = eqEnabled, levels = eqLevels.toList(), customPresets = customEqPresets.toList(), presetName = eqPresetName,
            onEnabledChange = { enabled -> eqEnabled = enabled; prefs.edit().putBoolean("eq_enabled", enabled).apply() },
            onLevelChange = { index, value -> eqLevels[index] = value; prefs.edit().putString("eq_levels", eqLevels.joinToString(",")).apply() },
            onApplyPreset = { preset -> applyEqPreset(preset.levels); eqPresetName = "" },
            onPresetNameChange = { eqPresetName = it }, onSavePreset = { saveEqPreset(eqPresetName) }, onDismiss = { showEqualizer = false }
        )
    }
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Create playlist", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Name your playlist. Selected tracks will be added automatically; you can also create an empty playlist.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Late night mix") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Text("${selectedTrackIds.size} selected track(s)", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { createLocalPlaylist(newPlaylistName) }, enabled = newPlaylistName.isNotBlank()) {
                    Text("Create playlist")
                }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") } }
        )
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
        Text("Loading tracks…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
    if (error != null) Text(error, color = Color(0xFFFFA6A6), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

internal fun normalizeArtworkUrl(raw: String?): String? {
    val value = raw?.trim()?.replace("&amp;", "&")?.takeIf { it.isNotBlank() } ?: return null
    val secureUrl = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) -> "https://" + value.substringAfter("://")
        value.startsWith("https://", ignoreCase = true) -> value
        else -> return null
    }
    return runCatching { Uri.parse(secureUrl) }.getOrNull()
        ?.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
        ?.toString()
}

@Composable
internal fun Cover(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageUrl = remember(url) { normalizeArtworkUrl(url) }
    var imageFailed by remember(imageUrl) { mutableStateOf(imageUrl == null) }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier.clip(shape).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface, Color(0xFF25234C)))
        ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((maxWidth * 0.34f).coerceAtMost(56.dp))
            )
        }

        if (imageUrl != null && !imageFailed) {
            AsyncImage(
                model = remember(imageUrl) {
                    ImageRequest.Builder(context).data(imageUrl).crossfade(180).build()
                },
                contentDescription = "Track artwork",
                contentScale = ContentScale.Crop,
                onSuccess = { imageFailed = false },
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(
    track: MusicTrack,
    isActive: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    favorite: Boolean,
    onDownload: () -> Unit = {},
    onSelect: (() -> Unit)? = null,
    selected: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.artworkUrl, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.durationLabel, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favourite", tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val queueActions = LocalQueueActions.current
        if (queueActions != null) {
            val context = LocalContext.current
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (menuOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 20.dp
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(track.artworkUrl, Modifier.size(56.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("TRACK ACTIONS", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            MenuSheetAction("Play next", Icons.Rounded.PlaylistPlay, "Play after the current track") { menuOpen = false; queueActions.playNext(track) }
                            MenuSheetAction("Add to queue", Icons.Rounded.PlaylistAdd, "Keep it in Up next") { menuOpen = false; queueActions.addToQueue(track) }
                            MenuSheetAction("Add to playlist", Icons.Rounded.QueueMusic, "Save it to a Nuvora playlist") { menuOpen = false; queueActions.addToPlaylist(track) }
                            MenuSheetAction("Download audio + artwork", Icons.Rounded.Download, "Save available files to this device") { menuOpen = false; queueActions.download(track) }
                            MenuSheetAction("Equalizer", Icons.Rounded.GraphicEq, "Tune bass, mids and treble") { menuOpen = false; queueActions.openEqualizer() }
                            MenuSheetAction("Share", Icons.Rounded.Share, "Share this track") {
                                menuOpen = false
                                val text = if (track.permalink.startsWith("https://")) "${track.title} · ${track.artist}\n${track.permalink}" else "${track.title} · ${track.artist}"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, "Share track"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSheetAction(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EqualizerDialog(
    enabled: Boolean,
    levels: List<Int>,
    customPresets: List<EqPreset>,
    presetName: String,
    onEnabledChange: (Boolean) -> Unit,
    onLevelChange: (Int, Int) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onPresetNameChange: (String) -> Unit,
    onSavePreset: () -> Unit,
    onDismiss: () -> Unit
) {
    val builtIn = listOf(
        EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
        EqPreset("Bass Boost", listOf(6, 4, 2, 0, 0)),
        EqPreset("Pop", listOf(-1, 2, 4, 3, 1)),
        EqPreset("Rock", listOf(4, 2, -1, 2, 4)),
        EqPreset("Classical", listOf(0, 1, 2, 1, 3)),
        EqPreset("Vocal", listOf(-3, 0, 3, 4, 1))
    )
    val bands = listOf("60 Hz · Bass", "230 Hz · Low", "910 Hz · Mid", "3.6 kHz · Presence", "14 kHz · Air")
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Equalizer", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio effects", fontWeight = FontWeight.SemiBold)
                        Text("Adjust five bands and save your own presets.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                Text("PRESETS", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(builtIn) { preset -> FilterChip(selected = false, onClick = { onApplyPreset(preset) }, label = { Text(preset.name) }) }
                }
                if (customPresets.isNotEmpty()) {
                    Text("MY PRESETS", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(customPresets, key = { it.name }) { preset -> FilterChip(selected = false, onClick = { onApplyPreset(preset) }, label = { Text(preset.name) }) }
                    }
                }
                bands.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Text("${levels.getOrElse(index) { 0 }} dB", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(value = levels.getOrElse(index) { 0 }.toFloat(), onValueChange = { onLevelChange(index, it.toInt().coerceIn(-12, 12)) },
                        valueRange = -12f..12f, steps = 23, enabled = enabled,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary, activeTrackColor = MaterialTheme.colorScheme.primary))
                }
                HorizontalDivider()
                OutlinedTextField(value = presetName, onValueChange = onPresetNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Custom preset name") }, placeholder = { Text("e.g. My headphones") })
                Button(onClick = onSavePreset, enabled = presetName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Save, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Save current preset")
                }
                Text("Effect depends on Android audio-effect support and the active player session.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SkeletonRows(count: Int = 6) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = pulse)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(block))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.62f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(block))
                    Box(Modifier.fillMaxWidth(0.36f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(block))
                }
            }
        }
    }
}
