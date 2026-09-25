package com.sonify.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.google.common.util.concurrent.ListenableFuture
import com.sonify.music.data.DemoCatalog
import com.sonify.music.model.Track
import com.sonify.music.playback.PlaybackService
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

private val Bg = Color(0xFF050505)
private val Surface1 = Color(0xFF111111)
private val Surface2 = Color(0xFF1A1A1A)
private val Text = Color(0xFFF8F8F8)
private val Muted = Color(0xFF9B9B9B)
private val Accent = Color(0xFFB7FF45)

private sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data class Category(val id: String) : Screen
    data object Liked : Screen
    data object Playlists : Screen
    data class PlaylistDetail(val id: String) : Screen
}

private data class CategoryModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val trackIds: List<String>
)

private data class PlaylistModel(
    val id: String,
    val name: String,
    val trackIds: List<String>
)

private val categories = listOf(
    CategoryModel(
        id = "sad",
        title = "Sad Songs",
        subtitle = "Hindi, Pakistani and heartbreak moods",
        artworkUrl = "https://picsum.photos/seed/sonify-sad/600",
        trackIds = listOf("5", "3", "10", "9", "1")
    ),
    CategoryModel(
        id = "chill",
        title = "Chill",
        subtitle = "Easy listening for a quiet mood",
        artworkUrl = "https://picsum.photos/seed/sonify-chill/600",
        trackIds = listOf("2", "5", "1", "7", "10")
    ),
    CategoryModel(
        id = "workout",
        title = "Workout",
        subtitle = "High energy picks",
        artworkUrl = "https://picsum.photos/seed/sonify-workout/600",
        trackIds = listOf("6", "7", "8", "4", "2")
    ),
    CategoryModel(
        id = "late-night",
        title = "Late Night",
        subtitle = "Dark, slow and cinematic",
        artworkUrl = "https://picsum.photos/seed/sonify-night/600",
        trackIds = listOf("1", "3", "9", "10", "5")
    )
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
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
        val uiExecutor = Executor { command -> runOnUiThread(command) }
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener(
                { controllerState = runCatching { future.get() }.getOrNull() },
                uiExecutor
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
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var current by remember { mutableStateOf<Track?>(null) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatAll by remember { mutableStateOf(false) }
    var playlistDialogOpen by remember { mutableStateOf(false) }
    var addTrackDialog by remember { mutableStateOf<Track?>(null) }

    val liked = remember {
        mutableStateListOf<String>().apply { addAll(loadLiked(context)) }
    }
    val playlists = remember {
        mutableStateListOf<PlaylistModel>().apply { addAll(loadPlaylists(context)) }
    }

    fun toggleLike(id: String) {
        if (liked.contains(id)) liked.remove(id) else liked.add(id)
        saveLiked(context, liked)
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        playlists.add(
            PlaylistModel(
                id = System.currentTimeMillis().toString(),
                name = clean,
                trackIds = emptyList()
            )
        )
        savePlaylists(context, playlists)
    }

    fun addTrackToPlaylist(track: Track, playlistId: String) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return
        val playlist = playlists[index]
        if (track.id in playlist.trackIds) {
            Toast.makeText(context, "Already in ${playlist.name}", Toast.LENGTH_SHORT).show()
            return
        }
        playlists[index] = playlist.copy(trackIds = playlist.trackIds + track.id)
        savePlaylists(context, playlists)
        Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
    }

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
        }
        controller.addListener(listener)
        isPlaying = controller.isPlaying
        shuffle = controller.shuffleModeEnabled
        repeatAll = controller.repeatMode == Player.REPEAT_MODE_ALL
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0L } ?: 1L
                isPlaying = player.isPlaying
                player.currentMediaItem?.mediaId?.let { id ->
                    DemoCatalog.allTracks.firstOrNull { it.id == id }?.let { current = it }
                }
            }
            delay(500)
        }
    }

    fun playTrack(track: Track) {
        val player = controller
        if (player == null) {
            Toast.makeText(context, "Player is getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        val queue = DemoCatalog.allTracks
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        player.prepare()
        player.play()
        current = track
        nowPlayingOpen = true
    }

    fun togglePlayback() {
        val player = controller ?: return
        val track = current ?: return
        if (player.currentMediaItem == null) {
            val queue = DemoCatalog.allTracks
            val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            player.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
            player.prepare()
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun previous() {
        controller?.let { player ->
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0L)
        }
    }

    fun next() {
        controller?.let { player ->
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
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
            bottomBar = {
                Column {
                    current?.let { track ->
                        MiniPlayer(
                            track = track,
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
                            selected = screen is Screen.Home,
                            onClick = { screen = Screen.Home },
                            icon = { Icon(Icons.Rounded.Home, null) },
                            label = { Text("Home") },
                            colors = navColors()
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Search,
                            onClick = { screen = Screen.Search },
                            icon = { Icon(Icons.Rounded.Search, null) },
                            label = { Text("Search") },
                            colors = navColors()
                        )
                        NavigationBarItem(
                            selected = screen is Screen.Library || screen is Screen.Liked || screen is Screen.Playlists || screen is Screen.PlaylistDetail,
                            onClick = { screen = Screen.Library },
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
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(90)) },
                    label = "screen"
                ) { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(
                            onTrack = ::playTrack,
                            onCategory = { screen = Screen.Category(it) }
                        )

                        Screen.Search -> SearchScreen(
                            liked = liked,
                            onTrack = ::playTrack,
                            onToggleLike = ::toggleLike,
                            onAddToPlaylist = { addTrackDialog = it }
                        )

                        Screen.Library -> LibraryScreen(
                            likedCount = liked.size,
                            playlistCount = playlists.size,
                            onLiked = { screen = Screen.Liked },
                            onPlaylists = { screen = Screen.Playlists }
                        )

                        is Screen.Category -> {
                            val category = categories.firstOrNull { it.id == target.id }
                            if (category != null) {
                                CategoryScreen(
                                    category = category,
                                    onBack = { screen = Screen.Home },
                                    onTrack = ::playTrack,
                                    liked = liked,
                                    onToggleLike = ::toggleLike,
                                    onAddToPlaylist = { addTrackDialog = it }
                                )
                            }
                        }

                        Screen.Liked -> LikedSongsScreen(
                            liked = liked,
                            onBack = { screen = Screen.Library },
                            onTrack = ::playTrack,
                            onToggleLike = ::toggleLike,
                            onAddToPlaylist = { addTrackDialog = it }
                        )

                        Screen.Playlists -> PlaylistsScreen(
                            playlists = playlists,
                            onBack = { screen = Screen.Library },
                            onCreate = { playlistDialogOpen = true },
                            onPlaylist = { screen = Screen.PlaylistDetail(it) }
                        )

                        is Screen.PlaylistDetail -> {
                            val playlist = playlists.firstOrNull { it.id == target.id }
                            if (playlist != null) {
                                PlaylistDetailScreen(
                                    playlist = playlist,
                                    onBack = { screen = Screen.Playlists },
                                    onTrack = ::playTrack,
                                    onAddSongs = { screen = Screen.Search }
                                )
                            }
                        }
                    }
                }
            }

            current?.let { track ->
                if (nowPlayingOpen) {
                    NowPlaying(
                        track = track,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        liked = liked.contains(track.id),
                        shuffle = shuffle,
                        repeatAll = repeatAll,
                        onClose = { nowPlayingOpen = false },
                        onPrevious = ::previous,
                        onToggle = ::togglePlayback,
                        onNext = ::next,
                        onSeek = { controller?.seekTo(it) },
                        onToggleLike = { toggleLike(track.id) },
                        onAddToPlaylist = { addTrackDialog = track },
                        onToggleShuffle = {
                            controller?.let { player ->
                                player.shuffleModeEnabled = !player.shuffleModeEnabled
                                shuffle = player.shuffleModeEnabled
                            }
                        },
                        onToggleRepeat = {
                            controller?.let { player ->
                                player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                                    Player.REPEAT_MODE_OFF
                                } else {
                                    Player.REPEAT_MODE_ALL
                                }
                                repeatAll = player.repeatMode == Player.REPEAT_MODE_ALL
                            }
                        }
                    )
                }
            }
        }

        if (playlistDialogOpen) {
            CreatePlaylistDialog(
                onDismiss = { playlistDialogOpen = false },
                onCreate = {
                    createPlaylist(it)
                    playlistDialogOpen = false
                }
            )
        }

        addTrackDialog?.let { track ->
            AddToPlaylistDialog(
                track = track,
                playlists = playlists,
                onDismiss = { addTrackDialog = null },
                onCreatePlaylist = {
                    addTrackDialog = null
                    playlistDialogOpen = true
                },
                onAdd = { playlistId ->
                    addTrackToPlaylist(track, playlistId)
                    addTrackDialog = null
                }
            )
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
    onCategory: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item { HomeHeader() }

        item { SectionTitle("For you", "Fresh picks for your next session") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(DemoCatalog.featured, key = { it.id }) { track ->
                    AlbumCard(track, onTrack)
                }
            }
        }

        item { SectionTitle("Browse moods", "Pick a category") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryCard(category) { onCategory(category.id) }
                }
            }
        }

        item { SectionTitle("Trending now", "Most played in Sonify Preview") }
        items(DemoCatalog.trending, key = { it.id }) { track ->
            CompactTrackRow(track = track, onTrack = onTrack)
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
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SONIFY",
                    color = Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(7.dp))
                Box(Modifier.size(8.dp).background(Accent, CircleShape))
            }
            Spacer(Modifier.height(7.dp))
            Text("Good evening", color = Muted, fontSize = 14.sp)
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(46.dp).background(Surface2, CircleShape)
        ) {
            Icon(Icons.Rounded.NotificationsNone, null, tint = Text)
        }
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier.size(46.dp).background(Color(0xFF222222), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, null, tint = Accent)
        }
    }
}

@Composable
private fun CategoryCard(category: CategoryModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(154.dp).clickable(onClick = onClick)
    ) {
        ArtworkUrl(
            url = category.artworkUrl,
            modifier = Modifier
                .size(154.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(10.dp))
        Text(
            category.title,
            color = Text,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1
        )
        Text(
            category.subtitle,
            color = Muted,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryScreen(
    category: CategoryModel,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    liked: List<String>,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    val tracks = category.trackIds.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1D2510), Bg)))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).background(Color(0x66000000), CircleShape)
                ) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = Text)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtworkUrl(
                        url = category.artworkUrl,
                        modifier = Modifier.size(210.dp).clip(RoundedCornerShape(28.dp))
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(category.title, color = Text, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    Text(category.subtitle, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        item {
            Text(
                "Songs",
                color = Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }

        items(tracks, key = { it.id }) { track ->
            FunctionalTrackRow(
                track = track,
                liked = liked.contains(track.id),
                onTrack = onTrack,
                onToggleLike = { onToggleLike(track.id) },
                onAddToPlaylist = { onAddToPlaylist(track) }
            )
        }
    }
}

@Composable
private fun SearchScreen(
    liked: List<String>,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { DemoCatalog.search(query) }

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
                placeholder = { Text("Songs, artists, playlists, moods…", color = Muted) },
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
            Spacer(Modifier.height(22.dp))
        }

        if (query.isBlank()) {
            item {
                Text("Browse all", color = Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
            }
            items(categories.chunked(2)) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { category ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(92.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Surface2)
                                .padding(14.dp)
                        ) {
                            Text(
                                category.title,
                                color = Text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else if (results.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No preview tracks found", color = Text, fontWeight = FontWeight.Bold)
                    Text("Live catalog search comes next", color = Muted)
                }
            }
        } else {
            items(results, key = { it.id }) { track ->
                FunctionalTrackRow(
                    track = track,
                    liked = liked.contains(track.id),
                    onTrack = onTrack,
                    onToggleLike = { onToggleLike(track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    likedCount: Int,
    playlistCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            Text("Your Library", color = Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text("Saved music and playlists", color = Muted)
            Spacer(Modifier.height(22.dp))

            LibraryNavRow(
                icon = Icons.Rounded.Favorite,
                title = "Liked Songs",
                subtitle = "$likedCount saved tracks",
                onClick = onLiked
            )
            LibraryNavRow(
                icon = Icons.Rounded.QueueMusic,
                title = "Playlists",
                subtitle = "$playlistCount playlists",
                onClick = onPlaylists
            )
        }
    }
}

@Composable
private fun LibraryNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun LikedSongsScreen(
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    val likedTracks = DemoCatalog.allTracks.filter { it.id in liked }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            DetailHeader(title = "Liked Songs", subtitle = "${likedTracks.size} tracks", onBack = onBack)
        }
        if (likedTracks.isEmpty()) {
            item {
                EmptyState("No liked songs yet", "Tap the heart on a song and it will appear here.")
            }
        } else {
            items(likedTracks, key = { it.id }) { track ->
                FunctionalTrackRow(
                    track = track,
                    liked = true,
                    onTrack = onTrack,
                    onToggleLike = { onToggleLike(track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(
    playlists: List<PlaylistModel>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onPlaylist: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            DetailHeader(title = "Playlists", subtitle = "Your collections", onBack = onBack)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Accent)
                }
                Spacer(Modifier.width(14.dp))
                Text("Create new playlist", color = Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }

        if (playlists.isEmpty()) {
            item { EmptyState("No playlists yet", "Create one and add your favorite tracks.") }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylist(playlist.id) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = Accent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${playlist.trackIds.size} tracks", color = Muted, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: PlaylistModel,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onAddSongs: () -> Unit
) {
    val tracks = playlist.trackIds.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            DetailHeader(title = playlist.name, subtitle = "${tracks.size} tracks", onBack = onBack)
            TextButton(
                onClick = onAddSongs,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Rounded.Add, null, tint = Accent)
                Spacer(Modifier.width(6.dp))
                Text("Add songs", color = Accent, fontWeight = FontWeight.Bold)
            }
        }

        if (tracks.isEmpty()) {
            item { EmptyState("This playlist is empty", "Use a song menu and choose Add to playlist.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                CompactTrackRow(track = track, onTrack = onTrack)
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, null, tint = Text)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = Text,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            subtitle,
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Muted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
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
    Column(Modifier.width(154.dp).padding(end = 12.dp).clickable { onTrack(track) }) {
        Artwork(track, Modifier.size(142.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun CompactTrackRow(track: Track, onTrack: (Track) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTrack(track) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun FunctionalTrackRow(
    track: Track,
    liked: Boolean,
    onTrack: (Track) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTrack(track) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
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
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, null, tint = Muted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                    onClick = {
                        menuOpen = false
                        onAddToPlaylist()
                    }
                )
            }
        }
    }
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
    Surface(color = Color(0xFF151515), modifier = Modifier.fillMaxWidth()) {
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
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(track, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Text)
                }
                IconButton(onClick = onToggle) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Text)
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
    onAddToPlaylist: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { _, amount ->
                        if (amount > 0) dragDistance += amount
                    },
                    onDragEnd = {
                        if (dragDistance > 120f) onClose()
                        dragDistance = 0f
                    }
                )
            },
        color = Bg
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF202812), Bg, Bg)))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF696969))
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Text)
                }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Rounded.PlaylistAdd, null, tint = Text)
                }
            }

            Spacer(Modifier.height(20.dp))
            Artwork(
                track,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(30.dp))
            )
            Spacer(Modifier.height(26.dp))

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
                    Text(track.artist, color = Muted, fontSize = 16.sp, maxLines = 1)
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (liked) Accent else Text
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Slider(
                value = progress,
                onValueChange = { onSeek((safeDuration * it).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Surface2
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(positionMs), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(safeDuration), color = Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) Accent else Muted)
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Text, modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = onToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Text, contentColor = Bg),
                    modifier = Modifier.size(70.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Text, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onToggleRepeat) {
                    Icon(Icons.Rounded.Repeat, null, tint = if (repeatAll) Accent else Muted)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface2)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.QueueMusic, null, tint = Accent)
                Spacer(Modifier.width(12.dp))
                Text("Queue & playlist controls", color = Text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlists: List<PlaylistModel>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onAdd: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(track.title, color = Muted)
                Spacer(Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Text("You don't have a playlist yet.")
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(playlist.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.QueueMusic, null)
                            Spacer(Modifier.width(10.dp))
                            Text(playlist.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreatePlaylist) { Text("New playlist") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun Artwork(track: Track, modifier: Modifier) {
    ArtworkUrl(track.artworkUrl, modifier)
}

@Composable
private fun ArtworkUrl(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(false)
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Surface2)
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun loadLiked(context: Context): Set<String> {
    return context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("liked", emptySet())
        ?.toSet()
        ?: emptySet()
}

private fun saveLiked(context: Context, liked: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("liked", liked.toSet())
        .apply()
}

private fun loadPlaylists(context: Context): List<PlaylistModel> {
    val raw = context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("playlists", emptySet())
        ?: emptySet()

    return raw.mapNotNull { entry ->
        val parts = entry.split("|", limit = 3)
        if (parts.size < 2) return@mapNotNull null
        val ids = parts.getOrNull(2)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        PlaylistModel(parts[0], parts[1], ids)
    }.sortedBy { it.name.lowercase() }
}

private fun savePlaylists(context: Context, playlists: List<PlaylistModel>) {
    val raw = playlists.map { playlist ->
        val cleanName = playlist.name.replace("|", " ")
        "${playlist.id}|$cleanName|${playlist.trackIds.joinToString(",")}" 
    }.toSet()

    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("playlists", raw)
        .apply()
}
