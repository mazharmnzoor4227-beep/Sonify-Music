package com.sonify.music.data

import android.content.Context
import com.sonify.music.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Stores tracks locally only when the provider marks the track as downloadable.
 * Files stay in Sonify's private app storage and are removed when the app is uninstalled.
 */
object OfflineStore {
    private const val PREFS = "sonify_offline"
    private const val KEY_INDEX = "tracks"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isDownloaded(context: Context, id: String): Boolean =
        readIndex(context).any { it.id == id && File(it.streamUrl.removePrefix("file://")).exists() }

    fun resolve(context: Context, track: Track): Track =
        readIndex(context).firstOrNull { it.id == track.id && File(it.streamUrl.removePrefix("file://")).exists() } ?: track

    fun allDownloaded(context: Context): List<Track> =
        readIndex(context).filter { File(it.streamUrl.removePrefix("file://")).exists() }

    suspend fun download(context: Context, track: Track): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            require(track.downloadable) { "This track is not offered for download by its provider." }
            require(track.streamUrl.startsWith("http")) { "No downloadable source available." }

            val dir = File(context.filesDir, "sonify_downloads").apply { mkdirs() }
            val safeName = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(dir, "$safeName.audio")
            val temp = File(dir, "$safeName.part")

            val request = Request.Builder()
                .url(track.streamUrl)
                .header("User-Agent", "Sonify-Android/0.2")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed (${response.code})")
                val body = response.body ?: error("Empty download")
                temp.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
            }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            val local = track.copy(streamUrl = target.toURI().toString())
            val current = readIndex(context).filterNot { it.id == track.id }.toMutableList()
            current.add(0, local)
            writeIndex(context, current)
            CatalogRegistry.remember(context, local)
            local
        }
    }

    fun remove(context: Context, id: String) {
        val current = readIndex(context)
        current.firstOrNull { it.id == id }?.streamUrl?.removePrefix("file://")?.let { File(it).delete() }
        writeIndex(context, current.filterNot { it.id == id })
    }

    private fun readIndex(context: Context): List<Track> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_INDEX, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val track = Track(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        artworkUrl = o.optString("artworkUrl"),
                        streamUrl = o.optString("streamUrl"),
                        source = o.optString("source"),
                        durationMs = o.optLong("durationMs", 0L),
                        downloadable = true
                    )
                    if (track.id.isNotBlank() && track.streamUrl.startsWith("file:")) add(track)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(context: Context, tracks: List<Track>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("artworkUrl", track.artworkUrl)
                put("streamUrl", track.streamUrl)
                put("source", track.source)
                put("durationMs", track.durationMs)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_INDEX, array.toString()).apply()
    }
}
