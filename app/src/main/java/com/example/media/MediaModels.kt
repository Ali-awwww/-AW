package com.example.media

enum class MediaType {
    VIDEO,
    AUDIO
}

data class MediaItemData(
    val id: String,
    val title: String,
    val artistOrDesc: String,
    val mediaUri: String,
    val artworkUri: String,
    val durationMs: Long = 0L,
    val mediaType: MediaType = MediaType.AUDIO,
    val category: String = "General",
    val isFavorite: Boolean = false,
    val isLocal: Boolean = false
)

data class PlaylistCheckpoint(
    val playlistId: String,
    val trackId: String,
    val trackTitle: String,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val items: List<MediaItemData> = emptyList(),
    val checkpoint: PlaylistCheckpoint? = null
)

enum class RepeatState {
    OFF,
    ALL,
    ONE
}

enum class SleepTimerMode {
    OFF,
    DURATION,
    END_OF_TRACK
}

