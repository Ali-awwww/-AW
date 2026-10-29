package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.media.MediaPlayerViewModel
import com.example.media.SleepTimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SleepTimerTest {

    private lateinit var app: Application
    private lateinit var viewModel: MediaPlayerViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MediaPlayerViewModel(app)
    }

    @Test
    fun `test initial sleep timer is off`() {
        assertEquals(SleepTimerMode.OFF, viewModel.sleepTimerMode.value)
        assertEquals(0, viewModel.sleepTimerSeconds.value)
    }

    @Test
    fun `test setting duration sleep timer sets mode and seconds`() {
        viewModel.setSleepTimerDuration(15, fadeOut = true)
        assertEquals(SleepTimerMode.DURATION, viewModel.sleepTimerMode.value)
        assertEquals(15 * 60, viewModel.sleepTimerSeconds.value)
        assertTrue(viewModel.isFadeOutEnabled.value)
    }

    @Test
    fun `test setting end of track sleep timer`() {
        viewModel.setSleepTimerEndOfTrack()
        assertEquals(SleepTimerMode.END_OF_TRACK, viewModel.sleepTimerMode.value)
        assertEquals(0, viewModel.sleepTimerSeconds.value)
    }

    @Test
    fun `test add five minutes to active timer`() {
        viewModel.setSleepTimerDuration(10, fadeOut = false)
        val initialSeconds = viewModel.sleepTimerSeconds.value
        viewModel.addFiveMinutes()
        assertEquals(initialSeconds + 300, viewModel.sleepTimerSeconds.value)
    }

    @Test
    fun `test cancel sleep timer resets state`() {
        viewModel.setSleepTimerDuration(30)
        viewModel.cancelSleepTimer()
        assertEquals(SleepTimerMode.OFF, viewModel.sleepTimerMode.value)
        assertEquals(0, viewModel.sleepTimerSeconds.value)
    }
}
