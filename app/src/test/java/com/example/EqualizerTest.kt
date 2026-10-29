package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.media.AudioEqualizerManager
import com.example.media.DefaultEqualizerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqualizerTest {

    private lateinit var app: Application
    private lateinit var eqManager: AudioEqualizerManager

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        eqManager = AudioEqualizerManager(app)
    }

    @Test
    fun `test initial equalizer state has five default bands`() {
        val state = eqManager.equalizerState.value
        assertEquals(5, state.bands.size)
        assertEquals(DefaultEqualizerData.defaultFrequencies, state.bands.map { it.centerFreqHz })
    }

    @Test
    fun `test selecting bass boost preset applies correct levels`() {
        eqManager.selectPreset("bass_boost")
        val state = eqManager.equalizerState.value
        assertEquals("bass_boost", state.currentPresetId)
        assertEquals(7, state.bands[0].levelDb)
        assertEquals(5, state.bands[1].levelDb)
    }

    @Test
    fun `test custom band level adjustment marks preset as custom`() {
        eqManager.setBandLevel(bandIndex = 2, levelDb = 8)
        val state = eqManager.equalizerState.value
        assertEquals("custom", state.currentPresetId)
        assertEquals(8, state.bands[2].levelDb)
    }

    @Test
    fun `test bass boost percentage clamped between 0 and 100`() {
        eqManager.setBassBoost(65)
        assertEquals(65, eqManager.equalizerState.value.bassBoostStrength)

        eqManager.setBassBoost(150)
        assertEquals(100, eqManager.equalizerState.value.bassBoostStrength)

        eqManager.setBassBoost(-20)
        assertEquals(0, eqManager.equalizerState.value.bassBoostStrength)
    }

    @Test
    fun `test equalizer enable disable toggle`() {
        eqManager.setEqualizerEnabled(false)
        assertFalse(eqManager.equalizerState.value.isEnabled)

        eqManager.setEqualizerEnabled(true)
        assertTrue(eqManager.equalizerState.value.isEnabled)
    }
}
