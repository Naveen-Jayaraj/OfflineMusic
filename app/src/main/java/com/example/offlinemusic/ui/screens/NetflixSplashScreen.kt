package com.example.offlinemusic.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class SpectrumRibbon(
    val xOffsetFraction: Float, // relative to emblem center (-1f to 1f)
    val widthPx: Float,
    val color: Color,
    val speedMultiplier: Float,
    val heightFraction: Float,
    val initialAlpha: Float
)

@Composable
fun NetflixSplashScreen(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val progress = remember { Animatable(0f) }
    val fadeOutAlpha = remember { Animatable(1f) }

    // Pre-calculate randomized vibrant spectrum ribbons (like Netflix's vertical light threads)
    val ribbons = remember {
        val colorPalette = listOf(
            Color(0xFFE50914), // Netflix Red
            Color(0xFFFF334B), // Vivid Crimson
            Color(0xFFFF6B81), // Neon Coral
            Color(0xFF9333EA), // Royal Violet
            Color(0xFFA855F7), // Electric Purple
            Color(0xFFEC4899), // Hot Pink
            Color(0xFF3B82F6), // Azure Blue
            Color(0xFF06B6D4), // Cyan
            Color(0xFF10B981), // Emerald
            Color(0xFFF59E0B), // Warm Gold
            Color(0xFFEF4444)  // Scarlet
        )
        val rnd = Random(42)
        List(48) {
            SpectrumRibbon(
                xOffsetFraction = (rnd.nextFloat() * 2f - 1f),
                widthPx = rnd.nextFloat() * 7f + 2f,
                color = colorPalette[rnd.nextInt(colorPalette.size)],
                speedMultiplier = rnd.nextFloat() * 0.8f + 0.8f,
                heightFraction = rnd.nextFloat() * 0.5f + 0.6f,
                initialAlpha = rnd.nextFloat() * 0.4f + 0.6f
            )
        }
    }

    LaunchedEffect(Unit) {
        // Trigger tactile "Ta..."
        launch {
            delay(400)
            try { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Exception) {}
            // "...DUM!"
            delay(350)
            try { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
        }

        // Animate the master timeline (0.0 to 1.0) over 2300ms
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2300,
                easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
            )
        )

        // Smooth dissolve into the app
        fadeOutAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300, easing = LinearEasing)
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
                        Color(0xFF150A21), // Subtle deep violet aura in center
                        Color(0xFF0A070E),
                        Color(0xFF040306)  // Pitch obsidian
                    ),
                    center = Offset.Unspecified,
                    radius = 1400f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Allow tap to skip immediately
                onAnimationComplete()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val emblemWidth = (size.width * 0.42f).coerceIn(160f, 260f)
            val emblemHeight = emblemWidth * 1.55f

            // Timeline Breakdown:
            // 0.00 .. 0.32: Phase 1 - Initial glow & appearance of the monogram ribbons
            // 0.32 .. 0.55: Phase 2 - Ta-Dum impact & vibrant glow expansion
            // 0.55 .. 1.00: Phase 3 - Zoom explosion & spectrum ribbon streaking into camera

            val scaleFactor = when {
                p < 0.35f -> 0.95f + (p / 0.35f) * 0.08f // gentle breathe in
                p < 0.55f -> 1.03f + ((p - 0.35f) / 0.20f) * 0.05f // swell before launch
                else -> {
                    // Massive exponential zoom like Netflix camera rush
                    val zoomP = (p - 0.55f) / 0.45f
                    1.08f + (zoomP * zoomP * zoomP * 16f)
                }
            }

            val emblemAlpha = when {
                p < 0.15f -> (p / 0.15f).coerceIn(0f, 1f)
                p < 0.70f -> 1f
                else -> (1f - ((p - 0.70f) / 0.30f)).coerceIn(0f, 1f)
            }

            val spectrumAlpha = when {
                p < 0.35f -> 0f
                p < 0.55f -> ((p - 0.35f) / 0.20f).coerceIn(0f, 1f)
                p < 0.85f -> 1f
                else -> (1f - ((p - 0.85f) / 0.15f)).coerceIn(0f, 1f)
            }

            scale(scaleFactor, pivot = Offset(cx, cy)) {
                // 1. Radial Energy Glow behind the monogram
                val glowRadius = emblemWidth * (1.2f + if (p > 0.35f) (p - 0.35f) * 4f else 0f)
                val glowAlpha = (if (p in 0.30f..0.75f) 0.6f else 0.25f) * emblemAlpha
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE50914).copy(alpha = 0.55f * glowAlpha),
                            Color(0xFF9333EA).copy(alpha = 0.35f * glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(cx, cy)
                )

                // 2. The Multi-Colored Spectrum Ribbons (Shooting during and after Ta-Dum)
                if (spectrumAlpha > 0.01f) {
                    val ribbonExpansion = if (p > 0.55f) (p - 0.55f) / 0.45f else 0f
                    ribbons.forEach { ribbon ->
                        val ribbonX = cx + (ribbon.xOffsetFraction * (emblemWidth * 0.52f)) * (1f + ribbonExpansion * 3.5f)
                        val ribbonH = emblemHeight * ribbon.heightFraction * (1f + ribbonExpansion * 5f)
                        val ribbonW = ribbon.widthPx * (1f + ribbonExpansion * 1.5f)
                        val topY = cy - ribbonH / 2f
                        val bottomY = cy + ribbonH / 2f

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ribbon.color.copy(alpha = ribbon.initialAlpha * spectrumAlpha * 0.85f),
                                    ribbon.color.copy(alpha = ribbon.initialAlpha * spectrumAlpha),
                                    ribbon.color.copy(alpha = ribbon.initialAlpha * spectrumAlpha * 0.85f),
                                    Color.Transparent
                                ),
                                startY = topY,
                                endY = bottomY
                            ),
                            topLeft = Offset(ribbonX - ribbonW / 2f, topY),
                            size = Size(ribbonW, ribbonH),
                            blendMode = BlendMode.Screen
                        )
                    }
                }

                // 3. The Iconic Music Monogram (Triple Interlocking Ribbons: Left Stem, Diagonal Ribbon, Right Stem)
                if (emblemAlpha > 0.01f) {
                    drawNetflixMusicMonogram(
                        cx = cx,
                        cy = cy,
                        width = emblemWidth,
                        height = emblemHeight,
                        alpha = emblemAlpha,
                        surgeProgress = ((p - 0.35f) / 0.35f).coerceIn(0f, 1f)
                    )
                }
            }

            // 4. Horizontal Anamorphic Lens Flare glinting across the emblem (at ~0.45s)
            if (p in 0.30f..0.70f) {
                val flareProgress = ((p - 0.30f) / 0.40f)
                val flareAlpha = (1f - kotlin.math.abs(flareProgress - 0.5f) * 2f).coerceIn(0f, 1f)
                val flareY = cy + (flareProgress - 0.5f) * (emblemHeight * 0.6f)
                val flareWidth = size.width * (0.8f + flareProgress * 0.4f)
                val flareHeight = 4f

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFE50914).copy(alpha = 0.4f * flareAlpha),
                            Color(0xFFFFFFFF).copy(alpha = 0.85f * flareAlpha),
                            Color(0xFF9333EA).copy(alpha = 0.4f * flareAlpha),
                            Color.Transparent
                        ),
                        startX = cx - flareWidth / 2f,
                        endX = cx + flareWidth / 2f
                    ),
                    topLeft = Offset(cx - flareWidth / 2f, flareY - flareHeight / 2f),
                    size = Size(flareWidth, flareHeight),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}

/**
 * Draws the triple-stem ribbon monogram representing Music & Offline playback in the Netflix aesthetic.
 */
private fun DrawScope.drawNetflixMusicMonogram(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    alpha: Float,
    surgeProgress: Float
) {
    val stemWidth = width * 0.28f
    val halfW = width / 2f
    val halfH = height / 2f

    val leftX = cx - halfW
    val rightX = cx + halfW - stemWidth
    val topY = cy - halfH
    val bottomY = cy + halfH

    // Netflix signature colors & luminous music gradient
    val baseRed = Color(0xFFB80710)
    val brightRed = Color(0xFFE50914)
    val neonCrimson = Color(0xFFFF2E51)
    val purpleGlow = Color(0xFF7C3AED)

    // Surge color shift when Ta-Dum peaks
    val activeRed = if (surgeProgress > 0f) {
        Color(
            red = (brightRed.red + surgeProgress * 0.1f).coerceIn(0f, 1f),
            green = (brightRed.green + surgeProgress * 0.05f).coerceIn(0f, 1f),
            blue = (brightRed.blue + surgeProgress * 0.15f).coerceIn(0f, 1f),
            alpha = brightRed.alpha
        )
    } else brightRed

    // Stem 1: Left Vertical Ribbon
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                activeRed.copy(alpha = 0.95f * alpha),
                baseRed.copy(alpha = 0.90f * alpha),
                Color(0xFF6B0208).copy(alpha = 0.85f * alpha)
            ),
            startY = topY,
            endY = bottomY
        ),
        topLeft = Offset(leftX, topY),
        size = Size(stemWidth, height)
    )

    // Stem 2: Right Vertical Ribbon
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                activeRed.copy(alpha = 0.95f * alpha),
                baseRed.copy(alpha = 0.90f * alpha),
                Color(0xFF6B0208).copy(alpha = 0.85f * alpha)
            ),
            startY = topY,
            endY = bottomY
        ),
        topLeft = Offset(rightX, topY),
        size = Size(stemWidth, height)
    )

    // Stem 3: The Dynamic Diagonal Ribbon with dimensional shadow & lighting
    val diagonalPath = Path().apply {
        moveTo(leftX, topY)
        lineTo(leftX + stemWidth, topY)
        lineTo(rightX + stemWidth, bottomY)
        lineTo(rightX, bottomY)
        close()
    }

    // Cast soft shadow underneath the diagonal ribbon for 3D depth
    drawPath(
        path = diagonalPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.45f * alpha),
                Color.Transparent
            ),
            start = Offset(leftX, topY),
            end = Offset(leftX + stemWidth * 1.5f, topY + height * 0.2f)
        )
    )

    // Diagonal foreground ribbon with glowing sheen
    drawPath(
        path = diagonalPath,
        brush = Brush.linearGradient(
            colors = listOf(
                neonCrimson.copy(alpha = alpha),
                activeRed.copy(alpha = alpha),
                purpleGlow.copy(alpha = 0.85f * alpha),
                activeRed.copy(alpha = alpha),
                baseRed.copy(alpha = alpha)
            ),
            start = Offset(leftX, topY),
            end = Offset(rightX + stemWidth, bottomY)
        )
    )

    // Top highlight rim on diagonal
    val highlightPath = Path().apply {
        moveTo(leftX, topY)
        lineTo(leftX + stemWidth * 0.3f, topY)
        lineTo(rightX + stemWidth, bottomY)
        lineTo(rightX + stemWidth * 0.7f, bottomY)
        close()
    }
    drawPath(
        path = highlightPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f * alpha),
                Color.Transparent
            ),
            start = Offset(leftX, topY),
            end = Offset(leftX + stemWidth, topY + height * 0.3f)
        ),
        blendMode = BlendMode.Screen
    )
}
