package com.example.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val MEDIA_CHANNEL_ID = "media_playback_channel"
        const val MEDIA_NOTIFICATION_ID = 2001
        const val ACTION_REWIND_10 = "com.example.media.ACTION_REWIND_10"
        const val ACTION_FORWARD_10 = "com.example.media.ACTION_FORWARD_10"
        const val EXTRA_OPEN_AUDIO_PLAYER = "extra_open_audio_player"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_CHANNEL_ID)
            .setChannelName(R.string.media_notification_channel_name)
            .setNotificationId(MEDIA_NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_media)
        setMediaNotificationProvider(notificationProvider)

        val player = PlaybackManager.getOrCreatePlayer(applicationContext) ?: return

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_AUDIO_PLAYER, true)
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            200,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindCommand = SessionCommand(ACTION_REWIND_10, Bundle.EMPTY)
        val forwardCommand = SessionCommand(ACTION_FORWARD_10, Bundle.EMPTY)

        val rewindButton = CommandButton.Builder(CommandButton.ICON_REWIND)
            .setDisplayName("تأخير 10 ثوانٍ")
            .setCustomIconResId(R.drawable.ic_rewind_10)
            .setSessionCommand(rewindCommand)
            .build()

        val forwardButton = CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("تقديم 10 ثوانٍ")
            .setCustomIconResId(R.drawable.ic_forward_10)
            .setSessionCommand(forwardCommand)
            .build()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_REWIND_10 -> PlaybackManager.skipBackward(10)
                    ACTION_FORWARD_10 -> PlaybackManager.skipForward(10)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .setCustomLayout(listOf(rewindButton, forwardButton))
            .build()

        mediaSession?.let { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
