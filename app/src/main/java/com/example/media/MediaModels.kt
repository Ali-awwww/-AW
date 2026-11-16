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
    val isLocal: Boolean = false,
    val subtitleUri: String? = null
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
    val checkpoint: PlaylistCheckpoint? = null,
    val sourceTreeUri: String? = null
)

data class PlaybackHistoryEntry(
    val item: MediaItemData,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playedAt: Long = System.currentTimeMillis()
)

data class ABLoopState(
    val pointAMs: Long? = null,
    val pointBMs: Long? = null,
    val isActive: Boolean = false
)

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean
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
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val mediaType: MediaType = MediaType.VIDEO,
    val savedUri: String? = null,
    val errorMessage: String? = null,
    val supportsResume: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class LinkInspectionResult(
    val url: String,
    val isSupportedPlatformPage: Boolean,
    val platformName: String,
    val suggestedFileName: String,
    val mimeType: String?,
    val contentLengthBytes: Long,
    val supportsRange: Boolean,
    val detectedMediaType: MediaType,
    val errorNote: String? = null
)
