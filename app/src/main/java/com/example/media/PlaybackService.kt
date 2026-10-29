package com.example.media

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return PlaybackManager.getMediaSession()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
