package com.example.offlinemusic.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.offlinemusic.R

@Composable
fun BrandMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(
        painter = painterResource(R.drawable.ic_bittune_mark),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
