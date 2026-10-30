package com.simbuild.nuvora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF080D1A)
private val Panel = Color(0xFF121B2E)
private val Blue = Color(0xFF4D8DFF)
private val Cyan = Color(0xFF5CE1E6)
private val Muted = Color(0xFF9AA8C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue, secondary = Cyan, background = Night, surface = Panel,
                onBackground = Color(0xFFF4F7FF), onSurface = Color(0xFFF4F7FF)
            )) { MuPlayerApp() }
        }
    }
}

private data class DemoTrack(val title: String, val artist: String, val color: Color, val mark: String)
private val tracks = listOf(
    DemoTrack("Midnight City", "M83", Color(0xFF3654A5), "✦"),
    DemoTrack("After Hours", "The Weeknd", Color(0xFF7B3D79), "☾"),
    DemoTrack("Ocean Eyes", "Billie Eilish", Color(0xFF247F91), "≈"),
    DemoTrack("Sunset Lover", "Petit Biscuit", Color(0xFFB86B4C), "☼"),
    DemoTrack("A Moment Apart", "ODESZA", Color(0xFF5C6E9C), "◈")
)

@Composable
private fun MuPlayerApp() {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var fullPlayer by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Night,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Night).padding(horizontal = 12.dp)) {
                MiniPlayer(tracks[active], playing, { fullPlayer = true }, { playing = !playing }, {
                    active = (active + 1) % tracks.size
                })
                NavigationBar(containerColor = Night, tonalElevation = 0.dp) {
                    val labels = listOf("Home", "Search", "Library", "Playlists")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.LibraryMusic, Icons.Rounded.PlaylistPlay)
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
        if (fullPlayer) {
            NowPlaying(tracks[active], playing, { fullPlayer = false }, { playing = !playing },
                { active = (active + 1) % tracks.size },
                { active = (active - 1 + tracks.size) % tracks.size })
        } else when (tab) {
            0 -> HomeScreen(Modifier.padding(insets), query, { query = it }, { active = it; fullPlayer = true }, { tab = 1 })
            1 -> SearchScreen(Modifier.padding(insets), query, { query = it }, { active = it; fullPlayer = true })
            2 -> CollectionScreen(Modifier.padding(insets), "Your library", "Your saved artists, albums and tracks will appear here.", "Explore music") { tab = 0 }
            else -> CollectionScreen(Modifier.padding(insets), "Your playlists", "Build collections around every mood.", "Discover music") { tab = 0 }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, query: String, onQuery: (String) -> Unit, onTrack: (Int) -> Unit, onSearch: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MUPLAYER", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
                    Text("Find your frequency.", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("A world of music, one tap away.", color = Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Cyan)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery, readOnly = true,
                modifier = Modifier.fillMaxWidth().clickable { onSearch() },
                placeholder = { Text("Artists, tracks, moods...", color = Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
                shape = RoundedCornerShape(18.dp), singleLine = true
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(190.dp).clickable { onTrack(0) },
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF183F79), Color(0xFF17213C), Color(0xFF301C54)))).padding(22.dp)) {
                    Column(Modifier.align(Alignment.CenterStart)) {
                        Text("YOUR NEXT OBSESSION", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(9.dp))
                        Text("Late night\nfrequency", fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("A mood, not a genre.", color = Color(0xFFD0D9EF), fontSize = 12.sp)
                    }
                    Box(Modifier.align(Alignment.CenterEnd).size(90.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x334D8DFF)), contentAlignment = Alignment.Center) {
                        Text("♫", fontSize = 50.sp, color = Color.White)
                    }
                }
            }
        }
        item { SectionHeader("Made for your mood", "Explore") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(tracks) { index, track -> MoodCard(track) { onTrack(index) } }
            }
        }
        item { SectionHeader("Trending right now", "See all") }
        itemsIndexed(tracks) { index, track -> TrackRow(track, index) { onTrack(index) } }
        item { Text("Design preview · Online catalogue and playback provider are not connected yet.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun SearchScreen(modifier: Modifier, query: String, onQuery: (String) -> Unit, onTrack: (Int) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Search", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text("Search across connected music sources.", color = Muted, fontSize = 13.sp)
        }
        item {
            OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What do you want to hear?") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true)
        }
        item { SectionHeader(if (query.isBlank()) "Popular searches" else "Preview matches", null) }
        itemsIndexed(tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }) { _, track ->
            val index = tracks.indexOf(track)
            TrackRow(track, index) { onTrack(index) }
        }
        item { Text("These are sample UI entries, not live search results.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun CollectionScreen(modifier: Modifier, title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(90.dp).clip(RoundedCornerShape(28.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Cyan, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Muted)
        Spacer(Modifier.height(20.dp))
        Surface(color = Color(0xFF182D50), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable(onClick = onAction)) {
            Text(action, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun MoodCard(track: DemoTrack, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Artwork(track, Modifier.fillMaxWidth().height(132.dp), true)
        Spacer(Modifier.height(8.dp))
        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun TrackRow(track: DemoTrack, index: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp)
        }
        IconButton(onClick = onClick) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview track", tint = Color.White) }
    }
}

@Composable
private fun Artwork(track: DemoTrack, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(if (large) 22.dp else 15.dp)).background(Brush.linearGradient(listOf(track.color, Panel, Color(0xFF1A2C4A)))), contentAlignment = Alignment.Center) {
        Text(track.mark, color = Color.White, fontSize = if (large) 43.sp else 26.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SectionHeader(title: String, action: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null) Text(action, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniPlayer(track: DemoTrack, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF182641), Panel))).clickable(onClick = onOpen).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text("Player preview", color = Muted, fontSize = 11.sp)
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Toggle preview") }
        IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next preview") }
    }
}

@Composable
private fun NowPlaying(track: DemoTrack, playing: Boolean, onBack: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    var progress by remember(track.title) { mutableFloatStateOf(0.32f) }
    Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 24.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options") }
        }
        Spacer(Modifier.weight(1f))
        Artwork(track, Modifier.fillMaxWidth().weight(1.5f), true)
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(track.artist, color = Muted, fontSize = 14.sp)
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Save track", tint = Cyan) }
        }
        Slider(value = progress, onValueChange = { progress = it }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1:24", color = Muted, fontSize = 11.sp)
            Text("4:08", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp)) }
            Surface(shape = CircleShape, color = Blue, modifier = Modifier.size(72.dp).clickable(onClick = onToggle)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Toggle preview", tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Favorites", tint = Muted)
            Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = Muted)
            Icon(Icons.Rounded.Album, contentDescription = "Album", tint = Muted)
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Muted)
        }
    }
}
