package com.sonify.music.data

import com.sonify.music.model.Track

object DemoCatalog {
    val featured = listOf(
        Track(
            "1",
            "Midnight Drive",
            "Nova Lane",
            "https://picsum.photos/seed/sonify01/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "Sonify Preview"
        ),
        Track(
            "2",
            "Neon Rain",
            "Kairo",
            "https://picsum.photos/seed/sonify02/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "Sonify Preview"
        ),
        Track(
            "3",
            "After Hours",
            "Luma",
            "https://picsum.photos/seed/sonify03/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            "Sonify Preview"
        ),
        Track(
            "4",
            "No Signal",
            "Atlas 99",
            "https://picsum.photos/seed/sonify04/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            "Sonify Preview"
        ),
        Track(
            "5",
            "Slow Motion",
            "Mira",
            "https://picsum.photos/seed/sonify05/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            "Sonify Preview"
        )
    )

    val trending = listOf(
        Track(
            "6",
            "Black Gold",
            "Iris",
            "https://picsum.photos/seed/sonify06/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            "Sonify Preview"
        ),
        Track(
            "7",
            "Outrun",
            "Serein",
            "https://picsum.photos/seed/sonify07/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            "Sonify Preview"
        ),
        Track(
            "8",
            "City Lights",
            "Northline",
            "https://picsum.photos/seed/sonify08/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            "Sonify Preview"
        ),
        Track(
            "9",
            "Lost Frequency",
            "Vanta",
            "https://picsum.photos/seed/sonify09/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            "Sonify Preview"
        ),
        Track(
            "10",
            "Glass Hearts",
            "Avery",
            "https://picsum.photos/seed/sonify10/500",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            "Sonify Preview"
        )
    )

    val allTracks: List<Track> = (featured + trending).distinctBy { it.id }

    fun search(query: String): List<Track> {
        val q = query.trim()
        if (q.isBlank()) return allTracks
        return allTracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.source.contains(q, ignoreCase = true)
        }
    }
}
