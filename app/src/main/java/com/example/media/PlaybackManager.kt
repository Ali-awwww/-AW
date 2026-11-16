package com.example.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlaybackManager {

    private var exoPlayer: ExoPlayer? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var appContext: Context? = null
    var equalizerManager: AudioEqualizerManager? = null
        private set
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _currentMediaItem = MutableStateFlow<MediaItemData?>(null)
    val currentMediaItem: StateFlow<MediaItemData?> = _currentMediaItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatState.OFF)
    val repeatMode: StateFlow<RepeatState> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItemData>>(emptyList())
    val queue: StateFlow<List<MediaItemData>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _abLoopState = MutableStateFlow(ABLoopState())
    val abLoopState: StateFlow<ABLoopState> = _abLoopState.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackOption>>(emptyList())
    val audioTracks: StateFlow<List<TrackOption>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackOption>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackOption>> = _subtitleTracks.asStateFlow()

    private var latestTracks: Tracks = Tracks.EMPTY

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activePlaylistId = MutableStateFlow<String?>(null)
    val activePlaylistId: StateFlow<String?> = _activePlaylistId.asStateFlow()

    var onTrackEndedListener: (() -> Boolean)? = null

    fun init(context: Context) {
        val application = context.applicationContext
        appContext = application
        getOrCreatePlayer(application)
        ensureMediaControllerConnected(application)
    }

    @Synchronized
    fun getOrCreatePlayer(context: Context): ExoPlayer? {
        val application = context.applicationContext
        appContext = application
        if (equalizerManager == null) {
            equalizerManager = AudioEqualizerManager(application)
        }
        if (exoPlayer != null) return exoPlayer

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(application)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        equalizerManager?.attachToSession(audioSessionId)
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId ?: return
                        val q = _queue.value
                        val idx = q.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            _queueIndex.value = idx
                            _currentMediaItem.value = q[idx]
                        }
                        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                            clearABLoop()
                        }
                        _durationMs.value = duration.coerceAtLeast(0L)
                        _currentPositionMs.value = currentPosition.coerceAtLeast(0L)
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        latestTracks = tracks
                        parseTracks(tracks)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _durationMs.value = duration.coerceAtLeast(0L)
                                _bufferedPositionMs.value = bufferedPosition.coerceAtLeast(0L)
                                equalizerManager?.attachToSession(audioSessionId)
                            }
                            Player.STATE_ENDED -> {
                                handleTrackEnded()
                            }
                            else -> Unit
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _errorMessage.value = error.localizedMessage ?: "تعذر تشغيل المقطع"
                        _isPlaying.value = false
                    }
                })
            }

        exoPlayer?.let { player ->
            equalizerManager?.attachToSession(player.audioSessionId)
        }
        return exoPlayer
    }

    private fun parseTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackOption>()
        val subList = mutableListOf<TrackOption>()

        tracks.groups.forEachIndexed { groupIdx, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: "افتراضي"
                    val label = format.label?.takeIf { it.isNotBlank() } ?: "صوت ${audioList.size + 1} ($lang)"
                    audioList.add(
                        TrackOption(
                            groupIndex = groupIdx,
                            trackIndex = i,
                            label = label,
                            language = lang,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language?.takeIf { it.isNotBlank() && it != "und" } ?: "خارجي"
                    val label = format.label?.takeIf { it.isNotBlank() } ?: "ترجمة ${subList.size + 1} ($lang)"
                    subList.add(
                        TrackOption(
                            groupIndex = groupIdx,
                            trackIndex = i,
                            label = label,
                            language = lang,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        _audioTracks.value = audioList
        _subtitleTracks.value = subList
    }

    private fun ensureMediaControllerConnected(context: Context) {
        if (mediaController != null || controllerFuture != null) return
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        mediaController = future.get()
                    } catch (_: Exception) {
                        controllerFuture = null
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } catch (_: Exception) {
            controllerFuture = null
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun hasActivePlayback(): Boolean {
        val player = exoPlayer ?: return false
        return (player.isPlaying || player.playWhenReady) && _currentMediaItem.value != null
    }

    fun play(
        item: MediaItemData,
        newQueue: List<MediaItemData>? = null,
        playlistId: String? = null,
        startPositionMs: Long = 0L
    ) {
        val ctx = appContext
        val player = exoPlayer ?: (ctx?.let { getOrCreatePlayer(it) }) ?: return
        ctx?.let { ensureMediaControllerConnected(it) }

        val currentQueue = when {
            newQueue != null && newQueue.isNotEmpty() -> {
                if (newQueue.any { it.id == item.id }) newQueue else listOf(item) + newQueue
            }
            _queue.value.isNotEmpty() && _queue.value.any { it.id == item.id } -> _queue.value
            else -> listOf(item)
        }

        _queue.value = currentQueue
        val index = currentQueue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        _queueIndex.value = index
        _currentMediaItem.value = item
        _activePlaylistId.value = playlistId
        _errorMessage.value = null
        clearABLoop()

        val mediaItems = currentQueue.map { toMedia3Item(it) }
        player.setMediaItems(mediaItems, index, startPositionMs)
        player.prepare()
        player.play()
    }

    // Dynamic Queue Operations (Musicolet / Poweramp style)
    fun addToQueueNext(item: MediaItemData) {
        val player = exoPlayer
        val currentQueue = _queue.value.toMutableList()
        if (currentQueue.isEmpty() || player == null) {
            play(item)
            return
        }
        val insertIdx = (_queueIndex.value + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIdx, item)
        _queue.value = currentQueue
        player.addMediaItem(insertIdx, toMedia3Item(item))
    }

    fun addToQueueEnd(item: MediaItemData) {
        val player = exoPlayer
        val currentQueue = _queue.value.toMutableList()
        if (currentQueue.isEmpty() || player == null) {
            play(item)
            return
        }
        currentQueue.add(item)
        _queue.value = currentQueue
        player.addMediaItem(toMedia3Item(item))
    }

    fun playQueueItemAt(index: Int) {
        val q = _queue.value
        if (index !in q.indices) return
        val player = exoPlayer ?: return
        _queueIndex.value = index
        _currentMediaItem.value = q[index]
        player.seekTo(index, 0L)
        player.play()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val q = _queue.value.toMutableList()
        if (fromIndex !in q.indices || toIndex !in q.indices || fromIndex == toIndex) return
        val item = q.removeAt(fromIndex)
        q.add(toIndex, item)
        _queue.value = q

        val curIdx = _queueIndex.value
        _queueIndex.value = when {
            curIdx == fromIndex -> toIndex
            fromIndex < curIdx && toIndex >= curIdx -> curIdx - 1
            fromIndex > curIdx && toIndex <= curIdx -> curIdx + 1
            else -> curIdx
        }
        exoPlayer?.moveMediaItem(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index !in q.indices || q.size <= 1) return
        q.removeAt(index)
        _queue.value = q

        val curIdx = _queueIndex.value
        if (index < curIdx) {
            _queueIndex.value = curIdx - 1
        } else if (index == curIdx) {
            val safeIdx = curIdx.coerceAtMost(q.lastIndex)
            _queueIndex.value = safeIdx
            _currentMediaItem.value = q[safeIdx]
        }
        exoPlayer?.removeMediaItem(index)
    }

    fun clearUpcomingQueue() {
        val q = _queue.value
        val curIdx = _queueIndex.value
        if (curIdx !in q.indices) return
        val current = q[curIdx]
        val player = exoPlayer
        if (player != null && q.size > 1) {
            for (i in q.lastIndex downTo 0) {
                if (i != curIdx) {
                    player.removeMediaItem(i)
                }
            }
        }
        _queue.value = listOf(current)
        _queueIndex.value = 0
    }

    // A-B Repeat Loop
    fun togglePointA() {
        val pos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _currentPositionMs.value
        val cur = _abLoopState.value
        if (cur.pointAMs != null && cur.isActive) {
            clearABLoop()
        } else {
            _abLoopState.value = ABLoopState(pointAMs = pos, pointBMs = null, isActive = false)
        }
    }

    fun togglePointB() {
        val pos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _currentPositionMs.value
        val cur = _abLoopState.value
        val start = cur.pointAMs ?: 0L
        if (pos > start + 300L) {
            _abLoopState.value = ABLoopState(pointAMs = start, pointBMs = pos, isActive = true)
            exoPlayer?.seekTo(start)
        }
    }

    fun clearABLoop() {
        _abLoopState.value = ABLoopState()
    }

    // Subtitles & Audio Tracks
    fun selectAudioTrack(option: TrackOption) {
        val player = exoPlayer ?: return
        val group = latestTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun selectSubtitleTrack(option: TrackOption?) {
        val player = exoPlayer ?: return
        if (option == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            return
        }
        val group = latestTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun attachExternalSubtitle(subtitleUri: String, fileName: String? = null) {
        val player = exoPlayer ?: return
        val item = _currentMediaItem.value ?: return
        val updatedItem = item.copy(subtitleUri = subtitleUri)
        _currentMediaItem.value = updatedItem

        val idx = _queueIndex.value
        if (idx in _queue.value.indices) {
            val mutable = _queue.value.toMutableList()
            mutable[idx] = updatedItem
            _queue.value = mutable
        }

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying || player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        val mediaItem = toMedia3Item(updatedItem, fileName)
        if (idx in 0 until player.mediaItemCount) {
            player.replaceMediaItem(idx, mediaItem)
        } else {
            player.setMediaItem(mediaItem, currentPos)
        }
        player.seekTo(currentPos)
        player.prepare()
        if (wasPlaying) player.play()
    }

    private fun toMedia3Item(item: MediaItemData, subtitleLabel: String? = null): MediaItem {
        val artworkParsed = item.artworkUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistOrDesc)
            .setDisplayTitle(item.title)
            .setSubtitle(item.category)
            .setArtworkUri(artworkParsed)
            .setMediaType(
                if (item.mediaType == MediaType.VIDEO) MediaMetadata.MEDIA_TYPE_VIDEO
                else MediaMetadata.MEDIA_TYPE_MUSIC
            )
            .build()

        val builder = MediaItem.Builder()
            .setUri(Uri.parse(item.mediaUri))
            .setMediaId(item.id)
            .setMediaMetadata(metadata)

        val subUriStr = item.subtitleUri
        if (!subUriStr.isNullOrBlank()) {
            val mime = when {
                subUriStr.contains(".vtt", ignoreCase = true) ||
                    subtitleLabel?.endsWith(".vtt", ignoreCase = true) == true -> MimeTypes.TEXT_VTT
                subUriStr.contains(".ass", ignoreCase = true) ||
                    subUriStr.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUriStr))
                .setMimeType(mime)
                .setLabel(subtitleLabel ?: "ترجمة خارجية")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }

        return builder.build()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val maxDur = _durationMs.value.coerceAtLeast(1L)
        val safePos = positionMs.coerceIn(0L, maxDur)
        player.seekTo(safePos)
        _currentPositionMs.value = safePos
    }

    fun skipForward(seconds: Long = 10) {
        val player = exoPlayer ?: return
        val dur = player.duration.takeIf { it > 0 } ?: _durationMs.value
        val maxTarget = if (dur > 0) dur else Long.MAX_VALUE
        val newPos = (player.currentPosition + seconds * 1000).coerceAtMost(maxTarget)
        player.seekTo(newPos)
        _currentPositionMs.value = newPos
    }

    fun skipBackward(seconds: Long = 10) {
        val player = exoPlayer ?: return
        val newPos = (player.currentPosition - seconds * 1000).coerceAtLeast(0L)
        player.seekTo(newPos)
        _currentPositionMs.value = newPos
    }

    fun next() {
        val player = exoPlayer
        val q = _queue.value
        if (q.isEmpty()) return
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
            return
        }
        val currentIdx = _queueIndex.value
        val nextIdx = if (_isShuffle.value && q.size > 1) {
            (q.indices - currentIdx).random()
        } else {
            (currentIdx + 1) % q.size
        }
        play(q[nextIdx], playlistId = _activePlaylistId.value)
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return
        val player = exoPlayer
        if (player != null && player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        if (player != null && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
            return
        }
        val currentIdx = _queueIndex.value
        val prevIdx = if (currentIdx - 1 < 0) q.size - 1 else currentIdx - 1
        play(q[prevIdx], playlistId = _activePlaylistId.value)
    }

    fun setPlaybackSpeed(speed: Float) {
        val player = exoPlayer ?: return
        _playbackSpeed.value = speed
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun cycleRepeatMode() {
        val player = exoPlayer ?: return
        val nextMode = when (_repeatMode.value) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
        _repeatMode.value = nextMode
        player.repeatMode = when (nextMode) {
            RepeatState.OFF -> Player.REPEAT_MODE_OFF
            RepeatState.ALL -> Player.REPEAT_MODE_ALL
            RepeatState.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun toggleShuffle() {
        val player = exoPlayer ?: return
        val shuffle = !_isShuffle.value
        _isShuffle.value = shuffle
        player.shuffleModeEnabled = shuffle
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun handleTrackEnded() {
        if (onTrackEndedListener?.invoke() == true) {
            _isPlaying.value = false
            stopProgressUpdates()
            return
        }
        when (_repeatMode.value) {
            RepeatState.ONE -> {
                seekTo(0L)
                exoPlayer?.play()
            }
            RepeatState.ALL -> {
                next()
            }
            RepeatState.OFF -> {
                val q = _queue.value
                val idx = _queueIndex.value
                if (idx < q.size - 1) {
                    next()
                } else {
                    _isPlaying.value = false
                    stopProgressUpdates()
                }
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                    _bufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(0L)

                    val loop = _abLoopState.value
                    if (loop.isActive && loop.pointAMs != null && loop.pointBMs != null && pos >= loop.pointBMs) {
                        player.seekTo(loop.pointAMs)
                        _currentPositionMs.value = loop.pointAMs
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdates()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        equalizerManager?.release()
        equalizerManager = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
