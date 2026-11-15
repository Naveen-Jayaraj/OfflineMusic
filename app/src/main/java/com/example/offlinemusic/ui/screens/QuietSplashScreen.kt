package com.example.offlinemusic.ui.screens

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.offlinemusic.R
import com.example.offlinemusic.ui.components.BrandMark

/** A quiet reveal followed by a dissolve into the already-rendered library. */
@Composable
fun QuietSplashScreen(onAnimationComplete: () -> Unit, modifier: Modifier = Modifier) {
    val complete = rememberUpdatedState(onAnimationComplete)
    val reveal = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    val finished = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val finish = remember {
        { if (finished.compareAndSet(false, true)) complete.value() }
    }

    LaunchedEffect(Unit) {
        // Respect Android's Remove animations setting.
        if (ValueAnimator.areAnimatorsEnabled()) {
            reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            opacity.animateTo(0f, tween(250))
        }
        finish()
    }
    BackHandler(onBack = finish)

    Box(
        modifier = modifier.fillMaxSize()
            .graphicsLayer { alpha = opacity.value }
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip introduction",
                onClick = finish
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 10.dp.toPx()
            }
        ) {
            BrandMark(
                modifier = Modifier.size(100.dp).graphicsLayer {
                    rotationZ = -8f * (1f - reveal.value)
                }
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
