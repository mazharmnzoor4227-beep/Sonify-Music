from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

# Imports for official YouTube playback.
s = s.replace('import android.content.ComponentName\n', 'import android.content.ComponentName\nimport android.content.Intent\n')
s = s.replace('import android.widget.Toast\n', 'import android.widget.Toast\nimport android.webkit.WebChromeClient\nimport android.webkit.WebView\nimport android.webkit.WebViewClient\n')
s = s.replace('import androidx.compose.ui.unit.sp\n', 'import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.viewinterop.AndroidView\n')

# New official video screen.
s = s.replace('''    data class Editorial(val title: String, val query: String) : V3Screen\n    data object Downloads : V3Screen\n}''', '''    data class Editorial(val title: String, val query: String) : V3Screen
    data class OfficialVideo(val videoId: String, val title: String, val artist: String, val channel: String) : V3Screen
    data object Downloads : V3Screen
}''')

# Back navigation for official video.
s = s.replace('''            screen is V3Screen.Category || screen is V3Screen.Artist || screen is V3Screen.Editorial -> screen = V3Screen.Home''', '''            screen is V3Screen.Category || screen is V3Screen.Artist || screen is V3Screen.Editorial || screen is V3Screen.OfficialVideo -> screen = V3Screen.Home''')

# Home editorial click -> official YouTube player, stop old audio first.
s = s.replace('''                                onEditorial = { song -> screen = V3Screen.Editorial(song.title, song.query) }''', '''                                onEditorial = { song ->
                                    if (song.videoId.isNotBlank()) {
                                        dismissPlayer()
                                        screen = V3Screen.OfficialVideo(song.videoId, song.title, song.artist, song.channelName)
                                    }
                                }''')

# Search receives official click handler.
s = s.replace('''                                onPlayNext = ::playNext,
                                onArtist = { screen = V3Screen.Artist(it) }
                            )''', '''                                onPlayNext = ::playNext,
                                onArtist = { screen = V3Screen.Artist(it) },
                                onOfficial = { song ->
                                    dismissPlayer()
                                    screen = V3Screen.OfficialVideo(song.videoId, song.title, song.artist, song.channelName)
                                }
                            )''', 1)

# Artist screen receives official click handler.
s = s.replace('''                                        followed = followedArtists.contains(artist.id),
                                        onToggleFollow = { toggleFollowArtist(artist.id) }
                                    )''', '''                                        followed = followedArtists.contains(artist.id),
                                        onToggleFollow = { toggleFollowArtist(artist.id) },
                                        onOfficial = { song ->
                                            dismissPlayer()
                                            screen = V3Screen.OfficialVideo(song.videoId, song.title, song.artist, song.channelName)
                                        }
                                    )''')

# Official video route.
route_anchor = '''                            is V3Screen.Editorial -> V3EditorialResultsScreen(
                                title = target.title,
                                query = target.query,
                                liked = liked,
                                onBack = { screen = V3Screen.Home },
                                onTrack = ::playTrack,
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { addTrackDialog = it },
                                onPlayNext = ::playNext
                            )
                            V3Screen.Downloads ->'''
route_repl = '''                            is V3Screen.Editorial -> V3EditorialResultsScreen(
                                title = target.title,
                                query = target.query,
                                liked = liked,
                                onBack = { screen = V3Screen.Home },
                                onTrack = ::playTrack,
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { addTrackDialog = it },
                                onPlayNext = ::playNext
                            )
                            is V3Screen.OfficialVideo -> V3OfficialVideoScreen(
                                videoId = target.videoId,
                                title = target.title,
                                artist = target.artist,
                                channel = target.channel,
                                onBack = { screen = V3Screen.Home }
                            )
                            V3Screen.Downloads ->'''
if route_anchor not in s:
    raise SystemExit('official route anchor not found')
s = s.replace(route_anchor, route_repl, 1)

# Replace Home with official-only content. No Audius/user uploads on Home.
start = s.index('@Composable\nprivate fun V3HomeScreen')
end = s.index('@Composable\nprivate fun V3FilterChip', start)
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

'''
s = s[:start] + new_home + s[end:]

# Artist portrait: official YouTube thumbnail is the fallback when Wikipedia portrait is unavailable.
old_portrait_launch = '''    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        resolved = true
    }'''
new_portrait_launch = '''    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        if (image.isNullOrBlank()) {
            image = EditorialFeed.load(context).flatMap { it.songs }
                .firstOrNull { it.artistId == artist.id && it.videoId.isNotBlank() }
                ?.thumbnailUrl
        }
        resolved = true
    }'''
# Ensure context exists before effect.
s = s.replace('''private fun V3ArtistPortrait(artist: ArtistProfile, modifier: Modifier) {
    var image''', '''private fun V3ArtistPortrait(artist: ArtistProfile, modifier: Modifier) {
    val context = LocalContext.current
    var image''')
s = s.replace(old_portrait_launch, new_portrait_launch, 1)
# Avoid redeclaring context inside image branch.
s = s.replace('''    if (!image.isNullOrBlank()) {
        val context = LocalContext.current
        AsyncImage(''', '''    if (!image.isNullOrBlank()) {
        AsyncImage(''', 1)

# Editorial cards always use official YouTube thumbnails.
card_start = s.index('@Composable\nprivate fun V3EditorialSongCard')
card_end = s.index('@Composable\nprivate fun V3EditorialResultsScreen', card_start)
new_card = r'''@Composable
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

'''
s = s[:card_start] + new_card + s[card_end:]

# Replace Search with official-catalog-only search.
search_start = s.index('@Composable\nprivate fun V3SearchScreen')
search_end = s.index('@Composable\nprivate fun V3LibraryScreen', search_start)
new_search = r'''@Composable
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

'''
s = s[:search_start] + new_search + s[search_end:]

# Replace artist screen so it never shows random Audius/user uploads.
artist_start = s.index('@Composable\nprivate fun V3ArtistScreen')
artist_end = s.index('@Composable\nprivate fun V3DownloadsScreen', artist_start)
new_artist = r'''@Composable
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
                        Text("Official YouTube release", color = V3Accent, fontSize = 10.sp)
                    }
                    Icon(Icons.Rounded.PlayCircle, null, tint = V3Text, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun V3OfficialVideoScreen(
    videoId: String,
    title: String,
    artist: String,
    channel: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(bottom = 26.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = V3Text) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = V3Text, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Official • $channel", color = V3Muted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        item {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(235.dp).background(Color.Black),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("https://www.youtube.com/embed/$videoId?playsinline=1&rel=0")
                    }
                },
                update = { web ->
                    val wanted = "https://www.youtube.com/embed/$videoId?playsinline=1&rel=0"
                    if (web.url != wanted) web.loadUrl(wanted)
                }
            )
        }
        item {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = V3Text, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(artist, color = V3Muted, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Verified, null, tint = V3Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Official YouTube source · $channel", color = V3Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Rounded.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open on YouTube", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text("Sonify uses YouTube's official player for these releases. It does not rip, re-upload or extract the audio.", color = V3Muted, fontSize = 11.sp)
            }
        }
    }
}

'''
s = s[:artist_start] + new_artist + s[artist_end:]

p.write_text(s)
print('patched official YouTube UI')
