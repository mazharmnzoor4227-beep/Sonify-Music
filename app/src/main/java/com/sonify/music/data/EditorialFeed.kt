package com.sonify.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class EditorialSong(
    val title: String,
    val artist: String,
    val query: String,
    val artistId: String,
    val videoId: String = "",
    val channelName: String = "",
    val official: Boolean = true
) {
    val thumbnailUrl: String
        get() = if (videoId.isBlank()) "" else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    val youtubeUrl: String
        get() = if (videoId.isBlank()) "" else "https://www.youtube.com/watch?v=$videoId"
}

data class EditorialSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: List<EditorialSong>
)

object EditorialFeed {
    private const val URL = "https://raw.githubusercontent.com/mazharmnzoor4227-beep/Sonify-Music/main/catalog/editorial.json"
    private const val PREFS = "sonify_editorial"
    private const val KEY_JSON = "cached_json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(context: Context): List<EditorialSection> = withContext(Dispatchers.IO) {
        val remote = runCatching {
            val request = Request.Builder()
                .url(URL)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "SonifyMusic/1.2")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Editorial HTTP ${response.code}")
                response.body?.string().orEmpty().also { json ->
                    if (json.isNotBlank()) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString(KEY_JSON, json).apply()
                    }
                }
            }
        }.getOrNull().orEmpty()

        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null).orEmpty()

        parse(remote.ifBlank { cached }).ifEmpty { fallback }
    }

    private fun parse(json: String): List<EditorialSection> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val sections = root.optJSONArray("sections") ?: JSONArray()
            buildList {
                for (i in 0 until sections.length()) {
                    val s = sections.optJSONObject(i) ?: continue
                    val songsArray = s.optJSONArray("songs") ?: JSONArray()
                    val songs = buildList {
                        for (j in 0 until songsArray.length()) {
                            val x = songsArray.optJSONObject(j) ?: continue
                            val title = x.optString("title").trim()
                            val artist = x.optString("artist").trim()
                            val videoId = x.optString("videoId").trim()
                            if (title.isBlank() || artist.isBlank() || videoId.isBlank()) continue
                            add(
                                EditorialSong(
                                    title = title,
                                    artist = artist,
                                    query = x.optString("query", "$title $artist").trim(),
                                    artistId = x.optString("artistId").trim(),
                                    videoId = videoId,
                                    channelName = x.optString("channelName").trim(),
                                    official = x.optBoolean("official", true)
                                )
                            )
                        }
                    }
                    if (songs.isNotEmpty()) {
                        add(
                            EditorialSection(
                                id = s.optString("id"),
                                title = s.optString("title"),
                                subtitle = s.optString("subtitle"),
                                songs = songs
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun search(sections: List<EditorialSection>, query: String): List<EditorialSong> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return sections.flatMap { it.songs }
            .distinctBy { it.videoId }
            .filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.channelName.contains(q, ignoreCase = true)
            }
    }

    private val fallback = listOf(
        EditorialSection(
            "pakistan-official",
            "Official Pakistan",
            "Verified official releases and Coke Studio Pakistan",
            listOf(
                EditorialSong("Jhol", "Maanu & Annural Khalid", "Jhol Maanu Annural Khalid", "maanu", "-2RAq5o5pwc", "Coke Studio Pakistan"),
                EditorialSong("Pasoori", "Ali Sethi & Shae Gill", "Pasoori Ali Sethi Shae Gill", "ali-sethi", "5Eqb_-j3FDA", "Coke Studio Pakistan"),
                EditorialSong("Kana Yaari", "Kaifi Khalil x Eva B x Abdul Wahab Bugti", "Kana Yaari Kaifi Khalil", "kaifi-khalil", "zQDAi8tI-cU", "Coke Studio Pakistan"),
                EditorialSong("Tajdar-e-Haram", "Atif Aslam", "Tajdar e Haram Atif Aslam", "atif-aslam", "a18py61_F_w", "Coke Studio Pakistan"),
                EditorialSong("Blockbuster", "Faris Shafi x Umair Butt x Gharwi Group", "Blockbuster Coke Studio Pakistan", "", "-urTPhh7gNk", "Coke Studio Pakistan"),
                EditorialSong("Aayi Aayi", "Noman Ali Rajper x Babar Mangi x Marvi Saiban", "Aayi Aayi Coke Studio Pakistan", "", "0SkXKAY5rRQ", "Coke Studio Pakistan"),
                EditorialSong("Iraaday", "Abdul Hannan & Rovalio", "Iraaday Abdul Hannan Rovalio", "abdul-hannan", "Qwm6BSGrOq0", "Abdul Hannan"),
                EditorialSong("Bikhra", "Rovalio & Abdul Hannan", "Bikhra Abdul Hannan Rovalio", "abdul-hannan", "aRzbHxJZSTo", "Rovalio")
            )
        ),
        EditorialSection(
            "india-official",
            "Official India",
            "Verified label releases",
            listOf(
                EditorialSong("Channa Mereya", "Arijit Singh", "Channa Mereya Arijit Singh", "arijit-singh", "jglVv0JfZUc", "Sony Music India"),
                EditorialSong("Kesariya", "Arijit Singh", "Kesariya Arijit Singh", "arijit-singh", "BddP6PYo2gs", "Sony Music India"),
                EditorialSong("O Maahi", "Arijit Singh", "O Maahi Arijit Singh", "arijit-singh", "Etkd-07gnxM", "T-Series"),
                EditorialSong("Apna Bana Le", "Arijit Singh", "Apna Bana Le Arijit Singh", "arijit-singh", "ElZfdU54Cp8", "Zee Music Company")
            )
        )
    )
}
