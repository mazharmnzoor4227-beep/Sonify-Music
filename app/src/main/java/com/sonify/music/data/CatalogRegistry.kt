package com.sonify.music.data

import android.content.Context
import com.sonify.music.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

/** Keeps demo and discovered online tracks resolvable across player, likes and playlists. */
object CatalogRegistry {
    private const val PREFS = "sonify_catalog"
    private const val KEY_TRACKS = "cached_tracks"
    private const val MAX_CACHE = 250

    private val tracks = LinkedHashMap<String, Track>()
    private var seeded = false

    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        DemoCatalog.allTracks.forEach { tracks[it.id] = it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TRACKS, null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val track = Track(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        artworkUrl = o.optString("artworkUrl"),
                        streamUrl = o.optString("streamUrl"),
                        source = o.optString("source"),
                        durationMs = o.optLong("durationMs", 0L)
                    )
                    if (track.id.isNotBlank() && track.title.isNotBlank()) tracks[track.id] = track
                }
            }
        }
        seeded = true
    }

    @Synchronized
    fun remember(context: Context, incoming: List<Track>): List<Track> {
        seed(context)
        incoming.forEach { tracks[it.id] = it }
        trim()
        persist(context)
        return incoming
    }

    @Synchronized
    fun remember(context: Context, track: Track): Track {
        remember(context, listOf(track))
        return track
    }

    @Synchronized
    fun get(id: String): Track? = tracks[id] ?: DemoCatalog.allTracks.firstOrNull { it.id == id }

    @Synchronized
    fun allTracks(): List<Track> {
        if (tracks.isEmpty()) DemoCatalog.allTracks.forEach { tracks[it.id] = it }
        return tracks.values.toList()
    }

    private fun trim() {
        while (tracks.size > MAX_CACHE + DemoCatalog.allTracks.size) {
            val firstLiveKey = tracks.keys.firstOrNull { key -> DemoCatalog.allTracks.none { it.id == key } } ?: break
            tracks.remove(firstLiveKey)
        }
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        tracks.values
            .filter { it.source != "Sonify Preview" }
            .takeLast(MAX_CACHE)
            .forEach { track ->
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
            .edit()
            .putString(KEY_TRACKS, array.toString())
            .apply()
    }
}
