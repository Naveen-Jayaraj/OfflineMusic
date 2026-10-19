package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.LyricsLine
import com.example.offlinemusic.downloader.models.SyncedLyrics
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

object LrclibClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Number? = null
    ): SyncedLyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)

        // 1. Try exact /api/get if artist is known
        if (cleanArtist.isNotBlank()) {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
            urlBuilder?.addQueryParameter("track_name", cleanTitle)
            urlBuilder?.addQueryParameter("artist_name", cleanArtist)
            if (!album.isNullOrBlank() && album != "Single") {
                urlBuilder?.addQueryParameter("album_name", album)
            }
            if (durationSeconds != null && durationSeconds.toDouble() > 0) {
                urlBuilder?.addQueryParameter("duration", durationSeconds.toInt().toString())
            }

            urlBuilder?.build()?.let { url ->
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OfflineMusic/1.0.0 ( Android )")
                    .build()

                try {
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JSONObject(body)
                                val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
                                val synced = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                                if (plain != null || synced != null) {
                                    return@withContext parseSyncedLyrics(synced, plain)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Fallback to /api/search with query
        val searchBuilder = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
        if (cleanArtist.isNotBlank()) {
            searchBuilder?.addQueryParameter("track_name", cleanTitle)
            searchBuilder?.addQueryParameter("artist_name", cleanArtist)
        } else {
            searchBuilder?.addQueryParameter("q", "$cleanTitle $cleanArtist".trim())
        }

        searchBuilder?.build()?.let { url ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OfflineMusic/1.0.0 ( Android )")
                .build()

            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val arr = JSONArray(body)
                            if (arr.length() > 0) {
                                val first = arr.getJSONObject(0)
                                val plain = first.optString("plainLyrics", "").takeIf { it.isNotBlank() }
                                val synced = first.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                                if (plain != null || synced != null) {
                                    return@withContext parseSyncedLyrics(synced, plain)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        null
    }

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

    fun cleanTitle(title: String): String {
        var s = title.replace(Regex("[\\u2013\\u2014\\u2212]"), "-")
        for (ext in listOf(".mp3", ".flac", ".m4a", ".opus", ".wav", ".aac", ".ogg")) {
            if (s.lowercase().endsWith(ext)) {
                s = s.substring(0, s.length - ext.length)
            }
        }
        return s.replace(Regex("(?i)\\b(official\\s*video|official\\s*audio|lyrics|lyric\\s*video|hd|hq|4k|audio)\\b"), "")
            .replace("[]", "")
            .replace("()", "")
            .trim()
    }

    fun cleanArtist(artist: String): String {
        return artist.replace(Regex("(?i)\\b(vevo|official|channel|records|music)\\b"), "")
            .trim()
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

