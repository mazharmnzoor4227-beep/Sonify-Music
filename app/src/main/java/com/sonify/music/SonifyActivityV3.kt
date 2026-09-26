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
import com.sonify.music.data.ArtistDirectory
import com.sonify.music.data.ArtistProfile
import com.sonify.music.data.CatalogRegistry
import com.sonify.music.data.EditorialFeed
import com.sonify.music.data.EditorialSong
import com.sonify.music.data.OfflineStore
import com.sonify.music.data.DemoCatalog
import com.sonify.music.model.Track
import com.sonify.music.playback.PlaybackService
import java.util.concurrent.Executor
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    data class Artist(val id: String) : V3Screen
    data class Editorial(val title: String, val query: String) : V3Screen
    data object Downloads : V3Screen
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
    V3Category("bollywood", "Bollywood & Hindi", "Hindi and Bollywood discovery", "", "bollywood hindi", listOf("1","3","5","7","9")),
    V3Category("pakistani", "Pakistani", "Urdu and Pakistani discovery", "", "pakistani urdu", listOf("2","4","6","8","10")),
    V3Category("punjabi", "Punjabi", "Punjabi music discovery", "", "punjabi", listOf("4","7","2","8","6")),
    V3Category("sad", "Sad Songs", "Heartbreak and emotional moods", "", "sad heartbreak", listOf("5","3","10","9","1")),
    V3Category("romantic", "Romantic", "Love songs and soft moods", "", "romantic love", listOf("1","2","5","9","10")),
    V3Category("chill", "Chill", "Easy listening for a quiet mood", "", "chill", listOf("2","5","1","7","10")),
    V3Category("workout", "Workout", "High-energy picks", "", "workout energy", listOf("6","7","8","4","2")),
    V3Category("hiphop", "Hip-Hop", "Rap and hip-hop discovery", "", "hip hop rap", listOf("8","6","4","7","2")),
    V3Category("electronic", "Electronic", "Electronic and dance", "", "electronic dance", listOf("6","2","7","4","8")),
    V3Category("rnb", "R&B", "R&B and soul", "", "r&b soul", listOf("3","5","9","1","10")),
    V3Category("rock", "Rock", "Rock and alternative", "", "rock alternative", listOf("7","8","4","6","2")),
    V3Category("cinematic", "Cinematic", "Soundtrack-style and cinematic music", "", "cinematic soundtrack", listOf("1","3","9","10","5"))
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
    val followedArtists = remember { mutableStateListOf<String>().apply { addAll(loadV3FollowedArtists(context)) } }
    val playlists = remember { mutableStateListOf<V3Playlist>().apply { addAll(loadV3Playlists(context)) } }

    fun toggleLike(id: String) {
        if (liked.contains(id)) liked.remove(id) else liked.add(id)
        saveV3Liked(context, liked)
    }

    fun toggleFollowArtist(id: String) {
        if (followedArtists.contains(id)) followedArtists.remove(id) else followedArtists.add(id)
        saveV3FollowedArtists(context, followedArtists)
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
                val nextPosition = player.currentPosition.coerceAtLeast(0L)
                val nextDuration = player.duration.takeIf { it > 0L } ?: 1L
                val nextPlaying = player.isPlaying
                val nextShuffle = player.shuffleModeEnabled
                val nextRepeat = player.repeatMode

                if (positionMs != nextPosition) positionMs = nextPosition
                if (durationMs != nextDuration) durationMs = nextDuration
                if (isPlaying != nextPlaying) isPlaying = nextPlaying
                if (shuffle != nextShuffle) shuffle = nextShuffle
                if (repeatMode != nextRepeat) repeatMode = nextRepeat

                player.currentMediaItem?.mediaId?.let { id ->
                    if (current?.id != id) CatalogRegistry.get(id)?.let { current = it }
                }
            }
            // 300ms forced frequent whole-screen recomposition on slower phones.
            // 750ms keeps the progress bar responsive while making scrolling much smoother.
            delay(750)
        }
    }

    fun playTrack(track: Track) {
        val player = controller
        if (player == null) {
            Toast.makeText(context, "Player is getting ready…", Toast.LENGTH_SHORT).show()
            return
        }
        val playable = OfflineStore.resolve(context, track)
        CatalogRegistry.remember(context, playable)
        val queue = CatalogRegistry.allTracks().map { OfflineStore.resolve(context, it) }
        val start = queue.indexOfFirst { it.id == playable.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toV3MediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = playable
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
            screen is V3Screen.Playlists || screen is V3Screen.Liked || screen is V3Screen.Downloads -> screen = V3Screen.Library
            screen is V3Screen.Category || screen is V3Screen.Artist || screen is V3Screen.Editorial -> screen = V3Screen.Home
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
                                    .height(66.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                V3BottomNavItem(
                                    selected = screen is V3Screen.Home,
                                    icon = if (screen is V3Screen.Home) Icons.Rounded.Home else Icons.Rounded.Home,
                                    label = "Home"
                                ) { screen = V3Screen.Home }
                                V3BottomNavItem(
                                    selected = screen is V3Screen.Search,
                                    icon = Icons.Rounded.Search,
                                    label = "Search"
                                ) { screen = V3Screen.Search }
                                V3BottomNavItem(
                                    selected = screen is V3Screen.Library || screen is V3Screen.Liked || screen is V3Screen.Playlists || screen is V3Screen.PlaylistDetail || screen is V3Screen.Downloads,
                                    icon = Icons.Rounded.LibraryMusic,
                                    label = "Your Library"
                                ) { screen = V3Screen.Library }
                                V3BottomNavItem(
                                    selected = false,
                                    icon = Icons.Rounded.Add,
                                    label = "Create"
                                ) { createPlaylistOpen = true }
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
                            V3Screen.Home -> V3HomeScreen(
                                onTrack = ::playTrack,
                                onCategory = { screen = V3Screen.Category(it) },
                                onArtist = { screen = V3Screen.Artist(it) },
                                onSearch = { screen = V3Screen.Search },
                                onEditorial = { song ->
                                    screen = V3Screen.Editorial(song.title, song.query)
                                }
                            )
                            V3Screen.Search -> V3SearchScreen(
                                liked = liked,
                                onTrack = ::playTrack,
                                onCategory = { screen = V3Screen.Category(it) },
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { addTrackDialog = it },
                                onPlayNext = ::playNext,
                                onArtist = { screen = V3Screen.Artist(it) },
                                onOfficial = { song ->
                                    screen = V3Screen.Editorial(song.title, song.query)
                                }
                            )
                            V3Screen.Library -> V3LibraryScreen(
                                likedCount = liked.size,
                                playlistCount = playlists.size,
                                followedArtistIds = followedArtists,
                                downloadCount = OfflineStore.allDownloaded(context).size,
                                onLiked = { screen = V3Screen.Liked },
                                onPlaylists = { screen = V3Screen.Playlists },
                                onDownloads = { screen = V3Screen.Downloads },
                                onArtist = { screen = V3Screen.Artist(it) },
                                onSearch = { screen = V3Screen.Search },
                                onCreate = { createPlaylistOpen = true }
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
                            is V3Screen.Artist -> {
                                ArtistDirectory.get(target.id)?.let { artist ->
                                    V3ArtistScreen(
                                        artist = artist,
                                        liked = liked,
                                        onBack = { screen = V3Screen.Home },
                                        onTrack = ::playTrack,
                                        onToggleLike = ::toggleLike,
                                        onAddToPlaylist = { addTrackDialog = it },
                                        onPlayNext = ::playNext,
                                        followed = followedArtists.contains(artist.id),
                                        onToggleFollow = { toggleFollowArtist(artist.id) },
                                        onOfficial = { song ->
                                            screen = V3Screen.Editorial(song.title, song.query)
                                        }
                                    )
                                }
                            }
                            is V3Screen.Editorial -> V3EditorialResultsScreen(
                                title = target.title,
                                query = target.query,
                                liked = liked,
                                onBack = { screen = V3Screen.Home },
                                onTrack = ::playTrack,
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { addTrackDialog = it },
                                onPlayNext = ::playNext
                            )
                            V3Screen.Downloads -> V3DownloadsScreen(
                                onBack = { screen = V3Screen.Library },
                                onTrack = ::playTrack
                            )
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
    val iconColor = if (selected) V3Text else V3Muted
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) V3Text else V3Muted,
            fontSize = 9.sp,
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
private fun V3HomeScreen(
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onEditorial: (EditorialSong) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("All") }
    var editorial by remember { mutableStateOf(emptyList<com.sonify.music.data.EditorialSection>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        editorial = withTimeoutOrNull(9000) { EditorialFeed.load(context) }.orEmpty()
        loading = false
    }

    val visibleEditorial = when (filter) {
        "Hindi" -> editorial.filter { it.id.contains("india", ignoreCase = true) }
        "Pakistani" -> editorial.filter { it.id.contains("pakistan", ignoreCase = true) }
        else -> editorial
    }
    val officialSongs = visibleEditorial.flatMap { it.songs }.distinctBy { it.videoId }
    val availableArtistIds = editorial.flatMap { it.songs }.map { it.artistId }.filter { it.isNotBlank() }.toSet()
    val visibleArtists = ArtistDirectory.popular.filter { it.id in availableArtistIds }.take(12)

    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(bottom = 26.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFB86B4A)),
                    contentAlignment = Alignment.Center
                ) { Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Spacer(Modifier.width(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(listOf("All", "Hindi", "Pakistani")) { label ->
                        V3FilterChip(label, selected = filter == label) { filter = label }
                    }
                }
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search", tint = V3Text) }
            }
        }

        if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        } else {
            if (officialSongs.isNotEmpty()) {
                items(officialSongs.take(6).chunked(2)) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { song ->
                            V3OfficialQuickCard(song, Modifier.weight(1f)) { onEditorial(song) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (visibleArtists.isNotEmpty()) {
                item { V3SectionTitle("Popular artists", "Official music only") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(visibleArtists, key = { it.id }) { artist ->
                            V3ArtistCard(artist) { onArtist(artist.id) }
                        }
                    }
                }
            }

            visibleEditorial.forEach { section ->
                item { V3SectionTitle(section.title, section.subtitle) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(section.songs, key = { section.id + it.videoId }) { song ->
                            V3EditorialSongCard(song) { onEditorial(song) }
                        }
                    }
                }
            }

            item { V3SectionTitle("Browse", "Genres, moods and regional music") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(v3Categories, key = { it.id }) { c -> V3CategoryCard(c) { onCategory(c.id) } }
                }
            }
        }
    }
}

@Composable
private fun V3OfficialQuickCard(song: EditorialSong, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(58.dp).clip(RoundedCornerShape(5.dp)).background(V3Surface2).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        V3ArtworkUrl(song.thumbnailUrl, Modifier.size(58.dp))
        Column(Modifier.padding(horizontal = 9.dp).weight(1f)) {
            Text(song.title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = V3Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V3FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) V3Accent else V3Surface2,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            color = if (selected) Color.Black else V3Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun V3QuickTrackCard(track: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(58.dp).clip(RoundedCornerShape(5.dp)).background(V3Surface2).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        V3Artwork(track, Modifier.size(58.dp))
        Text(
            track.title,
            color = V3Text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp).weight(1f)
        )
    }
}

@Composable
private fun V3HomeChip(text: String, onClick: () -> Unit) {
    Surface(
        color = V3Surface2,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(text, color = V3Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

private fun v3Initials(name: String): String = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

@Composable
private fun V3ArtistPortrait(artist: ArtistProfile, modifier: Modifier) {
    val context = LocalContext.current
    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    var resolved by remember(artist.id) { mutableStateOf(false) }
    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        if (image.isNullOrBlank()) {
            image = EditorialFeed.load(context).flatMap { it.songs }
                .firstOrNull { it.artistId == artist.id && it.videoId.isNotBlank() }
                ?.thumbnailUrl
        }
        resolved = true
    }
    if (!image.isNullOrBlank()) {
        AsyncImage(
            model = remember(image) {
                ImageRequest.Builder(context)
                    .data(image)
                    .crossfade(false)
                    .build()
            },
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(V3Surface2),
            onError = {
                image = null
                resolved = true
            }
        )
    } else {
        Box(modifier.background(V3Surface2), contentAlignment = Alignment.Center) {
            Text(v3Initials(artist.name), color = V3Text, fontWeight = FontWeight.Black, fontSize = 30.sp)
            if (!resolved) CircularProgressIndicator(color = V3Accent, strokeWidth = 2.dp, modifier = Modifier.size(74.dp))
        }
    }
}

@Composable
private fun V3ArtistCard(artist: ArtistProfile, onClick: () -> Unit) {
    Column(Modifier.width(122.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        V3ArtistPortrait(artist, Modifier.size(122.dp).clip(CircleShape))
        Spacer(Modifier.height(9.dp))
        Text(artist.name, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Artist", color = V3Muted, fontSize = 11.sp)
    }
}

@Composable
private fun V3EditorialSongCard(song: EditorialSong, onClick: () -> Unit) {
    Column(Modifier.width(158.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(158.dp).clip(RoundedCornerShape(8.dp)).background(V3Surface2)) {
            V3ArtworkUrl(song.thumbnailUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8050505)))))
            Surface(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
            ) {
                Text("OFFICIAL", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp).align(Alignment.BottomEnd).padding(5.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = V3Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (song.channelName.isNotBlank()) {
            Text(song.channelName, color = V3Muted.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V3EditorialResultsScreen(
    title: String,
    query: String,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val context = LocalContext.current
    var tracks by remember(query) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(query) { mutableStateOf(true) }
    LaunchedEffect(query) {
        val found = AudiusCatalog.searchVerified(query, 30).getOrDefault(emptyList())
        tracks = if (found.isNotEmpty()) CatalogRegistry.remember(context, found) else emptyList()
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { V3DetailHeader(title, "Playable audio", onBack) }
        if (loading) {
            item { Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = V3Accent) } }
        } else if (tracks.isEmpty()) {
            item { V3EmptyState("Audio source not available", "This release is listed in Sonify, but the connected music catalog does not currently provide a playable audio stream for it.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                V3FunctionalTrackRow(
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
private fun V3ArtistScreen(
    artist: ArtistProfile,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onOfficial: (EditorialSong) -> Unit
) {
    val context = LocalContext.current
    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    var officialSongs by remember(artist.id) { mutableStateOf<List<EditorialSong>>(emptyList()) }
    var loading by remember(artist.id) { mutableStateOf(true) }

    LaunchedEffect(artist.id) {
        val sections = withTimeoutOrNull(9000) { EditorialFeed.load(context) }.orEmpty()
        officialSongs = sections.flatMap { it.songs }.filter { it.artistId == artist.id }.distinctBy { it.videoId }
        image = ArtistDirectory.portrait(artist) ?: officialSongs.firstOrNull()?.thumbnailUrl
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().height(330.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF283318), V3Bg)))
            ) {
                if (!image.isNullOrBlank()) {
                    V3ArtworkUrl(image.orEmpty(), Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD050505), V3Bg))))
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(v3Initials(artist.name), color = Color(0x33FFFFFF), fontSize = 96.sp, fontWeight = FontWeight.Black)
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x88000000), CircleShape)) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = V3Text)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(artist.name, color = V3Text, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 2)
                    Text("${artist.region} · Artist", color = V3Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onToggleFollow,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = V3Text)
                    ) { Text(if (followed) "Following" else "Follow", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { V3SectionTitle("Official releases", "Verified label / artist uploads") }
        if (loading) {
            item { Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = V3Accent) } }
        } else if (officialSongs.isEmpty()) {
            item { V3EmptyState("Official catalog not connected for this artist yet", "No random user uploads are shown. Add this artist's official channel to Sonify's live catalog to populate this page.") }
        } else {
            items(officialSongs, key = { it.videoId }) { song ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOfficial(song) }.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    V3ArtworkUrl(song.thumbnailUrl, Modifier.size(68.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text(song.channelName, color = V3Muted, fontSize = 12.sp, maxLines = 1)
                        Text("Featured release", color = V3Accent, fontSize = 10.sp)
                    }
                    Icon(Icons.Rounded.PlayCircle, null, tint = V3Text, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun V3DownloadsScreen(onBack: () -> Unit, onTrack: (Track) -> Unit) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf(OfflineStore.allDownloaded(context)) }
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { V3DetailHeader("Downloads", "Offline music on this device", onBack) }
        if (tracks.isEmpty()) {
            item { V3EmptyState("Nothing downloaded yet", "For tracks whose artist/provider allows downloads, tap the download icon in Search or song lists.") }
        } else {
            items(tracks, key = { it.id }) { track ->
                Row(Modifier.fillMaxWidth().clickable { onTrack(track) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    V3Artwork(track, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = V3Text, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(track.artist, color = V3Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    IconButton(onClick = {
                        OfflineStore.remove(context, track.id)
                        tracks = OfflineStore.allDownloaded(context)
                    }) { Icon(Icons.Rounded.DeleteOutline, null, tint = V3Muted) }
                }
            }
        }
    }
}

@Composable
private fun V3CategoryCard(category: V3Category, onClick: () -> Unit) {
    val shade = when (category.id) {
        "pakistani" -> Color(0xFF174D35)
        "bollywood" -> Color(0xFF7A3325)
        "punjabi" -> Color(0xFF765315)
        "sad" -> Color(0xFF344A6B)
        "romantic" -> Color(0xFF7A2F4C)
        else -> Color(0xFF303030)
    }
    Box(
        Modifier.width(154.dp).height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(shade)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(category.title, color = V3Text, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.align(Alignment.TopStart))
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(42.dp).align(Alignment.BottomEnd))
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
        val result = AudiusCatalog.searchVerified(category.query, 45)
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
                    V3ArtworkUrl(tracks.firstOrNull()?.artworkUrl.orEmpty(), Modifier.size(210.dp).clip(RoundedCornerShape(28.dp)))
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
    onPlayNext: (Track) -> Unit,
    onArtist: (String) -> Unit,
    onOfficial: (EditorialSong) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf(emptyList<com.sonify.music.data.EditorialSection>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        sections = withTimeoutOrNull(9000) { EditorialFeed.load(context) }.orEmpty()
        loading = false
    }

    val officialResults = remember(query, sections) { EditorialFeed.search(sections, query) }
    val artistMatches = remember(query) { ArtistDirectory.matching(query) }

    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        item {
            Text("Search", color = V3Text, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("What do you want to listen to?", color = Color(0xFF555555)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.Black) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null, tint = Color.Black) }
                },
                shape = RoundedCornerShape(6.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(20.dp))
        }

        if (loading) {
            item { Row(Modifier.fillMaxWidth().padding(30.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = V3Accent) } }
        } else if (query.isBlank()) {
            item {
                Text("Official releases", color = V3Text, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sections.flatMap { it.songs }.distinctBy { it.videoId }.take(10), key = { it.videoId }) { song ->
                        V3EditorialSongCard(song) { onOfficial(song) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Browse all", color = V3Text, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
            }
            items(v3Categories.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { category ->
                        val shade = when (category.id) {
                            "pakistani" -> Color(0xFF174D35)
                            "bollywood" -> Color(0xFF7A3325)
                            "punjabi" -> Color(0xFF765315)
                            "sad" -> Color(0xFF344A6B)
                            "romantic" -> Color(0xFF7A2F4C)
                            else -> Color(0xFF303030)
                        }
                        Box(
                            Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(8.dp)).background(shade).clickable { onCategory(category.id) }.padding(12.dp)
                        ) {
                            Text(category.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(34.dp).align(Alignment.BottomEnd))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        } else {
            if (artistMatches.isNotEmpty()) {
                item {
                    Text("Artists", color = V3Text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(artistMatches.take(10), key = { it.id }) { artist -> V3ArtistCard(artist) { onArtist(artist.id) } }
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }
            if (officialResults.isNotEmpty()) {
                item { Text("Official songs", color = V3Text, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                items(officialResults, key = { it.videoId }) { song ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOfficial(song) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        V3ArtworkUrl(song.thumbnailUrl, Modifier.size(62.dp).clip(RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = V3Text, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(song.artist, color = V3Muted, fontSize = 12.sp, maxLines = 1)
                            Text("Official • ${song.channelName}", color = V3Accent, fontSize = 10.sp, maxLines = 1)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = V3Muted)
                    }
                }
            } else if (artistMatches.isEmpty()) {
                item { V3EmptyState("No official match yet", "Sonify is currently showing verified official releases only. More official channels can be added to the live catalog without reinstalling the app.") }
            }
        }
    }
}

@Composable
private fun V3LibraryScreen(
    likedCount: Int,
    playlistCount: Int,
    followedArtistIds: List<String>,
    downloadCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit,
    onDownloads: () -> Unit,
    onArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onCreate: () -> Unit
) {
    var tab by remember { mutableStateOf("Playlists") }
    val followed = remember(followedArtistIds.toList()) {
        followedArtistIds.mapNotNull { ArtistDirectory.get(it) }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFB86B4A)), contentAlignment = Alignment.Center) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text("Your Library", color = V3Text, fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search library", tint = V3Text) }
                IconButton(onClick = onCreate) { Icon(Icons.Rounded.Add, "Create playlist", tint = V3Text) }
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Playlists", "Artists", "Downloads")) { name ->
                    V3LibraryFilterChip(name, tab == name) { tab = name }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SwapVert, null, tint = V3Text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recents", color = V3Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
        }

        when (tab) {
            "Artists" -> {
                if (followed.isEmpty()) {
                    item { V3EmptyState("No followed artists", "Open an artist page and tap Follow. They will appear here.") }
                } else {
                    items(followed, key = { it.id }) { artist ->
                        V3LibraryArtistRow(artist) { onArtist(artist.id) }
                    }
                }
            }
            "Downloads" -> {
                item { V3LibraryRow(Icons.Rounded.DownloadForOffline, "Downloads", "$downloadCount available offline", onDownloads) }
            }
            else -> {
                item { V3LibraryRow(Icons.Rounded.Favorite, "Liked Songs", "$likedCount saved tracks", onLiked) }
                item { V3LibraryRow(Icons.Rounded.QueueMusic, "Playlists", "$playlistCount playlists", onPlaylists) }
            }
        }
    }
}

@Composable
private fun V3LibraryFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) V3Accent else V3Surface2,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(text, color = if (selected) Color.Black else V3Text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
private fun V3LibraryArtistRow(artist: ArtistProfile, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        V3ArtistPortrait(artist, Modifier.size(58.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, color = V3Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Artist", color = V3Muted, fontSize = 12.sp)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var downloaded by remember(track.id) { mutableStateOf(OfflineStore.isDownloaded(context, track.id)) }
    var downloading by remember(track.id) { mutableStateOf(false) }
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
        if (track.downloadable) {
            IconButton(
                enabled = !downloading,
                onClick = {
                    if (downloaded) {
                        Toast.makeText(context, "Already available offline", Toast.LENGTH_SHORT).show()
                    } else {
                        downloading = true
                        scope.launch {
                            val result = OfflineStore.download(context, track)
                            downloaded = result.isSuccess
                            downloading = false
                            Toast.makeText(
                                context,
                                if (result.isSuccess) "Downloaded for offline listening" else result.exceptionOrNull()?.message ?: "Download failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) {
                if (downloading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = V3Accent)
                else Icon(if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.DownloadForOffline, null, tint = if (downloaded) V3Accent else V3Muted)
            }
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
    if (url.isBlank()) {
        Box(modifier.background(V3Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = V3Muted, modifier = Modifier.size(38.dp))
        }
    } else {
        AsyncImage(
            model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(false).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(V3Surface2)
        )
    }
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

private fun loadV3FollowedArtists(context: Context): Set<String> =
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("followed_artists", emptySet())?.toSet() ?: emptySet()

private fun saveV3FollowedArtists(context: Context, artists: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("followed_artists", artists.toSet()).apply()
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
