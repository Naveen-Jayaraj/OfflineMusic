package com.example.offlinemusic.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.offlinemusic.data.model.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSelectionTest {
    @Test
    fun defaultKeepsTheExistingSystemPalettes() {
        assertEquals(DarkBackground, colorSchemeFor(AppTheme.DEFAULT, systemDark = true).background)
        assertEquals(LightBackground, colorSchemeFor(AppTheme.DEFAULT, systemDark = false).background)
    }

    @Test
    fun explicitThemesOverrideTheSystemAppearance() {
        assertEquals(LightBackground, colorSchemeFor(AppTheme.LIGHT, systemDark = true).background)

        val amoled = colorSchemeFor(AppTheme.AMOLED_BLACK, systemDark = false)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
    }
}
