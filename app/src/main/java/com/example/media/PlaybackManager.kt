package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
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
    private var mediaSession: MediaSession? = null
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activePlaylistId = MutableStateFlow<String?>(null)
    val activePlaylistId: StateFlow<String?> = _activePlaylistId.asStateFlow()

    fun init(context: Context) {
        if (exoPlayer != null) return
        val application = context.applicationContext
        appContext = application

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        equalizerManager = AudioEqualizerManager(application)

        exoPlayer = ExoPlayer.Builder(application)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
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

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        _errorMessage.value = error.localizedMessage ?: "Playback error"
                        _isPlaying.value = false
                    }
                })
            }

        exoPlayer?.let { player ->
            mediaSession = MediaSession.Builder(application, player).build()
            equalizerManager?.attachToSession(player.audioSessionId)
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun getMediaSession(): MediaSession? = mediaSession

    fun play(
        item: MediaItemData,
        newQueue: List<MediaItemData>? = null,
        playlistId: String? = null,
        startPositionMs: Long = 0L
    ) {
        val player = exoPlayer ?: return
        val currentQueue = newQueue ?: if (_queue.value.isEmpty()) listOf(item) else _queue.value
        _queue.value = currentQueue
        val index = currentQueue.indexOfFirst { it.id == item.id }.let { if (it >= 0) it else 0 }
        _queueIndex.value = index
        _currentMediaItem.value = item
        _activePlaylistId.value = playlistId
        _errorMessage.value = null

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(item.mediaUri))
            .setMediaId(item.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artistOrDesc)
                    .setArtworkUri(Uri.parse(item.artworkUri))
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()

        // Start foreground service for audio playback
        if (item.mediaType == MediaType.AUDIO) {
            startPlaybackService()
        }
    }

    private fun startPlaybackService() {
        appContext?.let { ctx ->
            try {
                val serviceIntent = Intent(ctx, PlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(serviceIntent)
                } else {
                    ctx.startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Background start restriction fallback
            }
        }
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
        val safePos = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        player.seekTo(safePos)
        _currentPositionMs.value = safePos
    }

    fun skipForward(seconds: Long = 10) {
        val player = exoPlayer ?: return
        val newPos = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration.coerceAtLeast(0L))
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
        val q = _queue.value
        if (q.isEmpty()) return
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

    var onTrackEndedListener: (() -> Boolean)? = null

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
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
                    _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                    _bufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(0L)
                }
                delay(400)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressUpdates()
        equalizerManager?.release()
        equalizerManager = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
