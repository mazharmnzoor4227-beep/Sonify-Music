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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
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
private val Surface1 = Color(0xFF141414)
private val Surface2 = Color(0xFF1C1C1C)
private val Text = Color(0xFFF7F7F7)
private val Muted = Color(0xFF9D9D9D)
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

private data class Category(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val trackIds: List<String>
)

private data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>
)

private val categories = listOf(
    Category("sad", "Sad Songs", "Hindi, Pakistani and heartbreak moods",
        "https://picsum.photos/seed/sonify-sad-v3/600", listOf("5","3","10","9","1")),
    Category("chill", "Chill", "Easy listening for a quiet mood",
        "https://picsum.photos/seed/sonify-chill-v3/600", listOf("2","5","1","7","10")),
    Category("workout", "Workout", "High-energy picks",
        "https://picsum.photos/seed/sonify-workout-v3/600", listOf("6","7","8","4","2")),
    Category("night", "Late Night", "Dark, slow and cinematic",
        "https://picsum.photos/seed/sonify-night-v3/600", listOf("1","3","9","10","5"))
)

class SonifyActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerState by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
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

        setContent { SonifyRoot(controllerState) }
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
private fun SonifyRoot(controller: MediaController?) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var current by remember { mutableStateOf<Track?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var addTrackDialog by remember { mutableStateOf<Track?>(null) }

    val liked = remember {
        mutableStateListOf<String>().apply { addAll(loadLiked(context)) }
    }
    val playlists = remember {
        mutableStateListOf<Playlist>().apply { addAll(loadPlaylists(context)) }
    }

    fun toggleLike(id: String) {
        if (liked.contains(id)) liked.remove(id) else liked.add(id)
        saveLiked(context, liked)
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        playlists.add(Playlist(System.currentTimeMillis().toString(), clean, emptyList()))
        savePlaylists(context, playlists)
    }

    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        savePlaylists(context, playlists)
        screen = Screen.Playlists
    }

    fun addToPlaylist(track: Track, playlistId: String) {
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
        repeatMode = controller.repeatMode
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0L } ?: 1L
                isPlaying = player.isPlaying
                shuffle = player.shuffleModeEnabled
                repeatMode = player.repeatMode
                player.currentMediaItem?.mediaId?.let { id ->
                    DemoCatalog.allTracks.firstOrNull { it.id == id }?.let { current = it }
                }
            }
            delay(350)
        }
    }

    fun playTrack(track: Track, expand: Boolean = true) {
        val player = controller
        if (player == null) {
            Toast.makeText(context, "Player is getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        val queue = DemoCatalog.allTracks
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toMediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = track
        playerExpanded = expand
    }

    fun togglePlayback() {
        val player = controller ?: return
        val track = current ?: return
        if (player.currentMediaItem == null) {
            playTrack(track, false)
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun previous() {
        controller?.let { player ->
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else player.seekTo(0L)
        }
    }

    fun next() {
        controller?.let { player ->
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }
    }

    fun playNext(track: Track) {
        val player = controller ?: return
        if (player.currentMediaItem == null) {
            playTrack(track)
            return
        }
        val insertAt = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertAt, track.toMediaItem())
        Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
    }

    fun dismissPlayer() {
        controller?.stop()
        controller?.clearMediaItems()
        current = null
        playerExpanded = false
        queueOpen = false
        positionMs = 0L
        durationMs = 1L
        isPlaying = false
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
                NavigationBar(containerColor = Color(0xFF080808)) {
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
                        selected = screen is Screen.Library ||
                            screen is Screen.Liked ||
                            screen is Screen.Playlists ||
                            screen is Screen.PlaylistDetail,
                        onClick = { screen = Screen.Library },
                        icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                        label = { Text("Library") },
                        colors = navColors()
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(80)) },
                    label = "screen"
                ) { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(
                            onTrack = { playTrack(it, true) },
                            onCategory = { screen = Screen.Category(it) }
                        )

                        Screen.Search -> SearchScreen(
                            liked = liked,
                            onTrack = { playTrack(it, true) },
                            onCategory = { screen = Screen.Category(it) },
                            onToggleLike = ::toggleLike,
                            onAddToPlaylist = { addTrackDialog = it },
                            onPlayNext = ::playNext
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
                                    liked = liked,
                                    onBack = { screen = Screen.Home },
                                    onTrack = { playTrack(it, true) },
                                    onToggleLike = ::toggleLike,
                                    onAddToPlaylist = { addTrackDialog = it },
                                    onPlayNext = ::playNext
                                )
                            }
                        }

                        Screen.Liked -> LikedScreen(
                            liked = liked,
                            onBack = { screen = Screen.Library },
                            onTrack = { playTrack(it, true) },
                            onToggleLike = ::toggleLike,
                            onAddToPlaylist = { addTrackDialog = it },
                            onPlayNext = ::playNext
                        )

                        Screen.Playlists -> PlaylistsScreen(
                            playlists = playlists,
                            onBack = { screen = Screen.Library },
                            onCreate = { createPlaylistOpen = true },
                            onOpen = { screen = Screen.PlaylistDetail(it) }
                        )

                        is Screen.PlaylistDetail -> {
                            val playlist = playlists.firstOrNull { it.id == target.id }
                            if (playlist != null) {
                                PlaylistDetailScreen(
                                    playlist = playlist,
                                    onBack = { screen = Screen.Playlists },
                                    onTrack = { playTrack(it, true) },
                                    onAddSongs = { screen = Screen.Search },
                                    onDelete = { deletePlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }

                current?.let { track ->
                    MorphingPlayer(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        track = track,
                        expanded = playerExpanded,
                        onExpandedChange = { playerExpanded = it },
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        liked = liked.contains(track.id),
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onPrevious = ::previous,
                        onToggle = ::togglePlayback,
                        onNext = ::next,
                        onDismiss = ::dismissPlayer,
                        onSeek = { controller?.seekTo(it) },
                        onToggleLike = { toggleLike(track.id) },
                        onAddToPlaylist = { addTrackDialog = track },
                        onQueue = { queueOpen = true },
                        onToggleShuffle = {
                            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                        },
                        onToggleRepeat = {
                            controller?.let { player ->
                                player.repeatMode = when (player.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }
                        }
                    )
                }
            }
        }

        if (queueOpen) {
            QueueSheet(
                current = current,
                onDismiss = { queueOpen = false },
                onTrack = {
                    queueOpen = false
                    playTrack(it, true)
                }
            )
        }

        if (createPlaylistOpen) {
            CreatePlaylistDialog(
                onDismiss = { createPlaylistOpen = false },
                onCreate = {
                    createPlaylist(it)
                    createPlaylistOpen = false
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
                    createPlaylistOpen = true
                },
                onAdd = {
                    addToPlaylist(track, it)
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
private fun HomeScreen(onTrack: (Track) -> Unit, onCategory: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SONIFY", color = Text, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.size(8.dp).background(Accent, CircleShape))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Good evening", color = Muted, fontSize = 14.sp)
                }
            }
        }

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
            CompactTrackRow(track, onTrack)
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
private fun SearchScreen(
    liked: List<String>,
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
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
                        CategoryBrowseCard(
                            category = category,
                            modifier = Modifier.weight(1f),
                            onClick = { onCategory(category.id) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        } else if (results.isEmpty()) {
            item { EmptyState("No preview tracks found", "Live catalog search comes next.") }
        } else {
            items(results, key = { it.id }) { track ->
                FunctionalTrackRow(
                    track = track,
                    liked = liked.contains(track.id),
                    onTrack = onTrack,
                    onToggleLike = { onToggleLike(track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onPlayNext = { onPlayNext(track) }
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
            LibraryNavRow(Icons.Rounded.Favorite, "Liked Songs", "$likedCount saved tracks", onLiked)
            LibraryNavRow(Icons.Rounded.QueueMusic, "Playlists", "$playlistCount playlists", onPlaylists)
        }
    }
}

@Composable
private fun CategoryScreen(
    category: Category,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val tracks = category.trackIds.mapNotNull { id ->
        DemoCatalog.allTracks.firstOrNull { it.id == id }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            DetailHeader(category.title, category.subtitle, onBack)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ArtworkUrl(
                    category.artworkUrl,
                    Modifier.size(220.dp).clip(RoundedCornerShape(28.dp))
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        items(tracks, key = { it.id }) { track ->
            FunctionalTrackRow(
                track = track,
                liked = liked.contains(track.id),
                onTrack = onTrack,
                onToggleLike = { onToggleLike(track.id) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onPlayNext = { onPlayNext(track) }
            )
        }
    }
}

@Composable
private fun LikedScreen(
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val tracks = DemoCatalog.allTracks.filter { it.id in liked }
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { DetailHeader("Liked Songs", "${tracks.size} tracks", onBack) }
        if (tracks.isEmpty()) {
            item { EmptyState("No liked songs yet", "Tap the heart on a song and it will appear here.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                FunctionalTrackRow(
                    track = track,
                    liked = true,
                    onTrack = onTrack,
                    onToggleLike = { onToggleLike(track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onPlayNext = { onPlayNext(track) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(
    playlists: List<Playlist>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            DetailHeader("Playlists", "Your collections", onBack)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onCreate)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Surface2),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Add, null, tint = Accent) }
                Spacer(Modifier.width(14.dp))
                Text("Create new playlist", color = Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }

        if (playlists.isEmpty()) {
            item { EmptyState("No playlists yet", "Create one and add your favorite tracks.") }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Surface2),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.QueueMusic, null, tint = Accent) }
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
    playlist: Playlist,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit
) {
    val tracks = playlist.trackIds.mapNotNull { id ->
        DemoCatalog.allTracks.firstOrNull { it.id == id }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            DetailHeader(playlist.name, "${tracks.size} tracks", onBack)
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = onAddSongs) {
                    Icon(Icons.Rounded.Add, null, tint = Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Add songs", color = Accent)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Muted)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = Muted)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyState("This playlist is empty", "Use a song menu and choose Add to playlist.") }
        } else {
            items(tracks, key = { it.id }) { track -> CompactTrackRow(track, onTrack) }
        }
    }
}

@Composable
private fun MorphingPlayer(
    modifier: Modifier,
    track: Track,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (expanded) 1f else 0f) }

    val settledFraction by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playerMorph"
    )
    val fraction = if (dragging) dragFraction else settledFraction

    LaunchedEffect(expanded) {
        if (!dragging) dragFraction = if (expanded) 1f else 0f
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val miniHeight = 78.dp
        val horizontalMargin = 8.dp * (1f - fraction)
        val playerHeight = miniHeight + (maxHeight - miniHeight) * fraction
        val corner = 18.dp * (1f - fraction)
        val fullAlpha = ((fraction - 0.32f) / 0.68f).coerceIn(0f, 1f)
        val miniAlpha = ((0.52f - fraction) / 0.52f).coerceIn(0f, 1f)

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = horizontalMargin)
                .fillMaxWidth()
                .height(playerHeight)
                .clip(RoundedCornerShape(corner))
                .background(
                    if (fraction > 0.35f) {
                        Brush.verticalGradient(listOf(Color(0xFF202812), Bg, Bg))
                    } else {
                        Brush.verticalGradient(listOf(Surface1, Surface1))
                    }
                )
                .pointerInput(track.id, expanded) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = settledFraction
                            dragging = true
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            val delta = -amount / size.height.toFloat().coerceAtLeast(1f)
                            dragFraction = (dragFraction + delta).coerceIn(0f, 1f)
                        },
                        onDragCancel = {
                            val open = dragFraction > 0.5f
                            dragging = false
                            onExpandedChange(open)
                        },
                        onDragEnd = {
                            val open = dragFraction > 0.5f
                            dragging = false
                            onExpandedChange(open)
                        }
                    )
                }
        ) {
            if (fullAlpha > 0.01f) {
                FullPlayerContent(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fullAlpha },
                    track = track,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    liked = liked,
                    shuffle = shuffle,
                    repeatMode = repeatMode,
                    onCollapse = { onExpandedChange(false) },
                    onPrevious = onPrevious,
                    onToggle = onToggle,
                    onNext = onNext,
                    onSeek = onSeek,
                    onToggleLike = onToggleLike,
                    onAddToPlaylist = onAddToPlaylist,
                    onQueue = onQueue,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat
                )
            }

            if (miniAlpha > 0.01f) {
                MiniPlayerContent(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { alpha = miniAlpha }
                        .clickable { onExpandedChange(true) },
                    track = track,
                    isPlaying = isPlaying,
                    progress = (positionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat())
                        .coerceIn(0f, 1f),
                    onPrevious = onPrevious,
                    onToggle = onToggle,
                    onNext = onNext,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerContent(
    modifier: Modifier,
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Accent,
            trackColor = Color(0xFF2A2A2A)
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(track, Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Text, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Text)
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Text)
            }
            IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SkipNext, null, tint = Text)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun FullPlayerContent(
    modifier: Modifier,
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onCollapse: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.width(44.dp).height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF666666))
        )
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Text)
            }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Rounded.PlaylistAdd, null, tint = Text)
            }
        }

        Spacer(Modifier.height(14.dp))
        Artwork(
            track,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
        )
        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(18.dp))
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

        Spacer(Modifier.height(12.dp))
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
                val tint = if (repeatMode == Player.REPEAT_MODE_OFF) Muted else Accent
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    null,
                    tint = tint
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Surface2).clickable(onClick = onQueue).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.QueueMusic, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Text("Queue", color = Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    current: Track?,
    onDismiss: () -> Unit,
    onTrack: (Track) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface1
    ) {
        Text(
            "Up next",
            color = Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            items(DemoCatalog.allTracks, key = { it.id }) { track ->
                Row(
                    Modifier.fillMaxWidth().clickable { onTrack(track) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(track, Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = if (current?.id == track.id) Accent else Text,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(track.artist, color = Muted, fontSize = 12.sp)
                    }
                    if (current?.id == track.id) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionalTrackRow(
    track: Track,
    liked: Boolean,
    onTrack: (Track) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onTrack(track) }
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
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null) },
                    onClick = {
                        menuOpen = false
                        onPlayNext()
                    }
                )
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
private fun CategoryCard(category: Category, onClick: () -> Unit) {
    Column(Modifier.width(154.dp).clickable(onClick = onClick)) {
        ArtworkUrl(
            category.artworkUrl,
            Modifier.size(154.dp).clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(10.dp))
        Text(category.title, color = Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
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
private fun CategoryBrowseCard(category: Category, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(100.dp).clip(RoundedCornerShape(18.dp))
            .background(Surface2).clickable(onClick = onClick)
    ) {
        ArtworkUrl(
            category.artworkUrl,
            Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f }
        )
        Text(
            category.title,
            color = Text,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
        )
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
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
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 13.dp)) {
        Text(title, color = Text, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 13.sp)
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
        Modifier.fillMaxWidth().clickable { onTrack(track) }
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
    playlists: List<Playlist>,
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
                            Modifier.fillMaxWidth().clickable { onAdd(playlist.id) }
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
            ImageRequest.Builder(context).data(url).crossfade(false).build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Surface2)
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

private fun loadLiked(context: Context): Set<String> =
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("liked", emptySet())?.toSet() ?: emptySet()

private fun saveLiked(context: Context, liked: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("liked", liked.toSet()).apply()
}

private fun loadPlaylists(context: Context): List<Playlist> {
    val raw = context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("playlists", emptySet()) ?: emptySet()

    return raw.mapNotNull { entry ->
        val parts = entry.split("|", limit = 3)
        if (parts.size < 2) return@mapNotNull null
        val ids = parts.getOrNull(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        Playlist(parts[0], parts[1], ids)
    }.sortedBy { it.name.lowercase() }
}

private fun savePlaylists(context: Context, playlists: List<Playlist>) {
    val raw = playlists.map { playlist ->
        val cleanName = playlist.name.replace("|", " ")
        "${playlist.id}|$cleanName|${playlist.trackIds.joinToString(",")}" 
    }.toSet()

    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("playlists", raw).apply()
}
