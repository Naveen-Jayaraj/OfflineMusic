package com.example.offlinemusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.offlinemusic.data.model.AppTheme
import com.example.offlinemusic.ui.theme.colorSchemeFor

@Composable
internal fun ThemePicker(selectedTheme: AppTheme, onThemeSelected: (AppTheme) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppTheme.entries.forEach { theme ->
            val selected = selectedTheme == theme
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onThemeSelected(theme) })
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
                ) {
                    Row(Modifier.fillMaxSize()) {
                        ThemeSample(
                            colorSchemeFor(theme, systemDark = true),
                            Modifier.weight(1f).fillMaxHeight()
                        )
                        if (theme == AppTheme.DEFAULT) {
                            ThemeSample(
                                colorSchemeFor(theme, systemDark = false),
                                Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(17.dp)
                                .background(colors.primary, CircleShape)
                                .padding(3.dp)
                        )
                    }
                }
                Text(
                    text = theme.displayName,
                    color = if (selected) colors.primary else colors.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
        }
    }
}

@Composable
private fun ThemeSample(colors: ColorScheme, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val inset = size.width * 0.13f
        val contentWidth = size.width - 2 * inset
        val radius = CornerRadius(3.dp.toPx())
        drawRect(colors.background)
        drawRoundRect(
            color = colors.primary,
            topLeft = Offset(inset, 9.dp.toPx()),
            size = Size(contentWidth * 0.42f, 3.dp.toPx()),
            cornerRadius = radius
        )
        drawRoundRect(
            color = colors.surfaceVariant,
            topLeft = Offset(inset, 18.dp.toPx()),
            size = Size(contentWidth, 18.dp.toPx()),
            cornerRadius = radius
        )
        drawRoundRect(
            color = colors.outline,
            topLeft = Offset(inset + contentWidth * 0.12f, 25.dp.toPx()),
            size = Size(contentWidth * 0.55f, 3.dp.toPx()),
            cornerRadius = radius
        )
        drawRoundRect(
            color = colors.primaryContainer,
            topLeft = Offset(inset, 42.dp.toPx()),
            size = Size(contentWidth, 8.dp.toPx()),
            cornerRadius = radius
        )
    }
}
