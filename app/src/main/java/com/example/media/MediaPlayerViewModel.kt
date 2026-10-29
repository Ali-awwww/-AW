package com.example.media

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID

enum class AppTab {
    VIDEOS,
    MUSIC,
    PLAYLISTS,
    DOWNLOADER
}

class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playback = PlaybackManager
    private val prefs = application.getSharedPreferences("vibe_player_checkpoints", Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(application)

    private val _currentTab = MutableStateFlow(AppTab.VIDEOS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _allVideos = MutableStateFlow<List<MediaItemData>>(emptyList())
    val allVideos: StateFlow<List<MediaItemData>> = _allVideos.asStateFlow()

    private val _allAudios = MutableStateFlow<List<MediaItemData>>(emptyList())
    val allAudios: StateFlow<List<MediaItemData>> = _allAudios.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _selectedVideo = MutableStateFlow<MediaItemData?>(null)
    val selectedVideo: StateFlow<MediaItemData?> = _selectedVideo.asStateFlow()

    private val _isAudioPlayerExpanded = MutableStateFlow(false)
    val isAudioPlayerExpanded: StateFlow<Boolean> = _isAudioPlayerExpanded.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedVideoCategory = MutableStateFlow("All")
    val selectedVideoCategory: StateFlow<String> = _selectedVideoCategory.asStateFlow()

    private val _selectedAudioCategory = MutableStateFlow("All")
    val selectedAudioCategory: StateFlow<String> = _selectedAudioCategory.asStateFlow()

    // Sleep Timer
    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerSeconds = MutableStateFlow(0)
    val sleepTimerSeconds: StateFlow<Int> = _sleepTimerSeconds.asStateFlow()

    private val _isFadeOutEnabled = MutableStateFlow(true)
    val isFadeOutEnabled: StateFlow<Boolean> = _isFadeOutEnabled.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadItem>> = _activeDownloads.asStateFlow()

    private val _downloadUrl = MutableStateFlow("")
    val downloadUrl: StateFlow<String> = _downloadUrl.asStateFlow()
 
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()
 
    private val _extractedMetadata = MutableStateFlow<ExtractedMetadata?>(null)
    val extractedMetadata: StateFlow<ExtractedMetadata?> = _extractedMetadata.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        PlaybackManager.init(application)
        PlaybackManager.onTrackEndedListener = {
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
                cancelSleepTimer()
                playback.getPlayer()?.pause()
                true
            } else {
                false
            }
        }
        loadInitialPlaylistsWithCheckpoints()
        observePlaybackForCheckpoints()
    }

    private fun loadInitialPlaylistsWithCheckpoints() {
        val initialList = listOf(
            Playlist(
                id = "fav_playlist",
                name = "المفضلة (Favorites)",
                description = "المقاطع الصوتية والفيديوهات المفضلة لديك",
                items = emptyList(),
                checkpoint = loadSavedCheckpoint("fav_playlist")
            )
        )
        _playlists.value = initialList
    }

    private fun observePlaybackForCheckpoints() {
        viewModelScope.launch {
            combine(
                playback.activePlaylistId,
                playback.currentMediaItem,
                playback.currentPositionMs,
                playback.durationMs
            ) { activePlId, currentItem, posMs, durMs ->
                if (activePlId != null && currentItem != null && posMs > 0) {
                    saveCheckpoint(
                        playlistId = activePlId,
                        trackId = currentItem.id,
                        trackTitle = currentItem.title,
                        posMs = posMs,
                        durMs = durMs
                    )
                }
            }.collect {}
        }
    }

    private fun saveCheckpoint(
        playlistId: String,
        trackId: String,
        trackTitle: String,
        posMs: Long,
        durMs: Long
    ) {
        val newCp = PlaylistCheckpoint(
            playlistId = playlistId,
            trackId = trackId,
            trackTitle = trackTitle,
            positionMs = posMs,
            durationMs = durMs
        )

        // Update in-memory playlist
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(checkpoint = newCp) else pl
        }

        // Persist to SharedPreferences
        prefs.edit()
            .putString("cp_trackId_$playlistId", trackId)
            .putString("cp_trackTitle_$playlistId", trackTitle)
            .putLong("cp_pos_$playlistId", posMs)
            .putLong("cp_dur_$playlistId", durMs)
            .apply()
    }

    private fun loadSavedCheckpoint(playlistId: String): PlaylistCheckpoint? {
        val trackId = prefs.getString("cp_trackId_$playlistId", null) ?: return null
        val trackTitle = prefs.getString("cp_trackTitle_$playlistId", "") ?: ""
        val posMs = prefs.getLong("cp_pos_$playlistId", 0L)
        val durMs = prefs.getLong("cp_dur_$playlistId", 0L)
        return PlaylistCheckpoint(
            playlistId = playlistId,
            trackId = trackId,
            trackTitle = trackTitle,
            positionMs = posMs,
            durationMs = durMs
        )
    }

    val filteredVideos: StateFlow<List<MediaItemData>> = combine(
        _allVideos,
        _searchQuery,
        _selectedVideoCategory
    ) { videos, query, category ->
        videos.filter { video ->
            val matchesCategory = category == "All" || video.category.equals(category, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    video.title.contains(query, ignoreCase = true) ||
                    video.artistOrDesc.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _allVideos.value)

    val filteredAudios: StateFlow<List<MediaItemData>> = combine(
        _allAudios,
        _searchQuery,
        _selectedAudioCategory
    ) { audios, query, category ->
        audios.filter { audio ->
            val matchesCategory = category == "All" || audio.category.equals(category, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    audio.title.contains(query, ignoreCase = true) ||
                    audio.artistOrDesc.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _allAudios.value)

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setVideoCategory(category: String) {
        _selectedVideoCategory.value = category
    }

    fun setAudioCategory(category: String) {
        _selectedAudioCategory.value = category
    }

    fun playVideo(video: MediaItemData) {
        _selectedVideo.value = video
        playback.play(item = video, newQueue = _allVideos.value, playlistId = null)
    }

    fun closeVideoPlayer() {
        _selectedVideo.value = null
        if (playback.currentMediaItem.value?.mediaType == MediaType.VIDEO) {
            playback.getPlayer()?.pause()
        }
    }

    fun playAudio(audio: MediaItemData) {
        playback.play(item = audio, newQueue = _allAudios.value, playlistId = null)
    }

    fun resumePlaylist(playlistId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (playlist.items.isEmpty()) return

        val cp = playlist.checkpoint
        if (cp != null) {
            val targetItem = playlist.items.find { it.id == cp.trackId } ?: playlist.items.first()
            val targetPos = if (targetItem.id == cp.trackId) cp.positionMs else 0L

            if (targetItem.mediaType == MediaType.VIDEO) {
                _selectedVideo.value = targetItem
            }
            playback.play(
                item = targetItem,
                newQueue = playlist.items,
                playlistId = playlist.id,
                startPositionMs = targetPos
            )
        } else {
            playPlaylistFromStart(playlistId)
        }
    }

    fun playPlaylistFromStart(playlistId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (playlist.items.isEmpty()) return

        val first = playlist.items.first()
        if (first.mediaType == MediaType.VIDEO) {
            _selectedVideo.value = first
        }
        playback.play(
            item = first,
            newQueue = playlist.items,
            playlistId = playlist.id,
            startPositionMs = 0L
        )
    }

    fun playItemInPlaylist(playlistId: String, item: MediaItemData) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (item.mediaType == MediaType.VIDEO) {
            _selectedVideo.value = item
        }
        playback.play(
            item = item,
            newQueue = playlist.items,
            playlistId = playlist.id,
            startPositionMs = 0L
        )
    }

    fun expandAudioPlayer(expand: Boolean) {
        _isAudioPlayerExpanded.value = expand
    }

    fun toggleFavorite(item: MediaItemData) {
        val updatedVideos = _allVideos.value.map {
            if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        val updatedAudios = _allAudios.value.map {
            if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        _allVideos.value = updatedVideos
        _allAudios.value = updatedAudios

        val isCurrentlyFav = item.isFavorite
        val newItem = item.copy(isFavorite = !isCurrentlyFav)

        val favPlaylist = _playlists.value.find { it.id == "fav_playlist" } ?: return
        val newFavItems = if (!isCurrentlyFav) {
            favPlaylist.items + newItem
        } else {
            favPlaylist.items.filterNot { it.id == item.id }
        }
        _playlists.value = _playlists.value.map {
            if (it.id == "fav_playlist") it.copy(items = newFavItems) else it
        }
    }

    fun addCustomMedia(
        title: String,
        artistOrDesc: String,
        uri: String,
        artworkUri: String,
        mediaType: MediaType
    ) {
        val newItem = MediaItemData(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Custom Media" },
            artistOrDesc = artistOrDesc.ifBlank { if (mediaType == MediaType.VIDEO) "Online Video Stream" else "Web Audio Stream" },
            mediaUri = uri,
            artworkUri = artworkUri.ifBlank {
                if (mediaType == MediaType.VIDEO)
                    "https://images.unsplash.com/photo-1536240478700-b869070f9279?w=600&auto=format&fit=crop&q=80"
                else
                    "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80"
            },
            durationMs = 0L,
            mediaType = mediaType,
            category = "Custom Streams"
        )
        if (mediaType == MediaType.VIDEO) {
            _allVideos.value = listOf(newItem) + _allVideos.value
            playVideo(newItem)
        } else {
            _allAudios.value = listOf(newItem) + _allAudios.value
            playAudio(newItem)
        }
    }

    fun importLocalMedia(uri: Uri, context: Context, isVideo: Boolean) {
        var fileName = "Local Media"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: "Local Media"
                    }
                }
            }
        } catch (e: Exception) {
            fileName = uri.lastPathSegment ?: "Local File"
        }

        val newItem = MediaItemData(
            id = UUID.randomUUID().toString(),
            title = fileName,
            artistOrDesc = if (isVideo) "ملف فيديو محلي (Local Video)" else "ملف صوت محلي (Local Audio)",
            mediaUri = uri.toString(),
            artworkUri = if (isVideo)
                "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop&q=80"
            else
                "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            durationMs = 0L,
            mediaType = if (isVideo) MediaType.VIDEO else MediaType.AUDIO,
            category = "Local Files",
            isLocal = true
        )

        if (isVideo) {
            _allVideos.value = listOf(newItem) + _allVideos.value
            playVideo(newItem)
        } else {
            _allAudios.value = listOf(newItem) + _allAudios.value
            playAudio(newItem)
        }
    }

    fun importFolderAsPlaylist(treeUri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore permission error if already granted or unsupported
            }

            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val items = mutableListOf<MediaItemData>()
            var folderName = "مجلد الهاتف"

            try {
                val cursor: Cursor? = context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (it.moveToNext()) {
                        val fileDocId = it.getString(idCol)
                        val name = it.getString(nameCol) ?: "Media"
                        val mime = it.getString(mimeCol) ?: ""

                        val isAudio = mime.startsWith("audio/") || name.endsWith(".mp3", true) || name.endsWith(".m4a", true) || name.endsWith(".wav", true) || name.endsWith(".aac", true)
                        val isVideo = mime.startsWith("video/") || name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".mov", true)

                        if (isAudio || isVideo) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                            val type = if (isVideo) MediaType.VIDEO else MediaType.AUDIO
                            items.add(
                                MediaItemData(
                                    id = UUID.randomUUID().toString(),
                                    title = name,
                                    artistOrDesc = if (isVideo) "فيديو محلي من المجلد" else "مقطع صوتي محلي من المجلد",
                                    mediaUri = fileUri.toString(),
                                    artworkUri = if (isVideo)
                                        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop&q=80"
                                    else
                                        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
                                    durationMs = 0L,
                                    mediaType = type,
                                    category = "Local Folder",
                                    isLocal = true
                                )
                            )
                        }
                    }
                }
                folderName = treeUri.lastPathSegment?.substringAfterLast(":") ?: "مجلد الهاتف"
            } catch (e: Exception) {
                // Fallback
            }

            withContext(Dispatchers.Main) {
                val newPl = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = "مجلد: $folderName",
                    description = "${items.size} ملفات وسائط مستوردة",
                    items = items,
                    checkpoint = null
                )
                _playlists.value = listOf(newPl) + _playlists.value
            }
        }
    }

    fun importMultipleFilesAsPlaylist(uris: List<Uri>, context: Context, customName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<MediaItemData>()

            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore
                }

                var fileName = "Local Media"
                var mimeType: String? = null
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) {
                                fileName = it.getString(nameIdx) ?: "Media File"
                            }
                        }
                    }
                    mimeType = context.contentResolver.getType(uri)
                } catch (e: Exception) {
                    fileName = uri.lastPathSegment ?: "Media File"
                }

                val isVideo = mimeType?.startsWith("video/") == true || fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true)
                val type = if (isVideo) MediaType.VIDEO else MediaType.AUDIO

                items.add(
                    MediaItemData(
                        id = UUID.randomUUID().toString(),
                        title = fileName,
                        artistOrDesc = if (isVideo) "فيديو محلي من الهاتف" else "مقطع صوت محلي من الهاتف",
                        mediaUri = uri.toString(),
                        artworkUri = if (isVideo)
                            "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop&q=80"
                        else
                            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
                        durationMs = 0L,
                        mediaType = type,
                        category = "Batch Import",
                        isLocal = true
                    )
                )
            }

            withContext(Dispatchers.Main) {
                val playlistName = customName?.ifBlank { null } ?: "قائمة ملفات الهاتف (${items.size})"
                val newPl = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = playlistName,
                    description = "${items.size} ملفات تم اختيارها من الجهاز",
                    items = items,
                    checkpoint = null
                )
                _playlists.value = listOf(newPl) + _playlists.value
            }
        }
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        val newPl = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            items = emptyList(),
            checkpoint = null
        )
        _playlists.value = _playlists.value + newPl
    }

    fun setSleepTimerDuration(minutes: Int, fadeOut: Boolean = _isFadeOutEnabled.value) {
        cancelSleepTimer()
        if (minutes <= 0) return

        _sleepTimerMode.value = SleepTimerMode.DURATION
        _sleepTimerSeconds.value = minutes * 60
        _isFadeOutEnabled.value = fadeOut

        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerSeconds.value > 0) {
                delay(1000)
                _sleepTimerSeconds.value -= 1

                // Gentle audio fade out in the last 20 seconds
                if (_isFadeOutEnabled.value && _sleepTimerSeconds.value in 1..20) {
                    val fadeRatio = _sleepTimerSeconds.value / 20f
                    playback.setVolume(fadeRatio)
                }
            }

            playback.getPlayer()?.pause()
            playback.setVolume(1.0f)
            _sleepTimerMode.value = SleepTimerMode.OFF
            _sleepTimerSeconds.value = 0
        }
    }

    fun setSleepTimerEndOfTrack() {
        cancelSleepTimer()
        _sleepTimerMode.value = SleepTimerMode.END_OF_TRACK
        _sleepTimerSeconds.value = 0
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        playback.setVolume(1.0f)
        _sleepTimerMode.value = SleepTimerMode.OFF
        _sleepTimerSeconds.value = 0
    }

    fun addFiveMinutes() {
        if (_sleepTimerMode.value == SleepTimerMode.DURATION) {
            _sleepTimerSeconds.value += 300
            playback.setVolume(1.0f)
        }
    }

    fun toggleFadeOut() {
        _isFadeOutEnabled.value = !_isFadeOutEnabled.value
    }

    // Backward compatibility helper
    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
        } else {
            setSleepTimerDuration(minutes)
        }
    }

    // Equalizer controls
    val equalizerState: StateFlow<EqualizerState> by lazy {
        playback.equalizerManager?.equalizerState ?: MutableStateFlow(EqualizerState())
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playback.equalizerManager?.setEqualizerEnabled(enabled)
    }

    fun selectEqualizerPreset(presetId: String) {
        playback.equalizerManager?.selectPreset(presetId)
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelDb: Int) {
        playback.equalizerManager?.setBandLevel(bandIndex, levelDb)
    }

    fun setBassBoost(percentage: Int) {
        playback.equalizerManager?.setBassBoost(percentage)
    }

    fun setDownloadUrl(url: String) {
        _downloadUrl.value = url
        if (url.isBlank()) {
            _extractedMetadata.value = null
        } else if (url.startsWith("http")) {
            simulateExtraction(url)
        }
    }

    private fun simulateExtraction(url: String) {
        viewModelScope.launch {
            _isExtracting.value = true
            delay(1500) // Simulate network delay
            
            val platform = when {
                url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube"
                url.contains("instagram.com") -> "Instagram"
                url.contains("tiktok.com") -> "TikTok"
                url.contains("facebook.com") -> "Facebook"
                else -> "Direct Link"
            }

            _extractedMetadata.value = ExtractedMetadata(
                title = "فيديو من $platform - ${System.currentTimeMillis() % 10000}",
                thumbnailUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=400&auto=format&fit=crop&q=60",
                platform = platform,
                qualities = listOf(
                    VideoQuality("1080p (FHD)", "25 MB"),
                    VideoQuality("720p (HD)", "15 MB"),
                    VideoQuality("480p (SD)", "8 MB"),
                    VideoQuality("Audio Only (MP3)", "4 MB")
                )
            )
            _isExtracting.value = false
        }
    }

    fun startDownload(url: String, mediaType: MediaType, quality: String? = null) {
        if (url.isBlank()) return
        
        val metadata = _extractedMetadata.value
        val baseName = metadata?.title ?: "download_${System.currentTimeMillis()}"
        val extension = if (mediaType == MediaType.VIDEO) "mp4" else "mp3"
        val fileName = "$baseName.$extension"
        
        val id = UUID.randomUUID().toString()
        val newItem = DownloadItem(
            id = id,
            url = url,
            fileName = fileName,
            mediaType = mediaType,
            status = DownloadStatus.QUEUED
        )
        
        _activeDownloads.value = _activeDownloads.value + newItem
        _downloadUrl.value = ""
        _extractedMetadata.value = null

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_URL to url,
                    DownloadWorker.KEY_FILE_NAME to fileName,
                    DownloadWorker.KEY_MEDIA_TYPE to mediaType.name
                )
            )
            .addTag(id)
            .build()

        workManager.enqueue(workRequest)

        // Observe progress
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    val progressValue = workInfo.progress.getInt(DownloadWorker.PROGRESS, 0)
                    val status = when (workInfo.state) {
                        WorkInfo.State.RUNNING -> DownloadStatus.DOWNLOADING
                        WorkInfo.State.SUCCEEDED -> DownloadStatus.COMPLETED
                        WorkInfo.State.FAILED -> DownloadStatus.FAILED
                        else -> DownloadStatus.QUEUED
                    }

                    _activeDownloads.value = _activeDownloads.value.map { item ->
                        if (item.id == id) {
                            item.copy(
                                progress = progressValue / 100f,
                                status = status
                            )
                        } else item
                    }
                    
                    // If completed, maybe refresh library or something
                }
            }
        }
    }

    fun removeDownload(id: String) {
        _activeDownloads.value = _activeDownloads.value.filterNot { it.id == id }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
    }
}
