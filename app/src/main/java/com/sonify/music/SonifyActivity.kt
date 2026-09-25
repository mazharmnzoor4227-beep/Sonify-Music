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

private val SonifyBg = Color(0xFF050505)
private val SonifySurface = Color(0xFF141414)
private val SonifySurface2 = Color(0xFF1C1C1C)
private val SonifyText = Color(0xFFF7F7F7)
private val SonifyMuted = Color(0xFF9D9D9D)
private val SonifyAccent = Color(0xFFB7FF45)

private sealed interface SonifyScreen {
    data object Home : SonifyScreen
    data object Search : SonifyScreen
    data object Library : SonifyScreen
    data class Category(val id: String) : SonifyScreen
    data object Liked : SonifyScreen
    data object Playlists : SonifyScreen
    data class PlaylistDetail(val id: String) : SonifyScreen
}

private data class SonifyCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val trackIds: List<String>
)

private data class SonifyPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String>
)

private val sonifyCategories = listOf(
    SonifyCategory(
        "sad",
        "Sad Songs",
        "Hindi, Pakistani and heartbreak moods",
        "https://picsum.photos/seed/sonify-sad-v2/600",
        listOf("5", "3", "10", "9", "1")
    ),
    SonifyCategory(
        "chill",
        "Chill",
        "Easy listening for a quiet mood",
        "https://picsum.photos/seed/sonify-chill-v2/600",
        listOf("2", "5", "1", "7", "10")
    ),
    SonifyCategory(
        "workout",
        "Workout",
        "High-energy picks",
        "https://picsum.photos/seed/sonify-workout-v2/600",
        listOf("6", "7", "8", "4", "2")
    ),
    SonifyCategory(
        "night",
        "Late Night",
        "Dark, slow and cinematic",
        "https://picsum.photos/seed/sonify-night-v2/600",
        listOf("1", "3", "9", "10", "5")
    )
)

class SonifyActivity : ComponentActivity() {
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

        setContent { SonifyRoot(controllerState) }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controllerState = null
        super.onDestroy()
    }
}

private fun Track.sonifyMediaItem(): MediaItem = MediaItem.Builder()
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
    var screen by remember { mutableStateOf<SonifyScreen>(SonifyScreen.Home) }
    var current by remember { mutableStateOf<Track?>(null) }
    var fullPlayerOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var addTrackToPlaylist by remember { mutableStateOf<Track?>(null) }

    val liked = remember {
        mutableStateListOf<String>().apply { addAll(loadSonifyLiked(context)) }
    }
    val playlists = remember {
        mutableStateListOf<SonifyPlaylist>().apply { addAll(loadSonifyPlaylists(context)) }
    }

    fun toggleLike(id: String) {
        if (liked.contains(id)) liked.remove(id) else liked.add(id)
        saveSonifyLiked(context, liked)
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        playlists.add(SonifyPlaylist(System.currentTimeMillis().toString(), clean, emptyList()))
        saveSonifyPlaylists(context, playlists)
    }

    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        saveSonifyPlaylists(context, playlists)
        screen = SonifyScreen.Playlists
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
        saveSonifyPlaylists(context, playlists)
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
            delay(400)
        }
    }

    fun playTrack(track: Track, openPlayer: Boolean = true) {
        val player = controller
        if (player == null) {
            Toast.makeText(context, "Player is getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        val queue = DemoCatalog.allTracks
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.sonifyMediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = track
        fullPlayerOpen = openPlayer
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
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0L)
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
        player.addMediaItem(insertAt, track.sonifyMediaItem())
        Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
    }

    fun dismissPlayer() {
        controller?.stop()
        controller?.clearMediaItems()
        current = null
        fullPlayerOpen = false
        queueOpen = false
        positionMs = 0L
        durationMs = 1L
        isPlaying = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SonifyBg,
            surface = SonifySurface,
            primary = SonifyAccent,
            onBackground = SonifyText,
            onSurface = SonifyText
        )
    ) {
        Scaffold(
            containerColor = SonifyBg,
            bottomBar = {
                Column {
                    current?.let { track ->
                        SonifyMiniPlayer(
                            track = track,
                            isPlaying = isPlaying,
                            progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                            onOpen = { fullPlayerOpen = true },
                            onToggle = ::togglePlayback,
                            onDismiss = ::dismissPlayer
                        )
                    }
                    NavigationBar(containerColor = Color(0xFF080808)) {
                        NavigationBarItem(
                            selected = screen is SonifyScreen.Home,
                            onClick = { screen = SonifyScreen.Home },
                            icon = { Icon(Icons.Rounded.Home, null) },
                            label = { Text("Home") },
                            colors = sonifyNavColors()
                        )
                        NavigationBarItem(
                            selected = screen is SonifyScreen.Search,
                            onClick = { screen = SonifyScreen.Search },
                            icon = { Icon(Icons.Rounded.Search, null) },
                            label = { Text("Search") },
                            colors = sonifyNavColors()
                        )
                        NavigationBarItem(
                            selected = screen is SonifyScreen.Library || screen is SonifyScreen.Liked || screen is SonifyScreen.Playlists || screen is SonifyScreen.PlaylistDetail,
                            onClick = { screen = SonifyScreen.Library },
                            icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                            label = { Text("Library") },
                            colors = sonifyNavColors()
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(80)) },
                    label = "sonify-screen"
                ) { target ->
                    when (target) {
                        SonifyScreen.Home -> SonifyHomeScreen(
                            onTrack = { playTrack(it, true) },
                            onCategory = { screen = SonifyScreen.Category(it) }
                        )

                        SonifyScreen.Search -> SonifySearchScreen(
                            liked = liked,
                            onTrack = { playTrack(it, true) },
                            onCategory = { screen = SonifyScreen.Category(it) },
                            onToggleLike = ::toggleLike,
                            onAddToPlaylist = { addTrackToPlaylist = it },
                            onPlayNext = ::playNext
                        )

                        SonifyScreen.Library -> SonifyLibraryScreen(
                            likedCount = liked.size,
                            playlistCount = playlists.size,
                            onLiked = { screen = SonifyScreen.Liked },
                            onPlaylists = { screen = SonifyScreen.Playlists }
                        )

                        is SonifyScreen.Category -> {
                            val category = sonifyCategories.firstOrNull { it.id == target.id }
                            if (category != null) {
                                SonifyCategoryScreen(
                                    category = category,
                                    liked = liked,
                                    onBack = { screen = SonifyScreen.Home },
                                    onTrack = { playTrack(it, true) },
                                    onToggleLike = ::toggleLike,
                                    onAddToPlaylist = { addTrackToPlaylist = it },
                                    onPlayNext = ::playNext
                                )
                            }
                        }

                        SonifyScreen.Liked -> SonifyLikedScreen(
                            liked = liked,
                            onBack = { screen = SonifyScreen.Library },
                            onTrack = { playTrack(it, true) },
                            onToggleLike = ::toggleLike,
                            onAddToPlaylist = { addTrackToPlaylist = it },
                            onPlayNext = ::playNext
                        )

                        SonifyScreen.Playlists -> SonifyPlaylistsScreen(
                            playlists = playlists,
                            onBack = { screen = SonifyScreen.Library },
                            onCreate = { createPlaylistOpen = true },
                            onOpenPlaylist = { screen = SonifyScreen.PlaylistDetail(it) }
                        )

                        is SonifyScreen.PlaylistDetail -> {
                            val playlist = playlists.firstOrNull { it.id == target.id }
                            if (playlist != null) {
                                SonifyPlaylistDetailScreen(
                                    playlist = playlist,
                                    onBack = { screen = SonifyScreen.Playlists },
                                    onTrack = { playTrack(it, true) },
                                    onAddSongs = { screen = SonifyScreen.Search },
                                    onDelete = { deletePlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }
            }

            current?.let { track ->
                if (fullPlayerOpen) {
                    SonifyNowPlaying(
                        track = track,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        liked = liked.contains(track.id),
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onCollapse = { fullPlayerOpen = false },
                        onPrevious = ::previous,
                        onToggle = ::togglePlayback,
                        onNext = ::next,
                        onSeek = { controller?.seekTo(it) },
                        onToggleLike = { toggleLike(track.id) },
                        onAddToPlaylist = { addTrackToPlaylist = track },
                        onQueue = { queueOpen = true },
                        onToggleShuffle = {
                            controller?.let { player -> player.shuffleModeEnabled = !player.shuffleModeEnabled }
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
            SonifyQueueSheet(
                controller = controller,
                current = current,
                onDismiss = { queueOpen = false },
                onTrack = { track ->
                    queueOpen = false
                    playTrack(track, true)
                }
            )
        }

        if (createPlaylistOpen) {
            SonifyCreatePlaylistDialog(
                onDismiss = { createPlaylistOpen = false },
                onCreate = {
                    createPlaylist(it)
                    createPlaylistOpen = false
                }
            )
        }

        addTrackToPlaylist?.let { track ->
            SonifyAddToPlaylistDialog(
                track = track,
                playlists = playlists,
                onDismiss = { addTrackToPlaylist = null },
                onCreatePlaylist = {
                    addTrackToPlaylist = null
                    createPlaylistOpen = true
                },
                onAdd = { playlistId ->
                    addToPlaylist(track, playlistId)
                    addTrackToPlaylist = null
                }
            )
        }
    }
}

@Composable
private fun sonifyNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = SonifyAccent,
    selectedTextColor = SonifyText,
    unselectedIconColor = SonifyMuted,
    unselectedTextColor = SonifyMuted,
    indicatorColor = Color.Transparent
)

@Composable
private fun SonifyHomeScreen(onTrack: (Track) -> Unit, onCategory: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SonifyBg),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SONIFY", color = SonifyText, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).background(SonifyAccent, CircleShape))
                }
                Spacer(Modifier.height(6.dp))
                Text("Good evening", color = SonifyMuted, fontSize = 15.sp)
            }
        }

        item { SonifySectionTitle("For you", "Fresh picks for your next session") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(DemoCatalog.featured, key = { it.id }) { track -> SonifyAlbumCard(track, onTrack) }
            }
        }

        item { SonifySectionTitle("Browse moods", "Pick a category") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(sonifyCategories, key = { it.id }) { category ->
                    SonifyCategoryCard(category) { onCategory(category.id) }
                }
            }
        }

        item { SonifySectionTitle("Trending now", "Popular in Sonify Preview") }
        items(DemoCatalog.trending, key = { it.id }) { track ->
            SonifyCompactTrackRow(track, onTrack)
        }
    }
}

@Composable
private fun SonifyCategoryCard(category: SonifyCategory, onClick: () -> Unit) {
    Column(Modifier.width(154.dp).clickable(onClick = onClick)) {
        SonifyArtworkUrl(category.artworkUrl, Modifier.size(154.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(9.dp))
        Text(category.title, color = SonifyText, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        Text(category.subtitle, color = SonifyMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SonifyCategoryScreen(
    category: SonifyCategory,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val tracks = category.trackIds.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }
    LazyColumn(Modifier.fillMaxSize().background(SonifyBg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF202A12), SonifyBg)))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).background(Color(0x66000000), CircleShape)) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = SonifyText)
                }
                Column(Modifier.fillMaxWidth().padding(top = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SonifyArtworkUrl(category.artworkUrl, Modifier.size(210.dp).clip(RoundedCornerShape(28.dp)))
                    Spacer(Modifier.height(18.dp))
                    Text(category.title, color = SonifyText, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    Text(category.subtitle, color = SonifyMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        item { SonifySectionTitle("Songs", "Tap a track to open the player") }
        items(tracks, key = { it.id }) { track ->
            SonifyFunctionalTrackRow(
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
private fun SonifySearchScreen(
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
        Modifier.fillMaxSize().background(SonifyBg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Text("Search", color = SonifyText, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Songs, artists, playlists, moods…", color = SonifyMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SonifyMuted) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null, tint = SonifyMuted) }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SonifySurface2,
                    unfocusedContainerColor = SonifySurface2,
                    focusedBorderColor = SonifyAccent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = SonifyText,
                    unfocusedTextColor = SonifyText,
                    cursorColor = SonifyAccent
                )
            )
            Spacer(Modifier.height(22.dp))
        }

        if (query.isBlank()) {
            item {
                Text("Browse all", color = SonifyText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
            }
            items(sonifyCategories.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { category ->
                        Box(
                            Modifier.weight(1f).height(104.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onCategory(category.id) }
                        ) {
                            SonifyArtworkUrl(category.artworkUrl, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                            Text(
                                category.title,
                                color = SonifyText,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else if (results.isEmpty()) {
            item { SonifyEmptyState("No preview tracks found", "Live catalog search will replace the preview catalog.") }
        } else {
            items(results, key = { it.id }) { track ->
                SonifyFunctionalTrackRow(
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
private fun SonifyLibraryScreen(
    likedCount: Int,
    playlistCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(SonifyBg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        item {
            Text("Your Library", color = SonifyText, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text("Saved music and playlists", color = SonifyMuted)
            Spacer(Modifier.height(22.dp))
            SonifyLibraryRow(Icons.Rounded.Favorite, "Liked Songs", "$likedCount saved tracks", onLiked)
            SonifyLibraryRow(Icons.Rounded.QueueMusic, "Playlists", "$playlistCount playlists", onPlaylists)
        }
    }
}

@Composable
private fun SonifyLibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(SonifySurface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SonifyAccent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SonifyText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = SonifyMuted, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = SonifyMuted)
    }
}

@Composable
private fun SonifyLikedScreen(
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val tracks = DemoCatalog.allTracks.filter { it.id in liked }
    LazyColumn(Modifier.fillMaxSize().background(SonifyBg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SonifyDetailHeader("Liked Songs", "${tracks.size} tracks", onBack) }
        if (tracks.isEmpty()) {
            item { SonifyEmptyState("No liked songs yet", "Tap the heart on a song and it will appear here.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                SonifyFunctionalTrackRow(
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
private fun SonifyPlaylistsScreen(
    playlists: List<SonifyPlaylist>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().background(SonifyBg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SonifyDetailHeader("Playlists", "Your collections", onBack)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onCreate).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(SonifySurface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = SonifyAccent)
                }
                Spacer(Modifier.width(14.dp))
                Text("Create new playlist", color = SonifyText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        if (playlists.isEmpty()) {
            item { SonifyEmptyState("No playlists yet", "Create one and add your favorite tracks.") }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) }.padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(SonifySurface2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = SonifyAccent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = SonifyText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${playlist.trackIds.size} tracks", color = SonifyMuted, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = SonifyMuted)
                }
            }
        }
    }
}

@Composable
private fun SonifyPlaylistDetailScreen(
    playlist: SonifyPlaylist,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit
) {
    val tracks = playlist.trackIds.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }
    LazyColumn(Modifier.fillMaxSize().background(SonifyBg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SonifyDetailHeader(playlist.name, "${tracks.size} tracks", onBack)
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = onAddSongs) {
                    Icon(Icons.Rounded.Add, null, tint = SonifyAccent)
                    Spacer(Modifier.width(6.dp))
                    Text("Add songs", color = SonifyAccent, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = SonifyMuted)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = SonifyMuted)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { SonifyEmptyState("This playlist is empty", "Use a song menu and choose Add to playlist.") }
        } else {
            items(tracks, key = { it.id }) { track -> SonifyCompactTrackRow(track, onTrack) }
        }
    }
}

@Composable
private fun SonifyDetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = SonifyText) }
        Spacer(Modifier.height(8.dp))
        Text(title, color = SonifyText, fontWeight = FontWeight.Black, fontSize = 32.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text(subtitle, color = SonifyMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun SonifySectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 13.dp)) {
        Text(title, color = SonifyText, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = SonifyMuted, fontSize = 13.sp)
    }
}

@Composable
private fun SonifyAlbumCard(track: Track, onTrack: (Track) -> Unit) {
    Column(Modifier.width(154.dp).padding(end = 12.dp).clickable { onTrack(track) }) {
        SonifyArtwork(track, Modifier.size(142.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = SonifyText, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(track.artist, color = SonifyMuted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun SonifyCompactTrackRow(track: Track, onTrack: (Track) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SonifyArtwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = SonifyText, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = SonifyMuted, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = SonifyMuted)
    }
}

@Composable
private fun SonifyFunctionalTrackRow(
    track: Track,
    liked: Boolean,
    onTrack: (Track) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SonifyArtwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = SonifyText, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = SonifyMuted, fontSize = 13.sp, maxLines = 1)
        }
        IconButton(onClick = onToggleLike) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) SonifyAccent else SonifyMuted)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, null, tint = SonifyMuted) }
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
private fun SonifyMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF181818),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 7.dp, end = 3.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SonifyArtwork(track, Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = SonifyText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = SonifyMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(42.dp)) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = SonifyText)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = SonifyMuted)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = SonifyAccent,
                trackColor = Color(0xFF2A2A2A)
            )
        }
    }
}

@Composable
private fun SonifyNowPlaying(
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
    var dragY by remember { mutableFloatStateOf(0f) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                translationY = dragY
                alpha = 1f - (dragY / 2200f).coerceIn(0f, 0.22f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onVerticalDrag = { _, amount -> dragY = (dragY + amount).coerceAtLeast(0f) },
                    onDragCancel = { dragY = 0f },
                    onDragEnd = {
                        if (dragY > 180f) onCollapse()
                        dragY = 0f
                    }
                )
            },
        color = SonifyBg
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF242D15), SonifyBg, SonifyBg)))
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(42.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF6A6A6A)))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = SonifyText) }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", color = SonifyMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAddToPlaylist) { Icon(Icons.Rounded.PlaylistAdd, null, tint = SonifyText) }
            }
            Spacer(Modifier.height(18.dp))
            SonifyArtwork(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(30.dp)))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = SonifyText, fontSize = 27.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = SonifyMuted, fontSize = 16.sp, maxLines = 1)
                }
                IconButton(onClick = onToggleLike) {
                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) SonifyAccent else SonifyText)
                }
            }
            Spacer(Modifier.height(18.dp))
            Slider(
                value = progress,
                onValueChange = { onSeek((safeDuration * it).toLong()) },
                colors = SliderDefaults.colors(thumbColor = SonifyAccent, activeTrackColor = SonifyAccent, inactiveTrackColor = SonifySurface2)
            )
            Row(Modifier.fillMaxWidth()) {
                Text(sonifyTime(positionMs), color = SonifyMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(sonifyTime(safeDuration), color = SonifyMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleShuffle) { Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) SonifyAccent else SonifyMuted) }
                IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, null, tint = SonifyText, modifier = Modifier.size(36.dp)) }
                FilledIconButton(
                    onClick = onToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = SonifyText, contentColor = SonifyBg),
                    modifier = Modifier.size(70.dp)
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, null, tint = SonifyText, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        null,
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) SonifyMuted else SonifyAccent
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onQueue) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = SonifyText)
                    Spacer(Modifier.width(6.dp))
                    Text("Queue", color = SonifyText)
                }
                TextButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Rounded.PlaylistAdd, null, tint = SonifyText)
                    Spacer(Modifier.width(6.dp))
                    Text("Add to playlist", color = SonifyText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SonifyQueueSheet(
    controller: MediaController?,
    current: Track?,
    onDismiss: () -> Unit,
    onTrack: (Track) -> Unit
) {
    val ids = remember(controller, current) {
        if (controller == null || controller.mediaItemCount == 0) {
            DemoCatalog.allTracks.map { it.id }
        } else {
            (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
        }
    }
    val tracks = ids.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SonifySurface
    ) {
        Text("Play queue", color = SonifyText, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            items(tracks, key = { it.id }) { track ->
                Row(
                    Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SonifyArtwork(track, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = if (current?.id == track.id) SonifyAccent else SonifyText, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(track.artist, color = SonifyMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (current?.id == track.id) Icon(Icons.Rounded.GraphicEq, null, tint = SonifyAccent)
                }
            }
        }
    }
}

@Composable
private fun SonifyCreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Playlist name") }) },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SonifyAddToPlaylistDialog(
    track: Track,
    playlists: List<SonifyPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onAdd: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(track.title, color = SonifyMuted)
                Spacer(Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Text("You don't have a playlist yet.")
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onAdd(playlist.id) }.padding(vertical = 10.dp),
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
        confirmButton = { TextButton(onClick = onCreatePlaylist) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SonifyArtwork(track: Track, modifier: Modifier) {
    SonifyArtworkUrl(track.artworkUrl, modifier)
}

@Composable
private fun SonifyArtworkUrl(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(false).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(SonifySurface2)
    )
}

@Composable
private fun SonifyEmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = SonifyMuted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = SonifyText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = SonifyMuted, fontSize = 13.sp)
    }
}

private fun sonifyTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun loadSonifyLiked(context: Context): Set<String> =
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("liked", emptySet())?.toSet() ?: emptySet()

private fun saveSonifyLiked(context: Context, liked: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("liked", liked.toSet()).apply()
}

private fun loadSonifyPlaylists(context: Context): List<SonifyPlaylist> {
    val raw = context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("playlists", emptySet()) ?: emptySet()
    return raw.mapNotNull { entry ->
        val parts = entry.split("|", limit = 3)
        if (parts.size < 2) return@mapNotNull null
        val ids = parts.getOrNull(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        SonifyPlaylist(parts[0], parts[1], ids)
    }.sortedBy { it.name.lowercase() }
}

private fun saveSonifyPlaylists(context: Context, playlists: List<SonifyPlaylist>) {
    val raw = playlists.map { playlist ->
        "${playlist.id}|${playlist.name.replace("|", " ")}|${playlist.trackIds.joinToString(",")}" 
    }.toSet()
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("playlists", raw).apply()
}
