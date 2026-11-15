package com.example.offlinemusic.downloader.engine

import android.content.Context
import android.util.Log
import com.example.offlinemusic.data.remote.ApiClient
import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadableTrack
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Modern Lucida Downloader engine powered by the official Lucida package (https://www.npmjs.com/package/lucida).
 *
 * It connects to the Lucida NPM REST Bridge (lucida-server) when available,
 * and includes a native direct client for zero-dependency standalone execution.
 */
internal class LucidaDownloader(context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val searchClient = httpClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedSoundcloudClientId: String? = null

    /**
     * Search tracks via Lucida NPM Bridge or native streamer
     */
    suspend fun search(query: String, limit: Int = 10): Result<List<DownloadableTrack>> = withContext(Dispatchers.IO) {
        if (query.isBlank() || limit <= 0) return@withContext Result.success(emptyList())

        // 1. Try Lucida NPM REST Bridge first (e.g. local or remote backend)
        for (baseUrl in getCandidateServerUrls()) {
            try {
                val results = searchViaServer(baseUrl, query, limit)
                if (results.isNotEmpty()) {
                    return@withContext Result.success(results)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Lucida server check on $baseUrl skipped: ${e.message}")
            }
        }

        // 2. Native Lucida Soundcloud streamer fallback (matches lucida/streamers/soundcloud)
        try {
            val tracks = searchViaSoundcloudStreamer(query, limit)
            Result.success(tracks)
        } catch (e: Exception) {
            Log.w(TAG, "Lucida native Soundcloud search failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download track audio stream from Lucida
     */
    suspend fun downloadTrack(
        trackUrl: String,
        targetFile: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        // 1. Try downloading via Lucida NPM Bridge
        for (baseUrl in getCandidateServerUrls()) {
            try {
                val streamUrl = "$baseUrl/api/stream?url=${java.net.URLEncoder.encode(trackUrl, "UTF-8")}"
                val req = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body ?: continue
                    val contentLength = body.contentLength()
                    var bytesCopied = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                val prog = if (contentLength > 0) bytesCopied.toFloat() / contentLength else 0.5f
                                onProgress(prog.coerceIn(0f, 1f), bytesCopied, contentLength)
                                bytes = input.read(buffer)
                            }
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        return@withContext Result.success(targetFile)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Server stream on $baseUrl failed: ${e.message}")
            }
        }

        // 2. Native Lucida Soundcloud stream resolution fallback
        try {
            downloadViaNativeSoundcloud(trackUrl, targetFile, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Native Lucida download failed", e)
            Result.failure(e)
        }
    }

    private fun getCandidateServerUrls(): List<String> {
        return listOf(
            "http://10.0.2.2:3000",   // Android Emulator host
            "http://127.0.0.1:3000",  // Localhost (if termux/local)
            "${ApiClient.BASE_URL}lucida".removeSuffix("/") // Render backend route if deployed
        )
    }

    private fun searchViaServer(baseUrl: String, query: String, limit: Int): List<DownloadableTrack> {
        val url = "$baseUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit"
        val req = Request.Builder().url(url).build()
        val response = searchClient.newCall(req).execute()
        if (!response.isSuccessful) return emptyList()

        val jsonStr = response.body?.string() ?: return emptyList()
        val array = JsonParser.parseString(jsonStr).asJsonArray
        val tracks = mutableListOf<DownloadableTrack>()

        for (elem in array) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            tracks.add(
                DownloadableTrack(
                    id = obj.get("id")?.asString ?: "lucida_${System.currentTimeMillis()}",
                    title = obj.get("title")?.asString ?: "Unknown Title",
                    artist = obj.get("artist")?.asString ?: "Unknown Artist",
                    album = obj.get("album")?.asString.orEmpty(),
                    durationMs = obj.get("durationMs")?.asLong ?: 0L,
                    duration = obj.get("duration")?.asString.orEmpty(),
                    source = DownloadSource.LUCIDA,
                    sourceUrl = obj.get("sourceUrl")?.asString.orEmpty(),
                    quality = obj.get("quality")?.asString ?: "Lucida Lossless",
                    thumbnailUrl = obj.get("thumbnailUrl")?.takeIf { !it.isJsonNull }?.asString
                )
            )
        }
        return tracks
    }

    /**
     * Native Lucida Soundcloud streamer implementation
     */
    private fun searchViaSoundcloudStreamer(query: String, limit: Int): List<DownloadableTrack> {
        val clientId = getSoundcloudClientId() ?: return emptyList()
        val searchUrl = "https://api-v2.soundcloud.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&offset=0&limit=$limit&client_id=$clientId"

        val req = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://soundcloud.com/")
            .build()

        val resp = searchClient.newCall(req).execute()
        if (!resp.isSuccessful) return emptyList()

        val body = resp.body?.string() ?: return emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val collection = root.getAsJsonArray("collection") ?: return emptyList()

        val results = mutableListOf<DownloadableTrack>()
        for (item in collection) {
            val obj = item as? JsonObject ?: continue
            val kind = obj.get("kind")?.asString ?: continue
            if (kind != "track") continue

            val id = obj.get("id")?.asLong ?: continue
            val title = obj.get("title")?.asString ?: "Unknown Track"
            val permalink = obj.get("permalink_url")?.asString ?: continue
            val user = obj.getAsJsonObject("user")
            val artistName = user?.get("username")?.asString ?: "Unknown Artist"
            val durationMs = obj.get("duration")?.asLong ?: 0L
            val artworkUrl = obj.get("artwork_url")?.takeIf { !it.isJsonNull }?.asString
                ?: user?.get("avatar_url")?.takeIf { !it.isJsonNull }?.asString

            results.add(
                DownloadableTrack(
                    id = "lucida_sc_$id",
                    title = title,
                    artist = artistName,
                    album = "",
                    durationMs = durationMs,
                    duration = formatDuration(durationMs),
                    source = DownloadSource.LUCIDA,
                    sourceUrl = permalink,
                    quality = "Soundcloud (Lucida)",
                    thumbnailUrl = artworkUrl?.replace("-large", "-t500x500")
                )
            )
        }
        return results
    }

    private fun downloadViaNativeSoundcloud(
        trackUrl: String,
        targetFile: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> {
        val clientId = getSoundcloudClientId() ?: throw IOException("Could not obtain Soundcloud client ID")

        // 1. Resolve track metadata / transcodings
        val resolveUrl = "https://api-v2.soundcloud.com/resolve?url=${java.net.URLEncoder.encode(trackUrl, "UTF-8")}&client_id=$clientId"
        val req = Request.Builder()
            .url(resolveUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://soundcloud.com/")
            .build()

        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IOException("Failed to resolve Soundcloud track, HTTP ${resp.code}")

        val json = JSONObject(resp.body?.string() ?: "")
        val media = json.optJSONObject("media") ?: throw IOException("Missing media in track metadata")
        val transcodings = media.optJSONArray("transcodings") ?: throw IOException("Missing transcodings")
        val trackAuth = json.optString("track_authorization", "")

        // Prioritize progressive MP3 if available, otherwise AAC/MP3 HLS
        var selectedUrl: String? = null
        for (i in 0 until transcodings.length()) {
            val t = transcodings.getJSONObject(i)
            val format = t.optJSONObject("format")
            if (format?.optString("protocol") == "progressive") {
                selectedUrl = t.getString("url")
                break
            }
        }
        if (selectedUrl == null && transcodings.length() > 0) {
            selectedUrl = transcodings.getJSONObject(0).getString("url")
        }

        if (selectedUrl == null) throw IOException("No usable audio streams found")

        // 2. Fetch direct CDN stream URL
        val streamUrl = "$selectedUrl?client_id=$clientId" +
                (if (trackAuth.isNotBlank()) "&track_authorization=$trackAuth" else "")
        val streamReq = Request.Builder().url(streamUrl).header("User-Agent", USER_AGENT).build()
        val streamResp = httpClient.newCall(streamReq).execute()
        if (!streamResp.isSuccessful) throw IOException("Failed to fetch stream auth URL")

        val streamJson = JSONObject(streamResp.body?.string() ?: "")
        val cdnUrl = streamJson.optString("url")
        if (cdnUrl.isBlank()) throw IOException("CDN audio URL was empty")

        // 3. Download the audio file
        val audioReq = Request.Builder().url(cdnUrl).header("User-Agent", USER_AGENT).build()
        val audioResp = httpClient.newCall(audioReq).execute()
        if (!audioResp.isSuccessful) throw IOException("Failed to download audio data, HTTP ${audioResp.code}")

        val audioBody = audioResp.body ?: throw IOException("Empty audio response body")
        val totalBytes = audioBody.contentLength()
        var downloadedBytes = 0L

        audioBody.byteStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    downloadedBytes += bytes
                    val prog = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0.5f
                    onProgress(prog.coerceIn(0f, 1f), downloadedBytes, totalBytes)
                    bytes = input.read(buffer)
                }
            }
        }

        return Result.success(targetFile)
    }

    private fun getSoundcloudClientId(): String? {
        cachedSoundcloudClientId?.let { return it }
        try {
            val req = Request.Builder()
                .url("https://soundcloud.com/")
                .header("User-Agent", USER_AGENT)
                .build()
            val html = httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }

            val scriptMatcher = SCRIPT_REGEX.matcher(html)
            while (scriptMatcher.find()) {
                val scriptUrl = scriptMatcher.group(1) ?: continue
                if (!scriptUrl.startsWith("http")) continue
                val scriptReq = Request.Builder().url(scriptUrl).build()
                val scriptText = httpClient.newCall(scriptReq).execute().use { it.body?.string().orEmpty() }
                val clientMatcher = CLIENT_ID_REGEX.matcher(scriptText)
                if (clientMatcher.find()) {
                    val id = clientMatcher.group(1)
                    if (!id.isNullOrBlank()) {
                        cachedSoundcloudClientId = id
                        return id
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Soundcloud client ID: ${e.message}")
        }
        // Fallback default working key
        return "qLcgHhXgeEFFaSolTB9I71LBsqncsD93"
    }

    companion object {
        private const val TAG = "LucidaDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val SCRIPT_REGEX = Pattern.compile("<script crossorigin src=\"([^\"]+)\"")
        private val CLIENT_ID_REGEX = Pattern.compile("client_id:\"([a-zA-Z0-9]+)\"")

        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
