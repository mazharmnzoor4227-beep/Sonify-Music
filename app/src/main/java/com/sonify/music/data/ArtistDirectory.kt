package com.sonify.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
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
        ArtistProfile("ap-dhillon", "AP Dhillon", "India / Canada", "AP Dhillon"),
        ArtistProfile("aur", "AUR", "Pakistan", "AUR (musical group)", "AUR Pakistan"),
        ArtistProfile("abdul-hannan", "Abdul Hannan", "Pakistan", "Abdul Hannan (singer)", "Abdul Hannan Pakistan"),
        ArtistProfile("kaifi-khalil", "Kaifi Khalil", "Pakistan", "Kaifi Khalil"),
        ArtistProfile("young-stunners", "Young Stunners", "Pakistan", "Young Stunners"),
        ArtistProfile("maanu", "Maanu", "Pakistan", "Maanu (singer)", "Maanu Pakistan"),
        ArtistProfile("annural-khalid", "Annural Khalid", "Pakistan", "Annural Khalid")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val portraitCache = ConcurrentHashMap<String, String?>()

    fun get(id: String): ArtistProfile? = popular.firstOrNull { it.id == id }

    fun matching(query: String): List<ArtistProfile> {
        val q = query.trim()
        if (q.isBlank()) return popular
        return popular.filter { it.name.contains(q, ignoreCase = true) || it.region.contains(q, ignoreCase = true) }
    }

    suspend fun portrait(profile: ArtistProfile): String? = withContext(Dispatchers.IO) {
        if (portraitCache.containsKey(profile.id)) return@withContext portraitCache[profile.id]
        val image = summaryPortrait(profile.wikipediaTitle) ?: mediaWikiPortrait(profile.wikipediaTitle)
        portraitCache[profile.id] = image
        image
    }

    private fun summaryPortrait(title: String): String? = runCatching {
        val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
            .header("Accept", "application/json")
            .header("User-Agent", "SonifyMusic/1.0 (Android music discovery app)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = JSONObject(response.body?.string().orEmpty())
            root.optJSONObject("originalimage")?.optString("source")?.takeIf { it.isNotBlank() }
                ?: root.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun mediaWikiPortrait(title: String): String? = runCatching {
        val url = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("prop", "pageimages")
            .addQueryParameter("piprop", "thumbnail|original")
            .addQueryParameter("pithumbsize", "1000")
            .addQueryParameter("titles", title)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SonifyMusic/1.0 (Android music discovery app)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = JSONObject(response.body?.string().orEmpty())
            val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return@use null
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val original = page.optJSONObject("original")?.optString("source").orEmpty()
                if (original.isNotBlank()) return@use original
                val thumb = page.optJSONObject("thumbnail")?.optString("source").orEmpty()
                if (thumb.isNotBlank()) return@use thumb
            }
            null
        }
    }.getOrNull()
}
