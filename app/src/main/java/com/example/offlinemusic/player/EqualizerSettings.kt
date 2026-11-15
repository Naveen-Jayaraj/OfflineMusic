package com.example.offlinemusic.player

import kotlin.math.ln
import kotlin.math.roundToInt

private val standardFrequencies = listOf(60, 230, 910, 3600, 14000)

internal val defaultEqualizerBands = standardFrequencies.map { EqualizerBand(it, 0) }

internal data class EqualizerBand(val frequencyHz: Int, val gainMb: Int)

internal enum class EqualizerStatus {
    WAITING_FOR_PLAYER,
    READY,
    CONTROLLED_BY_SYSTEM,
    UNAVAILABLE
}

internal data class EqualizerState(
    val enabled: Boolean,
    val bands: List<EqualizerBand>,
    val gainRange: IntRange = -1500..1500,
    val audioSessionId: Int = 0,
    val status: EqualizerStatus = EqualizerStatus.WAITING_FOR_PLAYER
)

internal enum class EqualizerPreset(val label: String, gainsMb: List<Int>) {
    FLAT("Flat", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("Bass Boost", listOf(800, 500, 0, -100, 100)),
    WARM_HIFI("Warm Hi-Fi", listOf(600, 350, 0, 150, 400)),
    VOCAL_CLARITY("Vocal Clarity", listOf(-200, -100, 400, 600, 400)),
    ELECTRONIC("Electronic", listOf(700, 400, 100, 300, 600));

    private val curve = standardFrequencies.zip(gainsMb).toMap()

    fun bandsFor(state: EqualizerState): List<EqualizerBand> = state.bands.map { band ->
        val gain = gainAtFrequency(band.frequencyHz, curve).coerceIn(state.gainRange)
        band.copy(gainMb = gain)
    }
}

/** Resamples a saved curve when the current audio effect exposes different bands. */
internal fun gainAtFrequency(frequencyHz: Int, gains: Map<Int, Int>): Int {
    gains[frequencyHz]?.let { return it }

    val lower = gains.keys.filter { it < frequencyHz }.maxOrNull()
    val upper = gains.keys.filter { it > frequencyHz }.minOrNull()
    if (lower == null) return upper?.let(gains::get) ?: 0
    if (upper == null) return gains.getValue(lower)

    val fraction = ln(frequencyHz.toDouble() / lower) / ln(upper.toDouble() / lower)
    val start = gains.getValue(lower).toDouble()
    val end = gains.getValue(upper).toDouble()
    return (start + (end - start) * fraction).roundToInt()
}
