from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str) -> str:
    a = text.find(start_marker)
    if a < 0:
        raise SystemExit(f'start marker not found: {start_marker}')
    b = text.find(end_marker, a)
    if b < 0:
        raise SystemExit(f'end marker not found: {end_marker}')
    return text[:a] + replacement + text[b:]


s = s.replace(
    'import com.sonify.music.data.DemoCatalog',
    'import com.sonify.music.data.AudiusCatalog\nimport com.sonify.music.data.CatalogRegistry\nimport com.sonify.music.data.DemoCatalog'
)

models = '''private data class V3Category(
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

'''
s = replace_between(s, 'private data class V3Category(', 'class SonifyActivityV3', models)

s = s.replace(
    '    val context = LocalContext.current\n    var screen by remember',
    '    val context = LocalContext.current\n    LaunchedEffect(Unit) { CatalogRegistry.seed(context) }\n    var screen by remember',
    1
)

s = s.replace('DemoCatalog.allTracks.firstOrNull { it.id == id }', 'CatalogRegistry.get(id)')
s = s.replace('val queue = DemoCatalog.allTracks', 'val queue = CatalogRegistry.allTracks()')
s = s.replace('DemoCatalog.allTracks.map { it.id }', 'CatalogRegistry.allTracks().map { it.id }')
s = s.replace('DemoCatalog.allTracks.filter { it.id in liked }', 'CatalogRegistry.allTracks().filter { it.id in liked }')
s = s.replace(
    'playlist.trackIds.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }',
    'playlist.trackIds.mapNotNull { id -> CatalogRegistry.get(id) }'
)

needle = '''        val queue = CatalogRegistry.allTracks()
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)'''
replacement = '''        CatalogRegistry.remember(context, track)
        val queue = CatalogRegistry.allTracks()
        val start = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)'''
if needle in s:
    s = s.replace(needle, replacement, 1)

home = '''@Composable
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

'''
s = replace_between(s, '@Composable\nprivate fun V3HomeScreen', '@Composable\nprivate fun V3CategoryCard', home)

category_screen = '''@Composable
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

'''
s = replace_between(s, '@Composable\nprivate fun V3CategoryScreen', '@Composable\nprivate fun V3SearchScreen', category_screen)

search_screen = '''@Composable
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

'''
s = replace_between(s, '@Composable\nprivate fun V3SearchScreen', '@Composable\nprivate fun V3LibraryScreen', search_screen)

s = s.replace(
    'val tracks = category.trackIds.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }',
    'val tracks = category.trackIds.mapNotNull { id -> CatalogRegistry.get(id) }'
)
s = s.replace(
    'val tracks = ids.mapNotNull { id -> DemoCatalog.allTracks.firstOrNull { it.id == id } }',
    'val tracks = ids.mapNotNull { id -> CatalogRegistry.get(id) }'
)

p.write_text(s)
