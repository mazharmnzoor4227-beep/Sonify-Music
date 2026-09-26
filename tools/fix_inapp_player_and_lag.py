from pathlib import Path
import re

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

old_loop = '''    LaunchedEffect(controller) {
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
'''
new_loop = '''    LaunchedEffect(controller) {
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
'''
if old_loop not in s:
    raise SystemExit('player polling block not found')
s = s.replace(old_loop, new_loop, 1)

s = s.replace('AudiusCatalog.search(category.query, 45)', 'AudiusCatalog.searchVerified(category.query, 45)')
s = s.replace('AudiusCatalog.search(query, 30)', 'AudiusCatalog.searchVerified(query, 30)')
s = s.replace('.crossfade(true)\n                    .build()', '.crossfade(false)\n                    .build()')

new_official = r'''@Composable
private fun V3OfficialVideoScreen(
    videoId: String,
    title: String,
    artist: String,
    channel: String,
    onBack: () -> Unit
) {
    var webView by remember(videoId) { mutableStateOf<WebView?>(null) }
    val embedUrl = remember(videoId) {
        "https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&origin=https%3A%2F%2Fgithub.com"
    }
    val headers = remember { mapOf("Referer" to "https://github.com/mazharmnzoor4227-beep/Sonify-Music/") }

    DisposableEffect(videoId) {
        onDispose {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
            webView = null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(V3Bg),
        contentPadding = PaddingValues(bottom = 26.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = V3Text) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = V3Text, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Official · $channel", color = V3Muted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        item {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(235.dp).background(Color.Black),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        settings.setSupportZoom(false)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        setBackgroundColor(android.graphics.Color.BLACK)
                        loadUrl(embedUrl, headers)
                    }
                },
                update = { web ->
                    webView = web
                    if (web.url?.contains(videoId) != true) web.loadUrl(embedUrl, headers)
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
                    Text("Official source · $channel", color = V3Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
                Surface(color = V3Surface2, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayCircle, null, tint = V3Accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Play it above without leaving Sonify", color = V3Text, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Official YouTube playback stays inside Sonify. Offline download is only shown for sources that explicitly permit downloading.",
                    color = V3Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

'''
pattern = r'@Composable\nprivate fun V3OfficialVideoScreen\(.*?\n\}\n\n(?=@Composable\nprivate fun V3DownloadsScreen)'
s2, count = re.subn(pattern, new_official, s, flags=re.S)
if count != 1:
    raise SystemExit(f'official screen replacement count={count}')
s = s2

p.write_text(s)
print('Patched SonifyActivityV3.kt')
