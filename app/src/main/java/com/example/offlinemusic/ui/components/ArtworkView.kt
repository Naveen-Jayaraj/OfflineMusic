package com.example.offlinemusic.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ArtworkView(
    artworkUri: Uri?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    isLikedSongsSpecial: Boolean = false
) {
    if (isLikedSongsSpecial) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4527A0),
                            Color(0xFF7B1FA2)
                        )
                    )
                )
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Liked Songs",
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = "Album Artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Elegant monochrome vinyl disc placeholder
                Box(
                    modifier = Modifier
                        .size(size * 0.7f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(size * 0.35f)
                    )
                }
            }
        }
    }
}
