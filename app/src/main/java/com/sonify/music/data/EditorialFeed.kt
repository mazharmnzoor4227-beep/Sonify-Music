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
    val artistId: String
)

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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(context: Context): List<EditorialSection> = withContext(Dispatchers.IO) {
        val remote = runCatching {
            val request = Request.Builder()
                .url(URL)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "SonifyMusic/1.0")
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
                            val query = x.optString("query").trim()
                            if (title.isBlank() || artist.isBlank() || query.isBlank()) continue
                            add(EditorialSong(title, artist, query, x.optString("artistId").trim()))
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

    private val fallback = listOf(
        EditorialSection(
            "pakistan-popular",
            "Popular in Pakistan",
            "Pakistani hits and OST discovery",
            listOf(
                EditorialSong("Jhol", "Maanu & Annural Khalid", "Jhol Maanu Annural Khalid", "maanu"),
                EditorialSong("Tu Hai Kahan", "AUR", "Tu Hai Kahan AUR", "aur"),
                EditorialSong("Chal Diye Tum Kahan", "AUR", "Chal Diye Tum Kahan AUR", "aur"),
                EditorialSong("Kahani Suno 2.0", "Kaifi Khalil", "Kahani Suno 2.0 Kaifi Khalil", "kaifi-khalil")
            )
        ),
        EditorialSection(
            "india-popular",
            "Popular in India",
            "Bollywood and Hindi favorites",
            listOf(
                EditorialSong("Tum Hi Ho", "Arijit Singh", "Tum Hi Ho Arijit Singh", "arijit-singh"),
                EditorialSong("Channa Mereya", "Arijit Singh", "Channa Mereya Arijit Singh", "arijit-singh"),
                EditorialSong("Kesariya", "Arijit Singh", "Kesariya Arijit Singh", "arijit-singh"),
                EditorialSong("O Maahi", "Arijit Singh", "O Maahi Arijit Singh", "arijit-singh")
            )
        )
    )
}
