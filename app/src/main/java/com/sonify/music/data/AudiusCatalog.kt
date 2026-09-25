package com.sonify.music.data

import com.sonify.music.BuildConfig
import com.sonify.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only live catalog backed by Audius/Open Audio. */
object AudiusCatalog {
    private const val BASE_URL = "https://api.audius.co/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun trending(limit: Int = 30): Result<List<Track>> = requestTracks(
        path = "/tracks/trending",
        params = mapOf("limit" to limit.coerceIn(1, 100).toString(), "time" to "week")
    )

    suspend fun search(query: String, limit: Int = 35): Result<List<Track>> {
        val clean = query.trim()
        if (clean.isBlank()) return Result.success(emptyList())
        return requestTracks(
            path = "/tracks/search",
            params = mapOf(
                "query" to clean,
                "limit" to limit.coerceIn(1, 100).toString(),
                "sort_method" to "relevant"
            )
        )
    }

    suspend fun downloadable(query: String, limit: Int = 35): Result<List<Track>> {
        val clean = query.trim()
        if (clean.isBlank()) return Result.success(emptyList())
        return requestTracks(
            path = "/tracks/search",
            params = mapOf(
                "query" to clean,
                "limit" to limit.coerceIn(1, 100).toString(),
                "sort_method" to "relevant",
                "only_downloadable" to "true"
            )
        )
    }

    private suspend fun requestTracks(
        path: String,
        params: Map<String, String>
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = (BASE_URL + path).toHttpUrl().newBuilder()
            params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            BuildConfig.AUDIUS_API_KEY.trim().takeIf { it.isNotEmpty() }?.let {
                builder.addQueryParameter("api_key", it)
            }

            val request = Request.Builder()
                .url(builder.build())
                .header("Accept", "application/json")
                .header("User-Agent", "Sonify-Android/0.2")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Audius HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: return@use emptyList()
                buildList {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        item.toTrackOrNull()?.let(::add)
                    }
                }.distinctBy { it.id }
            }
        }
    }

    private fun JSONObject.toTrackOrNull(): Track? {
        val rawId = optString("id").trim()
        val title = optString("title").trim()
        if (rawId.isBlank() || title.isBlank()) return null

        val user = optJSONObject("user")
        val artist = user?.optString("name")?.takeIf { it.isNotBlank() }
            ?: user?.optString("handle")?.takeIf { it.isNotBlank() }
            ?: "Audius Artist"

        val artwork = optJSONObject("artwork")
        val artworkUrl = listOf(
            artwork?.optString("1000x1000"),
            artwork?.optString("_1000x1000"),
            artwork?.optString("480x480"),
            artwork?.optString("_480x480"),
            artwork?.optString("150x150"),
            artwork?.optString("_150x150")
        ).firstOrNull { !it.isNullOrBlank() }
            ?: ""

        val key = BuildConfig.AUDIUS_API_KEY.trim()
        val streamUrl = "$BASE_URL/tracks/$rawId/stream" +
            if (key.isNotBlank()) "?api_key=${java.net.URLEncoder.encode(key, "UTF-8")}" else ""

        val canDownload = optBoolean("downloadable", false) || optBoolean("is_downloadable", false)

        return Track(
            id = "audius:$rawId",
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            streamUrl = streamUrl,
            source = "Audius",
            durationMs = optLong("duration", 0L).coerceAtLeast(0L) * 1000L,
            downloadable = canDownload
        )
    }
}
