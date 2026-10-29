package com.example.media

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelDb: Int // Range: -10 to +10 dB
)

data class EqualizerPreset(
    val id: String,
    val name: String,
    val bandLevelsDb: List<Int> // 5 values for 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
)

object DefaultEqualizerData {
    val defaultFrequencies = listOf(60, 230, 910, 3600, 14000)

    val defaultBands = defaultFrequencies.mapIndexed { index, freq ->
        EqualizerBand(index = index, centerFreqHz = freq, levelDb = 0)
    }

    val presets = listOf(
        EqualizerPreset("flat", "Flat (متوازن)", listOf(0, 0, 0, 0, 0)),
        EqualizerPreset("bass_boost", "Bass Boost (تضخيم الباس)", listOf(7, 5, 1, 0, 0)),
        EqualizerPreset("electronic", "Electronic (إلكتروني)", listOf(5, 3, 0, 3, 5)),
        EqualizerPreset("vocal", "Vocal (صوت غنائي)", listOf(-2, 1, 5, 2, -1)),
        EqualizerPreset("rock", "Rock (روك ونبض)", listOf(4, 2, -1, 3, 5)),
        EqualizerPreset("pop", "Pop (بوب وإيقاع)", listOf(-1, 2, 4, 2, -1)),
        EqualizerPreset("treble", "Treble Boost (تضخيم التريبل)", listOf(-3, -1, 1, 4, 7)),
        EqualizerPreset("custom", "Custom (مخصص)", listOf(0, 0, 0, 0, 0))
    )
}

data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPresetId: String = "flat",
    val bands: List<EqualizerBand> = DefaultEqualizerData.defaultBands,
    val bassBoostStrength: Int = 0 // 0 to 1000 millibels or 0 to 100%
)
