package com.sonify.music.data

import com.sonify.music.model.Track

object DemoCatalog {
    val featured = listOf(
        Track("1", "Midnight Drive", "Nova Lane", "https://picsum.photos/seed/sonify01/900"),
        Track("2", "Neon Rain", "Kairo", "https://picsum.photos/seed/sonify02/900"),
        Track("3", "After Hours", "Luma", "https://picsum.photos/seed/sonify03/900"),
        Track("4", "No Signal", "Atlas 99", "https://picsum.photos/seed/sonify04/900"),
        Track("5", "Slow Motion", "Mira", "https://picsum.photos/seed/sonify05/900")
    )

    val trending = listOf(
        Track("6", "Black Gold", "Iris", "https://picsum.photos/seed/sonify06/900"),
        Track("7", "Outrun", "Serein", "https://picsum.photos/seed/sonify07/900"),
        Track("8", "City Lights", "Northline", "https://picsum.photos/seed/sonify08/900"),
        Track("9", "Lost Frequency", "Vanta", "https://picsum.photos/seed/sonify09/900"),
        Track("10", "Glass Hearts", "Avery", "https://picsum.photos/seed/sonify10/900")
    )
}
