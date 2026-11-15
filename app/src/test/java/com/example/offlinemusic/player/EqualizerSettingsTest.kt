package com.example.offlinemusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerSettingsTest {
    @Test
    fun savedGainsInterpolateOnALogarithmicFrequencyScale() {
        val savedGains = mapOf(100 to 600, 1600 to -200)
        assertEquals(200, gainAtFrequency(400, savedGains))
        assertEquals(600, gainAtFrequency(100, savedGains))
        assertEquals(600, gainAtFrequency(60, savedGains))
        assertEquals(-200, gainAtFrequency(2000, savedGains))
        assertEquals(0, gainAtFrequency(400, emptyMap()))
    }

    @Test
    fun presetsRespectTheDevicesGainRange() {
        val state = EqualizerState(
            enabled = true,
            bands = defaultEqualizerBands,
            gainRange = -300..300
        )
        val bassBoost = EqualizerPreset.BASS_BOOST.bandsFor(state)
        assertEquals(state.bands.map { it.frequencyHz }, bassBoost.map { it.frequencyHz })
        assertEquals(listOf(300, 300, 0, -100, 100), bassBoost.map { it.gainMb })
        assertEquals(defaultEqualizerBands, EqualizerPreset.FLAT.bandsFor(state.copy(bands = bassBoost)))
    }
}
