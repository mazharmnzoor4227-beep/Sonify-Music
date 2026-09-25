package com.sonify.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ArtistProfile(
    val id: String,
    val name: String,
    val region: String,
    val wikipediaTitle: String,
    val musicQuery: String = name
)

object ArtistDirectory {
    val popular = listOf(
        ArtistProfile("arijit-singh", "Arijit Singh", "India", "Arijit Singh"),
        ArtistProfile("atif-aslam", "Atif Aslam", "Pakistan", "Atif Aslam"),
        ArtistProfile("rahat-fateh-ali-khan", "Rahat Fateh Ali Khan", "Pakistan", "Rahat Fateh Ali Khan"),
        ArtistProfile("shreya-ghoshal", "Shreya Ghoshal", "India", "Shreya Ghoshal"),
        ArtistProfile("ali-zafar", "Ali Zafar", "Pakistan", "Ali Zafar"),
        ArtistProfile("aima-baig", "Aima Baig", "Pakistan", "Aima Baig"),
        ArtistProfile("diljit-dosanjh", "Diljit Dosanjh", "India", "Diljit Dosanjh"),
        ArtistProfile("ap-dhillon", "AP Dhillon", "India / Canada", "AP Dhillon")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun get(id: String): ArtistProfile? = popular.firstOrNull { it.id == id }

    fun matching(query: String): List<ArtistProfile> {
        val q = query.trim()
        if (q.isBlank()) return popular
        return popular.filter { it.name.contains(q, ignoreCase = true) || it.region.contains(q, ignoreCase = true) }
    }

    suspend fun portrait(profile: ArtistProfile): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("redirects", "1")
                .addQueryParameter("prop", "pageimages")
                .addQueryParameter("piprop", "thumbnail")
                .addQueryParameter("pithumbsize", "900")
                .addQueryParameter("titles", profile.wikipediaTitle)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Sonify-Android/0.2")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return@use null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val source = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                    if (source.isNotBlank()) return@use source
                }
                null
            }
        }.getOrNull()
    }
}
