from pathlib import Path

p = Path('app/src/main/java/com/sonify/music/SonifyActivityV3.kt')
s = p.read_text(encoding='utf-8')
original = s

# Remove the YouTube/WebView playback surface. Sonify is audio-first again.
for line in [
    'import android.content.Intent\n',
    'import android.webkit.WebChromeClient\n',
    'import android.webkit.WebView\n',
    'import android.webkit.WebViewClient\n',
    'import androidx.compose.ui.viewinterop.AndroidView\n',
]:
    s = s.replace(line, '')

s = s.replace(
    '    data class OfficialVideo(val videoId: String, val title: String, val artist: String, val channel: String) : V3Screen\n',
    ''
)

s = s.replace(
    '            screen is V3Screen.Category || screen is V3Screen.Artist || screen is V3Screen.Editorial || screen is V3Screen.OfficialVideo -> screen = V3Screen.Home\n',
    '            screen is V3Screen.Category || screen is V3Screen.Artist || screen is V3Screen.Editorial -> screen = V3Screen.Home\n'
)

old_home = '''                                onEditorial = { song ->\n                                    if (song.videoId.isNotBlank()) {\n                                        dismissPlayer()\n                                        screen = V3Screen.OfficialVideo(song.videoId, song.title, song.artist, song.channelName)\n                                    }\n                                }\n'''
new_home = '''                                onEditorial = { song ->\n                                    screen = V3Screen.Editorial(song.title, song.query)\n                                }\n'''
s = s.replace(old_home, new_home)

old_search = '''                                onOfficial = { song ->\n                                    dismissPlayer()\n                                    screen = V3Screen.OfficialVideo(song.videoId, song.title, song.artist, song.channelName)\n                                }\n'''
new_search = '''                                onOfficial = { song ->\n                                    screen = V3Screen.Editorial(song.title, song.query)\n                                }\n'''
s = s.replace(old_search, new_search)

old_artist = '''                                        onOfficial = { song ->\n                                            dismissPlayer()\n                                            screen = V3Screen.OfficialVideo(song.videoId, song.title, song.artist, song.channelName)\n                                        }\n'''
new_artist = '''                                        onOfficial = { song ->\n                                            screen = V3Screen.Editorial(song.title, song.query)\n                                        }\n'''
s = s.replace(old_artist, new_artist)

old_branch = '''                            is V3Screen.OfficialVideo -> V3OfficialVideoScreen(\n                                videoId = target.videoId,\n                                title = target.title,\n                                artist = target.artist,\n                                channel = target.channel,\n                                onBack = { screen = V3Screen.Home }\n                            )\n'''
s = s.replace(old_branch, '')

start = s.find('@Composable\nprivate fun V3OfficialVideoScreen(')
end_marker = '@Composable\nprivate fun V3DownloadsScreen('
if start != -1:
    end = s.find(end_marker, start)
    if end == -1:
        raise SystemExit('Could not locate end of V3OfficialVideoScreen')
    s = s[:start] + s[end:]

s = s.replace('Text("Official YouTube release", color = V3Accent, fontSize = 10.sp)',
              'Text("Featured release", color = V3Accent, fontSize = 10.sp)')
s = s.replace(
    'V3EmptyState("Full track source not connected yet", "This popular song is in Sonify\'s live editorial feed, but the current open catalog does not provide a playable copy. Connect a licensed mainstream catalog for full playback.")',
    'V3EmptyState("Audio source not available", "This release is listed in Sonify, but the connected music catalog does not currently provide a playable audio stream for it.")'
)

# Make the editorial/audio result page feel like a music page, not a video page.
s = s.replace('item { V3DetailHeader(title, "Popular pick", onBack) }',
              'item { V3DetailHeader(title, "Playable audio", onBack) }')

if s == original:
    print('No changes needed')
else:
    p.write_text(s, encoding='utf-8')
    print('Restored Spotify-style in-app audio flow and removed YouTube video player surface')
