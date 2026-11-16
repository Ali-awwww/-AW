package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.media.AudioEqualizerManager
import com.example.media.DownloadItem
import com.example.media.DownloadStatus
import com.example.media.LibraryPersistence
import com.example.media.MediaItemData
import com.example.media.MediaPlayerViewModel
import com.example.media.MediaType
import com.example.media.Playlist
import com.example.media.PlaylistCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistPersistenceAndQueueTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("media_player_storage", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.getSharedPreferences("vibe_player_checkpoints", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app.getSharedPreferences("vibe_player_eq", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `playlists and media items survive viewmodel restart`() {
        val vm1 = MediaPlayerViewModel(app)
        vm1.createPlaylist("مختارات السفر", "صوتيات")
        val created = vm1.playlists.value.first { it.name == "مختارات السفر" }

        val sampleTrack = MediaItemData(
            id = "track_1",
            title = "Track One",
            artistOrDesc = "Artist",
            mediaUri = "https://example.com/track.mp3",
            artworkUri = "",
            durationMs = 180_000L,
            mediaType = MediaType.AUDIO,
            subtitleUri = "content://subs/test.srt"
        )
        vm1.addItemsToPlaylist(created.id, listOf(sampleTrack))
        vm1.renamePlaylist(created.id, "مختاراتي المحفوظة")

        // Simulate app restart by constructing a new ViewModel
        val vm2 = MediaPlayerViewModel(app)
        val restored = vm2.playlists.value.find { it.id == created.id }
        assertNotNull(restored)
        assertEquals("مختاراتي المحفوظة", restored?.name)
        assertEquals(1, restored?.items?.size)
        assertEquals("content://subs/test.srt", restored?.items?.first()?.subtitleUri)

        // Verify item removal and playlist deletion persist
        vm2.removeItemFromPlaylist(created.id, "track_1")
        vm2.deletePlaylist(created.id)

        val vm3 = MediaPlayerViewModel(app)
        assertNull(vm3.playlists.value.find { it.id == created.id })
    }

    @Test
    fun `persistence recovers interrupted downloads as paused and preserves playlist metadata`() {
        val storage = LibraryPersistence(app)
        val dl = DownloadItem(
            id = "dl_1",
            url = "https://example.com/video.mp4",
            fileName = "video.mp4",
            progress = 0.42f,
            bytesDownloaded = 420L,
            totalBytes = 1000L,
            speedBytesPerSec = 500L,
            status = DownloadStatus.DOWNLOADING,
            mediaType = MediaType.VIDEO
        )
        storage.saveDownloads(listOf(dl))
        val restoredDownloads = storage.loadDownloads()
        assertEquals(1, restoredDownloads.size)
        assertEquals(DownloadStatus.PAUSED, restoredDownloads.first().status)

        val folderPl = Playlist(
            id = "folder_1",
            name = "مجلد: Music",
            description = "2 files",
            items = emptyList(),
            checkpoint = PlaylistCheckpoint("folder_1", "t1", "Song 1", 12000L, 60000L),
            sourceTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        )
        storage.savePlaylists(listOf(folderPl))
        val restoredPl = storage.loadPlaylists().first()
        assertTrue(restoredPl.sourceTreeUri?.contains("primary%3AMusic") == true)
        assertEquals(12000L, restoredPl.checkpoint?.positionMs)
    }

    @Test
    fun `preamp gain persists in audio equalizer manager`() {
        val eq1 = AudioEqualizerManager(app)
        eq1.setPreampGain(75)
        assertEquals(75, eq1.equalizerState.value.preampGainPercent)

        val eq2 = AudioEqualizerManager(app)
        assertEquals(75, eq2.equalizerState.value.preampGainPercent)
    }
}
