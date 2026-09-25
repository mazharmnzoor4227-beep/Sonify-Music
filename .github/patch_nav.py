from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text()

# Android/system back must navigate inside Sonify before the Activity is allowed to exit.
if 'import androidx.activity.compose.BackHandler' not in s:
    s = s.replace(
        'import androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent',
        'import androidx.activity.ComponentActivity\nimport androidx.activity.compose.BackHandler\nimport androidx.activity.compose.setContent',
        1,
    )

back_handler = '''    BackHandler(
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

'''
if 'enabled = playerExpanded || queueOpen || createPlaylistOpen' not in s:
    anchor = '    MaterialTheme(\n'
    if anchor not in s:
        raise SystemExit('MaterialTheme anchor not found')
    s = s.replace(anchor, back_handler + anchor, 1)

old_nav = '''                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF080808)) {
                        NavigationBarItem(
                            selected = screen is V3Screen.Home,
                            onClick = { screen = V3Screen.Home },
                            icon = { Icon(Icons.Rounded.Home, null) },
                            label = { Text("Home") },
                            colors = v3NavColors()
                        )
                        NavigationBarItem(
                            selected = screen is V3Screen.Search,
                            onClick = { screen = V3Screen.Search },
                            icon = { Icon(Icons.Rounded.Search, null) },
                            label = { Text("Search") },
                            colors = v3NavColors()
                        )
                        NavigationBarItem(
                            selected = screen is V3Screen.Library || screen is V3Screen.Liked || screen is V3Screen.Playlists || screen is V3Screen.PlaylistDetail,
                            onClick = { screen = V3Screen.Library },
                            icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                            label = { Text("Library") },
                            colors = v3NavColors()
                        )
                    }
                }'''

new_nav = '''                bottomBar = {
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
                }'''
if old_nav in s:
    s = s.replace(old_nav, new_nav, 1)
elif 'V3BottomNavItem(' not in s:
    raise SystemExit('bottom navigation pattern not found')

old_colors = '''@Composable
private fun v3NavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = V3Accent,
    selectedTextColor = V3Text,
    unselectedIconColor = V3Muted,
    unselectedTextColor = V3Muted,
    indicatorColor = Color.Transparent
)
'''
new_colors = '''@Composable
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
'''
if old_colors in s:
    s = s.replace(old_colors, new_colors, 1)
elif 'private fun RowScope.V3BottomNavItem(' not in s:
    raise SystemExit('nav helper pattern not found')

old_header = '''        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SONIFY", color = V3Text, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).background(V3Accent, CircleShape))
                }
                Spacer(Modifier.height(6.dp))
                Text("Good evening", color = V3Muted, fontSize = 15.sp)
            }
        }'''
new_header = '''        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SONIFY",
                        color = V3Text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.size(7.dp).background(V3Accent, CircleShape))
                }
                Spacer(Modifier.height(2.dp))
                Text("Good evening · Your music, your mood", color = V3Muted, fontSize = 12.sp)
            }
        }'''
if old_header in s:
    s = s.replace(old_header, new_header, 1)
elif 'Good evening · Your music, your mood' not in s:
    raise SystemExit('home header pattern not found')

s = s.replace(
    'Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 13.dp)) {',
    'Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 9.dp)) {',
    1,
)

p.write_text(s)
