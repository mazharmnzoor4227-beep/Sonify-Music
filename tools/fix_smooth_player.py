from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

start = s.index('@Composable\nprivate fun V3MorphingPlayer(')
end = s.index('@Composable\nprivate fun V3ExpandedPlayerContent(', start)

new = '''@Composable
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

'''
s = s[:start] + new + s[end:]

mini_start = s.index('@Composable\nprivate fun V3MiniPlayerContent(')
mini_end = s.index('@Composable\nprivate fun V3HomeScreen(', mini_start)

new_mini = '''@Composable
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

'''
s = s[:mini_start] + new_mini + s[mini_end:]

p.write_text(s)
