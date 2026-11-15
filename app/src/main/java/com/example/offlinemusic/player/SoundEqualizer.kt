package com.example.offlinemusic.player

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.annotation.MainThread
import com.example.offlinemusic.data.repository.MusicPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the Android effect for the player service and shares its settings with the UI. */
@MainThread
internal class SoundEqualizer private constructor(context: Context) {
    private val preferences = MusicPreferences(context)
    private var savedGains = preferences.equalizerGains
    private var effect: Equalizer? = null
    private var appliedToSession = false

    private val _state = MutableStateFlow(
        EqualizerState(
            enabled = preferences.equalizerEnabled,
            bands = defaultEqualizerBands.map { band ->
                band.copy(gainMb = gainAtFrequency(band.frequencyHz, savedGains).coerceIn(-1500, 1500))
            }
        )
    )
    val state = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        val sessionId = audioSessionId.coerceAtLeast(0)
        val current = _state.value
        if (current.audioSessionId == sessionId && current.status != EqualizerStatus.UNAVAILABLE) return

        releaseEffect()
        _state.value = current.copy(audioSessionId = sessionId, status = EqualizerStatus.WAITING_FOR_PLAYER)
        if (sessionId == 0) return

        try {
            // Let the system audio panel take control of a shared effect when opened.
            val equalizer = Equalizer(-1, sessionId)
            effect = equalizer
            val levelRange = equalizer.bandLevelRange
            val gainRange = levelRange[0].toInt()..levelRange[1].toInt()
            val frequencies = List(equalizer.numberOfBands.toInt()) { band ->
                equalizer.getCenterFreq(band.toShort()) / 1000
            }
            check(gainRange.first <= 0 && gainRange.last >= 0 && gainRange.first < gainRange.last)
            check(frequencies.isNotEmpty() && frequencies.all { it > 0 } && frequencies.distinct().size == frequencies.size)

            _state.value = _state.value.copy(
                bands = frequencies.map { frequency ->
                    EqualizerBand(frequency, gainAtFrequency(frequency, savedGains).coerceIn(gainRange))
                },
                gainRange = gainRange
            )
            equalizer.setControlStatusListener { _, _ ->
                if (effect === equalizer) applySettings()
            }
            applySettings()
        } catch (error: RuntimeException) {
            markUnavailable(error)
        }
    }

    fun setEnabled(enabled: Boolean) {
        val current = _state.value
        if (current.enabled == enabled) return
        _state.value = current.copy(enabled = enabled)
        preferences.saveEqualizer(enabled, savedGains)
        if (enabled && current.status == EqualizerStatus.UNAVAILABLE) {
            attach(current.audioSessionId)
        } else {
            applySettings()
        }
    }

    fun selectPreset(preset: EqualizerPreset) {
        val current = _state.value
        updateBands(preset.bandsFor(current))
    }

    fun setBandGain(frequencyHz: Int, gainMb: Int) {
        val current = _state.value
        val index = current.bands.indexOfFirst { it.frequencyHz == frequencyHz }
        if (index == -1) return
        val bands = current.bands.toMutableList()
        bands[index] = bands[index].copy(gainMb = gainMb.coerceIn(current.gainRange))
        updateBands(bands, changedBand = index)
    }

    private fun updateBands(bands: List<EqualizerBand>, changedBand: Int? = null) {
        val current = _state.value
        if (bands == current.bands) return
        _state.value = current.copy(bands = bands)
        savedGains = bands.associate { it.frequencyHz to it.gainMb }
        preferences.saveEqualizer(current.enabled, savedGains)
        applySettings(changedBand)
    }

    private fun applySettings(changedBand: Int? = null) {
        val equalizer = effect ?: return
        val current = _state.value
        try {
            if (!equalizer.hasControl()) {
                _state.value = current.copy(status = EqualizerStatus.CONTROLLED_BY_SYSTEM)
                return
            }

            if (current.enabled) {
                if (changedBand != null && appliedToSession) {
                    equalizer.setBandLevel(changedBand.toShort(), current.bands[changedBand].gainMb.toShort())
                } else {
                    current.bands.forEachIndexed { index, band ->
                        equalizer.setBandLevel(index.toShort(), band.gainMb.toShort())
                    }
                }
                if (!equalizer.enabled) check(equalizer.setEnabled(true) == AudioEffect.SUCCESS)
                appliedToSession = true
            } else if (appliedToSession) {
                check(equalizer.setEnabled(false) == AudioEffect.SUCCESS)
                appliedToSession = false
            }
            _state.value = current.copy(status = EqualizerStatus.READY)
        } catch (error: RuntimeException) {
            markUnavailable(error)
        }
    }

    private fun markUnavailable(error: RuntimeException) {
        releaseEffect()
        _state.value = _state.value.copy(status = EqualizerStatus.UNAVAILABLE)
        Log.w("SoundEqualizer", "Equalizer unavailable for the playback session", error)
    }

    private fun releaseEffect() {
        val previousEffect = effect
        effect = null
        appliedToSession = false
        previousEffect?.release()
    }

    companion object {
        @Volatile private var instance: SoundEqualizer? = null

        fun getInstance(context: Context): SoundEqualizer = instance ?: synchronized(this) {
            instance ?: SoundEqualizer(context.applicationContext).also { instance = it }
        }
    }
}
