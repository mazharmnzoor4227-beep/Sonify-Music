from pathlib import Path
p=Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s=p.read_text()

# imports
s=s.replace('import com.sonify.music.data.CatalogRegistry\n', 'import com.sonify.music.data.CatalogRegistry\nimport com.sonify.music.data.EditorialFeed\nimport com.sonify.music.data.EditorialSong\n')

# screen
s=s.replace('''    data class Artist(val id: String) : V3Screen
    data object Downloads : V3Screen
}''','''    data class Artist(val id: String) : V3Screen
    data class Editorial(val title: String, val query: String) : V3Screen
    data object Downloads : V3Screen
}''')

# back
s=s.replace('''            screen is V3Screen.Category || screen is V3Screen.Artist -> screen = V3Screen.Home''','''            screen is V3Screen.Category || screen is V3Screen.Artist || screen is V3Screen.Editorial -> screen = V3Screen.Home''')

# home root invocation
old='''                            V3Screen.Home -> V3HomeScreen(
                                onTrack = ::playTrack,
                                onCategory = { screen = V3Screen.Category(it) },
                                onArtist = { screen = V3Screen.Artist(it) }
                            )'''
new='''                            V3Screen.Home -> V3HomeScreen(
                                onTrack = ::playTrack,
                                onCategory = { screen = V3Screen.Category(it) },
                                onArtist = { screen = V3Screen.Artist(it) },
                                onSearch = { screen = V3Screen.Search },
                                onEditorial = { song -> screen = V3Screen.Editorial(song.title, song.query) }
                            )'''
if old not in s: raise SystemExit('home root marker missing')
s=s.replace(old,new,1)

# add editorial screen case before Downloads
needle='''                            V3Screen.Downloads -> V3DownloadsScreen(
                                onBack = { screen = V3Screen.Library },
                                onTrack = ::playTrack
                            )'''
replacement='''                            is V3Screen.Editorial -> V3EditorialResultsScreen(
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
                            )'''
if needle not in s: raise SystemExit('downloads case missing')
s=s.replace(needle,replacement,1)

# replace home area until ArtistScreen, including chip/card funcs
start=s.index('@Composable\nprivate fun V3HomeScreen')
end=s.index('@Composable\nprivate fun V3ArtistScreen', start)
new_home=r'''@Composable
private fun V3HomeScreen(
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onEditorial: (EditorialSong) -> Unit
) {
    val context = LocalContext.current
    var liveTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var editorial by remember { mutableStateOf(emptyList<com.sonify.music.data.EditorialSection>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        editorial = EditorialFeed.load(context)
        val onlineTracks = AudiusCatalog.trending(28).getOrDefault(emptyList())
        if (onlineTracks.isNotEmpty()) liveTracks = CatalogRegistry.remember(context, onlineTracks)
        loading = false
    }

    val freshTracks = liveTracks.take(12).ifEmpty { DemoCatalog.featured }

    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF16220C), V3Bg)))
                    .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Good evening", color = V3Text, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("Your music, updated live", color = V3Muted, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = onSearch,
                        modifier = Modifier.size(42.dp).background(V3Surface2, CircleShape)
                    ) { Icon(Icons.Rounded.Search, "Search", tint = V3Text) }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { V3HomeChip("Music") { } }
                    item { V3HomeChip("Hindi") { onCategory("bollywood") } }
                    item { V3HomeChip("Pakistani") { onCategory("pakistani") } }
                    item { V3HomeChip("Punjabi") { onCategory("punjabi") } }
                }
            }
        }

        item { V3SectionTitle("Popular artists", "India & Pakistan") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ArtistDirectory.popular.take(14), key = { it.id }) { artist ->
                    V3ArtistCard(artist) { onArtist(artist.id) }
                }
            }
        }

        editorial.forEach { section ->
            item { V3SectionTitle(section.title, section.subtitle) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.songs, key = { section.id + it.title + it.artist }) { song ->
                        V3EditorialSongCard(song) { onEditorial(song) }
                    }
                }
            }
        }

        item { V3SectionTitle("Fresh discoveries", "Playable music from the live open catalog") }
        item {
            if (loading && liveTracks.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp)) {
                    items(freshTracks, key = { it.id }) { V3AlbumCard(it, onTrack) }
                }
            }
        }

        item { V3SectionTitle("Browse all", "Genres, moods and regional music") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(v3Categories, key = { it.id }) { c -> V3CategoryCard(c) { onCategory(c.id) } }
            }
        }
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
    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    var resolved by remember(artist.id) { mutableStateOf(false) }
    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        resolved = true
    }
    if (!image.isNullOrBlank()) {
        V3ArtworkUrl(image.orEmpty(), modifier)
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
    val profile = remember(song.artistId) { ArtistDirectory.get(song.artistId) }
    Column(Modifier.width(158.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(158.dp).clip(RoundedCornerShape(10.dp)).background(V3Surface2)) {
            if (profile != null) {
                V3ArtistPortrait(profile, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB8050505)))))
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = V3Muted, modifier = Modifier.size(46.dp).align(Alignment.Center))
            }
            Text("SONIFY PICK", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopStart).padding(9.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, color = V3Text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = V3Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        val found = AudiusCatalog.search(query, 30).getOrDefault(emptyList())
        tracks = if (found.isNotEmpty()) CatalogRegistry.remember(context, found) else emptyList()
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize().background(V3Bg), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { V3DetailHeader(title, "Popular pick", onBack) }
        if (loading) {
            item { Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = V3Accent) } }
        } else if (tracks.isEmpty()) {
            item { V3EmptyState("Full track source not connected yet", "This popular song is in Sonify's live editorial feed, but the current open catalog does not provide a playable copy. Connect a licensed mainstream catalog for full playback.") }
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

'''
s=s[:start]+new_home+s[end:]

# Artist page portrait uses shared robust portrait helper and editorial order.
s=s.replace('''    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    var tracks by remember(artist.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(artist.id) { mutableStateOf(true) }

    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        val online = AudiusCatalog.search(artist.musicQuery, 50).getOrDefault(emptyList())
        tracks = if (online.isNotEmpty()) CatalogRegistry.remember(context, online) else emptyList()
        loading = false
    }''','''    var image by remember(artist.id) { mutableStateOf<String?>(null) }
    var tracks by remember(artist.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(artist.id) { mutableStateOf(true) }

    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        val preferred = EditorialFeed.load(context).flatMap { it.songs }
            .filter { it.artistId == artist.id }.map { it.title.lowercase() }
        val online = AudiusCatalog.search(artist.musicQuery, 50).getOrDefault(emptyList())
        val ordered = online.sortedBy { track ->
            val index = preferred.indexOfFirst { wanted -> track.title.lowercase().contains(wanted) || wanted.contains(track.title.lowercase()) }
            if (index >= 0) index else 1000
        }
        tracks = if (ordered.isNotEmpty()) CatalogRegistry.remember(context, ordered) else emptyList()
        loading = false
    }''')

# use portrait helper in artist hero when image not empty; otherwise initials hero, replacing existing conditional block
old='''                if (!image.isNullOrBlank()) {
                    V3ArtworkUrl(image.orEmpty(), Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD050505), V3Bg))))
                }'''
new='''                if (!image.isNullOrBlank()) {
                    V3ArtworkUrl(image.orEmpty(), Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD050505), V3Bg))))
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(v3Initials(artist.name), color = Color(0x33FFFFFF), fontSize = 96.sp, fontWeight = FontWeight.Black)
                    }
                }'''
if old not in s: raise SystemExit('artist hero marker missing')
s=s.replace(old,new,1)

p.write_text(s)
print('patched spotify home + remote editorial')
