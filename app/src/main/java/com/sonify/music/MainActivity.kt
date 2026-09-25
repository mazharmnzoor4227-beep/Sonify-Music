package com.sonify.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonify.music.data.DemoCatalog
import com.sonify.music.model.Track

private val Bg = Color(0xFF070707)
private val Surface1 = Color(0xFF121212)
private val Surface2 = Color(0xFF1A1A1A)
private val Text = Color(0xFFF7F7F7)
private val Muted = Color(0xFFA7A7A7)
private val Accent = Color(0xFFB7FF45)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SonifyApp() }
    }
}

@Composable
private fun SonifyApp() {
    var tab by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Track?>(DemoCatalog.featured.first()) }
    var nowPlayingOpen by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Surface1,
            primary = Accent,
            onBackground = Text,
            onSurface = Text
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = current != null, enter = fadeIn(), exit = fadeOut()) {
                        MiniPlayer(track = current!!, onOpen = { nowPlayingOpen = true })
                    }
                    NavigationBar(containerColor = Color(0xF20A0A0A)) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Rounded.Home, null) },
                            label = { Text("Home") },
                            colors = navColors()
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Rounded.Search, null) },
                            label = { Text("Search") },
                            colors = navColors()
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                            label = { Text("Library") },
                            colors = navColors()
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tabs"
                ) { selected ->
                    when (selected) {
                        0 -> HomeScreen(onTrack = {
                            current = it
                            nowPlayingOpen = true
                        })
                        1 -> SearchScreen()
                        else -> LibraryScreen()
                    }
                }
            }

            if (nowPlayingOpen && current != null) {
                NowPlaying(track = current!!, onClose = { nowPlayingOpen = false })
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent,
    selectedTextColor = Text,
    unselectedIconColor = Muted,
    unselectedTextColor = Muted,
    indicatorColor = Color.Transparent
)

@Composable
private fun HomeScreen(onTrack: (Track) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF26320F), Bg)))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("SONIFY", color = Accent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your music.\nYour atmosphere.",
                        color = Text,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        lineHeight = 38.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Fast, clean and built for listening.", color = Muted)
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier.align(Alignment.TopEnd).background(Color(0x221FFFFFF), CircleShape)
                ) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = Text)
                }
            }
        }

        item { SectionTitle("For you", "Fresh picks for your next session") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(DemoCatalog.featured) { track -> AlbumCard(track, onTrack) }
            }
        }

        item { SectionTitle("Trending now", "Most played this week") }
        items(DemoCatalog.trending) { track -> TrackRow(track, onTrack) }

        item { SectionTitle("Late night", "Dark, slow and cinematic") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(DemoCatalog.trending.reversed()) { track -> AlbumCard(track, onTrack) }
            }
        }
    }
}

@Composable
private fun SearchScreen() {
    Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("Search", color = Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        Surface(color = Surface2, shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = Muted)
                Spacer(Modifier.width(12.dp))
                Text("Artists, songs, albums...", color = Muted, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Browse by mood", color = Text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        val moods = listOf("Late Night", "Workout", "Focus", "Chill", "Energy", "Indie")
        moods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { mood ->
                    Box(
                        Modifier.weight(1f).height(92.dp).clip(RoundedCornerShape(18.dp))
                            .background(Surface2).padding(16.dp)
                    ) {
                        Text(
                            mood,
                            color = Text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LibraryScreen() {
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            Text("Your Library", color = Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))
        }
        val rows = listOf(
            Icons.Rounded.Favorite to "Liked Songs",
            Icons.Rounded.Download to "Downloads",
            Icons.Rounded.QueueMusic to "Playlists",
            Icons.Rounded.Person to "Artists"
        )
        items(rows) { (icon, title) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Surface2),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = Accent) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Your collection", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)) {
        Text(title, color = Text, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AlbumCard(track: Track, onTrack: (Track) -> Unit) {
    Column(Modifier.width(154.dp).padding(end = 12.dp).clickable { onTrack(track) }) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(142.dp).clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun TrackRow(track: Track, onTrack: (Track) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Text, fontWeight = FontWeight.Bold)
            Text(track.artist, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = Muted)
    }
}

@Composable
private fun MiniPlayer(track: Track, onOpen: () -> Unit) {
    Surface(color = Color(0xFF171717), modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = {}) { Icon(Icons.Rounded.SkipPrevious, null, tint = Text) }
            IconButton(onClick = {}) { Icon(Icons.Rounded.PlayArrow, null, tint = Text) }
            IconButton(onClick = {}) { Icon(Icons.Rounded.SkipNext, null, tint = Text) }
        }
    }
}

@Composable
private fun NowPlaying(track: Track, onClose: () -> Unit) {
    var playing by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2410), Bg, Bg)))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Text) }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) { Icon(Icons.Rounded.MoreHoriz, null, tint = Text) }
            }
            Spacer(Modifier.height(28.dp))
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp))
            )
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Text, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text(track.artist, color = Muted, fontSize = 16.sp)
                }
                Icon(Icons.Rounded.FavoriteBorder, null, tint = Text)
            }
            Spacer(Modifier.height(26.dp))
            Slider(
                value = 0.34f,
                onValueChange = {},
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Surface2
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text("1:18", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("-2:41", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Rounded.Shuffle, null, tint = Muted) }
                IconButton(onClick = {}) { Icon(Icons.Rounded.SkipPrevious, null, tint = Text, modifier = Modifier.size(38.dp)) }
                FilledIconButton(
                    onClick = { playing = !playing },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Text, contentColor = Bg),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = {}) { Icon(Icons.Rounded.SkipNext, null, tint = Text, modifier = Modifier.size(38.dp)) }
                IconButton(onClick = {}) { Icon(Icons.Rounded.Repeat, null, tint = Muted) }
            }
            Spacer(Modifier.height(22.dp))
            Surface(color = Surface2, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lyrics, null, tint = Accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Live lyrics", color = Text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}
