package com.example.offlinemusic.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.offlinemusic.data.model.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBackground,
    primaryContainer = DarkAccentMuted,
    onPrimaryContainer = DarkAccent,
    secondary = DarkTextSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceCard,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkClay,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkClayContainer,
    onTertiaryContainer = DarkClay,
    surfaceTint = DarkAccent,
    inverseSurface = LightBackground,
    inverseOnSurface = LightTextPrimary,
    inversePrimary = LightAccent,
    error = androidx.compose.ui.graphics.Color(0xFFF2B8AD),
    onError = androidx.compose.ui.graphics.Color(0xFF601410),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF79352D),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD4),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightBackground,
    primaryContainer = LightAccentMuted,
    onPrimaryContainer = LightAccent,
    secondary = LightTextSecondary,
    onSecondary = LightBackground,
    secondaryContainer = LightSurfaceCard,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightClay,
    onTertiary = LightBackground,
    tertiaryContainer = LightClayContainer,
    onTertiaryContainer = LightClay,
    surfaceTint = LightAccent,
    inverseSurface = DarkBackground,
    inverseOnSurface = DarkTextPrimary,
    inversePrimary = DarkAccent,
    error = androidx.compose.ui.graphics.Color(0xFF9B4034),
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD4),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF40100B),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightDivider
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceTint = Color.Black,
    surfaceVariant = Color(0xFF171A16),
    primaryContainer = Color(0xFF222B1E),
    secondaryContainer = Color(0xFF171A16),
    tertiaryContainer = Color(0xFF30221D),
    outlineVariant = Color(0xFF282D26)
)

internal fun colorSchemeFor(theme: AppTheme, systemDark: Boolean): ColorScheme = when (theme) {
    AppTheme.DEFAULT -> if (systemDark) DarkColorScheme else LightColorScheme
    AppTheme.LIGHT -> LightColorScheme
    AppTheme.AMOLED_BLACK -> AmoledColorScheme
}

@Composable
fun OfflineMusicTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(theme, systemDark = isSystemInDarkTheme())
    val lightBars = colorScheme === LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val background = colorScheme.background.toArgb()
            window.statusBarColor = background
            window.navigationBarColor = background
            window.decorView.setBackgroundColor(background)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = lightBars
            insetsController.isAppearanceLightNavigationBars = lightBars
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
