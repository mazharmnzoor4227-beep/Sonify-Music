package com.sonify.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sonify.music.data.DemoCatalog
import com.sonify.music.model.Track
import com.sonify.music.playback.PlaybackService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private val Bg = Color(0xFF050505)
private val Surface1 = Color(0xFF111111)
private val Surface2 = Color(0xFF1A1A1A)
private val Text = Color(0xFFF8F8F8)
private val Muted = Color(0xFF9B9B9B)
private val Accent = Color(0xFFB7FF45)

class MainActivity : ComponentActivity() {
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controllerState by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener(
                { controllerState = runCatching { future.get() }.getOrNull() },
                mainExecutor
            )
        }

        setContent { SonifyApp(controllerState) }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controllerState = null
        super.onDestroy()
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(streamUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(artworkUrl))
            .build()
    )
    .build()

@Composable
private fun SonifyApp(controller: MediaController?) {
    var tab by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf(DemoCatalog.featured.first()) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var searchSeed by remember { mutableStateOf("") }
    val liked = remember { mutableStateListOf<String>() }
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { id ->
                    DemoCatalog.allTracks.firstOrNull { it.id == id }?.let { current = it }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = controller.isPlaying
            }
        }

        controller.addListener(listener)
        isPlaying = controller.isPlaying

        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            val p = controller
            if (p != null) {
                positionMs = p.currentPosition.coerceAtLeast(0L)
                durationMs = p.duration.takeIf { it > 0 } ?: 1L
                p.currentMediaItem?.mediaId?.let { id ->
                    DemoCatalog.allTracks.firstOrNull { it.id == id }?.let { current = it }
                }
                isPlaying = p.isPlaying
            }
            delay(500)
        }
    }

    fun playTrack(track: Track) {
        val player = controller ?: return
        val queue = DemoCatalog.allTracks
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toMediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = track
    }

    fun togglePlayback() {
        val player = controller ?: return
        if (player.currentMediaItem == null) {
            playTrack(current)
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun previous() {
        controller?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L)
        }
    }

    fun next() {
        controller?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem()
        }
    }

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
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(140)),
                        exit = fadeOut(tween(120))
                    ) {
                        MiniPlayer(
                            track = current,
                            isPlaying = isPlaying,
                            progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                            onOpen = { nowPlayingOpen = true },
                            onPrevious = ::previous,
                            onToggle = ::togglePlayback,
                            onNext = ::next
                        )
                    }
                    NavigationBar(containerColor = Color(0xFA080808)) {
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
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                    },
                    label = "tabs"
                ) { selected ->
                    when (selected) {
                        0 -> HomeScreen(
                            onTrack = ::playTrack,
                            onCategory = { category ->
                                searchSeed = category
                                tab = 1
                            },
                            onNotification = {
                                LaunchedEffect(Unit) { snackbar.showSnackbar("You're all caught up") }
                            }
                        )

                        1 -> SearchScreen(
                            initialQuery = searchSeed,
                            liked = liked,
                            onTrack = ::playTrack,
                            onToggleLike = { id ->
                                if (liked.contains(id)) liked.remove(id) else liked.add(id)
                            }
                        )

                        else -> LibraryScreen(
                            liked = liked,
                            onTrack = ::playTrack
                        )
                    }
                }
            }

            if (nowPlayingOpen) {
                NowPlaying(
                    track = current,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    liked = liked.contains(current.id),
                    shuffle = controller?.shuffleModeEnabled == true,
                    repeatAll = controller?.repeatMode == Player.REPEAT_MODE_ALL,
                    onClose = { nowPlayingOpen = false },
                    onPrevious = ::previous,
                    onToggle = ::togglePlayback,
                    onNext = ::next,
                    onSeek = { value -> controller?.seekTo(value) },
                    onToggleLike = {
                        if (liked.contains(current.id)) liked.remove(current.id) else liked.add(current.id)
                    },
                    onToggleShuffle = {
                        controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false)
                    },
                    onToggleRepeat = {
                        controller?.repeatMode = if (controller.repeatMode == Player.REPEAT_MODE_ALL) {
                            Player.REPEAT_MODE_OFF
                        } else {
                            Player.REPEAT_MODE_ALL
                        }
                    },
                    onLyrics = {
                        LaunchedEffect(Unit) {
                            snackbar.showSnackbar("Lyrics will appear when the live catalog provides them")
                        }
                    }
                )
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
private fun HomeScreen(
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onNotification: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item { HomeHeader(onNotification) }

        item { SectionTitle("For you", "Fresh picks for your next session") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(DemoCatalog.featured, key = { it.id }) { track ->
                    AlbumCard(track, onTrack)
                }
            }
        }

        item { SectionTitle("Sad & heartbreak", "Pick a mood") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    listOf("Hindi Sad", "Pakistani Sad", "Heartbreak", "Slow & Acoustic")
                ) { label ->
                    MoodPill(label) { onCategory(label) }
                }
            }
        }

        item { SectionTitle("Trending now", "Most played in Sonify Preview") }
        items(DemoCatalog.trending, key = { it.id }) { track ->
            TrackRow(track = track, onTrack = onTrack)
        }

        item { SectionTitle("Late night", "Dark, slow and cinematic") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(DemoCatalog.trending.reversed(), key = { it.id }) { track ->
                    AlbumCard(track, onTrack)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onNotification: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF18200B), Color(0xFF0A0D06), Bg)
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "SONIFY",
                    color = Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Good evening",
                    color = Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "What do you want to hear?",
                    color = Muted,
                    fontSize = 14.sp
                )
            }

            IconButton(
                onClick = onNotification,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1D2415), CircleShape)
            ) {
                Icon(Icons.Rounded.NotificationsNone, null, tint = Text)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallTag("Music")
            SmallTag("Made for you")
            SmallTag("Fresh picks")
        }
    }
}

@Composable
private fun SmallTag(text: String) {
    Surface(color = Color(0xFF171717), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun MoodPill(label: String, onClick: () -> Unit) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(146.dp)
            .height(76.dp)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(14.dp)) {
            Icon(
                Icons.Rounded.Favorite,
                null,
                tint = Accent,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
            )
            Text(
                label,
                color = Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun SearchScreen(
    initialQuery: String,
    liked: List<String>,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) query = initialQuery
    }

    val results = remember(query) { DemoCatalog.search(query) }
    val catalogCategory = query.equals("Hindi Sad", true) || query.equals("Pakistani Sad", true)

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Text("Search", color = Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Artists, songs, albums...", color = Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, null, tint = Muted)
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Text,
                    unfocusedTextColor = Text,
                    cursorColor = Accent
                )
            )
            Spacer(Modifier.height(24.dp))
        }

        if (catalogCategory) {
            item {
                Surface(color = Surface2, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LibraryMusic, null, tint = Accent)
                            Spacer(Modifier.width(10.dp))
                            Text(query, color = Text, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The category is ready. Real Hindi and Pakistani sad tracks will populate here when the live music catalog is connected.",
                            color = Muted,
                            lineHeight = 20.sp
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        if (results.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.MusicOff, null, tint = Muted, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No preview tracks found", color = Text, fontWeight = FontWeight.Bold)
                    Text("Try another search", color = Muted)
                }
            }
        } else {
            items(results, key = { it.id }) { track ->
                SearchResultRow(
                    track = track,
                    liked = liked.contains(track.id),
                    onTrack = onTrack,
                    onToggleLike = { onToggleLike(track.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    track: Track,
    liked: Boolean,
    onTrack: (Track) -> Unit,
    onToggleLike: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTrack(track) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(58.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                null,
                tint = if (liked) Accent else Muted
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    liked: List<String>,
    onTrack: (Track) -> Unit
) {
    val likedTracks = DemoCatalog.allTracks.filter { liked.contains(it.id) }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            Text("Your Library", color = Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("Your music, one place.", color = Muted)
            Spacer(Modifier.height(22.dp))
        }

        item {
            LibraryQuickRow(Icons.Rounded.Favorite, "Liked Songs", "${likedTracks.size} saved tracks")
            LibraryQuickRow(Icons.Rounded.QueueMusic, "Playlists", "Create and organize mixes")
            LibraryQuickRow(Icons.Rounded.Download, "Downloads", "Offline music will appear here")
            Spacer(Modifier.height(22.dp))
            Text("Liked tracks", color = Text, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
        }

        if (likedTracks.isEmpty()) {
            item {
                Text("Tap the heart on a song to save it here.", color = Muted)
            }
        } else {
            items(likedTracks, key = { it.id }) { track ->
                TrackRow(track, onTrack)
            }
        }
    }
}

@Composable
private fun LibraryQuickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 13.dp)) {
        Text(title, color = Text, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AlbumCard(track: Track, onTrack: (Track) -> Unit) {
    Column(
        Modifier
            .width(154.dp)
            .padding(end = 12.dp)
            .clickable { onTrack(track) }
    ) {
        Artwork(track, Modifier.size(142.dp))
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun TrackRow(track: Track, onTrack: (Track) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTrack(track) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(58.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun Artwork(track: Track, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(track.artworkUrl) {
        ImageRequest.Builder(context)
            .data(track.artworkUrl)
            .crossfade(false)
            .size(500)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(18.dp))
    )
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Surface(color = Color(0xFF141414), modifier = Modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Accent,
                trackColor = Color(0xFF2A2A2A)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(track, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Text,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Text)
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Text
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Text)
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    shuffle: Boolean,
    repeatAll: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onLyrics: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF18200B), Bg, Bg)))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Text)
                }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (liked) Accent else Text
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(28.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Text,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(track.artist, color = Muted, fontSize = 16.sp)
                }
                Surface(color = Surface2, shape = RoundedCornerShape(50)) {
                    Text(
                        track.source.ifBlank { "Sonify" },
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Slider(
                value = positionMs.coerceAtMost(durationMs).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Surface2
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(positionMs), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(durationMs), color = Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) Accent else Muted)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Text, modifier = Modifier.size(38.dp))
                }
                FilledIconButton(
                    onClick = onToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Text,
                        contentColor = Bg
                    ),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Text, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(Icons.Rounded.Repeat, null, tint = if (repeatAll) Accent else Muted)
                }
            }

            Spacer(Modifier.height(20.dp))
            Surface(
                color = Surface2,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLyrics)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lyrics, null, tint = Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lyrics", color = Text, fontWeight = FontWeight.Bold)
                        Text("Tap to open", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
