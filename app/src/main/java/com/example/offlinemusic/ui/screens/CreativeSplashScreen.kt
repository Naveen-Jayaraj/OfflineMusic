package com.example.offlinemusic.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class SonicParticle(
    val angleRad: Float,
    val initialDistance: Float,
    val speed: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float
)

@Composable
fun CreativeSplashScreen(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val progress = remember { Animatable(0f) }
    val fadeOutAlpha = remember { Animatable(1f) }
    val textMeasurer = rememberTextMeasurer()

    // 50 ambient floating audio stardust particles
    val particles = remember {
        val colors = listOf(
            Color(0xFF00F5FF), // Electric Cyan
            Color(0xFF8B5CF6), // Royal Violet
            Color(0xFFEC4899), // Hot Magenta
            Color(0xFF3B82F6), // Vivid Blue
            Color(0xFFF59E0B), // Sonic Amber
            Color(0xFFFFFFFF)  // Starlight
        )
        val rnd = Random(1337)
        List(56) {
            SonicParticle(
                angleRad = rnd.nextFloat() * (2f * PI.toFloat()),
                initialDistance = rnd.nextFloat() * 220f + 30f,
                speed = rnd.nextFloat() * 1.5f + 0.6f,
                radius = rnd.nextFloat() * 2.8f + 1.2f,
                color = colors[rnd.nextInt(colors.size)],
                alpha = rnd.nextFloat() * 0.5f + 0.4f
            )
        }
    }

    LaunchedEffect(Unit) {
        // Tactile acoustic beats:
        // 1. Initial resonance tap
        launch {
            delay(350)
            try { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Exception) {}
            // 2. The drop / sonic blast
            delay(500)
            try { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
        }

        // Master animation timeline: 2400ms
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2400,
                easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
            )
        )

        // Seamless dissolve transition
        fadeOutAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 280, easing = LinearEasing)
        )
        onAnimationComplete()
    }

    val p = progress.value
    val currentFade = fadeOutAlpha.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = currentFade }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF100720), // Deep obsidian with sonic violet heart
                        Color(0xFF07040B),
                        Color(0xFF020104)
                    ),
                    center = Offset.Unspecified,
                    radius = 1600f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Instant skip on tap
                onAnimationComplete()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 30f

            // Timeline breakdown:
            // 0.00 .. 0.30: Singularity awakening, concentric ripple resonance
            // 0.30 .. 0.65: Equalizer circle blooming, metallic audio emblem fusion
            // 0.65 .. 0.85: The Sonic Blast (burst of shockwaves, stardust hyperspace, glowing typography)
            // 0.85 .. 1.00: Smooth camera fly-through expansion and dissolve

            val globalScale = when {
                p < 0.65f -> 0.88f + p * 0.25f // organic breath
                p < 0.85f -> 1.04f + ((p - 0.65f) / 0.20f) * 0.12f // energy charge
                else -> {
                    // Zoom into camera like sound travelling into your ears
                    val blastP = (p - 0.85f) / 0.15f
                    1.16f + (blastP * blastP * 6.5f)
                }
            }

            val emblemAlpha = when {
                p < 0.12f -> (p / 0.12f).coerceIn(0f, 1f)
                p < 0.85f -> 1f
                else -> (1f - ((p - 0.85f) / 0.15f)).coerceIn(0f, 1f)
            }

            // 1. Draw Acoustic Stardust Particles
            drawSonicParticles(
                particles = particles,
                cx = cx,
                cy = cy,
                progress = p
            )

            // 2. Draw Concentric Soundwave Shockwaves (expanding outward)
            drawAcousticShockwaves(
                cx = cx,
                cy = cy,
                progress = p
            )

            // Scaled Layer: Equalizer Ring + Central Emblem
            scale(globalScale, pivot = Offset(cx, cy)) {
                // 3. Central Ambient Aura Glow
                val auraRadius = 240f * (0.8f + p * 0.6f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.40f * emblemAlpha),
                            Color(0xFF00F5FF).copy(alpha = 0.22f * emblemAlpha),
                            Color(0xFFEC4899).copy(alpha = 0.10f * emblemAlpha),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = auraRadius
                    ),
                    radius = auraRadius,
                    center = Offset(cx, cy)
                )

                // 4. Circular Audio Equalizer Waves (36 radiating frequency beams)
                drawCircularEqualizer(
                    cx = cx,
                    cy = cy,
                    radius = 115f,
                    progress = p,
                    alpha = emblemAlpha
                )

                // 5. The Sculpted Soundwave & Headphone Infinity Emblem
                drawSymphonicEmblem(
                    cx = cx,
                    cy = cy,
                    progress = p,
                    alpha = emblemAlpha
                )
            }

            // 6. Glowing Typography ("OFFLINE MUSIC")
            if (p > 0.25f && emblemAlpha > 0.05f) {
                drawBrandTypography(
                    textMeasurer = textMeasurer,
                    cx = cx,
                    cy = cy + 185f,
                    progress = p,
                    alpha = emblemAlpha
                )
            }
        }
    }
}

/**
 * Draws floating, radiating acoustic stardust particles that burst outward during the beat drop.
 */
private fun DrawScope.drawSonicParticles(
    particles: List<SonicParticle>,
    cx: Float,
    cy: Float,
    progress: Float
) {
    val burstFactor = if (progress > 0.65f) (progress - 0.65f) / 0.35f else 0f

    particles.forEach { pt ->
        val dist = pt.initialDistance + (progress * 140f * pt.speed) + (burstFactor * burstFactor * 600f * pt.speed)
        val x = cx + cos(pt.angleRad) * dist
        val y = cy + sin(pt.angleRad) * dist

        val pAlpha = (pt.alpha * (1f - (progress * 0.5f))).coerceIn(0f, 1f)
        val pRadius = pt.radius * (1f + burstFactor * 1.5f)

        drawCircle(
            color = pt.color.copy(alpha = pAlpha),
            radius = pRadius,
            center = Offset(x, y),
            blendMode = BlendMode.Screen
        )
    }
}

/**
 * Concentric soundwave ripples emanating from the audio singularity.
 */
private fun DrawScope.drawAcousticShockwaves(
    cx: Float,
    cy: Float,
    progress: Float
) {
    // 3 rhythmic wave ripples
    val waveOffsets = listOf(0.0f, 0.22f, 0.44f)

    waveOffsets.forEachIndexed { idx, offset ->
        val waveP = ((progress - offset) / 0.55f).coerceIn(0f, 1f)
        if (waveP > 0f && waveP < 1f) {
            val radius = 40f + (waveP * 420f)
            val strokeW = (6f * (1f - waveP)).coerceAtLeast(1.5f)
            val waveAlpha = ((1f - waveP) * 0.7f).coerceIn(0f, 1f)

            val waveColors = if (idx % 2 == 0) {
                listOf(Color(0xFF00F5FF), Color(0xFF8B5CF6), Color.Transparent)
            } else {
                listOf(Color(0xFFEC4899), Color(0xFFFFAE00), Color.Transparent)
            }

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = waveColors,
                    center = Offset(cx, cy)
                ),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW),
                alpha = waveAlpha,
                blendMode = BlendMode.Screen
            )
        }
    }
}

/**
 * 36 frequency equalizer bars radiating outward in a 360-degree circle like a live music visualizer.
 */
private fun DrawScope.drawCircularEqualizer(
    cx: Float,
    cy: Float,
    radius: Float,
    progress: Float,
    alpha: Float
) {
    val barCount = 36
    val angleStep = (2f * PI.toFloat()) / barCount
    val rotationAngle = progress * 70f // smooth rotation

    rotate(rotationAngle, pivot = Offset(cx, cy)) {
        for (i in 0 until barCount) {
            val angle = i * angleStep
            // Dynamic sine bounce simulating audio frequencies
            val freq1 = sin((i * 0.7f) + progress * 14f)
            val freq2 = cos((i * 1.3f) - progress * 9f)
            val amplitude = ((freq1 + freq2) * 0.5f).coerceIn(0f, 1f)

            val barLength = 12f + (amplitude * 38f * (progress * 1.2f).coerceIn(0f, 1f))
            val barStart = radius
            val barEnd = radius + barLength

            val x1 = cx + cos(angle) * barStart
            val y1 = cy + sin(angle) * barStart
            val x2 = cx + cos(angle) * barEnd
            val y2 = cy + sin(angle) * barEnd

            // Color spectrum around the ring
            val hueRatio = i.toFloat() / barCount
            val barColor = when {
                hueRatio < 0.33f -> Color(0xFF00F5FF)
                hueRatio < 0.66f -> Color(0xFF8B5CF6)
                else -> Color(0xFFEC4899)
            }

            drawLine(
                color = barColor.copy(alpha = (0.35f + amplitude * 0.65f) * alpha),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Screen
            )
        }
    }
}

/**
 * The Sculpted Symphonic Emblem: An interconnected soundwave treble & headphone infinity loop.
 */
private fun DrawScope.drawSymphonicEmblem(
    cx: Float,
    cy: Float,
    progress: Float,
    alpha: Float
) {
    val emblemSize = 130f
    val half = emblemSize / 2f

    // 1. Flowing Headphone Acoustic Arch
    val archPath = Path().apply {
        moveTo(cx - half * 0.85f, cy + half * 0.2f)
        cubicTo(
            cx - half * 0.95f, cy - half * 0.9f,
            cx + half * 0.95f, cy - half * 0.9f,
            cx + half * 0.85f, cy + half * 0.2f
        )
    }

    // Iridescent metallic gradient that paints across the arch
    val iridescentGradient = Brush.sweepGradient(
        colors = listOf(
            Color(0xFF00F5FF),
            Color(0xFF8B5CF6),
            Color(0xFFEC4899),
            Color(0xFFFFAE00),
            Color(0xFF00F5FF)
        ),
        center = Offset(cx, cy)
    )

    drawPath(
        path = archPath,
        brush = iridescentGradient,
        style = Stroke(width = 8.5f, cap = StrokeCap.Round),
        alpha = alpha
    )

    // Glowing rim on the arch
    drawPath(
        path = archPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.85f * alpha), Color.Transparent),
            startY = cy - half,
            endY = cy
        ),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
        blendMode = BlendMode.Screen
    )

    // 2. Dual Ear-Cups / Sound Pods (Left & Right capsules)
    val leftPodX = cx - half * 0.85f
    val rightPodX = cx + half * 0.85f
    val podY = cy + half * 0.28f
    val podWidth = 14f
    val podHeight = 36f

    // Left Pod
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF00F5FF), Color(0xFF8B5CF6)),
            startY = podY - podHeight / 2f,
            endY = podY + podHeight / 2f
        ),
        topLeft = Offset(leftPodX - podWidth / 2f, podY - podHeight / 2f),
        size = Size(podWidth, podHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
        alpha = alpha
    )

    // Right Pod
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFEC4899), Color(0xFFFFAE00)),
            startY = podY - podHeight / 2f,
            endY = podY + podHeight / 2f
        ),
        topLeft = Offset(rightPodX - podWidth / 2f, podY - podHeight / 2f),
        size = Size(podWidth, podHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
        alpha = alpha
    )

    // 3. The Core Soundwave Equalizer Bars (inside the headphone arch)
    val bars = listOf(
        Pair(-32f, 24f),
        Pair(-16f, 44f),
        Pair(0f, 62f),
        Pair(16f, 44f),
        Pair(32f, 24f)
    )

    bars.forEachIndexed { idx, (xOffset, baseH) ->
        val bounce = sin(progress * 12f + idx * 0.9f)
        val curH = (baseH * (0.6f + bounce * 0.4f) * progress.coerceIn(0f, 1f)).coerceAtLeast(8f)
        val x = cx + xOffset
        val yTop = cy + half * 0.15f - curH / 2f

        val barBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF00F5FF),
                Color(0xFF8B5CF6),
                Color(0xFFEC4899)
            ),
            startY = yTop,
            endY = yTop + curH
        )

        drawRoundRect(
            brush = barBrush,
            topLeft = Offset(x - 4f, yTop),
            size = Size(8f, curH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            alpha = alpha
        )
    }

    // 4. Specular Sheen Sweep crossing over the emblem (at ~0.5s - 0.8s)
    if (progress in 0.40f..0.85f) {
        val sheenP = (progress - 0.40f) / 0.45f
        val sheenX = cx - half * 1.2f + (sheenP * half * 2.4f)

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.85f * alpha),
                    Color(0xFF00F5FF).copy(alpha = 0.5f * alpha),
                    Color.Transparent
                ),
                startX = sheenX - 25f,
                endX = sheenX + 25f
            ),
            start = Offset(sheenX - 20f, cy - half * 0.8f),
            end = Offset(sheenX + 20f, cy + half * 0.8f),
            strokeWidth = 6f,
            blendMode = BlendMode.Screen
        )
    }
}

/**
 * Elegant glowing typography for the app brand name.
 */
private fun DrawScope.drawBrandTypography(
    textMeasurer: TextMeasurer,
    cx: Float,
    cy: Float,
    progress: Float,
    alpha: Float
) {
    val textP = ((progress - 0.25f) / 0.40f).coerceIn(0f, 1f)
    val textAlpha = (textP * alpha).coerceIn(0f, 1f)

    val style = TextStyle(
        color = Color.White.copy(alpha = textAlpha),
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = FontFamily.SansSerif,
        letterSpacing = (6.5f + (1f - textP) * 8f).sp // dramatic letter tracking convergence
    )

    val layoutResult = textMeasurer.measure(
        text = "OFFLINE MUSIC",
        style = style
    )

    val textX = cx - layoutResult.size.width / 2f
    val textY = cy - layoutResult.size.height / 2f

    // Soft neon glow under the text
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF8B5CF6).copy(alpha = 0.25f * textAlpha),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = 160f
        ),
        radius = 160f,
        center = Offset(cx, cy)
    )

    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(textX, textY)
    )

    // Subtitle "IMMERSIVE AUDIO"
    if (progress > 0.45f) {
        val subP = ((progress - 0.45f) / 0.35f).coerceIn(0f, 1f)
        val subStyle = TextStyle(
            color = Color(0xFF00F5FF).copy(alpha = subP * textAlpha * 0.80f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 4.sp
        )
        val subResult = textMeasurer.measure(
            text = "PURE SOUND • OFFLINE",
            style = subStyle
        )
        drawText(
            textLayoutResult = subResult,
            topLeft = Offset(cx - subResult.size.width / 2f, textY + layoutResult.size.height + 8f)
        )
    }
}
