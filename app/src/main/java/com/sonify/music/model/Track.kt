package com.sonify.music.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String = "",
    val source: String = "",
    val durationMs: Long = 0L,
    val downloadable: Boolean = false
)
