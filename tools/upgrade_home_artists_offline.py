from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

# Imports
s = s.replace('import com.sonify.music.data.AudiusCatalog\nimport com.sonify.music.data.CatalogRegistry\n',
'''import com.sonify.music.data.AudiusCatalog
import com.sonify.music.data.ArtistDirectory
import com.sonify.music.data.ArtistProfile
import com.sonify.music.data.CatalogRegistry
import com.sonify.music.data.OfflineStore
''')
s = s.replace('import kotlinx.coroutines.delay\n', 'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n')

# Screens
s = s.replace('''    data object Playlists : V3Screen
    data class PlaylistDetail(val id: String) : V3Screen
}''', '''    data object Playlists : V3Screen
    data class PlaylistDetail(val id: String) : V3Screen
    data class Artist(val id: String) : V3Screen
    data object Downloads : V3Screen
}''')

# Remove random placeholder category artwork URLs; categories now render as designed tiles.
import re
s = re.sub(r'V3Category\(("[^"]+",\s*"[^"]+",\s*"[^"]+",)\s*"https://picsum\.photos/[^"]+"', r'V3Category(\1 ""', s)

# Local playback first when available.
s = s.replace('''        CatalogRegistry.remember(context, track)
        val queue = CatalogRegistry.allTracks()
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toV3MediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = track
        playerExpanded = true''', '''        val playable = OfflineStore.resolve(context, track)
        CatalogRegistry.remember(context, playable)
        val queue = CatalogRegistry.allTracks().map { OfflineStore.resolve(context, it) }
        val start = queue.indexOfFirst { it.id == playable.id }.coerceAtLeast(0)
        player.setMediaItems(queue.map { it.toV3MediaItem() }, start, 0L)
        player.prepare()
        player.play()
        current = playable
        playerExpanded = true''')

# Back behavior
s = s.replace('''            screen is V3Screen.Playlists || screen is V3Screen.Liked -> screen = V3Screen.Library
            screen is V3Screen.Category -> screen = V3Screen.Home
            screen is V3Screen.Search || screen is V3Screen.Library -> screen = V3Screen.Home''', '''            screen is V3Screen.Playlists || screen is V3Screen.Liked || screen is V3Screen.Downloads -> screen = V3Screen.Library
            screen is V3Screen.Category || screen is V3Screen.Artist -> screen = V3Screen.Home
            screen is V3Screen.Search || screen is V3Screen.Library -> screen = V3Screen.Home''')

# Bottom nav library selection includes downloads and removes selected pill background.
s = s.replace('''selected = screen is V3Screen.Library || screen is V3Screen.Liked || screen is V3Screen.Playlists || screen is V3Screen.PlaylistDetail,''', '''selected = screen is V3Screen.Library || screen is V3Screen.Liked || screen is V3Screen.Playlists || screen is V3Screen.PlaylistDetail || screen is V3Screen.Downloads,''')
s = s.replace('''    val iconColor = if (selected) V3Accent else V3Muted
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x12B7FF45) else Color.Transparent)
            .clickable(onClick = onClick),''', '''    val iconColor = if (selected) V3Text else V3Muted
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .clickable(onClick = onClick),''')
s = s.replace('''            color = if (selected) V3Text else V3Muted,
            fontSize = 10.sp,''', '''            color = if (selected) V3Text else V3Muted,
            fontSize = 11.sp,''')

# Root navigation calls and new screens.
s = s.replace('''V3Screen.Home -> V3HomeScreen(::playTrack) { screen = V3Screen.Category(it) }''', '''V3Screen.Home -> V3HomeScreen(
                                onTrack = ::playTrack,
                                onCategory = { screen = V3Screen.Category(it) },
                                onArtist = { screen = V3Screen.Artist(it) }
                            )''')
s = s.replace('''                                onPlayNext = ::playNext
                            )
                            V3Screen.Library -> V3LibraryScreen(''', '''                                onPlayNext = ::playNext,
                                onArtist = { screen = V3Screen.Artist(it) }
                            )
                            V3Screen.Library -> V3LibraryScreen(''')
s = s.replace('''                                onLiked = { screen = V3Screen.Liked },
                                onPlaylists = { screen = V3Screen.Playlists }
                            )''', '''                                onLiked = { screen = V3Screen.Liked },
                                onPlaylists = { screen = V3Screen.Playlists },
                                onDownloads = { screen = V3Screen.Downloads }
                            )''')
marker = '''                            is V3Screen.PlaylistDetail -> {
                                playlists.firstOrNull { it.id == target.id }?.let { playlist ->
                                    V3PlaylistDetailScreen(
                                        playlist = playlist,
                                        onBack = { screen = V3Screen.Playlists },
                                        onTrack = ::playTrack,
                                        onAddSongs = { screen = V3Screen.Search },
                                        onDelete = { deletePlaylist(playlist.id) }
                                    )
                                }
                            }'''
replacement = marker + '''
                            is V3Screen.Artist -> {
                                ArtistDirectory.get(target.id)?.let { artist ->
                                    V3ArtistScreen(
                                        artist = artist,
                                        liked = liked,
                                        onBack = { screen = V3Screen.Home },
                                        onTrack = ::playTrack,
                                        onToggleLike = ::toggleLike,
                                        onAddToPlaylist = { addTrackDialog = it },
                                        onPlayNext = ::playNext
                                    )
                                }
                            }
                            V3Screen.Downloads -> V3DownloadsScreen(
                                onBack = { screen = V3Screen.Library },
                                onTrack = ::playTrack
                            )'''
if marker not in s:
    raise SystemExit('playlist screen marker not found')
s = s.replace(marker, replacement, 1)

# Replace Home screen.
start = s.index('@Composable\nprivate fun V3HomeScreen')
end = s.index('@Composable\nprivate fun V3CategoryCard', start)
new_home = r'''@Composable
private fun V3HomeScreen(
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onArtist: (String) -> Unit
) {
    val context = LocalContext.current
    var liveTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var liveAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val onlineTracks = AudiusCatalog.trending(40).getOrDefault(emptyList())
        if (onlineTracks.isNotEmpty()) {
            liveTracks = CatalogRegistry.remember(context, onlineTracks)
            liveAvailable = true
        }
        loading = false
    }

    val featuredTracks = liveTracks.take(10).ifEmpty { DemoCatalog.featured }
    val trendingTracks = liveTracks.drop(10).take(20).ifEmpty { DemoCatalog.trending }

    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp)) {
                Text("Good evening", color = V3Text, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { V3HomeChip("Music") { } }
                    item { V3HomeChip("Hindi") { onCategory("bollywood") } }
                    item { V3HomeChip("Pakistani") { onCategory("pakistani") } }
                    item { V3HomeChip("Punjabi") { onCategory("punjabi") } }
                }
            }
        }

        item { V3SectionTitle("Popular artists", "Tap an artist to open their page") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ArtistDirectory.popular, key = { it.id }) { artist ->
                    V3ArtistCard(artist) { onArtist(artist.id) }
                }
            }
        }

        item { V3SectionTitle("Made for you", if (liveAvailable) "Fresh playable music" else "Fresh picks") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp)) {
                items(featuredTracks, key = { it.id }) { V3AlbumCard(it, onTrack) }
            }
        }

        item { V3SectionTitle("Browse all", "Hindi, Pakistani, Punjabi, moods and genres") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(v3Categories, key = { it.id }) { c -> V3CategoryCard(c) { onCategory(c.id) } }
            }
        }

        item { V3SectionTitle("Popular right now", if (liveAvailable) "Live open catalog" else "Sonify picks") }
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
private fun V3HomeChip(text: String, onClick: () -> Unit) {
    Surface(
        color = V3Surface2,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(text, color = V3Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

@Composable
private fun V3ArtistCard(artist: ArtistProfile, onClick: () -> Unit) {
    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(artist.id) { image = ArtistDirectory.portrait(artist) }
    Column(Modifier.width(126.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        if (image.isNullOrBlank()) {
            Box(Modifier.size(126.dp).clip(CircleShape).background(V3Surface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = V3Muted, modifier = Modifier.size(54.dp))
            }
        } else {
            V3ArtworkUrl(image.orEmpty(), Modifier.size(126.dp).clip(CircleShape))
        }
        Spacer(Modifier.height(9.dp))
        Text(artist.name, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Artist", color = V3Muted, fontSize = 12.sp)
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
    onPlayNext: (Track) -> Unit
) {
    val context = LocalContext.current
    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    var tracks by remember(artist.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(artist.id) { mutableStateOf(true) }

    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        val online = AudiusCatalog.search(artist.musicQuery, 50).getOrDefault(emptyList())
        tracks = if (online.isNotEmpty()) CatalogRegistry.remember(context, online) else emptyList()
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
                }
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x88000000), CircleShape)) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = V3Text)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(artist.name, color = V3Text, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 2)
                    Text("${artist.region} · Artist", color = V3Muted, fontSize = 13.sp)
                    Text("Artist image via Wikipedia", color = V3Muted, fontSize = 10.sp)
                }
            }
        }
        item { V3SectionTitle("Popular", "Playable tracks currently available in Sonify") }
        if (loading) {
            item { Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = V3Accent) } }
        } else if (tracks.isEmpty()) {
            item { V3EmptyState("No licensed stream found here yet", "The artist page is ready. Full mainstream catalogs need a licensed music provider connection.") }
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

'''
s = s[:start] + new_home + s[end:]

# Category card: designed tiles rather than random placeholder photos.
old_cat = '''@Composable
private fun V3CategoryCard(category: V3Category, onClick: () -> Unit) {
    Column(Modifier.width(154.dp).clickable(onClick = onClick)) {
        V3ArtworkUrl(category.artworkUrl, Modifier.size(154.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(9.dp))
        Text(category.title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        Text(category.subtitle, color = V3Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
'''
new_cat = '''@Composable
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
'''
if old_cat not in s:
    raise SystemExit('category card pattern not found')
s = s.replace(old_cat, new_cat, 1)

# Category hero uses real live artwork when available.
s = s.replace('''V3ArtworkUrl(category.artworkUrl, Modifier.size(210.dp).clip(RoundedCornerShape(28.dp)))''', '''V3ArtworkUrl(tracks.firstOrNull()?.artworkUrl.orEmpty(), Modifier.size(210.dp).clip(RoundedCornerShape(28.dp)))''')

# Search signature and artist results.
s = s.replace('''    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }''', '''    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onArtist: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }''', 1)

search_anchor = '''        if (query.isBlank()) {
            item {
                Text("Browse all", color = V3Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
            }'''
search_repl = '''        if (query.isBlank()) {
            item {
                Text("Popular artists", color = V3Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ArtistDirectory.popular, key = { it.id }) { artist ->
                        V3ArtistCard(artist) { onArtist(artist.id) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Browse all", color = V3Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
            }'''
if search_anchor not in s:
    raise SystemExit('search blank marker not found')
s = s.replace(search_anchor, search_repl, 1)

# When searching, show matched artist profiles before tracks.
anchor2 = '''        } else if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(36.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                }
            }
        } else if (results.isEmpty()) {'''
repl2 = '''        } else if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(36.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                }
            }
        } else if (results.isEmpty() && ArtistDirectory.matching(query).isEmpty()) {'''
s = s.replace(anchor2, repl2, 1)

# Add artist matches before result tracks when query is nonblank.
anchor3 = '''        } else {
            items(results, key = { it.id }) { track ->
                V3FunctionalTrackRow('''
repl3 = '''        } else {
            val artistMatches = ArtistDirectory.matching(query)
            if (artistMatches.isNotEmpty()) {
                item {
                    Text("Artists", color = V3Text, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(artistMatches, key = { it.id }) { artist -> V3ArtistCard(artist) { onArtist(artist.id) } }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Songs", color = V3Text, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(results, key = { it.id }) { track ->
                V3FunctionalTrackRow('''
if anchor3 not in s:
    raise SystemExit('search result marker not found')
s = s.replace(anchor3, repl3, 1)

# Library adds Downloads.
s = s.replace('''    playlistCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit
) {''', '''    playlistCount: Int,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit,
    onDownloads: () -> Unit
) {''', 1)
s = s.replace('''            V3LibraryRow(Icons.Rounded.Favorite, "Liked Songs", "$likedCount saved tracks", onLiked)
            V3LibraryRow(Icons.Rounded.QueueMusic, "Playlists", "$playlistCount playlists", onPlaylists)''', '''            V3LibraryRow(Icons.Rounded.Favorite, "Liked Songs", "$likedCount saved tracks", onLiked)
            V3LibraryRow(Icons.Rounded.QueueMusic, "Playlists", "$playlistCount playlists", onPlaylists)
            V3LibraryRow(Icons.Rounded.DownloadForOffline, "Downloads", "Available offline on this device", onDownloads)''')

# Add download action to all functional track rows.
func_marker = '''private fun V3FunctionalTrackRow(
    track: Track,
    liked: Boolean,
    onTrack: (Track) -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }'''
func_repl = '''private fun V3FunctionalTrackRow(
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
    var downloading by remember(track.id) { mutableStateOf(false) }'''
if func_marker not in s:
    raise SystemExit('functional row marker not found')
s = s.replace(func_marker, func_repl, 1)

heart_anchor = '''        IconButton(onClick = onToggleLike) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) V3Accent else V3Muted)
        }
        Box {'''
heart_repl = '''        IconButton(onClick = onToggleLike) {
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
        Box {'''
if heart_anchor not in s:
    raise SystemExit('heart marker not found')
s = s.replace(heart_anchor, heart_repl, 1)

# Artwork fallback without random images.
old_art = '''@Composable
private fun V3ArtworkUrl(url: String, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(false).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(V3Surface2)
    )
}'''
new_art = '''@Composable
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
}'''
if old_art not in s:
    raise SystemExit('artwork helper marker not found')
s = s.replace(old_art, new_art, 1)

p.write_text(s)
print('patched', p)
