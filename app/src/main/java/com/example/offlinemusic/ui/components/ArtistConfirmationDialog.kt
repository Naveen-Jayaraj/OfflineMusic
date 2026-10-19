package com.example.offlinemusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.database.entities.ArtistEntity

data class ArtistReviewPromptData(
    val reviewId: String,
    val trackId: String,
    val trackTitle: String,
    val rawCreditedString: String,
    val suggestedNewName: String,
    val candidateMatches: List<ArtistEntity>
)

private enum class ResolutionMode {
    EXISTING_SUGGESTION,
    CREATE_NEW,
    SEARCH_LIBRARY
}

@Composable
fun ArtistConfirmationDialog(
    prompt: ArtistReviewPromptData,
    existingArtists: List<ArtistEntity>,
    onSelectExisting: (ArtistEntity) -> Unit,
    onCreateNew: (String) -> Unit,
    onRemindLater: () -> Unit,
    onDismissForever: () -> Unit
) {
    var selectedMode by remember(prompt.reviewId) {
        mutableStateOf(
            if (prompt.candidateMatches.isNotEmpty()) ResolutionMode.EXISTING_SUGGESTION
            else ResolutionMode.CREATE_NEW
        )
    }

    var selectedExistingArtist by remember(prompt.reviewId) {
        mutableStateOf(prompt.candidateMatches.firstOrNull())
    }

    var newArtistName by remember(prompt.reviewId) {
        mutableStateOf(prompt.suggestedNewName)
    }

    var librarySearchQuery by remember { mutableStateOf("") }
    var selectedFromLibrary by remember { mutableStateOf<ArtistEntity?>(null) }

    val filteredLibraryArtists = remember(librarySearchQuery, existingArtists) {
        if (librarySearchQuery.isBlank()) {
            existingArtists.take(15)
        } else {
            val q = librarySearchQuery.trim().lowercase()
            existingArtists.filter { it.name.lowercase().contains(q) || it.normalizedName.contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onRemindLater,
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
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "IDENTIFY ARTIST",
                        style = MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "One track at a time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Track & Credit Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Detected Credit:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "\"${prompt.rawCreditedString}\"",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "on \"${prompt.trackTitle}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Option 1: Suggested Match (if fuzzy matches found)
                if (prompt.candidateMatches.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SUGGESTED MATCH IN LIBRARY",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        prompt.candidateMatches.forEach { candidate ->
                            val isSelected = selectedMode == ResolutionMode.EXISTING_SUGGESTION && selectedExistingArtist?.id == candidate.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedMode = ResolutionMode.EXISTING_SUGGESTION
                                        selectedExistingArtist = candidate
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedMode = ResolutionMode.EXISTING_SUGGESTION
                                        selectedExistingArtist = candidate
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Existing Artist",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Option 2: Create as New Artist
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isCreateSelected = selectedMode == ResolutionMode.CREATE_NEW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCreateSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(
                                width = 1.dp,
                                color = if (isCreateSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMode = ResolutionMode.CREATE_NEW }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isCreateSelected,
                            onClick = { selectedMode = ResolutionMode.CREATE_NEW },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Create new artist",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (isCreateSelected) {
                        OutlinedTextField(
                            value = newArtistName,
                            onValueChange = { newArtistName = it },
                            label = { Text("Artist Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                // Option 3: Choose from Existing Library
                if (existingArtists.isNotEmpty()) {
                    val isSearchSelected = selectedMode == ResolutionMode.SEARCH_LIBRARY
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSearchSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(
                                width = 1.dp,
                                color = if (isSearchSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMode = ResolutionMode.SEARCH_LIBRARY }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isSearchSelected,
                            onClick = { selectedMode = ResolutionMode.SEARCH_LIBRARY },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = if (selectedFromLibrary != null && isSearchSelected) "Selected: ${selectedFromLibrary!!.name}" else "Choose another artist from library",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isSearchSelected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = librarySearchQuery,
                                onValueChange = { librarySearchQuery = it },
                                placeholder = { Text("Search library artists...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                items(filteredLibraryArtists, key = { it.id }) { artist ->
                                    val isCurrent = selectedFromLibrary?.id == artist.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedFromLibrary = artist }
                                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedMode) {
                        ResolutionMode.EXISTING_SUGGESTION -> {
                            selectedExistingArtist?.let { onSelectExisting(it) }
                        }
                        ResolutionMode.CREATE_NEW -> {
                            if (newArtistName.isNotBlank()) {
                                onCreateNew(newArtistName.trim())
                            }
                        }
                        ResolutionMode.SEARCH_LIBRARY -> {
                            selectedFromLibrary?.let { onSelectExisting(it) }
                        }
                    }
                },
                enabled = when (selectedMode) {
                    ResolutionMode.EXISTING_SUGGESTION -> selectedExistingArtist != null
                    ResolutionMode.CREATE_NEW -> newArtistName.isNotBlank()
                    ResolutionMode.SEARCH_LIBRARY -> selectedFromLibrary != null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDismissForever,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Ignore")
                }
                TextButton(
                    onClick = onRemindLater,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Later")
                }
            }
        }
    )
}
