package com.example.offlinemusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.R

// Bundled locally for the very first offline launch.
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    *listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.manrope,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight))
        )
    }.toTypedArray()
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal,
                 tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp
)

val Typography = Typography(
    displayLarge = type(52, 60, FontWeight.Medium, -1.5f),
    displayMedium = type(44, 52, FontWeight.Medium, -1f),
    displaySmall = type(36, 44, FontWeight.Medium, -0.8f),
    headlineLarge = type(32, 40, FontWeight.SemiBold, -0.7f),
    headlineMedium = type(28, 36, FontWeight.SemiBold, -0.5f),
    headlineSmall = type(24, 32, FontWeight.SemiBold, -0.4f),
    titleLarge = type(22, 30, FontWeight.SemiBold, -0.4f),
    titleMedium = type(16, 24, FontWeight.SemiBold, -0.1f),
    titleSmall = type(14, 20, FontWeight.SemiBold),
    bodyLarge = type(16, 25),
    bodyMedium = type(14, 22),
    bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.Medium),
    labelSmall = type(11, 16, FontWeight.Medium, 0.1f)
)
