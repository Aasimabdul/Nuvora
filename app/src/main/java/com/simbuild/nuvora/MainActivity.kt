package com.simbuild.nuvora

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch

private val Night = Color(0xFF080D1A)
private val Panel = Color(0xFF121B2E)
private val Blue = Color(0xFF4D8DFF)
private val Cyan = Color(0xFF5CE1E6)
private val Muted = Color(0xFF9AA8C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("nuvora", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue, secondary = Cyan, background = Night, surface = Panel,
                onBackground = Color(0xFFF4F7FF), onSurface = Color(0xFFF4F7FF)
            )) {
                val player = remember { ExoPlayer.Builder(this@MainActivity).build() }
                var clientId by remember { mutableStateOf(preferences.getString("jamendo_client_id", "") ?: "") }
                var showSettings by remember { mutableStateOf(clientId.isBlank()) }
                var search by remember { mutableStateOf("") }
                var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var current by remember { mutableStateOf<MusicTrack?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var loading by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val repository = remember(clientId) { JamendoRepository(clientId) }

                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                        player.release()
                    }
                }

                fun loadTracks(term: String = search) {
                    if (clientId.isBlank()) {
                        showSettings = true
                        return
                    }
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            tracks = repository.searchTracks(term)
                            if (tracks.isEmpty()) error = "No playable tracks found. Try another search."
                        } catch (e: Exception) {
                            error = e.message ?: "Could not connect to the music catalogue."
                        } finally {
                            loading = false
                        }
                    }
                }

                Surface(Modifier.fillMaxSize(), color = Night) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 22.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("NUVORA", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                                Text("Find your frequency.", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                Text("Independent music, streamed online.", color = Muted, fontSize = 13.sp)
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Music provider settings", tint = Cyan)
                            }
                        }
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            placeholder = { Text("Search songs or artists", color = Muted) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
                            trailingIcon = {
                                IconButton(onClick = { loadTracks(search) }) {
                                    Icon(Icons.Rounded.ArrowForward, "Search", tint = Cyan)
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (search.isBlank()) "DISCOVER MUSIC" else "SEARCH RESULTS", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { search = ""; loadTracks("") }) { Text("Popular", color = Muted) }
                        }
                        when {
                            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Cyan)
                            }
                            tracks.isEmpty() -> Column(
                                Modifier.fillMaxWidth().weight(1f).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.GraphicEq, null, tint = Cyan, modifier = Modifier.size(58.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Real music. No fake tracks.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(error ?: "Connect your Jamendo developer app, then load the live catalogue.", color = Muted)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { if (clientId.isBlank()) showSettings = true else loadTracks("") }) {
                                    Text(if (clientId.isBlank()) "Connect music catalogue" else "Load music")
                                }
                            }
                            else -> LazyColumn(
                                Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tracks, key = { it.id }) { track ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                            .background(Panel).clickable {
                                                current = track
                                                player.setMediaItem(MediaItem.fromUri(track.streamUrl))
                                                player.prepare()
                                                player.play()
                                            }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                                                .background(Brush.linearGradient(listOf(Color(0xFF1D4C83), Color(0xFF27304F)))),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Rounded.MusicNote, null, tint = Cyan, modifier = Modifier.size(26.dp)) }
                                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (track.licenseUrl.isNotBlank()) Text("License details available", color = Cyan, fontSize = 10.sp)
                                        }
                                        IconButton(onClick = {
                                            current = track
                                            player.setMediaItem(MediaItem.fromUri(track.streamUrl))
                                            player.prepare()
                                            player.play()
                                        }) { Icon(Icons.Rounded.PlayArrow, "Play ${track.title}", tint = Color.White) }
                                    }
                                }
                            }
                        }
                        if (error != null && tracks.isNotEmpty()) Text(error!!, color = Color(0xFFFF9C9C), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        current?.let { track ->
                            Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = Muted, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Cyan)
                                    }
                                    IconButton(onClick = {
                                        val next = tracks.indexOfFirst { it.id == track.id } + 1
                                        if (next in tracks.indices) {
                                            current = tracks[next]
                                            player.setMediaItem(MediaItem.fromUri(tracks[next].streamUrl))
                                            player.prepare()
                                            player.play()
                                        }
                                    }) { Icon(Icons.Rounded.SkipNext, "Next track") }
                                }
                                if (track.licenseUrl.isNotBlank()) {
                                    TextButton(onClick = { }) { Text("Track licence: Jamendo / Creative Commons", color = Muted, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }

                if (showSettings) {
                    var draft by remember(clientId) { mutableStateOf(clientId) }
                    AlertDialog(
                        onDismissRequest = { if (clientId.isNotBlank()) showSettings = false },
                        containerColor = Panel,
                        title = { Text("Connect music catalogue", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Nuvora uses Jamendo's licensed independent-music catalogue. Create an app at developer.jamendo.com and paste its client ID below.", color = Muted)
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Jamendo client ID") },
                                    singleLine = true
                                )
                                Text("Jamendo's free API use is for non-commercial projects. Check each track's licence.", color = Muted, fontSize = 11.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                clientId = draft.trim()
                                preferences.edit().putString("jamendo_client_id", clientId).apply()
                                showSettings = false
                                tracks = emptyList()
                                loadTracks("")
                            }) { Text("Connect", color = Cyan) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettings = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
