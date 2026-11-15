package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.LyricsLine
import com.example.offlinemusic.downloader.models.SyncedLyrics
import com.example.offlinemusic.data.repository.ArtistNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs

object LrclibClient {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Number? = null
    ): SyncedLyrics? = withContext(Dispatchers.IO) {
        val searchTitle = MetadataEnricher.cleanTrackTitle(title)
        val artists = ArtistNormalizer.splitCollaborators(artist)
            .map(MetadataEnricher::cleanArtistName)
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        val primaryArtist = artists.firstOrNull() ?: return@withContext null
        if (searchTitle.isBlank()) return@withContext null

        val url = "https://lrclib.net/api/search".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("track_name", searchTitle)
            .addQueryParameter("artist_name", primaryArtist)
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "OfflineMusic/1.0.0 (Android)")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) return@withContext null
                selectLyrics(JSONArray(body), searchTitle, artists, album, durationSeconds?.toDouble())
            }
        } catch (_: java.io.IOException) {
            null
        } catch (_: org.json.JSONException) {
            null
        }
    }

    private fun selectLyrics(
        tracks: JSONArray,
        title: String,
        artists: List<String>,
        album: String?,
        duration: Double?
    ): SyncedLyrics? {
        val titleKey = ArtistNormalizer.normalize(title)
        val artistKeys = artists.map(ArtistNormalizer::normalize).toSet()
        val albumKey = album?.let(ArtistNormalizer::normalize)
        val expectedDuration = duration?.takeIf { it > 0 }
        var bestScore = Int.MIN_VALUE
        var lyrics: SyncedLyrics? = null
        for (index in 0 until tracks.length()) {
            val track = tracks.optJSONObject(index) ?: continue
            if (ArtistNormalizer.normalize(track.optString("trackName")) != titleKey) continue
            val credits = ArtistNormalizer.splitCollaborators(track.optString("artistName"))
            if (credits.none { ArtistNormalizer.normalize(it) in artistKeys }) continue
            val candidateDuration = track.optDouble("duration").takeIf { it.isFinite() && it > 0 }
            val difference = expectedDuration?.let { expected -> candidateDuration?.let { abs(it - expected) } }
            if (difference != null && difference > 12) continue
            val plain = track.lyricsText("plainLyrics")
            val synced = track.lyricsText("syncedLyrics")
                ?.takeIf { expectedDuration == null || (difference != null && difference <= 3) }
            if (plain == null && synced == null) continue

            val sameAlbum = !albumKey.isNullOrEmpty() &&
                ArtistNormalizer.normalize(track.optString("albumName")) == albumKey
            val score = (if (synced != null) 100 else 0) +
                (if (sameAlbum) 20 else 0) - (difference?.toInt() ?: 4)
            if (score > bestScore) {
                bestScore = score
                lyrics = parseSyncedLyrics(synced, plain)
            }
        }
        return lyrics
    }

    private fun JSONObject.lyricsText(key: String): String? =
        optString(key).takeIf { !isNull(key) && it.isNotBlank() }

    /**
     * Parse standard .lrc format: "[00:15.30] Line lyrics"
     */
    fun parseLrcContent(content: String): SyncedLyrics {
        val lines = mutableListOf<LyricsLine>()
        val plainLines = mutableListOf<String>()

        for (rawLine in content.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue

            val matcher = LRC_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = matcher.group(3) ?: "00"
                val ms = when (msPart.length) {
                    2 -> (msPart.toLongOrNull() ?: 0L) * 10
                    else -> msPart.toLongOrNull() ?: 0L
                }
                val timestampMs = (min * 60 * 1000) + (sec * 1000) + ms
                val text = matcher.group(4)?.trim() ?: ""

                lines.add(LyricsLine(timestampMs, text))
                if (text.isNotBlank()) {
                    plainLines.add(text)
                }
            } else if (!trimmed.startsWith("[ti:") && !trimmed.startsWith("[ar:") && !trimmed.startsWith("[al:")) {
                plainLines.add(trimmed)
            }
        }

        return SyncedLyrics(
            lines = lines.sortedBy { it.timestampMs },
            plainLyrics = plainLines.joinToString("\n")
        )
    }

    private fun parseSyncedLyrics(synced: String?, plain: String?): SyncedLyrics {
        if (!synced.isNullOrBlank()) {
            return parseLrcContent(synced)
        }
        return SyncedLyrics(lines = emptyList(), plainLyrics = plain)
    }

    /**
     * Save .lrc file next to the target audio file
     */
    fun saveLrcFile(targetAudioFile: File, lrcContent: String): File? {
        return try {
            val parent = targetAudioFile.parentFile ?: return null
            val baseName = targetAudioFile.nameWithoutExtension
            val lrcFile = File(parent, "$baseName.lrc")
            lrcFile.writeText(lrcContent, Charsets.UTF_8)
            lrcFile
        } catch (_: Exception) {
            null
        }
    }

    fun saveToLrcFile(lyrics: SyncedLyrics, file: File) {
        val content = if (lyrics.lines.isNotEmpty()) {
            lyrics.lines.joinToString("\n") { line ->
                val totalSec = line.timestampMs / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                val ms = (line.timestampMs % 1000) / 10
                String.format("[%02d:%02d.%02d]%s", min, sec, ms, line.text)
            }
        } else {
            lyrics.plainLyrics ?: ""
        }
        if (content.isNotBlank()) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }
}

