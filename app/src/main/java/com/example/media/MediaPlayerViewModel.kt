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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class AppTab {
    VIDEOS,
    MUSIC,
    PLAYLISTS,
    DOWNLOADER
}

class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playback = PlaybackManager
    private val prefs = application.getSharedPreferences("vibe_player_checkpoints", Context.MODE_PRIVATE)
    private val downloadPrefs = application.getSharedPreferences(DownloadWorker.PREFS_DOWNLOADS, Context.MODE_PRIVATE)
    private val storage = LibraryPersistence(application)
    private val workManager: WorkManager? by lazy {
        runCatching { WorkManager.getInstance(application) }.getOrNull()
    }

    private val _currentTab = MutableStateFlow(AppTab.VIDEOS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _allVideos = MutableStateFlow<List<MediaItemData>>(emptyList())
    val allVideos: StateFlow<List<MediaItemData>> = _allVideos.asStateFlow()

    private val _allAudios = MutableStateFlow<List<MediaItemData>>(emptyList())
    val allAudios: StateFlow<List<MediaItemData>> = _allAudios.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _playbackHistory = MutableStateFlow<List<PlaybackHistoryEntry>>(emptyList())
    val playbackHistory: StateFlow<List<PlaybackHistoryEntry>> = _playbackHistory.asStateFlow()

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

    // Downloader State
    private val _activeDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadItem>> = _activeDownloads.asStateFlow()

    private val _downloadUrl = MutableStateFlow("")
    val downloadUrl: StateFlow<String> = _downloadUrl.asStateFlow()

    private val _isInspectingLink = MutableStateFlow(false)
    val isInspectingLink: StateFlow<Boolean> = _isInspectingLink.asStateFlow()

    private val _linkInspection = MutableStateFlow<LinkInspectionResult?>(null)
    val linkInspection: StateFlow<LinkInspectionResult?> = _linkInspection.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var inspectJob: Job? = null
    private val activeWorkJobs = mutableMapOf<String, Job>()

    init {
        try {
            PlaybackManager.init(application)
        } catch (e: Exception) {
            android.util.Log.e("MediaPlayer", "Playback engine initialization failed", e)
        }
        PlaybackManager.onTrackEndedListener = {
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
                cancelSleepTimer()
                playback.getPlayer()?.pause()
                true
            } else {
                false
            }
        }
        loadPersistedLibrary()
        observePlaybackForCheckpointsAndHistory()
        syncAllFolderPlaylistsInBackground()
    }

    private fun loadPersistedLibrary() {
        val savedDownloads = storage.loadDownloadedMedia()
        val persistedVideos = storage.loadVideos()
        val persistedAudios = storage.loadAudios()

        val mergedVideos = (persistedVideos + savedDownloads.filter { it.mediaType == MediaType.VIDEO })
            .distinctBy { it.id }
        val mergedAudios = (persistedAudios + savedDownloads.filter { it.mediaType == MediaType.AUDIO })
            .distinctBy { it.id }

        _allVideos.value = mergedVideos
        _allAudios.value = mergedAudios
        _playbackHistory.value = storage.loadHistory()
        _activeDownloads.value = storage.loadDownloads()

        val savedPlaylists = storage.loadPlaylists()
        val resolvedPlaylists = if (savedPlaylists.isNotEmpty()) {
            val hasFav = savedPlaylists.any { it.id == "fav_playlist" }
            val hasDl = savedPlaylists.any { it.id == "downloads_playlist" }
            val result = savedPlaylists.map { pl ->
                val cp = pl.checkpoint ?: loadSavedCheckpoint(pl.id)
                if (pl.id == "downloads_playlist") {
                    val mergedDlItems = (savedDownloads + pl.items).distinctBy { it.id }
                    pl.copy(items = mergedDlItems, checkpoint = cp)
                } else {
                    pl.copy(checkpoint = cp)
                }
            }.toMutableList()

            if (!hasFav) {
                result.add(
                    0,
                    Playlist(
                        id = "fav_playlist",
                        name = "المفضلة (Favorites)",
                        description = "المقاطع الصوتية والفيديوهات المفضلة لديك",
                        items = emptyList(),
                        checkpoint = loadSavedCheckpoint("fav_playlist")
                    )
                )
            }
            if (!hasDl) {
                result.add(
                    Playlist(
                        id = "downloads_playlist",
                        name = "التحميلات (Downloads)",
                        description = "الملفات المحمّلة مباشرة على الجهاز",
                        items = savedDownloads,
                        checkpoint = loadSavedCheckpoint("downloads_playlist")
                    )
                )
            }
            result
        } else {
            listOf(
                Playlist(
                    id = "fav_playlist",
                    name = "المفضلة (Favorites)",
                    description = "المقاطع الصوتية والفيديوهات المفضلة لديك",
                    items = emptyList(),
                    checkpoint = loadSavedCheckpoint("fav_playlist")
                ),
                Playlist(
                    id = "downloads_playlist",
                    name = "التحميلات (Downloads)",
                    description = "الملفات المحمّلة مباشرة على الجهاز",
                    items = savedDownloads,
                    checkpoint = loadSavedCheckpoint("downloads_playlist")
                )
            )
        }
        _playlists.value = resolvedPlaylists
        persistPlaylists()
    }

    private fun persistVideos() = storage.saveVideos(_allVideos.value)
    private fun persistAudios() = storage.saveAudios(_allAudios.value)
    private fun persistPlaylists() = storage.savePlaylists(_playlists.value)
    private fun persistDownloads() = storage.saveDownloads(_activeDownloads.value)

    private fun observePlaybackForCheckpointsAndHistory() {
        viewModelScope.launch {
            combine(
                playback.activePlaylistId,
                playback.currentMediaItem,
                playback.currentPositionMs,
                playback.durationMs
            ) { activePlId, currentItem, posMs, durMs ->
                if (currentItem != null) {
                    updateHistoryEntry(currentItem, posMs, durMs)
                    if (activePlId != null && posMs > 0) {
                        saveCheckpoint(
                            playlistId = activePlId,
                            trackId = currentItem.id,
                            trackTitle = currentItem.title,
                            posMs = posMs,
                            durMs = durMs
                        )
                    }
                }
            }.collect {}
        }
    }

    private var lastHistoryPersistMs = 0L
    private var lastCheckpointPersistMs = 0L

    private fun updateHistoryEntry(item: MediaItemData, posMs: Long, durMs: Long) {
        val current = _playbackHistory.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.item.id == item.id }
        val updatedEntry = PlaybackHistoryEntry(
            item = item,
            lastPositionMs = posMs,
            durationMs = durMs,
            playedAt = System.currentTimeMillis()
        )
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        }
        current.add(0, updatedEntry)
        val trimmed = current.take(30)
        _playbackHistory.value = trimmed

        val now = System.currentTimeMillis()
        if (now - lastHistoryPersistMs >= 2000L || existingIdx != 0) {
            lastHistoryPersistMs = now
            storage.saveHistory(trimmed)
        }
    }

    fun resumeFromHistory(entry: PlaybackHistoryEntry) {
        if (entry.item.mediaType == MediaType.VIDEO) {
            _selectedVideo.value = entry.item
            playback.play(
                item = entry.item,
                newQueue = _allVideos.value.ifEmpty { listOf(entry.item) },
                startPositionMs = entry.lastPositionMs
            )
        } else {
            playback.play(
                item = entry.item,
                newQueue = _allAudios.value.ifEmpty { listOf(entry.item) },
                startPositionMs = entry.lastPositionMs
            )
            _isAudioPlayerExpanded.value = true
        }
    }

    fun clearHistory() {
        _playbackHistory.value = emptyList()
        storage.saveHistory(emptyList())
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

        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(checkpoint = newCp) else pl
        }

        val now = System.currentTimeMillis()
        if (now - lastCheckpointPersistMs >= 2000L) {
            lastCheckpointPersistMs = now
            persistPlaylists()
        }

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

    fun moveVideoToBackgroundAudio() {
        _selectedVideo.value = null
    }

    fun hasActiveBackgroundWork(): Boolean {
        val activeDl = _activeDownloads.value.any {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        return playback.hasActivePlayback() || activeDl
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

    fun shufflePlayPlaylist(playlistId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (playlist.items.isEmpty()) return
        val shuffled = playlist.items.shuffled()
        val first = shuffled.first()
        if (first.mediaType == MediaType.VIDEO) {
            _selectedVideo.value = first
        }
        playback.play(
            item = first,
            newQueue = shuffled,
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

    fun updateVideoSubtitleUri(videoId: String, subtitleUri: String, fileName: String? = null) {
        _allVideos.value = _allVideos.value.map {
            if (it.id == videoId) it.copy(subtitleUri = subtitleUri) else it
        }
        _playlists.value = _playlists.value.map { pl ->
            pl.copy(
                items = pl.items.map {
                    if (it.id == videoId) it.copy(subtitleUri = subtitleUri) else it
                }
            )
        }
        if (_selectedVideo.value?.id == videoId) {
            _selectedVideo.value = _selectedVideo.value?.copy(subtitleUri = subtitleUri)
        }
        persistVideos()
        persistPlaylists()
        playback.attachExternalSubtitle(subtitleUri, fileName)
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
            if (favPlaylist.items.any { it.id == newItem.id }) favPlaylist.items else favPlaylist.items + newItem
        } else {
            favPlaylist.items.filterNot { it.id == item.id }
        }
        _playlists.value = _playlists.value.map {
            if (it.id == "fav_playlist") it.copy(items = newFavItems) else it
        }
        persistVideos()
        persistAudios()
        persistPlaylists()
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
            title = title.ifBlank { "بث مباشر" },
            artistOrDesc = artistOrDesc.ifBlank { if (mediaType == MediaType.VIDEO) "بث فيديو مباشر" else "بث صوتي مباشر" },
            mediaUri = uri,
            artworkUri = artworkUri,
            durationMs = 0L,
            mediaType = mediaType,
            category = "روابط البث"
        )
        if (mediaType == MediaType.VIDEO) {
            _allVideos.value = listOf(newItem) + _allVideos.value
            persistVideos()
            playVideo(newItem)
        } else {
            _allAudios.value = listOf(newItem) + _allAudios.value
            persistAudios()
            playAudio(newItem)
        }
    }

    fun importLocalMedia(uri: Uri, context: Context, isVideo: Boolean) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        var fileName = "ملف محلي"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: "ملف محلي"
                    }
                }
            }
        } catch (_: Exception) {
            fileName = uri.lastPathSegment ?: "ملف محلي"
        }

        val newItem = MediaItemData(
            id = UUID.randomUUID().toString(),
            title = fileName,
            artistOrDesc = if (isVideo) "فيديو من الجهاز" else "صوت من الجهاز",
            mediaUri = uri.toString(),
            artworkUri = "",
            durationMs = 0L,
            mediaType = if (isVideo) MediaType.VIDEO else MediaType.AUDIO,
            category = "ملفات الجهاز",
            isLocal = true
        )

        if (isVideo) {
            _allVideos.value = listOf(newItem) + _allVideos.value
            persistVideos()
            playVideo(newItem)
        } else {
            _allAudios.value = listOf(newItem) + _allAudios.value
            persistAudios()
            playAudio(newItem)
        }
    }

    private fun scanTreeFolderItems(treeUri: Uri, context: Context): Pair<String, List<MediaItemData>> {
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

                    val isAudio = mime.startsWith("audio/") ||
                        name.endsWith(".mp3", true) || name.endsWith(".m4a", true) ||
                        name.endsWith(".wav", true) || name.endsWith(".aac", true) ||
                        name.endsWith(".flac", true) || name.endsWith(".ogg", true)
                    val isVideo = mime.startsWith("video/") ||
                        name.endsWith(".mp4", true) || name.endsWith(".mkv", true) ||
                        name.endsWith(".mov", true) || name.endsWith(".webm", true)

                    if (isAudio || isVideo) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                        val type = if (isVideo) MediaType.VIDEO else MediaType.AUDIO
                        items.add(
                            MediaItemData(
                                id = "tree_${fileUri.toString().hashCode()}",
                                title = name,
                                artistOrDesc = if (isVideo) "فيديو من المجلد" else "صوت من المجلد",
                                mediaUri = fileUri.toString(),
                                artworkUri = "",
                                durationMs = 0L,
                                mediaType = type,
                                category = "مجلدات محلية",
                                isLocal = true
                            )
                        )
                    }
                }
            }
            folderName = treeUri.lastPathSegment?.substringAfterLast(":") ?: "مجلد الهاتف"
        } catch (_: Exception) {
        }
        return folderName to items.sortedBy { it.title }
    }

    fun importFolderAsPlaylist(treeUri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            val (folderName, items) = scanTreeFolderItems(treeUri, context)

            withContext(Dispatchers.Main) {
                val newPl = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = "مجلد: $folderName",
                    description = "${items.size} ملفات وسائط مستوردة",
                    items = items,
                    checkpoint = null,
                    sourceTreeUri = treeUri.toString()
                )
                _playlists.value = listOf(newPl) + _playlists.value
                persistPlaylists()
            }
        }
    }

    fun syncFolderPlaylist(playlistId: String) {
        val pl = _playlists.value.find { it.id == playlistId } ?: return
        val treeStr = pl.sourceTreeUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val (_, scanned) = scanTreeFolderItems(Uri.parse(treeStr), getApplication())
            if (scanned.isEmpty() && pl.items.isNotEmpty()) return@launch
            withContext(Dispatchers.Main) {
                _playlists.value = _playlists.value.map {
                    if (it.id == playlistId) {
                        it.copy(
                            items = scanned,
                            description = "${scanned.size} ملفات وسائط متزامنة"
                        )
                    } else it
                }
                persistPlaylists()
            }
        }
    }

    private fun syncAllFolderPlaylistsInBackground() {
        val folderPlaylists = _playlists.value.filter { !it.sourceTreeUri.isNullOrBlank() }
        folderPlaylists.forEach { syncFolderPlaylist(it.id) }
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
                } catch (_: Exception) {
                }

                var fileName = "ملف وسائط"
                var mimeType: String? = null
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) {
                                fileName = it.getString(nameIdx) ?: "ملف وسائط"
                            }
                        }
                    }
                    mimeType = context.contentResolver.getType(uri)
                } catch (_: Exception) {
                    fileName = uri.lastPathSegment ?: "ملف وسائط"
                }

                val isVideo = mimeType?.startsWith("video/") == true ||
                    fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) ||
                    fileName.endsWith(".webm", true)
                val type = if (isVideo) MediaType.VIDEO else MediaType.AUDIO

                items.add(
                    MediaItemData(
                        id = UUID.randomUUID().toString(),
                        title = fileName,
                        artistOrDesc = if (isVideo) "فيديو من الجهاز" else "صوت من الجهاز",
                        mediaUri = uri.toString(),
                        artworkUri = "",
                        durationMs = 0L,
                        mediaType = type,
                        category = "ملفات محددة",
                        isLocal = true
                    )
                )
            }

            withContext(Dispatchers.Main) {
                val playlistName = customName?.ifBlank { null } ?: "ملفات الجهاز (${items.size})"
                val newPl = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = playlistName,
                    description = "${items.size} ملفات تم اختيارها",
                    items = items,
                    checkpoint = null
                )
                _playlists.value = listOf(newPl) + _playlists.value
                persistPlaylists()
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
        persistPlaylists()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (newName.isBlank()) return
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(name = newName.trim()) else pl
        }
        persistPlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        if (playlistId == "fav_playlist" || playlistId == "downloads_playlist") return
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        persistPlaylists()
    }

    fun addItemsToPlaylist(playlistId: String, items: List<MediaItemData>) {
        if (items.isEmpty()) return
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                val existingIds = pl.items.map { it.id }.toSet()
                val toAdd = items.filterNot { it.id in existingIds }
                pl.copy(items = pl.items + toAdd)
            } else pl
        }
        persistPlaylists()
    }

    fun removeItemFromPlaylist(playlistId: String, itemId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(items = pl.items.filterNot { it.id == itemId })
            } else pl
        }
        persistPlaylists()
    }

    fun saveQueueAsPlaylist(name: String) {
        val currentQueue = playback.queue.value
        if (name.isBlank() || currentQueue.isEmpty()) return
        val newPl = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = "محفوظة من طابور التشغيل (${currentQueue.size} مقاطع)",
            items = currentQueue
        )
        _playlists.value = _playlists.value + newPl
        persistPlaylists()
    }

    // Sleep Timer
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

    fun setPreampGain(percentage: Int) {
        playback.equalizerManager?.setPreampGain(percentage)
    }

    // Downloader Inspection & WorkManager Actions
    fun setDownloadUrl(url: String) {
        _downloadUrl.value = url
        inspectJob?.cancel()

        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _isInspectingLink.value = false
            _linkInspection.value = null
            return
        }

        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            _isInspectingLink.value = false
            _linkInspection.value = null
            return
        }

        val platform = detectExternalPlatform(trimmed)
        if (platform != null) {
            _isInspectingLink.value = false
            _linkInspection.value = LinkInspectionResult(
                url = trimmed,
                isSupportedPlatformPage = true,
                platformName = platform,
                suggestedFileName = "",
                mimeType = null,
                contentLengthBytes = -1L,
                supportsRange = false,
                detectedMediaType = MediaType.VIDEO
            )
            return
        }

        inspectJob = viewModelScope.launch {
            delay(350)
            inspectDirectUrl(trimmed)
        }
    }

    private fun detectExternalPlatform(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("facebook.com") || lower.contains("fb.watch") -> "Facebook"
            lower.contains("twitter.com") || lower.contains("x.com") -> "X / Twitter"
            else -> null
        }
    }

    private suspend fun inspectDirectUrl(url: String) {
        _isInspectingLink.value = true
        val result = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val headResp = client.newCall(
                    Request.Builder().url(url).head().header("User-Agent", "MediaPlayerAndroid/1.0").build()
                ).execute()

                var mime = headResp.header("Content-Type")?.substringBefore(";")?.trim()
                var length = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
                var acceptRanges = headResp.header("Accept-Ranges")?.contains("bytes", true) == true
                var disposition = headResp.header("Content-Disposition")
                val headCode = headResp.code
                headResp.close()

                if (headCode == 405 || headCode == 403 || (mime == null && length <= 0)) {
                    val rangeResp = client.newCall(
                        Request.Builder()
                            .url(url)
                            .header("Range", "bytes=0-0")
                            .header("User-Agent", "MediaPlayerAndroid/1.0")
                            .build()
                    ).execute()
                    mime = rangeResp.header("Content-Type")?.substringBefore(";")?.trim() ?: mime
                    val contentRange = rangeResp.header("Content-Range")
                    val rangeTotal = contentRange?.substringAfterLast("/")?.toLongOrNull()
                    length = rangeTotal ?: rangeResp.header("Content-Length")?.toLongOrNull() ?: length
                    acceptRanges = acceptRanges || rangeResp.code == 206 ||
                        rangeResp.header("Accept-Ranges")?.contains("bytes", true) == true
                    disposition = disposition ?: rangeResp.header("Content-Disposition")
                    rangeResp.close()
                }

                val isHtml = mime?.startsWith("text/html", true) == true
                val fileName = extractFileName(url, disposition, mime)
                val isAudio = mime?.startsWith("audio/", true) == true ||
                    fileName.endsWith(".mp3", true) || fileName.endsWith(".m4a", true) ||
                    fileName.endsWith(".aac", true) || fileName.endsWith(".wav", true) ||
                    fileName.endsWith(".ogg", true)

                LinkInspectionResult(
                    url = url,
                    isSupportedPlatformPage = false,
                    platformName = if (isHtml) "صفحة ويب" else "رابط مباشر",
                    suggestedFileName = fileName,
                    mimeType = mime,
                    contentLengthBytes = length,
                    supportsRange = acceptRanges,
                    detectedMediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO,
                    errorNote = if (isHtml) "الرابط يشير إلى صفحة HTML وليس ملف فيديو أو صوت مباشر." else null
                )
            } catch (_: Exception) {
                val fallbackName = extractFileName(url, null, null)
                val isAudio = fallbackName.endsWith(".mp3", true) || fallbackName.endsWith(".m4a", true)
                LinkInspectionResult(
                    url = url,
                    isSupportedPlatformPage = false,
                    platformName = "رابط مباشر",
                    suggestedFileName = fallbackName,
                    mimeType = null,
                    contentLengthBytes = -1L,
                    supportsRange = true,
                    detectedMediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO,
                    errorNote = null
                )
            }
        }
        _linkInspection.value = result
        _isInspectingLink.value = false
    }

    private fun extractFileName(url: String, contentDisposition: String?, mime: String?): String {
        val fromDisposition = contentDisposition
            ?.let { Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?").find(it)?.groupValues?.getOrNull(1) }
            ?.trim()
        if (!fromDisposition.isNullOrBlank()) return fromDisposition

        val pathName = Uri.parse(url).lastPathSegment?.substringBefore("?")?.trim()
        if (!pathName.isNullOrBlank() && pathName.contains(".")) return pathName

        val ext = when {
            mime?.contains("mpeg", true) == true -> "mp3"
            mime?.contains("mp4", true) == true -> "mp4"
            mime?.contains("webm", true) == true -> "webm"
            mime?.contains("aac", true) == true -> "aac"
            mime?.contains("wav", true) == true -> "wav"
            else -> "mp4"
        }
        return "media_${System.currentTimeMillis() % 10000}.$ext"
    }

    fun startDownload(url: String, mediaType: MediaType, customFileName: String? = null) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true)) return
        if (detectExternalPlatform(cleanUrl) != null) return

        val inspection = _linkInspection.value?.takeIf { it.url == cleanUrl }
        val defaultExt = if (mediaType == MediaType.VIDEO) "mp4" else "mp3"
        val suggested = customFileName?.takeIf { it.isNotBlank() }
            ?: inspection?.suggestedFileName?.takeIf { it.isNotBlank() }
            ?: "download_${System.currentTimeMillis() % 10000}.$defaultExt"

        val finalName = if (suggested.contains(".")) suggested else "$suggested.$defaultExt"
        val id = UUID.randomUUID().toString()

        val newItem = DownloadItem(
            id = id,
            url = cleanUrl,
            fileName = finalName,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = inspection?.contentLengthBytes ?: -1L,
            speedBytesPerSec = 0L,
            status = DownloadStatus.QUEUED,
            mediaType = mediaType,
            supportsResume = inspection?.supportsRange ?: true
        )

        _activeDownloads.value = listOf(newItem) + _activeDownloads.value
        persistDownloads()
        _downloadUrl.value = ""
        _linkInspection.value = null

        enqueueDownloadWorker(id, cleanUrl, finalName, mediaType)
    }

    fun pauseDownload(id: String) {
        val item = _activeDownloads.value.find { it.id == id } ?: return
        if (item.status != DownloadStatus.DOWNLOADING && item.status != DownloadStatus.QUEUED) return
        downloadPrefs.edit().putBoolean("${DownloadWorker.PAUSED_FLAG_PREFIX}$id", true).apply()
        workManager?.cancelUniqueWork("dl_$id")
        activeWorkJobs.remove(id)?.cancel()
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.id == id) it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L) else it
        }
        persistDownloads()
    }

    fun resumeDownload(id: String) {
        val item = _activeDownloads.value.find { it.id == id } ?: return
        if (item.status != DownloadStatus.PAUSED && item.status != DownloadStatus.FAILED) return
        downloadPrefs.edit().remove("${DownloadWorker.PAUSED_FLAG_PREFIX}$id").apply()
        _activeDownloads.value = _activeDownloads.value.map {
            if (it.id == id) it.copy(status = DownloadStatus.QUEUED, errorMessage = null) else it
        }
        persistDownloads()
        enqueueDownloadWorker(item.id, item.url, item.fileName, item.mediaType)
    }

    fun removeDownload(id: String) {
        downloadPrefs.edit().remove("${DownloadWorker.PAUSED_FLAG_PREFIX}$id").apply()
        workManager?.cancelUniqueWork("dl_$id")
        activeWorkJobs.remove(id)?.cancel()
        try {
            File(getApplication<Application>().cacheDir, "partial_downloads/$id.part").delete()
        } catch (_: Exception) {
        }
        _activeDownloads.value = _activeDownloads.value.filterNot { it.id == id }
        persistDownloads()
    }

    fun playDownloadedItem(item: DownloadItem) {
        val uri = item.savedUri ?: return
        val mediaItem = MediaItemData(
            id = "dl_${item.id}",
            title = item.fileName,
            artistOrDesc = "محمّل على الجهاز",
            mediaUri = uri,
            artworkUri = "",
            durationMs = 0L,
            mediaType = item.mediaType,
            category = "التحميلات",
            isLocal = true
        )
        if (item.mediaType == MediaType.VIDEO) {
            _selectedVideo.value = mediaItem
            playback.play(mediaItem, _allVideos.value.ifEmpty { listOf(mediaItem) })
        } else {
            playback.play(mediaItem, _allAudios.value.ifEmpty { listOf(mediaItem) })
            _isAudioPlayerExpanded.value = true
        }
    }

    private fun enqueueDownloadWorker(
        downloadId: String,
        url: String,
        fileName: String,
        mediaType: MediaType
    ) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                    DownloadWorker.KEY_URL to url,
                    DownloadWorker.KEY_FILE_NAME to fileName,
                    DownloadWorker.KEY_MEDIA_TYPE to mediaType.name
                )
            )
            .addTag(downloadId)
            .build()

        val wm = workManager ?: return
        wm.enqueueUniqueWork(
            "dl_$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        activeWorkJobs.remove(downloadId)?.cancel()
        activeWorkJobs[downloadId] = viewModelScope.launch {
            wm.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo == null) return@collect

                val progressValue = workInfo.progress.getInt(DownloadWorker.PROGRESS, -1)
                val bytesRead = workInfo.progress.getLong(DownloadWorker.KEY_BYTES_READ, -1L)
                val totalBytes = workInfo.progress.getLong(DownloadWorker.KEY_TOTAL_BYTES, -1L)
                val speedBps = workInfo.progress.getLong(DownloadWorker.KEY_SPEED_BPS, 0L)

                val currentItem = _activeDownloads.value.find { it.id == downloadId } ?: return@collect

                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        if (currentItem.status != DownloadStatus.PAUSED) {
                            updateDownloadItem(downloadId) {
                                it.copy(status = DownloadStatus.QUEUED)
                            }
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        updateDownloadItem(downloadId) {
                            it.copy(
                                status = DownloadStatus.DOWNLOADING,
                                progress = if (progressValue >= 0) progressValue / 100f else it.progress,
                                bytesDownloaded = if (bytesRead >= 0) bytesRead else it.bytesDownloaded,
                                totalBytes = if (totalBytes > 0) totalBytes else it.totalBytes,
                                speedBytesPerSec = speedBps,
                                errorMessage = null
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val savedUri = workInfo.outputData.getString(DownloadWorker.KEY_SAVED_URI)
                        val finalBytes = workInfo.outputData.getLong(DownloadWorker.KEY_BYTES_READ, currentItem.bytesDownloaded)
                        updateDownloadItem(downloadId) {
                            it.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 1f,
                                bytesDownloaded = finalBytes,
                                totalBytes = if (finalBytes > 0) finalBytes else it.totalBytes,
                                speedBytesPerSec = 0L,
                                savedUri = savedUri,
                                errorMessage = null
                            )
                        }
                        persistDownloads()
                        if (!savedUri.isNullOrBlank()) {
                            addCompletedDownloadToLibrary(downloadId, fileName, savedUri, mediaType)
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val err = workInfo.outputData.getString(DownloadWorker.KEY_ERROR_MESSAGE)
                        if (err == "PAUSED" || downloadPrefs.getBoolean("${DownloadWorker.PAUSED_FLAG_PREFIX}$downloadId", false)) {
                            updateDownloadItem(downloadId) {
                                it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
                            }
                        } else {
                            updateDownloadItem(downloadId) {
                                it.copy(
                                    status = DownloadStatus.FAILED,
                                    speedBytesPerSec = 0L,
                                    errorMessage = err ?: "تعذر إكمال التحميل"
                                )
                            }
                        }
                        persistDownloads()
                    }
                    WorkInfo.State.CANCELLED -> {
                        if (downloadPrefs.getBoolean("${DownloadWorker.PAUSED_FLAG_PREFIX}$downloadId", false)) {
                            updateDownloadItem(downloadId) {
                                it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
                            }
                            persistDownloads()
                        }
                    }
                }
            }
        }
    }

    private fun updateDownloadItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _activeDownloads.value = _activeDownloads.value.map { item ->
            if (item.id == id) transform(item) else item
        }
    }

    private fun addCompletedDownloadToLibrary(
        downloadId: String,
        fileName: String,
        savedUri: String,
        mediaType: MediaType
    ) {
        val mediaId = "dl_$downloadId"
        val newItem = MediaItemData(
            id = mediaId,
            title = fileName,
            artistOrDesc = "محمّل • مجلد التنزيلات",
            mediaUri = savedUri,
            artworkUri = "",
            durationMs = 0L,
            mediaType = mediaType,
            category = "التحميلات",
            isLocal = true
        )

        if (mediaType == MediaType.VIDEO) {
            if (_allVideos.value.none { it.id == mediaId }) {
                _allVideos.value = listOf(newItem) + _allVideos.value
                persistVideos()
            }
        } else {
            if (_allAudios.value.none { it.id == mediaId }) {
                _allAudios.value = listOf(newItem) + _allAudios.value
                persistAudios()
            }
        }

        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == "downloads_playlist" && pl.items.none { it.id == mediaId }) {
                pl.copy(items = listOf(newItem) + pl.items)
            } else pl
        }
        persistPlaylists()

        val savedList = storage.loadDownloadedMedia()
        if (savedList.none { it.id == mediaId }) {
            storage.saveDownloadedMedia(listOf(newItem) + savedList)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        inspectJob?.cancel()
        activeWorkJobs.values.forEach { it.cancel() }
    }
}
