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

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val mediaType: MediaType = MediaType.VIDEO,
    val timestamp: Long = System.currentTimeMillis()
)

data class VideoQuality(
    val label: String,
    val sizeLabel: String
)

data class ExtractedMetadata(
    val title: String,
    val thumbnailUrl: String,
    val platform: String,
    val qualities: List<VideoQuality>
)

