package com.example.offlinemusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.downloader.models.DownloadProgress
import com.example.offlinemusic.downloader.models.DownloadStatus
import com.example.offlinemusic.downloader.models.DownloadableTrack
import kotlinx.coroutines.launch

@Composable
fun BeginnerTutorialDialog(
    onDismiss: () -> Unit,
    downloads: Map<String, DownloadProgress>,
    allSongs: List<Song>,
    onDownloadStarterTrack: suspend (String) -> DownloadableTrack?,
    onPlaySong: (Song) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 4
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header: Step Indicator & Skip Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Step dots
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(totalSteps) { index ->
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (index == currentStep) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == currentStep) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tutorial",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Animated Step Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TutorialStepTransition"
                    ) { step ->
                        when (step) {
                            0 -> WelcomeStep()
                            1 -> WhatIsEverythingStep()
                            2 -> InteractiveDownloadStep(
                                downloads = downloads,
                                allSongs = allSongs,
                                onDownloadStarterTrack = onDownloadStarterTrack,
                                onPlaySong = onPlaySong
                            )
                            3 -> CompletionStep(onFinish = onDismiss)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Skip Tour", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (currentStep < totalSteps - 1) {
                        Button(
                            onClick = { currentStep++ },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(if (currentStep == 2) "Continue" else "Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Listening")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// STEP 1: WELCOME & PHILOSOPHY
// ==========================================
@Composable
private fun WelcomeStep() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Welcome to OfflineMusic",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your 100% offline, privacy-first lossless music sanctuary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FeaturePill(
                        icon = Icons.Default.MusicNote,
                        title = "100% Offline & Stored on Device",
                        description = "Every song you download is saved directly to your local phone storage. Listen anytime without internet or ads."
                    )
                    FeaturePill(
                        icon = Icons.Default.Download,
                        title = "Lossless Audio Streaming & Rip",
                        description = "Stream and download in pristine high-resolution formats (FLAC, AAC, 320kbps MP3) powered by YouTube and Lucida Lossless."
                    )
                    FeaturePill(
                        icon = Icons.Default.AutoAwesome,
                        title = "Automatic Metadata & Synced Lyrics",
                        description = "Smart background enrichment fills in missing album artwork, canonical artists, release years, and synced karaoke .LRC lyrics."
                    )
                }
            }
        }
    }
}

// ==========================================
// STEP 2: WHAT IS EVERYTHING? (APP TOUR)
// ==========================================
@Composable
private fun WhatIsEverythingStep() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "What is Everything?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "A quick guide to all tabs and features in the app:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            TourItemCard(
                icon = Icons.Default.Home,
                title = "Home: 6-Hour Dynamic Mixes",
                description = "Generates fresh playlists every 6 hours based on your listening habits: Recent Jam, Top Moods, Artist Spotlight, and Time-of-Day Vibe."
            )
        }

        item {
            TourItemCard(
                icon = Icons.Default.LibraryMusic,
                title = "Library: Your Local Music Collection",
                description = "Browse all your Songs, Artists, Albums, and Playlists. Sort any list by Time Added, Downloads, Listening Hours, Mood, or Genre."
            )
        }

        item {
            TourItemCard(
                icon = Icons.Default.Search,
                title = "Search: YouTube & Lucida Lossless",
                description = "Search across YouTube and Lucida. You can also paste any Spotify playlist URL to batch download complete playlists with 1 tap."
            )
        }

        item {
            TourItemCard(
                icon = Icons.Default.Analytics,
                title = "Stats: In-Depth Listening Analytics",
                description = "Track your replay habits, total hours played, top artists, favorite moods, and genres over time."
            )
        }

        item {
            TourItemCard(
                icon = Icons.Default.GraphicEq,
                title = "Player & Equalizer: Synced Lyrics",
                description = "Enjoy real-time line-by-line synchronized LRC lyrics, floating mini player, and a built-in 5-band sound equalizer."
            )
        }
    }
}

// ==========================================
// STEP 3: INTERACTIVE DEMO (DOWNLOAD ONE SONG)
// ==========================================
@Composable
private fun InteractiveDownloadStep(
    downloads: Map<String, DownloadProgress>,
    allSongs: List<Song>,
    onDownloadStarterTrack: suspend (String) -> DownloadableTrack?,
    onPlaySong: (Song) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedQuery by remember { mutableStateOf<String?>(null) }
    var activeDownloadTrack by remember { mutableStateOf<DownloadableTrack?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var customSearchText by remember { mutableStateOf("") }

    val currentDownload = activeDownloadTrack?.let { downloads[it.id] }
    val isCompleted = currentDownload?.status == DownloadStatus.COMPLETED

    // Find the downloaded song from library once completed
    val downloadedSong = remember(allSongs, activeDownloadTrack, isCompleted) {
        if (isCompleted && activeDownloadTrack != null) {
            allSongs.firstOrNull { it.id == activeDownloadTrack!!.id || it.title.equals(activeDownloadTrack!!.title, ignoreCase = true) }
        } else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Try It Now: Download a Song",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Pick a starter song below or type your own to see how downloading, auto-enrichment, and lyrics work:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Download Progress Box if active
        if (activeDownloadTrack != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtworkView(
                                artworkUri = activeDownloadTrack?.thumbnailUrl?.let { android.net.Uri.parse(it) },
                                size = 48.dp,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeDownloadTrack?.title ?: "Downloading...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeDownloadTrack?.artist ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Progress Bar & Status Text
                        val progressVal = currentDownload?.progress ?: 0.1f
                        LinearProgressIndicator(
                            progress = if (isCompleted) 1f else progressVal.coerceIn(0.05f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        val statusText = when (currentDownload?.status) {
                            DownloadStatus.DOWNLOADING -> "📥 Downloading lossless audio stream (${(progressVal * 100).toInt()}%)..."
                            DownloadStatus.ENRICHING -> "✨ Enriching album art, artist credits & tags..."
                            DownloadStatus.COMPLETED -> "🎉 Download Complete! Stored in offline storage."
                            DownloadStatus.FAILED -> "❌ Download failed: ${currentDownload.error ?: "Network error"}"
                            else -> "⚡ Initializing stream rip..."
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Action: Play Song Now
                        if (isCompleted && downloadedSong != null) {
                            Button(
                                onClick = { onPlaySong(downloadedSong) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Song Now")
                            }
                        }
                    }
                }
            }
        }

        // Resolving spinner
        if (isResolving) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Searching stream for \"$selectedQuery\"...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Error message if any
        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Starter Preset Choices
        item {
            Text(
                text = "POPULAR STARTER TRACKS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            StarterSongOption(
                title = "Get Lucky",
                artist = "Daft Punk ft. Pharrell Williams",
                tag = "Lossless",
                isLoading = isResolving && selectedQuery == "Daft Punk Get Lucky",
                onClick = {
                    selectedQuery = "Daft Punk Get Lucky"
                    isResolving = true
                    errorMessage = null
                    coroutineScope.launch {
                        val track = onDownloadStarterTrack("Daft Punk Get Lucky")
                        isResolving = false
                        if (track != null) {
                            activeDownloadTrack = track
                        } else {
                            errorMessage = "Could not find stream for this track."
                        }
                    }
                }
            )
        }

        item {
            StarterSongOption(
                title = "Lofi Hip Hop Chill Beat",
                artist = "Lofi Girl",
                tag = "Relaxing",
                isLoading = isResolving && selectedQuery == "Lofi Hip Hop Chill Beat",
                onClick = {
                    selectedQuery = "Lofi Hip Hop Chill Beat"
                    isResolving = true
                    errorMessage = null
                    coroutineScope.launch {
                        val track = onDownloadStarterTrack("Lofi Hip Hop Chill Beat")
                        isResolving = false
                        if (track != null) {
                            activeDownloadTrack = track
                        } else {
                            errorMessage = "Could not find stream for this track."
                        }
                    }
                }
            )
        }

        item {
            StarterSongOption(
                title = "Blinding Lights",
                artist = "The Weeknd",
                tag = "Synthwave",
                isLoading = isResolving && selectedQuery == "The Weeknd Blinding Lights",
                onClick = {
                    selectedQuery = "The Weeknd Blinding Lights"
                    isResolving = true
                    errorMessage = null
                    coroutineScope.launch {
                        val track = onDownloadStarterTrack("The Weeknd Blinding Lights")
                        isResolving = false
                        if (track != null) {
                            activeDownloadTrack = track
                        } else {
                            errorMessage = "Could not find stream for this track."
                        }
                    }
                }
            )
        }

        // Custom search option
        item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = customSearchText,
                onValueChange = { customSearchText = it },
                placeholder = { Text("Or type any custom song title...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (customSearchText.isNotBlank()) {
                                selectedQuery = customSearchText.trim()
                                isResolving = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val track = onDownloadStarterTrack(customSearchText.trim())
                                    isResolving = false
                                    if (track != null) {
                                        activeDownloadTrack = track
                                    } else {
                                        errorMessage = "No stream found for \"$customSearchText\""
                                    }
                                }
                            }
                        },
                        enabled = customSearchText.isNotBlank() && !isResolving
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (customSearchText.isNotBlank()) {
                            selectedQuery = customSearchText.trim()
                            isResolving = true
                            errorMessage = null
                            coroutineScope.launch {
                                val track = onDownloadStarterTrack(customSearchText.trim())
                                isResolving = false
                                if (track != null) {
                                    activeDownloadTrack = track
                                } else {
                                    errorMessage = "No stream found for \"$customSearchText\""
                                }
                            }
                        }
                    }
                )
            )
        }
    }
}

// ==========================================
// STEP 4: ALL SET
// ==========================================
@Composable
private fun CompletionStep(onFinish: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "You're All Set!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enjoy your music anywhere, anytime, completely offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💡", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "You can replay this tutorial anytime from Settings > About & Legal > Beginner Tutorial & Guide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "❤️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tap the heart on any song to add it to your Liked Songs playlist, which automatically pins to the top.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🔄", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your 6-hour dynamic mixes will adapt automatically based on what you listen to throughout the day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// HELPER COMPOSABLES
// ==========================================

@Composable
private fun FeaturePill(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TourItemCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StarterSongOption(
    title: String,
    artist: String,
    tag: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Get", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
