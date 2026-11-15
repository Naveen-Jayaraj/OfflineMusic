package com.example.offlinemusic.data.repository

import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.database.entities.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manages dynamic, ephemeral playlists that adapt to user listening statistics
 * and automatically rotate/delete every 6 hours.
 *
 * Evaluates play events with heavy recency priority (last 24 hours weighted most heavily),
 * creating personalized mixes for:
 * 1. Recent Jam (Most replayed tracks)
 * 2. Top Mood Mix (e.g. Happy, Energetic, Chill)
 * 3. Artist Spotlight (Most listened artist)
 * 4. Genre Mix (Top genre in recent rotation)
 * 5. Language Mix (Top language in recent rotation)
 * 6. Time-of-Day Vibe (Morning, Afternoon, Evening, Late Night)
 */
class DynamicPlaylistManager(
    private val database: AppDatabase,
    private val preferences: MusicPreferences
) {
    companion object {
        const val PLAYLIST_TYPE_DYNAMIC = "DYNAMIC_6H"
        const val DURATION_6_HOURS_MS = 6 * 3600 * 1000L // 6 hours lifecycle
    }

    /**
     * Checks if current dynamic playlists have expired (>= 6 hours old).
     * If expired or [force] is true, deletes old playlists and generates fresh ones.
     */
    suspend fun checkAndRefreshDynamicPlaylists(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastRefresh = preferences.lastDynamicMixTimestamp
        val existingDynamics = database.playlistDao().getPlaylistsByType(PLAYLIST_TYPE_DYNAMIC)

        val isExpired = existingDynamics.isEmpty() ||
                (now - lastRefresh >= DURATION_6_HOURS_MS) ||
                (existingDynamics.any { now - it.createdAt >= DURATION_6_HOURS_MS })

        if (!isExpired && !force) {
            return@withContext false
        }

        // 1. Delete all expired 6-hour playlists (cascade will delete playlist_tracks)
        for (pl in existingDynamics) {
            database.playlistDao().deletePlaylistById(pl.id)
        }

        // 2. Generate new dynamic playlists based on latest user statistics
        generateDynamicPlaylists(now)

        // 3. Update generation timestamp
        preferences.lastDynamicMixTimestamp = now
        true
    }

    private suspend fun generateDynamicPlaylists(now: Long) {
        val allTracks = database.trackDao().getAllTracks()
        if (allTracks.isEmpty()) return

        val tracksById = allTracks.associateBy { it.id }

        // Fetch play events from the last 7 days for scoring
        val since7Days = now - (7 * 24 * 3600 * 1000L)
        val playEvents = database.playEventDao().getPlayEventsWithTrackInfo(since7Days)

        // Score aggregators
        val trackScores = mutableMapOf<String, Float>()
        val moodScores = mutableMapOf<String, Float>()
        val artistScores = mutableMapOf<String, Float>()
        val genreScores = mutableMapOf<String, Float>()
        val languageScores = mutableMapOf<String, Float>()

        // 1. Aggregate play events with heavy recency weighting
        for (event in playEvents) {
            val ageHours = (now - event.playbackTimestamp).coerceAtLeast(0) / (3600 * 1000f)
            val recencyMultiplier = when {
                ageHours <= 12f -> 6.0f   // Last 12h: highest priority (6x)
                ageHours <= 24f -> 4.0f   // Last 24h: high priority (4x)
                ageHours <= 72f -> 2.5f   // 1 to 3 days: medium priority (2.5x)
                else -> 1.0f              // Older than 3 days: standard priority (1x)
            }
            val completionMultiplier = if (event.completionStatus) 1.25f else 1.0f
            val score = recencyMultiplier * completionMultiplier

            // Track score
            trackScores[event.trackId] = (trackScores[event.trackId] ?: 0f) + score

            // Mood score
            if (!event.mood.isNullOrBlank()) {
                val moodKey = event.mood.trim()
                moodScores[moodKey] = (moodScores[moodKey] ?: 0f) + score
            }

            // Artist score
            if (event.artistName.isNotBlank() && event.artistName != "Unknown Artist") {
                val artistKey = event.artistName.trim()
                artistScores[artistKey] = (artistScores[artistKey] ?: 0f) + score
            }

            // Genre score
            if (!event.genre.isNullOrBlank() && event.genre != "Unknown") {
                val genreKey = event.genre.trim()
                genreScores[genreKey] = (genreScores[genreKey] ?: 0f) + score
            }

            // Language score
            if (!event.language.isNullOrBlank()) {
                val langKey = event.language.trim()
                languageScores[langKey] = (languageScores[langKey] ?: 0f) + score
            }
        }

        // Factor in baseline play counts from tracks
        for (track in allTracks) {
            if (track.playCount > 0) {
                val baseScore = track.playCount * 0.4f
                trackScores[track.id] = (trackScores[track.id] ?: 0f) + baseScore
                if (!track.mood.isNullOrBlank()) {
                    val m = track.mood.trim()
                    moodScores[m] = (moodScores[m] ?: 0f) + baseScore
                }
                if (!track.artist.isNullOrBlank() && track.artist != "Unknown Artist") {
                    val a = track.artist.trim()
                    artistScores[a] = (artistScores[a] ?: 0f) + baseScore
                }
                if (!track.genre.isNullOrBlank() && track.genre != "Unknown") {
                    val g = track.genre.trim()
                    genreScores[g] = (genreScores[g] ?: 0f) + baseScore
                }
                if (!track.language.isNullOrBlank()) {
                    val l = track.language.trim()
                    languageScores[l] = (languageScores[l] ?: 0f) + baseScore
                }
            }
        }

        // ==========================================
        // 1. RECENT JAM ("On Heavy Rotation")
        // ==========================================
        val sortedJamTracks = trackScores.entries
            .sortedByDescending { it.value }
            .mapNotNull { tracksById[it.key] }
            .toMutableList()

        // Fallback or fill up to 25 songs using recently played / most played
        if (sortedJamTracks.size < 15) {
            val mostPlayed = allTracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            for (t in mostPlayed) {
                if (sortedJamTracks.none { it.id == t.id }) {
                    sortedJamTracks.add(t)
                }
                if (sortedJamTracks.size >= 25) break
            }
        }
        if (sortedJamTracks.size < 10) {
            val recentAdded = allTracks.sortedByDescending { it.dateAdded }
            for (t in recentAdded) {
                if (sortedJamTracks.none { it.id == t.id }) {
                    sortedJamTracks.add(t)
                }
                if (sortedJamTracks.size >= 25) break
            }
        }

        if (sortedJamTracks.isNotEmpty()) {
            createPlaylistWithTracks(
                id = "dynamic_recent_jam",
                name = "Recent Jam",
                description = "Your most replayed jams right now • Refreshes every 6 hours",
                tracks = sortedJamTracks.take(25),
                createdAt = now
            )
        }

        // ==========================================
        // 2. MOOD SPECIFIC PLAYLIST (e.g. "Happy Vibes")
        // ==========================================
        val topMoodEntry = moodScores.entries.maxByOrNull { it.value }
            ?: allTracks.filter { !it.mood.isNullOrBlank() }
                .groupBy { it.mood!!.trim() }
                .maxByOrNull { it.value.size }
                ?.let { java.util.AbstractMap.SimpleEntry(it.key, it.value.size.toFloat()) }

        if (topMoodEntry != null) {
            val moodName = topMoodEntry.key
            val moodTracks = allTracks.filter { it.mood?.contains(moodName, ignoreCase = true) == true }
                .sortedWith(
                    compareByDescending<TrackEntity> { trackScores[it.id] ?: 0f }
                        .thenByDescending { it.playCount }
                        .thenBy { it.title }
                )
            if (moodTracks.size >= 2) {
                val formattedMood = moodName.replaceFirstChar { it.uppercase() }
                createPlaylistWithTracks(
                    id = "dynamic_mood_${moodName.lowercase().replace("\\s+".toRegex(), "_")}",
                    name = "$formattedMood Vibes",
                    description = "Heavy replay $formattedMood songs • Refreshes every 6 hours",
                    tracks = moodTracks.take(25),
                    createdAt = now
                )
            }
        }

        // ==========================================
        // 3. ARTIST SPECIFIC PLAYLIST
        // ==========================================
        val topArtistEntry = artistScores.entries.maxByOrNull { it.value }
            ?: allTracks.filter { !it.artist.isNullOrBlank() && it.artist != "Unknown Artist" }
                .groupBy { it.artist!!.trim() }
                .maxByOrNull { it.value.size }
                ?.let { java.util.AbstractMap.SimpleEntry(it.key, it.value.size.toFloat()) }

        if (topArtistEntry != null) {
            val artistName = topArtistEntry.key
            val artistTracks = allTracks.filter { it.artist?.contains(artistName, ignoreCase = true) == true }
                .sortedWith(
                    compareByDescending<TrackEntity> { trackScores[it.id] ?: 0f }
                        .thenByDescending { it.playCount }
                        .thenBy { it.title }
                )
            if (artistTracks.size >= 2) {
                createPlaylistWithTracks(
                    id = "dynamic_artist_${artistName.lowercase().replace("\\s+".toRegex(), "_").take(24)}",
                    name = "Spotlight: $artistName",
                    description = "Heavy rotation by $artistName • Refreshes every 6 hours",
                    tracks = artistTracks.take(25),
                    createdAt = now
                )
            }
        }

        // ==========================================
        // 4. GENRE SPECIFIC PLAYLIST
        // ==========================================
        val topGenreEntry = genreScores.entries.maxByOrNull { it.value }
            ?: allTracks.filter { !it.genre.isNullOrBlank() && it.genre != "Unknown" }
                .groupBy { it.genre!!.trim() }
                .maxByOrNull { it.value.size }
                ?.let { java.util.AbstractMap.SimpleEntry(it.key, it.value.size.toFloat()) }

        if (topGenreEntry != null) {
            val genreName = topGenreEntry.key
            val genreTracks = allTracks.filter { it.genre?.contains(genreName, ignoreCase = true) == true }
                .sortedWith(
                    compareByDescending<TrackEntity> { trackScores[it.id] ?: 0f }
                        .thenByDescending { it.playCount }
                        .thenBy { it.title }
                )
            if (genreTracks.size >= 2) {
                val formattedGenre = genreName.replaceFirstChar { it.uppercase() }
                createPlaylistWithTracks(
                    id = "dynamic_genre_${genreName.lowercase().replace("\\s+".toRegex(), "_").take(24)}",
                    name = "Genre Mix: $formattedGenre",
                    description = "Top picks from your favorite genre • Refreshes every 6 hours",
                    tracks = genreTracks.take(25),
                    createdAt = now
                )
            }
        }

        // ==========================================
        // 5. LANGUAGE SPECIFIC PLAYLIST
        // ==========================================
        val topLanguageEntry = languageScores.entries.maxByOrNull { it.value }
            ?: allTracks.filter { !it.language.isNullOrBlank() }
                .groupBy { it.language!!.trim() }
                .maxByOrNull { it.value.size }
                ?.let { java.util.AbstractMap.SimpleEntry(it.key, it.value.size.toFloat()) }

        if (topLanguageEntry != null) {
            val langName = topLanguageEntry.key
            val langTracks = allTracks.filter { it.language?.equals(langName, ignoreCase = true) == true }
                .sortedWith(
                    compareByDescending<TrackEntity> { trackScores[it.id] ?: 0f }
                        .thenByDescending { it.playCount }
                        .thenBy { it.title }
                )
            if (langTracks.size >= 2) {
                val formattedLang = langName.replaceFirstChar { it.uppercase() }
                createPlaylistWithTracks(
                    id = "dynamic_lang_${langName.lowercase().replace("\\s+".toRegex(), "_").take(24)}",
                    name = "$formattedLang Wave",
                    description = "Top tracks in $formattedLang • Refreshes every 6 hours",
                    tracks = langTracks.take(25),
                    createdAt = now
                )
            }
        }

        // ==========================================
        // 6. TIME-OF-DAY VIBE (Contextual for this 6h window)
        // ==========================================
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (vibeName, vibeDesc) = when (currentHour) {
            in 5..11 -> "Morning Kickstart" to "Upbeat tracks to start your day • Refreshes every 6 hours"
            in 12..16 -> "Afternoon Flow" to "Focus & rhythm for your afternoon • Refreshes every 6 hours"
            in 17..21 -> "Evening Chillout" to "Smooth tunes to relax into the evening • Refreshes every 6 hours"
            else -> "Late Night Drift" to "Atmospheric jams for the midnight hours • Refreshes every 6 hours"
        }

        val vibeTracks = allTracks.shuffled().sortedByDescending { trackScores[it.id] ?: 0f }
        if (vibeTracks.isNotEmpty()) {
            createPlaylistWithTracks(
                id = "dynamic_time_vibe",
                name = vibeName,
                description = vibeDesc,
                tracks = vibeTracks.take(25),
                createdAt = now
            )
        }
    }

    private suspend fun createPlaylistWithTracks(
        id: String,
        name: String,
        description: String,
        tracks: List<TrackEntity>,
        createdAt: Long
    ) {
        if (tracks.isEmpty()) return

        val artworkUri = tracks.firstOrNull { it.artworkUri != null }?.artworkUri

        val playlist = PlaylistEntity(
            id = id,
            name = name,
            description = description,
            artworkUri = artworkUri,
            playlistType = PLAYLIST_TYPE_DYNAMIC,
            createdAt = createdAt,
            sortOrder = "MANUAL"
        )
        database.playlistDao().insertOrUpdatePlaylist(playlist)

        // Clear previous tracks just in case
        database.playlistDao().clearPlaylistTracks(id)

        val trackEntities = tracks.mapIndexed { idx, track ->
            PlaylistTrackEntity(
                playlistId = id,
                trackId = track.id,
                position = idx,
                addedTimestamp = createdAt
            )
        }
        database.playlistDao().insertPlaylistTracks(trackEntities)
    }
}
