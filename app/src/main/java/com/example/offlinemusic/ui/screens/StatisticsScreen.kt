package com.example.offlinemusic.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.model.AlbumStat
import com.example.offlinemusic.data.model.ArtistStat
import com.example.offlinemusic.data.model.GenreStat
import com.example.offlinemusic.data.model.ListeningOverviewStats
import com.example.offlinemusic.data.model.MoodStat
import com.example.offlinemusic.data.model.PlayHistoryItem
import com.example.offlinemusic.data.model.SongStat
import com.example.offlinemusic.data.model.StatsTimeRange
import com.example.offlinemusic.ui.components.ArtworkView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class StatsSection(val label: String) {
    OVERVIEW("Overview"),
    SONGS("Top Songs"),
    ARTISTS("Top Artists"),
    MOODS_GENRES("Moods & Genres"),
    HISTORY("History")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    overview: ListeningOverviewStats,
    topSongs: List<SongStat>,
    topArtists: List<ArtistStat>,
    topMoods: List<MoodStat>,
    topGenres: List<GenreStat>,
    topAlbums: List<AlbumStat>,
    playHistory: List<PlayHistoryItem>,
    selectedTimeRange: StatsTimeRange,
    onTimeRangeSelected: (StatsTimeRange) -> Unit,
    onBackClick: () -> Unit,
    onSongClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSectionIndex by remember { mutableIntStateOf(0) }
    val sections = StatsSection.values()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Listening Statistics",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your music habits & playback insights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Time Range Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(StatsTimeRange.values()) { range ->
                FilterChip(
                    selected = selectedTimeRange == range,
                    onClick = { onTimeRangeSelected(range) },
                    label = {
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTimeRange == range) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // 3. Section Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedSectionIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSectionIndex]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 2.5.dp
                )
            },
            divider = {}
        ) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = selectedSectionIndex == index,
                    onClick = { selectedSectionIndex = index },
                    text = {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedSectionIndex == index) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selectedSectionIndex == index) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Content Area by Tab
        when (sections[selectedSectionIndex]) {
            StatsSection.OVERVIEW -> {
                OverviewTabContent(
                    overview = overview,
                    topSongs = topSongs,
                    topArtists = topArtists,
                    topMoods = topMoods,
                    topGenres = topGenres,
                    topAlbums = topAlbums,
                    onViewMoreSongs = { selectedSectionIndex = StatsSection.SONGS.ordinal },
                    onViewMoreArtists = { selectedSectionIndex = StatsSection.ARTISTS.ordinal },
                    onViewMoreMoods = { selectedSectionIndex = StatsSection.MOODS_GENRES.ordinal },
                    onSongClick = onSongClick,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick
                )
            }
            StatsSection.SONGS -> {
                TopSongsTabContent(
                    songs = topSongs,
                    onSongClick = onSongClick
                )
            }
            StatsSection.ARTISTS -> {
                TopArtistsTabContent(
                    artists = topArtists,
                    onArtistClick = onArtistClick
                )
            }
            StatsSection.MOODS_GENRES -> {
                MoodsAndGenresTabContent(
                    moods = topMoods,
                    genres = topGenres
                )
            }
            StatsSection.HISTORY -> {
                HistoryTabContent(
                    history = playHistory,
                    onSongClick = onSongClick
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 1: Overview Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun OverviewTabContent(
    overview: ListeningOverviewStats,
    topSongs: List<SongStat>,
    topArtists: List<ArtistStat>,
    topMoods: List<MoodStat>,
    topGenres: List<GenreStat>,
    topAlbums: List<AlbumStat>,
    onViewMoreSongs: () -> Unit,
    onViewMoreArtists: () -> Unit,
    onViewMoreMoods: () -> Unit,
    onSongClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // High-level KPI Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricHighlightCard(
                        title = "LISTENING TIME",
                        mainValue = overview.formattedTotalDuration,
                        subValue = "${overview.totalHoursDecimal} total hours",
                        icon = Icons.Default.Equalizer,
                        modifier = Modifier.weight(1f)
                    )
                    MetricHighlightCard(
                        title = "TOTAL PLAYS",
                        mainValue = "${overview.totalPlays}",
                        subValue = "${overview.completedPlays} full • ${overview.skippedPlays} skips",
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricHighlightCard(
                        title = "COMPLETION RATE",
                        mainValue = "${overview.completionRate.toInt()}%",
                        subValue = if (overview.totalPlays > 0) "Finished vs skipped" else "No plays recorded",
                        icon = Icons.Default.Check,
                        progress = overview.completionRate / 100f,
                        modifier = Modifier.weight(1f)
                    )
                    MetricHighlightCard(
                        title = "UNIQUE CATALOG",
                        mainValue = "${overview.uniqueTracksCount} songs",
                        subValue = "across ${overview.uniqueArtistsCount} artists",
                        icon = Icons.Default.MusicNote,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Top Songs Preview (Top 5)
        if (topSongs.isNotEmpty()) {
            item {
                SectionHeaderWithAction(
                    title = "Top Songs",
                    actionLabel = "See All (${topSongs.size})",
                    onAction = onViewMoreSongs
                )
            }
            itemsIndexed(topSongs.take(5)) { index, song ->
                SongStatRow(
                    rank = index + 1,
                    song = song,
                    onClick = { onSongClick(song.trackId) }
                )
            }
        }

        // Top Artists Preview (Top 4)
        if (topArtists.isNotEmpty()) {
            item {
                SectionHeaderWithAction(
                    title = "Top Artists",
                    actionLabel = "See All (${topArtists.size})",
                    onAction = onViewMoreArtists
                )
            }
            itemsIndexed(topArtists.take(4)) { index, artist ->
                ArtistStatRow(
                    rank = index + 1,
                    artist = artist,
                    onClick = { onArtistClick(artist.artistName) }
                )
            }
        }

        // Moods & Genres Preview
        if (topMoods.isNotEmpty() || topGenres.isNotEmpty()) {
            item {
                SectionHeaderWithAction(
                    title = "Moods & Genres Breakdown",
                    actionLabel = "See All",
                    onAction = onViewMoreMoods
                )
            }

            if (topMoods.isNotEmpty()) {
                item {
                    Text(
                        text = "TOP MOODS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val maxMoodDuration = topMoods.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topMoods.take(4).forEach { mood ->
                            MoodProgressRow(mood = mood, maxDurationMs = maxMoodDuration)
                        }
                    }
                }
            }

            if (topGenres.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "TOP GENRES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val maxGenreDuration = topGenres.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topGenres.take(4).forEach { genre ->
                            GenreProgressRow(genre = genre, maxDurationMs = maxGenreDuration)
                        }
                    }
                }
            }
        }

        // Top Albums Preview
        if (topAlbums.isNotEmpty()) {
            item {
                Text(
                    text = "TOP ALBUMS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            itemsIndexed(topAlbums.take(4)) { index, album ->
                AlbumStatRow(
                    rank = index + 1,
                    album = album,
                    onClick = { onAlbumClick(album.albumId) }
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 2: Top Songs Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun TopSongsTabContent(
    songs: List<SongStat>,
    onSongClick: (String) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyStatsPlaceholder(message = "No song playback recorded in this time range.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "${songs.size} Most Played Songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        itemsIndexed(songs) { index, song ->
            SongStatRow(
                rank = index + 1,
                song = song,
                onClick = { onSongClick(song.trackId) }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 3: Top Artists Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun TopArtistsTabContent(
    artists: List<ArtistStat>,
    onArtistClick: (String) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyStatsPlaceholder(message = "No artist playback recorded in this time range.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "${artists.size} Top Artists",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        itemsIndexed(artists) { index, artist ->
            ArtistStatRow(
                rank = index + 1,
                artist = artist,
                onClick = { onArtistClick(artist.artistName) }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 4: Moods & Genres Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun MoodsAndGenresTabContent(
    moods: List<MoodStat>,
    genres: List<GenreStat>
) {
    if (moods.isEmpty() && genres.isEmpty()) {
        EmptyStatsPlaceholder(message = "No mood or genre tags recorded in this time range.")
        return
    }

    val maxMoodDuration = moods.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
    val maxGenreDuration = genres.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (moods.isNotEmpty()) {
            item {
                Text(
                    text = "MOOD BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(moods) { mood ->
                MoodProgressRow(mood = mood, maxDurationMs = maxMoodDuration)
            }
        }

        if (genres.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "GENRE BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(genres) { genre ->
                GenreProgressRow(genre = genre, maxDurationMs = maxGenreDuration)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 5: Recent Play History Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun HistoryTabContent(
    history: List<PlayHistoryItem>,
    onSongClick: (String) -> Unit
) {
    if (history.isEmpty()) {
        EmptyStatsPlaceholder(message = "No playback events recorded yet.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "${history.size} Recent Play Events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(history) { item ->
            PlayHistoryRow(
                item = item,
                onClick = { onSongClick(item.trackId) }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Reusable Rows and Components
// -------------------------------------------------------------------------------------------------

@Composable
private fun MetricHighlightCard(
    title: String,
    mainValue: String,
    subValue: String,
    icon: ImageVector,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = mainValue,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }

            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SongStatRow(
    rank: Int,
    song: SongStat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            ArtworkView(
                artworkUri = song.artworkUri?.let { Uri.parse(it) },
                size = 46.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = song.formattedDuration,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${song.playCount} ${if (song.playCount == 1) "play" else "plays"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArtistStatRow(
    rank: Int,
    artist: ArtistStat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            ArtworkView(
                artworkUri = artist.artworkUri?.let { Uri.parse(it) },
                size = 46.dp,
                shape = CircleShape
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.artistName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.trackCount} ${if (artist.trackCount == 1) "song" else "songs"} played",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = artist.formattedDuration,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${artist.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumStatRow(
    rank: Int,
    album: AlbumStat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            ArtworkView(
                artworkUri = album.artworkUri?.let { Uri.parse(it) },
                size = 46.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.albumTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artistName ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = album.formattedDuration,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${album.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MoodProgressRow(
    mood: MoodStat,
    maxDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = (mood.totalDurationMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mood.mood,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mood.formattedDuration,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${mood.playCount} plays)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun GenreProgressRow(
    genre: GenreStat,
    maxDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = (genre.totalDurationMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = genre.genre,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = genre.formattedDuration,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "(${genre.playCount} plays)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun PlayHistoryRow(
    item: PlayHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkView(
                artworkUri = item.artworkUri?.let { Uri.parse(it) },
                size = 44.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRelativeTimestamp(item.playbackTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (item.completionStatus) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (item.completionStatus) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = if (item.completionStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (item.completionStatus) "Completed" else "Skipped",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (item.completionStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = item.formattedPlayedDuration,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderWithAction(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onAction() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun EmptyStatsPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRelativeTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60_000L) return "Just now"
    if (diff < 3600_000L) return "${diff / 60_000L}m ago"

    val calNow = Calendar.getInstance()
    val calEvent = Calendar.getInstance().apply { timeInMillis = timestamp }
    val isToday = calNow.get(Calendar.YEAR) == calEvent.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calEvent.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    if (isToday) {
        return "Today, " + timeFormat.format(Date(timestamp))
    }

    calNow.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = calNow.get(Calendar.YEAR) == calEvent.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calEvent.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) {
        return "Yesterday, " + timeFormat.format(Date(timestamp))
    }

    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

