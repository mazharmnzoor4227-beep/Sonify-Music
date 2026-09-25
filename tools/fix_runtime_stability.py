from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

if 'import kotlinx.coroutines.withTimeoutOrNull' not in s:
    s = s.replace('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.withTimeoutOrNull\n')

old_home = '''    LaunchedEffect(Unit) {
        editorial = EditorialFeed.load(context)
        val onlineTracks = AudiusCatalog.trending(28).getOrDefault(emptyList())
        if (onlineTracks.isNotEmpty()) liveTracks = CatalogRegistry.remember(context, onlineTracks)
        loading = false
    }'''
new_home = '''    LaunchedEffect(Unit) {
        try {
            editorial = withTimeoutOrNull(8000) { EditorialFeed.load(context) }.orEmpty()
            val onlineTracks = withTimeoutOrNull(8000) {
                AudiusCatalog.trending(28).getOrDefault(emptyList())
            }.orEmpty()
            if (onlineTracks.isNotEmpty()) liveTracks = CatalogRegistry.remember(context, onlineTracks)
        } finally {
            loading = false
        }
    }'''
if old_home in s:
    s = s.replace(old_home, new_home, 1)

old_portrait = '''    if (!image.isNullOrBlank()) {
        V3ArtworkUrl(image.orEmpty(), modifier)
    } else {
        Box(modifier.background(V3Surface2), contentAlignment = Alignment.Center) {
            Text(v3Initials(artist.name), color = V3Text, fontWeight = FontWeight.Black, fontSize = 30.sp)
            if (!resolved) CircularProgressIndicator(color = V3Accent, strokeWidth = 2.dp, modifier = Modifier.size(74.dp))
        }
    }'''
new_portrait = '''    if (!image.isNullOrBlank()) {
        val context = LocalContext.current
        AsyncImage(
            model = remember(image) {
                ImageRequest.Builder(context)
                    .data(image)
                    .crossfade(true)
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
    }'''
if old_portrait in s:
    s = s.replace(old_portrait, new_portrait, 1)

old_artist = '''    LaunchedEffect(artist.id) {
        image = ArtistDirectory.portrait(artist)
        val online = AudiusCatalog.search(artist.musicQuery, 50).getOrDefault(emptyList())
        tracks = if (online.isNotEmpty()) CatalogRegistry.remember(context, online) else emptyList()
        loading = false
    }'''
new_artist = '''    LaunchedEffect(artist.id) {
        try {
            image = withTimeoutOrNull(9000) { ArtistDirectory.portrait(artist) }
            val online = withTimeoutOrNull(8000) {
                AudiusCatalog.search(artist.musicQuery, 50).getOrDefault(emptyList())
            }.orEmpty()
            tracks = if (online.isNotEmpty()) CatalogRegistry.remember(context, online) else emptyList()
        } finally {
            loading = false
        }
    }'''
if old_artist in s:
    s = s.replace(old_artist, new_artist, 1)

# Make the top header less visually heavy and avoid the large green band.
s = s.replace(
'''                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF16220C), V3Bg)))
                    .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)''',
'''                Modifier.fillMaxWidth()
                    .background(V3Bg)
                    .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)''',
1
)
s = s.replace('Text("Good evening", color = V3Text, fontSize = 28.sp, fontWeight = FontWeight.Black)',
              'Text("Good evening", color = V3Text, fontSize = 25.sp, fontWeight = FontWeight.Black)', 1)

p.write_text(s)
print('runtime stability patch applied')
