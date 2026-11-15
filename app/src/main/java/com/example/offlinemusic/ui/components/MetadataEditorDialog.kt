package com.example.offlinemusic.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.offlinemusic.data.remote.LyricQuery
import com.example.offlinemusic.data.repository.MusicRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.offlinemusic.data.database.entities.TrackEntity
import com.example.offlinemusic.data.remote.ApiClient
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.data.repository.ArtistNormalizer

@Composable
fun MetadataEditorDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String?,
        genre: String?,
        artworkUri: Uri?,
        year: Int?,
        producer: String?,
        composer: String?,
        language: String?,
        mood: String?,
        source: String,
        lyricsAvailable: Boolean,
        metachecked: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artistString by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(if (song.album.equals("Unknown Album", ignoreCase = true)) "" else song.album) }
    var yearText by remember { mutableStateOf(song.year?.toString() ?: "") }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var producer by remember { mutableStateOf(song.producer ?: "") }
    var composer by remember { mutableStateOf(song.composer ?: "") }
    var language by remember { mutableStateOf(song.language ?: "") }
    var mood by remember { mutableStateOf(song.mood ?: "") }
    var source by remember { mutableStateOf(song.source) }
    var lyricsAvailable by remember { mutableStateOf(song.lyricsAvailable) }
    var metachecked by remember { mutableStateOf(song.metachecked) }
    var selectedArtworkUri by remember { mutableStateOf<Uri?>(song.artworkUri) }
    var isSaving by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isAutoEnriching by remember { mutableStateOf(false) }
    var autoEnrichMessage by remember { mutableStateOf<String?>(null) }
    var isSearchingLyrics by remember { mutableStateOf(false) }
    var lyricsSearchMessage by remember { mutableStateOf<String?>(null) }

    // System Image Picker for album art
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedArtworkUri = uri
        }
    }

    // Split artists live using "/" and other delimiters to preview separate artists
    val previewArtists = remember(artistString) {
        ArtistNormalizer.splitCollaborators(artistString)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    val popularGenres = listOf("Pop", "Hip-Hop", "Rock", "Electronic", "R&B", "Classical", "Jazz", "Dance", "Lo-Fi", "Acoustic")
    val popularMoods = listOf("Chill", "Energetic", "Happy", "Sad", "Focus", "Party", "Romantic", "Dark")
    val popularLanguages = listOf("English", "Spanish", "Korean", "Japanese", "Hindi", "French", "German", "Instrumental")

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "EDIT TRACK METADATA",
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Cover Photo Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedArtworkUri != null) {
                            AsyncImage(
                                model = selectedArtworkUri,
                                contentDescription = "Cover Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(110.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        // Camera icon overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Cover",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "Tap to change cover photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Auto-Enrich Action Button (MetaEnricher Backend)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isAutoEnriching = true
                                autoEnrichMessage = "Connecting to MetaEnricher..."
                                try {
                                    val dummyTrack = TrackEntity(
                                        id = song.id,
                                        title = title.ifBlank { song.title },
                                        normalizedTitle = (title.ifBlank { song.title }).lowercase(),
                                        durationMs = song.durationMs,
                                        fileUri = song.uri.toString(),
                                        artist = artistString.ifBlank { song.artist },
                                        album = album.ifBlank { song.album }
                                    )
                                    val response = ApiClient.metaEnricherApi.enrichSong(dummyTrack)
                                    if (response.success) {
                                        val s = response.song
                                        title = s.title?.ifBlank { title } ?: title
                                        artistString = s.artist?.ifBlank { artistString } ?: artistString
                                        album = s.album ?: album
                                        if (s.year != null) yearText = s.year.toString()
                                        if (!s.genre.isNullOrBlank()) genre = s.genre
                                        if (!s.producer.isNullOrBlank()) producer = s.producer
                                        if (!s.composer.isNullOrBlank()) composer = s.composer
                                        if (!s.language.isNullOrBlank()) language = s.language
                                        if (!s.mood.isNullOrBlank()) mood = s.mood
                                        if (!s.artworkUri.isNullOrBlank() && s.artworkUri.startsWith("http")) {
                                            try {
                                                val downloadedUri = withContext(Dispatchers.IO) {
                                                    val req = okhttp3.Request.Builder()
                                                        .url(s.artworkUri)
                                                        .header("User-Agent", "Mozilla/5.0")
                                                        .build()
                                                    com.example.offlinemusic.data.remote.ApiClient.okHttpClient.newCall(req).execute().use { resp ->
                                                        if (resp.isSuccessful) {
                                                            val bytes = resp.body?.bytes()
                                                            if (bytes != null && bytes.isNotEmpty()) {
                                                                val artworkDir = File(context.filesDir, "artwork").apply { mkdirs() }
                                                                val artFile = File(artworkDir, "art_${song.id}_${System.currentTimeMillis()}.jpg")
                                                                artFile.writeBytes(bytes)
                                                                try {
                                                                    FileProvider.getUriForFile(
                                                                        context,
                                                                        "${context.packageName}.fileprovider",
                                                                        artFile
                                                                    )
                                                                } catch (_: Exception) {
                                                                    Uri.fromFile(artFile)
                                                                }
                                                            } else {
                                                                Uri.parse(s.artworkUri)
                                                            }
                                                        } else {
                                                            Uri.parse(s.artworkUri)
                                                        }
                                                    }
                                                }
                                                selectedArtworkUri = downloadedUri
                                            } catch (_: Exception) {
                                                selectedArtworkUri = Uri.parse(s.artworkUri)
                                            }
                                        }

                                        // Auto-fetch & save lyrics as well
                                        try {
                                            val lrcQuery = LyricQuery(
                                                title = title,
                                                artist = artistString,
                                                album = album.ifEmpty { null },
                                                durationMs = if (song.durationMs > 0) song.durationMs else null
                                            )
                                            val lrcResp = ApiClient.metaEnricherApi.getLyrics(lrcQuery)
                                            if (lrcResp.lyricsAvailable && (!lrcResp.syncedLyrics.isNullOrBlank() || !lrcResp.plainLyrics.isNullOrBlank())) {
                                                val lrcContent = lrcResp.syncedLyrics ?: lrcResp.plainLyrics!!
                                                withContext(Dispatchers.IO) {
                                                    MusicRepository(context).saveLyricsForTrack(song.id, lrcContent)
                                                }
                                                lyricsAvailable = true
                                            }
                                        } catch (_: Exception) {}

                                        lyricsAvailable = s.lyricsAvailable || lyricsAvailable
                                        metachecked = true
                                        val score = ((response.audit?.confidenceScore ?: 0.9f) * 100).toInt()
                                        autoEnrichMessage = "✨ Auto-Enriched! ($score% confidence)"
                                    } else {
                                        autoEnrichMessage = "No deep search match found."
                                    }
                                } catch (e: Exception) {
                                    autoEnrichMessage = "Server connecting or cold starting: ${e.localizedMessage ?: "timeout"}"
                                } finally {
                                    isAutoEnriching = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        enabled = !isAutoEnriching
                    ) {
                        if (isAutoEnriching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Deep Searching MetaEnricher...", fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Enrich with MetaEnricher",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (autoEnrichMessage != null) {
                        Text(
                            text = autoEnrichMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // 2. Title Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TRACK NAME *",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // 3. Artist Field & Multi-Artist Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ARTIST(S) *",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use / to separate artists",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = artistString,
                        onValueChange = { artistString = it },
                        placeholder = { Text("e.g. Marshmello / Eminem") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Live Multi-Artist Chips Preview
                    if (previewArtists.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            previewArtists.forEach { parsedName ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = parsedName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Quick "+ Add" chip button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val trimmed = artistString.trim()
                                        artistString = if (trimmed.isEmpty()) "" else "$trimmed / "
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Add artist",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 4. Album & Year Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(2f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ALBUM",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = album,
                            onValueChange = { album = it },
                            placeholder = { Text("Album name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "YEAR",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = { Text("2024") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // 5. Genre Field & Quick Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "GENRE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        placeholder = { Text("e.g. Electronic, Pop") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        popularGenres.forEach { g ->
                            val isSelected = genre.equals(g, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { genre = g }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = g,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 6. Producer & Composer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "PRODUCER",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = producer,
                            onValueChange = { producer = it },
                            placeholder = { Text("Producer name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "COMPOSER",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = composer,
                            onValueChange = { composer = it },
                            placeholder = { Text("Composer name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // 7. Language & Mood Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LANGUAGE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = language,
                            onValueChange = { language = it },
                            placeholder = { Text("e.g. English") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "MOOD",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = mood,
                            onValueChange = { mood = it },
                            placeholder = { Text("e.g. Chill, Energetic") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // Quick Language Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularLanguages.forEach { l ->
                        val isSelected = language.equals(l, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { language = l }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = l,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Mood Selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularMoods.forEach { m ->
                        val isSelected = mood.equals(m, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { mood = m }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = m,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 8. System Tags & Verification Status
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "METADATA STATUS & TAGS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Tag 1: Source
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Source Tag",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Origin audio download provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("LOCAL", "YOUTUBE", "LUCIDA").forEach { src ->
                                val isSelected = source.equals(src, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { source = src }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = src,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Tag 2: Lyrics Available
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Lyrics Available",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lyricsAvailable) "Synchronized/Embedded lyrics found" else "No .lrc file linked yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (lyricsAvailable) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (lyricsAvailable) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { lyricsAvailable = !lyricsAvailable }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (lyricsAvailable) "YES" else "NO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (lyricsAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Dedicated Search & Save Lyrics (.lrc) Button
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSearchingLyrics = true
                                    lyricsSearchMessage = "Connecting to MetaEnricher..."
                                    try {
                                        val query = LyricQuery(
                                            title = title.ifBlank { song.title },
                                            artist = artistString.ifBlank { song.artist },
                                            album = album.ifBlank { song.album }.ifEmpty { null },
                                            durationMs = if (song.durationMs > 0) song.durationMs else null
                                        )
                                        val response = ApiClient.metaEnricherApi.getLyrics(query)
                                        if (response.lyricsAvailable && (!response.syncedLyrics.isNullOrBlank() || !response.plainLyrics.isNullOrBlank())) {
                                            val lrcText = response.syncedLyrics ?: response.plainLyrics!!
                                            val repo = MusicRepository(context)
                                            val saved = withContext(Dispatchers.IO) {
                                                repo.saveLyricsForTrack(song.id, lrcText)
                                            }
                                            if (saved) {
                                                lyricsAvailable = true
                                                val type = if (response.hasSynced) "Synced .lrc" else "Plain lyrics"
                                                lyricsSearchMessage = "✨ Found & saved $type (${response.linesCount} lines)!"
                                            } else {
                                                lyricsSearchMessage = "Lyrics fetched but failed saving to disk."
                                            }
                                        } else {
                                            lyricsSearchMessage = "No lyrics found on server for this track."
                                        }
                                    } catch (e: Exception) {
                                        lyricsSearchMessage = "Lyrics search error: ${e.localizedMessage ?: "timeout"}"
                                    } finally {
                                        isSearchingLyrics = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            enabled = !isSearchingLyrics
                        ) {
                            if (isSearchingLyrics) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching Synced Lyrics...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Search & Save Lyrics (.lrc)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }

                        if (!lyricsSearchMessage.isNullOrBlank()) {
                            Text(
                                text = lyricsSearchMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (lyricsSearchMessage!!.startsWith("✨")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    // Tag 3: Metachecked
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Metachecked",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (metachecked) "Verified by remote server" else "Pending remote server verification",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (metachecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (metachecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { metachecked = !metachecked }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (metachecked) "TRUE" else "FALSE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (metachecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        isSaving = true
                        onSave(
                            title.trim(),
                            artistString.trim(),
                            album.trim().ifEmpty { null },
                            genre.trim().ifEmpty { null },
                            selectedArtworkUri,
                            yearText.toIntOrNull(),
                            producer.trim().ifEmpty { null },
                            composer.trim().ifEmpty { null },
                            language.trim().ifEmpty { null },
                            mood.trim().ifEmpty { null },
                            source,
                            lyricsAvailable,
                            metachecked
                        )
                    }
                },
                enabled = title.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saving...")
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Cancel")
            }
        }
    )
}
