from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

# Persistent followed artists - real Library state.
s = s.replace(
    'val liked = remember { mutableStateListOf<String>().apply { addAll(loadV3Liked(context)) } }\n    val playlists = remember { mutableStateListOf<V3Playlist>().apply { addAll(loadV3Playlists(context)) } }',
    'val liked = remember { mutableStateListOf<String>().apply { addAll(loadV3Liked(context)) } }\n    val followedArtists = remember { mutableStateListOf<String>().apply { addAll(loadV3FollowedArtists(context)) } }\n    val playlists = remember { mutableStateListOf<V3Playlist>().apply { addAll(loadV3Playlists(context)) } }'
)
s = s.replace(
    'fun toggleLike(id: String) {\n        if (liked.contains(id)) liked.remove(id) else liked.add(id)\n        saveV3Liked(context, liked)\n    }',
    'fun toggleLike(id: String) {\n        if (liked.contains(id)) liked.remove(id) else liked.add(id)\n        saveV3Liked(context, liked)\n    }\n\n    fun toggleFollowArtist(id: String) {\n        if (followedArtists.contains(id)) followedArtists.remove(id) else followedArtists.add(id)\n        saveV3FollowedArtists(context, followedArtists)\n    }'
)

# Bottom navigation: Home, Search, Your Library, Create. No Premium.
old_nav = '''                            Row(
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
                                    selected = screen is V3Screen.Library || screen is V3Screen.Liked || screen is V3Screen.Playlists || screen is V3Screen.PlaylistDetail || screen is V3Screen.Downloads,
                                    icon = Icons.Rounded.LibraryMusic,
                                    label = "Library"
                                ) { screen = V3Screen.Library }
                            }'''
new_nav = '''                            Row(
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
                            }'''
if old_nav not in s:
    raise SystemExit('bottom nav block not found')
s = s.replace(old_nav, new_nav, 1)

# Root Library call.
old_libcall = '''                            V3Screen.Library -> V3LibraryScreen(
                                liked.size,
                                playlists.size,
                                onLiked = { screen = V3Screen.Liked },
                                onPlaylists = { screen = V3Screen.Playlists },
                                onDownloads = { screen = V3Screen.Downloads }
                            )'''
new_libcall = '''                            V3Screen.Library -> V3LibraryScreen(
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
                            )'''
if old_libcall not in s:
    raise SystemExit('library call block not found')
s = s.replace(old_libcall, new_libcall, 1)

# Root artist call adds real Follow state.
old_artistcall = '''                                        onTrack = ::playTrack,
                                        onToggleLike = ::toggleLike,
                                        onAddToPlaylist = { addTrackDialog = it },
                                        onPlayNext = ::playNext
                                    )'''
new_artistcall = '''                                        onTrack = ::playTrack,
                                        onToggleLike = ::toggleLike,
                                        onAddToPlaylist = { addTrackDialog = it },
                                        onPlayNext = ::playNext,
                                        followed = followedArtists.contains(artist.id),
                                        onToggleFollow = { toggleFollowArtist(artist.id) }
                                    )'''
# only replace the occurrence in artist branch; use rfind before Editorial branch
artist_branch = s.index('is V3Screen.Artist ->')
pos = s.find(old_artistcall, artist_branch)
if pos < 0:
    raise SystemExit('artist call pattern not found')
s = s[:pos] + s[pos:].replace(old_artistcall, new_artistcall, 1)

# Premium-inspired Home matching the supplied videos, while keeping only working content.
start = s.index('@Composable\nprivate fun V3HomeScreen')
end = s.index('@Composable\nprivate fun V3HomeChip', start)
new_home = r'''@Composable
private fun V3HomeScreen(
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onArtist: (String) -> Unit,
    onSearch: () -> Unit,
    onEditorial: (EditorialSong) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("All") }
    var liveTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var editorial by remember { mutableStateOf(emptyList<com.sonify.music.data.EditorialSection>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(filter) {
        loading = true
        try {
            editorial = withTimeoutOrNull(8000) { EditorialFeed.load(context) }.orEmpty()
            val incoming = withTimeoutOrNull(8000) {
                when (filter) {
                    "Hindi" -> AudiusCatalog.search("hindi bollywood", 30).getOrDefault(emptyList())
                    "Pakistani" -> AudiusCatalog.search("pakistani urdu", 30).getOrDefault(emptyList())
                    "Punjabi" -> AudiusCatalog.search("punjabi", 30).getOrDefault(emptyList())
                    else -> AudiusCatalog.trending(32).getOrDefault(emptyList())
                }
            }.orEmpty()
            liveTracks = if (incoming.isNotEmpty()) CatalogRegistry.remember(context, incoming) else emptyList()
        } finally {
            loading = false
        }
    }

    val visibleArtists = when (filter) {
        "Hindi" -> ArtistDirectory.popular.filter { it.region.contains("India") }.take(12)
        "Pakistani" -> ArtistDirectory.popular.filter { it.region.contains("Pakistan") }.take(12)
        "Punjabi" -> ArtistDirectory.popular.filter { it.id in setOf("diljit-dosanjh", "ap-dhillon") }
        else -> ArtistDirectory.popular.take(14)
    }
    val visibleEditorial = when (filter) {
        "Hindi" -> editorial.filter { it.id.contains("india") }
        "Pakistani" -> editorial.filter { it.id.contains("pakistan") }
        else -> editorial
    }
    val quickTracks = liveTracks.take(6).ifEmpty { DemoCatalog.featured.take(6) }
    val cards = liveTracks.drop(6).take(12).ifEmpty { DemoCatalog.featured }

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
                ) { Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Spacer(Modifier.width(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(listOf("All", "Hindi", "Pakistani", "Punjabi")) { label ->
                        V3FilterChip(label, selected = filter == label) { filter = label }
                    }
                }
            }
        }

        if (quickTracks.isNotEmpty()) {
            items(quickTracks.chunked(2)) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { track ->
                        V3QuickTrackCard(track, Modifier.weight(1f)) { onTrack(track) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (visibleArtists.isNotEmpty()) {
            item { V3SectionTitle("Popular artists", if (filter == "All") "India & Pakistan" else filter) }
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
                    items(section.songs, key = { section.id + it.title + it.artist }) { song ->
                        V3EditorialSongCard(song) { onEditorial(song) }
                    }
                }
            }
        }

        item { V3SectionTitle("Recommended for today", "Live playable music") }
        item {
            if (loading && liveTracks.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = V3Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    items(cards, key = { it.id }) { V3AlbumCard(it, onTrack) }
                }
            }
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

'''
s = s[:start] + new_home + s[end:]

# Search screen: Spotify-style white search bar and Browse All tiles. No fake camera/podcast buttons.
start = s.index('@Composable\nprivate fun V3SearchScreen')
end = s.index('@Composable\nprivate fun V3LibraryScreen', start)
new_search = r'''@Composable
private fun V3SearchScreen(
    liked: List<String>,
    onTrack: (Track) -> Unit,
    onCategory: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onArtist: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.isBlank()) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(300)
        val liveTracks = withTimeoutOrNull(8000) { AudiusCatalog.search(clean, 45).getOrDefault(emptyList()) }.orEmpty()
        results = if (liveTracks.isNotEmpty()) CatalogRegistry.remember(context, liveTracks) else DemoCatalog.search(clean)
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 26.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFB86B4A)), contentAlignment = Alignment.Center) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text("Search", color = V3Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                singleLine = true,
                placeholder = { Text("What do you want to listen to?", color = Color(0xFF4B4B4B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.Black) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null, tint = Color.Black) }
                },
                shape = RoundedCornerShape(5.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.Black
                )
            )
            Spacer(Modifier.height(22.dp))
        }

        if (query.isBlank()) {
            item {
                Text("Browse all", color = V3Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
            }
            items(v3Categories.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { category ->
                        V3SearchCategoryTile(category, Modifier.weight(1f)) { onCategory(category.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        } else if (loading) {
            item { Row(Modifier.fillMaxWidth().padding(36.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = V3Accent) } }
        } else if (results.isEmpty() && ArtistDirectory.matching(query).isEmpty()) {
            item { V3EmptyState("No results", "Try another song, artist, language or genre.") }
        } else {
            val artistMatches = ArtistDirectory.matching(query)
            if (artistMatches.isNotEmpty()) {
                item {
                    Text("Artists", color = V3Text, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(artistMatches, key = { it.id }) { artist -> V3ArtistCard(artist) { onArtist(artist.id) } }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Songs", color = V3Text, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(6.dp))
                }
            }
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

private fun v3CategoryColor(id: String): Color = when (id) {
    "bollywood" -> Color(0xFF8E1B55)
    "pakistani" -> Color(0xFF0D735D)
    "punjabi" -> Color(0xFFC04B19)
    "sad" -> Color(0xFF477D95)
    "romantic" -> Color(0xFF9D3553)
    "chill" -> Color(0xFF5D7887)
    "workout" -> Color(0xFF7B398F)
    "hiphop" -> Color(0xFFB85B20)
    "electronic" -> Color(0xFF136A80)
    "rnb" -> Color(0xFF8A3862)
    "rock" -> Color(0xFF16705A)
    else -> Color(0xFF5A4B75)
}

@Composable
private fun V3SearchCategoryTile(category: V3Category, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(92.dp).clip(RoundedCornerShape(5.dp)).background(v3CategoryColor(category.id)).clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(category.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.75f))
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(38.dp).graphicsLayer(rotationZ = 18f).align(Alignment.BottomEnd))
    }
}

'''
s = s[:start] + new_search + s[end:]

# Library matching the reference: profile + title + search/+ and actual filter tabs.
start = s.index('@Composable\nprivate fun V3LibraryScreen')
end = s.index('@Composable\nprivate fun V3LibraryRow', start)
new_library = r'''@Composable
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

'''
s = s[:start] + new_library + s[end:]

# Artist page gets a real Follow/Following action.
s = s.replace(
'''private fun V3ArtistScreen(
    artist: ArtistProfile,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {''',
'''private fun V3ArtistScreen(
    artist: ArtistProfile,
    liked: List<String>,
    onBack: () -> Unit,
    onTrack: (Track) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    followed: Boolean,
    onToggleFollow: () -> Unit
) {''', 1)

follow_anchor = '''                    Text("${artist.region} · Artist", color = V3Muted, fontSize = 13.sp)
                    Text("Artist image via Wikipedia", color = V3Muted, fontSize = 10.sp)
                }
            }
        }
        item { V3SectionTitle("Popular", "Playable tracks currently available in Sonify") }'''
follow_repl = '''                    Text("${artist.region} · Artist", color = V3Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onToggleFollow,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = V3Text)
                    ) {
                        Text(if (followed) "Following" else "Follow", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { V3SectionTitle("Popular", "Playable tracks currently available in Sonify") }'''
if follow_anchor not in s:
    raise SystemExit('artist follow anchor not found')
s = s.replace(follow_anchor, follow_repl, 1)

# Bottom nav label sizing for four items.
s = s.replace('fontSize = 11.sp,\n            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium', 'fontSize = 9.sp,\n            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium', 1)

# Persist Followed artists.
append_anchor = '''private fun saveV3Liked(context: Context, liked: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("liked", liked.toSet()).apply()
}
'''
append_repl = append_anchor + '''
private fun loadV3FollowedArtists(context: Context): Set<String> =
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .getStringSet("followed_artists", emptySet())?.toSet() ?: emptySet()

private fun saveV3FollowedArtists(context: Context, artists: List<String>) {
    context.getSharedPreferences("sonify", Context.MODE_PRIVATE)
        .edit().putStringSet("followed_artists", artists.toSet()).apply()
}
'''
if append_anchor not in s:
    raise SystemExit('liked helper anchor not found')
s = s.replace(append_anchor, append_repl, 1)

p.write_text(s)
print('patched premium UI')
