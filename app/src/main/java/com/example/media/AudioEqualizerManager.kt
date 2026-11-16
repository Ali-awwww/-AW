package com.example.media

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioEqualizerManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vibe_player_eq", Context.MODE_PRIVATE)

    private var androidEqualizer: Equalizer? = null
    private var androidBassBoost: BassBoost? = null
    private var androidLoudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    private val _equalizerState = MutableStateFlow(loadInitialState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    private fun loadInitialState(): EqualizerState {
        val isEnabled = prefs.getBoolean("eq_enabled", true)
        val presetId = prefs.getString("eq_preset", "flat") ?: "flat"
        val bassBoost = prefs.getInt("eq_bass_boost", 20)
        val preampGain = prefs.getInt("eq_preamp_gain", 0)

        val bands = DefaultEqualizerData.defaultFrequencies.mapIndexed { index, freq ->
            val savedLevel = prefs.getInt("eq_band_$index", 0)
            EqualizerBand(index = index, centerFreqHz = freq, levelDb = savedLevel)
        }

        return EqualizerState(
            isEnabled = isEnabled,
            currentPresetId = presetId,
            bands = bands,
            bassBoostStrength = bassBoost,
            preampGainPercent = preampGain
        )
    }

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        releaseEffects()

        try {
            androidEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = _equalizerState.value.isEnabled
            }
        } catch (_: Exception) {
            androidEqualizer = null
        }

        try {
            androidBassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _equalizerState.value.isEnabled
            }
        } catch (_: Exception) {
            androidBassBoost = null
        }

        try {
            androidLoudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = _equalizerState.value.isEnabled && _equalizerState.value.preampGainPercent > 0
            }
        } catch (_: Exception) {
            androidLoudnessEnhancer = null
        }

        applyCurrentStateToHardware()
    }

    private fun applyCurrentStateToHardware() {
        val state = _equalizerState.value
        try {
            androidEqualizer?.enabled = state.isEnabled
            androidBassBoost?.enabled = state.isEnabled
            androidLoudnessEnhancer?.enabled = state.isEnabled && state.preampGainPercent > 0

            if (state.isEnabled) {
                state.bands.forEach { band ->
                    val millibels = (band.levelDb * 100).coerceIn(-1000, 1000).toShort()
                    androidEqualizer?.setBandLevel(band.index.toShort(), millibels)
                }

                val bassStrength = (state.bassBoostStrength * 10).coerceIn(0, 1000).toShort()
                androidBassBoost?.setStrength(bassStrength)

                val targetGainMb = (state.preampGainPercent * 15).coerceIn(0, 1500)
                androidLoudnessEnhancer?.setTargetGain(targetGainMb)
            }
        } catch (_: Exception) {
            // Safe fallback for devices/emulators with partial audiofx support
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerState.value = _equalizerState.value.copy(isEnabled = enabled)
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        applyCurrentStateToHardware()
    }

    fun selectPreset(presetId: String) {
        val preset = DefaultEqualizerData.presets.find { it.id == presetId } ?: return
        val newBands = _equalizerState.value.bands.mapIndexed { index, band ->
            val level = preset.bandLevelsDb.getOrElse(index) { 0 }
            prefs.edit().putInt("eq_band_$index", level).apply()
            band.copy(levelDb = level)
        }

        _equalizerState.value = _equalizerState.value.copy(
            currentPresetId = presetId,
            bands = newBands
        )
        prefs.edit().putString("eq_preset", presetId).apply()
        applyCurrentStateToHardware()
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        val clampedLevel = levelDb.coerceIn(-10, 10)
        val newBands = _equalizerState.value.bands.map { band ->
            if (band.index == bandIndex) {
                prefs.edit().putInt("eq_band_$bandIndex", clampedLevel).apply()
                band.copy(levelDb = clampedLevel)
            } else band
        }

        _equalizerState.value = _equalizerState.value.copy(
            currentPresetId = "custom",
            bands = newBands
        )
        prefs.edit().putString("eq_preset", "custom").apply()

        try {
            androidEqualizer?.setBandLevel(bandIndex.toShort(), (clampedLevel * 100).toShort())
        } catch (_: Exception) {
        }
    }

    fun setBassBoost(percentage: Int) {
        val clamped = percentage.coerceIn(0, 100)
        _equalizerState.value = _equalizerState.value.copy(bassBoostStrength = clamped)
        prefs.edit().putInt("eq_bass_boost", clamped).apply()

        try {
            androidBassBoost?.setStrength((clamped * 10).toShort())
        } catch (_: Exception) {
        }
    }

    fun setPreampGain(percentage: Int) {
        val clamped = percentage.coerceIn(0, 100)
        _equalizerState.value = _equalizerState.value.copy(preampGainPercent = clamped)
        prefs.edit().putInt("eq_preamp_gain", clamped).apply()

        try {
            androidLoudnessEnhancer?.enabled = _equalizerState.value.isEnabled && clamped > 0
            androidLoudnessEnhancer?.setTargetGain((clamped * 15).coerceIn(0, 1500))
        } catch (_: Exception) {
        }
    }

    private fun releaseEffects() {
        try {
            androidEqualizer?.release()
        } catch (_: Exception) { }
        androidEqualizer = null

        try {
            androidBassBoost?.release()
        } catch (_: Exception) { }
        androidBassBoost = null

        try {
            androidLoudnessEnhancer?.release()
        } catch (_: Exception) { }
        androidLoudnessEnhancer = null
    }

    fun release() {
        releaseEffects()
        currentSessionId = 0
    }
}
