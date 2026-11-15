package com.example.offlinemusic.ui.components

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.player.EqualizerBand
import com.example.offlinemusic.player.EqualizerPreset
import com.example.offlinemusic.player.EqualizerState
import com.example.offlinemusic.player.EqualizerStatus
import com.example.offlinemusic.player.SoundEqualizer
import java.text.NumberFormat
import kotlin.math.ln
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoundEqualizerSheet(equalizer: SoundEqualizer, onDismiss: () -> Unit) {
    val state by equalizer.state.collectAsState()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val bands = remember(state.bands) { state.bands.sortedBy { it.frequencyHz } }
    val selectedPreset = remember(state.bands, state.gainRange) {
        EqualizerPreset.entries.firstOrNull { preset -> preset.bandsFor(state) == state.bands }
    }
    val status = when (state.status) {
        EqualizerStatus.WAITING_FOR_PLAYER -> if (state.enabled) "Applies when playback starts" else "Bypassed"
        EqualizerStatus.READY -> if (state.enabled) null else "Bypassed"
        EqualizerStatus.CONTROLLED_BY_SYSTEM -> "Controlled by system audio effects"
        EqualizerStatus.UNAVAILABLE -> "Equalizer unavailable on this device"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(46.dp).background(colors.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Equalizer, contentDescription = null, tint = colors.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("Sound Equalizer", style = typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (status != null) {
                        Text(status, style = typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = equalizer::setEnabled,
                    modifier = Modifier.semantics { contentDescription = "Sound equalizer" }
                )
            }

            EqualizerGraph(bands, state.gainRange, state.enabled && state.status == EqualizerStatus.READY)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Presets", style = typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (selectedPreset == null) {
                        Text("Custom", style = typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EqualizerPreset.entries) { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { equalizer.selectPreset(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }

            val mixerShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(mixerShape)
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.outlineVariant, mixerShape)
                    .padding(horizontal = 13.dp, vertical = 6.dp)
            ) {
                val rangeDb = (state.gainRange.first / 100f)..(state.gainRange.last / 100f)
                bands.forEach { band ->
                    val frequency = frequencyLabel(band.frequencyHz)
                    val gain = "${gainLabel(band.gainMb)} dB"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            frequency,
                            modifier = Modifier.widthIn(min = 52.dp),
                            style = typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Slider(
                            value = band.gainMb / 100f,
                            onValueChange = { db ->
                                val gainMb = (db * 2).roundToInt() * 50
                                equalizer.setBandGain(band.frequencyHz, gainMb)
                            },
                            valueRange = rangeDb,
                            colors = SliderDefaults.colors(inactiveTrackColor = colors.outline),
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "$frequency gain"
                                stateDescription = gain
                            }
                        )
                        Text(
                            gain,
                            modifier = Modifier.widthIn(min = 55.dp),
                            style = typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = colors.onSurface,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        if (state.audioSessionId > 0) putExtra(AudioEffect.EXTRA_AUDIO_SESSION, state.audioSessionId)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        Toast.makeText(context, "System audio effects are unavailable.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("System audio effects")
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun EqualizerGraph(bands: List<EqualizerBand>, range: IntRange, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shape = RoundedCornerShape(20.dp)
    val curveAlpha = if (active) 1f else 0.65f
    val startColor = colors.primary.copy(alpha = curveAlpha)
    val endColor = colors.tertiary.copy(alpha = curveAlpha)
    val tickStyle = typography.labelSmall.copy(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.55f), colors.surface))
            )
            .border(1.dp, colors.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Band gain", style = typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("dB", style = typography.labelSmall, color = colors.onSurfaceVariant)
        }

        Canvas(Modifier.fillMaxWidth().height(155.dp)) {
            val gainTicks = listOf(range.last, 0, range.first).distinct()
            val tickLayouts = gainTicks.map { measurer.measure(gainLabel(it), style = tickStyle) }
            val frequencies = bands.map { measurer.measure(frequencyLabel(it.frequencyHz, compact = true), style = tickStyle) }
            val left = tickLayouts.maxOf { it.size.width }.toFloat() + 12.dp.toPx()
            val right = size.width - (frequencies.last().size.width / 2f).coerceAtLeast(5.dp.toPx())
            val top = tickLayouts.first().size.height / 2f + 6.dp.toPx()
            val bottom = size.height - frequencies.maxOf { it.size.height } - 7.dp.toPx()
            if (right <= left || bottom <= top) return@Canvas

            fun yFor(gain: Int): Float = bottom - (bottom - top) * (gain - range.first) / (range.last - range.first)
            val minimumFrequency = ln(bands.first().frequencyHz.toFloat())
            val chartSpan = ln(bands.last().frequencyHz.toFloat()) - minimumFrequency
            val points = bands.map { band ->
                val ratio = if (chartSpan == 0f) 0.5f else (ln(band.frequencyHz.toFloat()) - minimumFrequency) / chartSpan
                Offset(left + (right - left) * ratio, yFor(band.gainMb))
            }
            val zeroY = yFor(0)
            val baseline = colors.onSurfaceVariant.copy(alpha = 0.45f)
            val grid = colors.onSurfaceVariant.copy(alpha = 0.18f)

            gainTicks.forEachIndexed { index, gain ->
                val y = yFor(gain)
                val label = tickLayouts[index]
                drawText(label, topLeft = Offset(left - label.size.width - 7.dp.toPx(), y - label.size.height / 2f))
                drawLine(if (gain == 0) baseline else grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            points.forEach { point ->
                drawLine(grid, Offset(point.x, top), Offset(point.x, bottom), strokeWidth = 1.dp.toPx())
            }

            val curve = Path().apply {
                val first = points.first()
                moveTo(first.x, first.y)
                for (index in 1 until points.size) {
                    val before = points[index - 1]
                    val after = points[index]
                    val midpoint = (before.x + after.x) / 2f
                    cubicTo(midpoint, before.y, midpoint, after.y, after.x, after.y)
                }
            }
            val area = Path().apply {
                addPath(curve)
                lineTo(points.last().x, zeroY)
                lineTo(points.first().x, zeroY)
                close()
            }
            drawPath(
                area,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        startColor.copy(alpha = 0.28f * curveAlpha),
                        endColor.copy(alpha = 0.18f * curveAlpha)
                    ),
                    startX = left,
                    endX = right
                )
            )
            drawPath(
                curve,
                brush = Brush.horizontalGradient(listOf(startColor, endColor), left, right),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            points.forEach { center ->
                drawCircle(startColor, radius = 4.4.dp.toPx(), center = center)
                drawCircle(colors.surface, radius = 2.3.dp.toPx(), center = center)
            }

            var labelEnd = 0f
            val lastLabelStart = right - frequencies.last().size.width / 2f
            frequencies.forEachIndexed { index, label ->
                val x = points[index].x - label.size.width / 2f
                val afterLabel = x + label.size.width + 5.dp.toPx()
                if (index == frequencies.lastIndex || (x >= labelEnd && afterLabel < lastLabelStart)) {
                    drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
                    labelEnd = afterLabel
                }
            }
        }
    }
}

private fun frequencyLabel(frequency: Int, compact: Boolean = false): String {
    if (frequency < 1000) return if (compact) frequency.toString() else "$frequency Hz"
    val kilohertz = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(frequency / 1000f)
    return if (compact) "${kilohertz}k" else "$kilohertz kHz"
}

private fun gainLabel(gainMb: Int): String {
    val value = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(gainMb / 100f)
    return if (gainMb > 0) "+$value" else value
}
