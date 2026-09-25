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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.sonify.music.data.AudiusCatalog
import com.sonify.music.data.CatalogRegistry
import com.sonify.music.data.DemoCatalog
import com.sonify.music.model.Track
import com.sonify.music.playback.PlaybackService
import java.util.concurrent.Executor
import kotlin.math.max
import kotlinx.coroutines.delay

private val V3Bg = Color(0xFF050505)
private val V3Surface = Color(0xFF151515)
private val V3Surface2 = Color(0xFF1D1D1D)
private val V3Text = Color(0xFFF8F8F8)
private val V3Muted = Color(0xFF9D9D9D)
private val V3Accent = Color(0xFFB7FF45)

private sealed interface V3Screen {
    data object Home : V3Screen
    data object Search : V3Screen
    data object Library : V3Screen
    data class Category(val id: String) : V3Screen
    data object Liked : V3Screen
    data object Playlists : V3Screen
    data class PlaylistDetail(val id: String) : V3Screen
}

private data class V3Category(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val query: String,
    val trackIds: List<String>
)

private data class V3Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>
)

private val v3Categories = listOf(
    V3Category("bollywood", "Bollywood & Hindi", "Hindi and Bollywood discovery", "https://picsum.photos/seed/sonify-bollywood/600", "bollywood hindi", listOf("1","3","5","7","9")),
    V3Category("pakistani", "Pakistani", "Urdu and Pakistani discovery", "https://picsum.photos/seed/sonify-pakistani/600", "pakistani urdu", listOf("2","4","6","8","10")),
    V3Category("punjabi", "Punjabi", "Punjabi music discovery", "https://picsum.photos/seed/sonify-punjabi/600", "punjabi", listOf("4","7","2","8","6")),
    V3Category("sad", "Sad Songs", "Heartbreak and emotional moods", "https://picsum.photos/seed/sonify-sad-v4/600", "sad heartbreak", listOf("5","3","10","9","1")),
    V3Category("romantic", "Romantic", "Love songs and soft moods", "https://picsum.photos/seed/sonify-romantic/600", "romantic love", listOf("1","2","5","9","10")),
    V3Category("chill", "Chill", "Easy listening for a quiet mood", "https://picsum.photos/seed/sonify-chill-v4/600", "chill", listOf("2","5","1","7","10")),
    V3Category("workout", "Workout", "High-energy picks", "https://picsum.photos/seed/sonify-workout-v4/600", "workout energy", listOf("6","7","8","4","2")),
    V3Category("hiphop", "Hip-Hop", "Rap and hip-hop discovery", "https://picsum.photos/seed/sonify-hiphop/600", "hip hop rap", listOf("8","6","4","7","2")),
    V3Category("electronic", "Electronic", "Electronic and dance", "https://picsum.photos/seed/sonify-electronic/600", "electronic dance", listOf("6","2","7","4","8")),
    V3Category("rnb", "R&B", "R&B and soul", "https://picsum.photos/seed/sonify-rnb/600", "r&b soul", listOf("3","5","9","1","10")),
    V3Category("rock", "Rock", "Rock and alternative", "https://picsum.photos/seed/sonify-rock/600", "rock alternative", listOf("7","8","4","6","2")),
    V3Category("cinematic", "Cinematic", "Soundtrack-style and cinematic music", "https://picsum.photos/seed/sonify-cinematic/600", "cinematic soundtrack", listOf("1","3","9","10","5"))
)

class SonifyActivityV3 : ComponentActivity() {
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

        setContent { SonifyV3Root(controllerState) }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controllerState = null
        super.onDestroy()
    }
}

private fun Track.toV3MediaItem(): MediaItem = MediaItem.Builder()
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
private fun SonifyV3Root(controller: MediaController?) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { CatalogRegistry.seed(context) }
    var screen by remember { mutableStateOf<V3Screen>(V3Screen.Home) }
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

    val liked = remember { mutableStateListOf<String>().apply { addAll(loadV3Liked(context)) } }
    val playlists = remember { mutableStateListOf<V3Playlist>().apply { addAll(loadV3Playlists(context)) } }

    fun toggleLike(id: String) {
        if (liked.contains(id)) liked.remove(id) else liked.add(id)
        saveV3Liked(context, liked)
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        playlists.add(V3Playlist(System.currentTimeMillis().toString(), clean, emptyList()))
        saveV3Playlists(context, playlists)
    }

    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        saveV3Playlists(context, playlists)
        screen = V3Screen.Playlists
    }

    fun addToPlaylist(track: Track, playlistId: String) {
        val i = playlists.indexOfFirst { it.id == playlistId }
        if (i < 0) return
        val p = playlists[i]
        if (track.id in p.trackIds) {
            Toast.makeText(context, "Already in ${p.name}", Toast.LENGTH_SHORT).show()
            return
        }
        playlists[i] = p.copy(trackIds = p.trackIds + track.id)
        saveV3Playlists(context, playlists)
        Toast.makeText(context, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { id ->
                    CatalogRegistry.get(id)?.let { current = it }
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
                    CatalogRegistry.get(id)?.let { current = it }
                }
            }
            delay(300)
        }
    }

    fun playTrack(track: Track) {
        val player = controller
        if (player == null) {
            Toast.makeText(context, "Player is getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        CatalogRegistry.remember(context, track)
        val queue = CatalogRegistry.allTracks()
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toV3MediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = track
        playerExpanded = true
    }

    fun togglePlayback() {
        val player = controller ?: return
        val track = current ?: return
        if (player.currentMediaItem == null) {
            val queue = CatalogRegistry.allTracks()
            val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            player.setMediaItems(queue.map { it.toV3MediaItem() }, start, 0L)
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

    fun playNext(track: Track) {
        val player = controller ?: return
        if (player.currentMediaItem == null) {
            playTrack(track)
            return
        }
        val at = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(at, track.toV3MediaItem())
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

    BackHandler(
        enabled = playerExpanded || queueOpen || createPlaylistOpen || addTrackDialog != null || screen !is V3Screen.Home
    ) {
        when {
            queueOpen -> queueOpen = false
            addTrackDialog != null -> addTrackDialog = null
            createPlaylistOpen -> createPlaylistOpen = false
            playerExpanded -> playerExpanded = false
            screen is V3Screen.PlaylistDetail -> screen = V3Screen.Playlists
            screen is V3Screen.Playlists || screen is V3Screen.Liked -> screen = V3Screen.Library
            screen is V3Screen.Category -> screen = V3Screen.Home
            screen is V3Screen.Search || screen is V3Screen.Library -> screen = V3Screen.Home
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = V3Bg,
            surface = V3Surface,
            primary = V3Accent,
            onBackground = V3Text,
            onSurface = V3Text
        )
    ) {
        Box(Modifier.fillMaxSize().background(V3Bg)) {
            Scaffold(
                containerColor = V3Bg,
                bottomBar = {
                    Surface(
                        color = Color(0xFF080808),
                        shadowElevation = 10.dp
                    ) {
                        Column {
                            HorizontalDivider(color = Color(0xFF202020), thickness = 0.5.dp)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                V3BottomNavItem(
                                    selected = screen is V3Screen.Home,
                                    icon = Icons.Rounded.Home,
                                    label = "Home"
                                ) { screen = V3Screen.Home }
                                V3BottomNavItem(
                                    selected = screen is V3Screen.Search,
                                    icon = Icons.Rounded.Search,
                                    label = "Search"
                                ) { screen = V3Screen.Search }
                                V3BottomNavItem(
                                    selected = screen is V3Screen.Library || screen is V3Screen.Liked || screen is V3Screen.Playlists || screen is V3Screen.PlaylistDetail,
                                    icon = Icons.Rounded.LibraryMusic,
                                    label = "Library"
                                ) { screen = V3Screen.Library }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(80)) },
                        label = "v3-screen"
                    ) { target ->
                        when (target) {
                            V3Screen.Home -> V3HomeScreen(::playTrack) { screen = V3Screen.Category(it) }
                            V3Screen.Search -> V3SearchScreen(
                                liked = liked,
                                onTrack = ::playTrack,
                                onCategory = { screen = V3Screen.Category(it) },
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { addTrackDialog = it },
                                onPlayNext = ::playNext
                            )
                            V3Screen.Library -> V3LibraryScreen(
                                liked.size,
                                playlists.size,
                                onLiked = { screen = V3Screen.Liked },
                                onPlaylists = { screen = V3Screen.Playlists }
                            )
                            is V3Screen.Category -> {
                                v3Categories.firstOrNull { it.id == target.id }?.let { category ->
                                    V3CategoryScreen(
                                        category = category,
                                        liked = liked,
                                        onBack = { screen = V3Screen.Home },
                                        onTrack = ::playTrack,
                                        onToggleLike = ::toggleLike,
                                        onAddToPlaylist = { addTrackDialog = it },
                                        onPlayNext = ::playNext
                                    )
                                }
                            }
                            V3Screen.Liked -> V3LikedScreen(
                                liked = liked,
                                onBack = { screen = V3Screen.Library },
                                onTrack = ::playTrack,
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { addTrackDialog = it },
                                onPlayNext = ::playNext
                            )
                            V3Screen.Playlists -> V3PlaylistsScreen(
                                playlists = playlists,
                                onBack = { screen = V3Screen.Library },
                                onCreate = { createPlaylistOpen = true },
                                onOpen = { screen = V3Screen.PlaylistDetail(it) }
                            )
                            is V3Screen.PlaylistDetail -> {
                                playlists.firstOrNull { it.id == target.id }?.let { playlist ->
                                    V3PlaylistDetailScreen(
                                        playlist = playlist,
                                        onBack = { screen = V3Screen.Playlists },
                                        onTrack = ::playTrack,
                                        onAddSongs = { screen = V3Screen.Search },
                                        onDelete = { deletePlaylist(playlist.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            current?.let { track ->
                V3MorphingPlayer(
                    track = track,
                    expanded = playerExpanded,
                    isPlaying = isPlaying,
                    progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    positionMs = positionMs,
                    durationMs = durationMs,
                    liked = liked.contains(track.id),
                    shuffle = shuffle,
                    repeatMode = repeatMode,
                    onExpandedChange = { playerExpanded = it },
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

        if (queueOpen) {
            V3QueueSheet(controller, current, onDismiss = { queueOpen = false }) { track ->
                queueOpen = false
                playTrack(track)
            }
        }

        if (createPlaylistOpen) {
            V3CreatePlaylistDialog(
                onDismiss = { createPlaylistOpen = false },
                onCreate = {
                    createPlaylist(it)
                    createPlaylistOpen = false
                }
            )
        }

        addTrackDialog?.let { track ->
            V3AddToPlaylistDialog(
                track = track,
                playlists = playlists,
                onDismiss = { addTrackDialog = null },
                onCreatePlaylist = {
                    addTrackDialog = null
                    createPlaylistOpen = true
                },
                onAdd = { id ->
                    addToPlaylist(track, id)
                    addTrackDialog = null
                }
            )
        }
    }
}

@Composable
private fun RowScope.V3BottomNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val iconColor = if (selected) V3Accent else V3Muted
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x12B7FF45) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) V3Text else V3Muted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun V3MorphingPlayer(
    track: Track,
    expanded: Boolean,
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onExpandedChange: (Boolean) -> Unit,
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
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)) {
        val density = LocalDensity.current
        val fullHeight = maxHeight
        val collapsedHeight = 70.dp
        val navReserve = 80.dp
        val travelPx = with(density) {
            max((fullHeight - collapsedHeight - navReserve).toPx(), 1f)
        }

        var dragging by remember(track.id) { mutableStateOf(false) }
        var dragFraction by remember(track.id) {
            mutableFloatStateOf(if (expanded) 0f else 1f)
        }
        var settleFraction by remember(track.id) {
            mutableFloatStateOf(if (expanded) 0f else 1f)
        }

        LaunchedEffect(expanded) {
            if (!dragging) settleFraction = if (expanded) 0f else 1f
        }

        val animatedFraction by animateFloatAsState(
            targetValue = settleFraction,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "player-morph"
        )

        val fraction = if (dragging) dragFraction else animatedFraction
        val playerHeight = (
            fullHeight.value + (collapsedHeight.value - fullHeight.value) * fraction
        ).dp
        val bottomSpace = (navReserve.value * fraction).dp
        val sidePad = (7f * fraction).dp
        val radius = (16f * fraction).dp

        val expandedAlpha = ((0.72f - fraction) / 0.72f).coerceIn(0f, 1f)
        val miniAlpha = ((fraction - 0.72f) / 0.28f).coerceIn(0f, 1f)

        Surface(
            color = V3Bg,
            shape = RoundedCornerShape(radius),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = sidePad, end = sidePad, bottom = bottomSpace)
                .fillMaxWidth()
                .height(playerHeight)
                .clip(RoundedCornerShape(radius))
                .pointerInput(track.id, travelPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragFraction = fraction
                            settleFraction = fraction
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragFraction = (dragFraction + amount / travelPx).coerceIn(0f, 1f)
                        },
                        onDragCancel = {
                            val target = if (dragFraction < 0.5f) 0f else 1f
                            settleFraction = target
                            dragging = false
                            onExpandedChange(target == 0f)
                        },
                        onDragEnd = {
                            val target = if (dragFraction < 0.5f) 0f else 1f
                            settleFraction = target
                            dragging = false
                            onExpandedChange(target == 0f)
                        }
                    )
                }
        ) {
            Box(Modifier.fillMaxSize()) {
                if (expandedAlpha > 0.001f) {
                    V3ExpandedPlayerContent(
                        track = track,
                        alpha = expandedAlpha,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        liked = liked,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onCollapse = {
                            settleFraction = 1f
                            onExpandedChange(false)
                        },
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

                if (miniAlpha > 0.001f) {
                    V3MiniPlayerContent(
                        track = track,
                        alpha = miniAlpha,
                        isPlaying = isPlaying,
                        progress = progress,
                        onOpen = {
                            settleFraction = 0f
                            onExpandedChange(true)
                        },
                        onPrevious = onPrevious,
                        onToggle = onToggle,
                        onNext = onNext,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun V3ExpandedPlayerContent(
    track: Track,
    alpha: Float,
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
    val p = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Brush.verticalGradient(listOf(Color(0xFF222B14), V3Bg, V3Bg)))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(42.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF6A6A6A)))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = V3Text) }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = V3Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAddToPlaylist) { Icon(Icons.Rounded.PlaylistAdd, null, tint = V3Text) }
        }
        Spacer(Modifier.height(16.dp))
        V3Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = V3Text, fontSize = 27.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = V3Muted, fontSize = 16.sp, maxLines = 1)
            }
            IconButton(onClick = onToggleLike) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) V3Accent else V3Text)
            }
        }
        Spacer(Modifier.height(14.dp))
        Slider(
            value = p,
            onValueChange = { onSeek((safeDuration * it).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = V3Accent,
                activeTrackColor = V3Accent,
                inactiveTrackColor = V3Surface2
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(v3Time(positionMs), color = V3Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(v3Time(safeDuration), color = V3Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) V3Accent else V3Muted)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = V3Text, modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = onToggle,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = V3Text, contentColor = V3Bg),
                modifier = Modifier.size(70.dp)
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, null, tint = V3Text, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onToggleRepeat) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    null,
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) V3Muted else V3Accent
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onQueue) {
                Icon(Icons.Rounded.QueueMusic, null, tint = V3Text)
                Spacer(Modifier.width(6.dp))
                Text("Queue", color = V3Text)
            }
            TextButton(onClick = onAddToPlaylist) {
                Icon(Icons.Rounded.PlaylistAdd, null, tint = V3Text)
                Spacer(Modifier.width(6.dp))
                Text("Add to playlist", color = V3Text)
            }
        }
    }
}

@Composable
private fun V3MiniPlayerContent(
    track: Track,
    alpha: Float,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF181818))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                V3Artwork(track, Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = V3Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = V3Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = V3Text)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(38.dp)) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = V3Text)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, tint = V3Text)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = V3Muted)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = V3Accent,
                trackColor = Color(0xFF2A2A2A)
            )
        }
    }
}

@Composable
private fun V3HomeScreen(onTrack: (Track) -> Unit, onCategory: (String) -> Unit) {
    val context = LocalContext.current
    var liveTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var liveAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = AudiusCatalog.trending(32)
        val onlineTracks = result.getOrDefault(emptyList())
        if (onlineTracks.isNotEmpty()) {
            liveTracks = CatalogRegistry.remember(context, onlineTracks)
            liveAvailable = true
        }
        loading = false
    }

    val featuredTracks = liveTracks.take(9).ifEmpty { DemoCatalog.featured }
    val trendingTracks = liveTracks.drop(9).take(18).ifEmpty { DemoCatalog.trending }

    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SONIFY", color = V3Text, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(7.dp).background(V3Accent, CircleShape))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (liveAvailable) "Live music discovery · Streaming from Audius" else "Your music, your mood",
                    color = V3Muted,
                    fontSize = 12.sp
                )
            }
        }
        item { V3SectionTitle("For you", if (liveAvailable) "Fresh live picks" else "Fresh picks for your next session") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(featuredTracks, key = { it.id }) { V3AlbumCard(it, onTrack) }
            }
        }
        item { V3SectionTitle("Browse music", "Genres, moods and regional discovery") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(v3Categories, key = { it.id }) { c -> V3CategoryCard(c) { onCategory(c.id) } }
            }
        }
        item { V3SectionTitle("Trending now", if (liveAvailable) "Live open music catalog" else "Sonify Preview") }
        if (loading && liveTracks.isEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        items(trendingTracks, key = { it.id }) { V3CompactTrackRow(it, onTrack) }
    }
}

@Composable
private fun V3CategoryCard(category: V3Category, onClick: () -> Unit) {
    Column(Modifier.width(154.dp).clickable(onClick = onClick)) {
        V3ArtworkUrl(category.artworkUrl, Modifier.size(154.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(9.dp))
        Text(category.title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        Text(category.subtitle, color = V3Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V3CategoryScreen(
    category: V3Category,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val context = LocalContext.current
    var tracks by remember(category.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(category.id) { mutableStateOf(true) }
    var liveAvailable by remember(category.id) { mutableStateOf(false) }

    LaunchedEffect(category.id) {
        val result = AudiusCatalog.search(category.query, 45)
        val onlineTracks = result.getOrDefault(emptyList())
        if (onlineTracks.isNotEmpty()) {
            tracks = CatalogRegistry.remember(context, onlineTracks)
            liveAvailable = true
        } else {
            tracks = category.trackIds.mapNotNull { id -> CatalogRegistry.get(id) }
        }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF202A12), V3Bg)))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).background(Color(0x66000000), CircleShape)) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = V3Text)
                }
                Column(Modifier.fillMaxWidth().padding(top = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    V3ArtworkUrl(category.artworkUrl, Modifier.size(210.dp).clip(RoundedCornerShape(28.dp)))
                    Spacer(Modifier.height(18.dp))
                    Text(category.title, color = V3Text, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    Text(category.subtitle, color = V3Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(if (liveAvailable) "Live results from Audius" else "Sonify catalog", color = V3Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item { V3SectionTitle("Songs", "Tap a track to play") }
        if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(30.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        } else if (tracks.isEmpty()) {
            item { V3EmptyState("No tracks found", "Try Search for a different artist, song or genre.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                V3FunctionalTrackRow(
                    track,
                    liked.contains(track.id),
                    onTrack,
                    { onToggleLike(track.id) },
                    { onAddToPlaylist(track) },
                    { onPlayNext(track) }
                )
            }
        }
    }
}

@Composable
private fun V3SearchScreen(
    liked: List<String>,
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.isBlank()) {
            results = emptyList()
            loading = false
            online = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)
        val response = AudiusCatalog.search(clean, 45)
        val liveTracks = response.getOrDefault(emptyList())
        if (liveTracks.isNotEmpty()) {
            results = CatalogRegistry.remember(context, liveTracks)
            online = true
        } else {
            results = DemoCatalog.search(clean)
            online = false
        }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            Text("Search", color = V3Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Songs, artists, genres, moods…", color = V3Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = V3Muted) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null, tint = V3Muted) }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = V3Surface2,
                    unfocusedContainerColor = V3Surface2,
                    focusedBorderColor = V3Accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = V3Text,
                    unfocusedTextColor = V3Text,
                    cursorColor = V3Accent
                )
            )
            Spacer(Modifier.height(18.dp))
            if (query.isNotBlank()) {
                Text(if (online) "Live results · Audius" else "Search results", color = V3Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (query.isBlank()) {
            item {
                Text("Browse all", color = V3Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
            }
            items(v3Categories.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { category ->
                        Box(
                            Modifier.weight(1f).height(104.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onCategory(category.id) }
                        ) {
                            V3ArtworkUrl(category.artworkUrl, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                            Text(
                                category.title,
                                color = V3Text,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(36.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                }
            }
        } else if (results.isEmpty()) {
            item { V3EmptyState("No tracks found", "Try another song, artist, language or genre.") }
        } else {
            items(results, key = { it.id }) { track ->
                V3FunctionalTrackRow(
                    track,
                    liked.contains(track.id),
                    onTrack,
                    { onToggleLike(track.id) },
                    { onAddToPlaylist(track) },
                    { onPlayNext(track) }
                )
            }
        }
    }
}

@Composable
private fun V3LibraryScreen(
    likedCount: Int,
    playlistCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
        item {
            Text("Your Library", color = V3Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text("Saved music and playlists", color = V3Muted)
            Spacer(Modifier.height(22.dp))
            V3LibraryRow(Icons.Rounded.Favorite, "Liked Songs", "$likedCount saved tracks", onLiked)
            V3LibraryRow(Icons.Rounded.QueueMusic, "Playlists", "$playlistCount playlists", onPlaylists)
        }
    }
}

@Composable
private fun V3LibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(V3Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = V3Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = V3Muted, fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = V3Muted)
    }
}

@Composable
private fun V3LikedScreen(
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val tracks = CatalogRegistry.allTracks().filter { it.id in liked }
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { V3DetailHeader("Liked Songs", "${tracks.size} tracks", onBack) }
        if (tracks.isEmpty()) {
            item { V3EmptyState("No liked songs yet", "Tap the heart on a song and it will appear here.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                V3FunctionalTrackRow(
                    track,
                    true,
                    onTrack,
                    { onToggleLike(track.id) },
                    { onAddToPlaylist(track) },
                    { onPlayNext(track) }
                )
            }
        }
    }
}

@Composable
private fun V3PlaylistsScreen(
    playlists: List<V3Playlist>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            V3DetailHeader("Playlists", "Your collections", onBack)
            Row(Modifier.fillMaxWidth().clickable(onClick = onCreate).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(V3Surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = V3Accent)
                }
                Spacer(Modifier.width(14.dp))
                Text("Create new playlist", color = V3Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        if (playlists.isEmpty()) {
            item { V3EmptyState("No playlists yet", "Create one and add your favorite tracks.") }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(V3Surface2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = V3Accent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${playlist.trackIds.size} tracks", color = V3Muted, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = V3Muted)
                }
            }
        }
    }
}

@Composable
private fun V3PlaylistDetailScreen(
    playlist: V3Playlist,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit
) {
    val tracks = playlist.trackIds.mapNotNull { id -> CatalogRegistry.get(id) }
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            V3DetailHeader(playlist.name, "${tracks.size} tracks", onBack)
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = onAddSongs) {
                    Icon(Icons.Rounded.Add, null, tint = V3Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Add songs", color = V3Accent, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = V3Muted)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = V3Muted)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { V3EmptyState("This playlist is empty", "Use a song menu and choose Add to playlist.") }
        } else {
            items(tracks, key = { it.id }) { V3CompactTrackRow(it, onTrack) }
        }
    }
}

@Composable
private fun V3DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = V3Text) }
        Spacer(Modifier.height(8.dp))
        Text(title, color = V3Text, fontWeight = FontWeight.Black, fontSize = 32.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text(subtitle, color = V3Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun V3SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 9.dp)) {
        Text(title, color = V3Text, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = V3Muted, fontSize = 13.sp)
    }
}

@Composable
private fun V3AlbumCard(track: Track, onTrack: (Track) -> Unit) {
    Column(Modifier.width(154.dp).padding(end = 12.dp).clickable { onTrack(track) }) {
        V3Artwork(track, Modifier.size(142.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = V3Text, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(track.artist, color = V3Muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun V3CompactTrackRow(track: Track, onTrack: (Track) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        V3Artwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = V3Text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = V3Muted, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = V3Muted)
    }
}

@Composable
private fun V3FunctionalTrackRow(
    track: Track,
    liked: Boolean,
    onTrack: (Track) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        V3Artwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = V3Text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = V3Muted, fontSize = 13.sp, maxLines = 1)
        }
        IconButton(onClick = onToggleLike) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) V3Accent else V3Muted)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, null, tint = V3Muted) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null) },
                    onClick = { menuOpen = false; onPlayNext() }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                    onClick = { menuOpen = false; onAddToPlaylist() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V3QueueSheet(
    controller: MediaController?,
    current: Track?,
    onDismiss: () -> Unit,
    onTrack: (Track) -> Unit
) {
    val ids = remember(controller, current) {
        if (controller == null || controller.mediaItemCount == 0) CatalogRegistry.allTracks().map { it.id }
        else (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
    }
    val tracks = ids.mapNotNull { id -> CatalogRegistry.get(id) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = V3Surface
    ) {
        Text("Play queue", color = V3Text, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            items(tracks, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    V3Artwork(track, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = if (current?.id == track.id) V3Accent else V3Text, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(track.artist, color = V3Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (current?.id == track.id) Icon(Icons.Rounded.GraphicEq, null, tint = V3Accent)
                }
            }
        }
    }
}

@Composable
private fun V3CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
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
private fun V3AddToPlaylistDialog(
    track: Track,
    playlists: List<V3Playlist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onAdd: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(track.title, color = V3Muted)
                Spacer(Modifier.height(12.dp))
                if (playlists.isEmpty()) {
                    Text("You don't have a playlist yet.")
                } else {
                    playlists.forEach { playlist ->
                        Row(Modifier.fillMaxWidth().clickable { onAdd(playlist.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun V3Artwork(track: Track, modifier: Modifier) {
    V3ArtworkUrl(track.artworkUrl, modifier)
}

@Composable
private fun V3ArtworkUrl(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(false).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(V3Surface2)
    )
}

@Composable
private fun V3EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.MusicNote, null, tint = V3Muted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = V3Muted, fontSize = 13.sp)
    }
}

private fun v3Time(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun loadV3Liked(context: Context): Set<String> =
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("liked", emptySet())?.toSet() ?: emptySet()

private fun saveV3Liked(context: Context, liked: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("liked", liked.toSet()).apply()
}

private fun loadV3Playlists(context: Context): List<V3Playlist> {
    val raw = context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("playlists", emptySet()) ?: emptySet()
    return raw.mapNotNull { entry ->
        val parts = entry.split("|", limit = 3)
        if (parts.size < 2) return@mapNotNull null
        val ids = parts.getOrNull(2)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        V3Playlist(parts[0], parts[1], ids)
    }.sortedBy { it.name.lowercase() }
}

private fun saveV3Playlists(context: Context, playlists: List<V3Playlist>) {
    val raw = playlists.map { playlist ->
        "${playlist.id}|${playlist.name.replace("|", " ")}|${playlist.trackIds.joinToString(",")}" 
    }.toSet()
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("playlists", raw).apply()
}
