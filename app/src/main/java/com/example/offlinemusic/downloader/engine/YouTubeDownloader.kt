package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadableTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeDownloader {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val VIDEO_ID_PATTERN = Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})")

    fun extractVideoId(urlOrId: String): String {
        val trimmed = urlOrId.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return trimmed
        }
        val matcher = VIDEO_ID_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1) ?: trimmed
        }
        return trimmed
    }

    /**
     * Search YouTube using innerTube API (matching lucida-flow youtube.rs search_innertube)
     */
    suspend fun search(query: String, limit: Int = 5): List<DownloadableTrack> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/youtubei/v1/search"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240101.00.00")
                    })
                })
                put("query", query)
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val root = JSONObject(body)
            val results = mutableListOf<DownloadableTrack>()

            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@withContext emptyList()

            for (s in 0 until contents.length()) {
                val sec = contents.getJSONObject(s)
                val items = sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val vr = item.optJSONObject("videoRenderer") ?: continue

                    val videoId = vr.optString("videoId", "")
                    if (videoId.isBlank()) continue

                    // Title
                    val titleObj = vr.optJSONObject("title")
                    val titleRuns = titleObj?.optJSONArray("runs")
                    val title = if (titleRuns != null && titleRuns.length() > 0) {
                        titleRuns.getJSONObject(0).optString("text", "Unknown Title")
                    } else {
                        titleObj?.optString("simpleText", "Unknown Title") ?: "Unknown Title"
                    }

                    // Channel / Owner
                    val ownerObj = vr.optJSONObject("ownerText") ?: vr.optJSONObject("longBylineText")
                    val ownerRuns = ownerObj?.optJSONArray("runs")
                    val channel = if (ownerRuns != null && ownerRuns.length() > 0) {
                        ownerRuns.getJSONObject(0).optString("text", "Unknown Channel")
                    } else {
                        "Unknown Channel"
                    }

                    // Duration
                    val duration = vr.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""

                    // Views
                    val viewsObj = vr.optJSONObject("viewCountText") ?: vr.optJSONObject("shortViewCountText")
                    val views = viewsObj?.optString("simpleText", "") ?: ""

                    // Thumbnail
                    val thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    results.add(
                        DownloadableTrack(
                            id = "yt_$videoId",
                            title = title,
                            artist = channel,
                            duration = duration,
                            views = views,
                            source = DownloadSource.YOUTUBE,
                            sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                            quality = "Highest Audio (320kbps MP3 / Best Opus)",
                            thumbnailUrl = thumbUrl
                        )
                    )

                    if (results.size >= limit) return@withContext results
                }
            }

            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch direct streaming audio URL for a YouTube video
     */
    suspend fun getAudioStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanId = extractVideoId(videoId)

            // 1. Try YouTube Android / iOS InnerTube Player
            val playerUrl = "https://www.youtube.com/youtubei/v1/player"

            val clients = listOf(
                JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.09.37")
                    put("androidSdkVersion", 34)
                },
                JSONObject().apply {
                    put("clientName", "IOS")
                    put("clientVersion", "19.29.1")
                    put("deviceModel", "iPhone16,2")
                }
            )

            for (clientObj in clients) {
                val payload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", clientObj)
                    })
                    put("videoId", cleanId)
                }

                val req = Request.Builder()
                    .url(playerUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                try {
                    val respStr = httpClient.newCall(req).execute().use { it.body?.string() ?: "" }
                    if (respStr.isNotBlank()) {
                        val playerJson = JSONObject(respStr)
                        val streamingData = playerJson.optJSONObject("streamingData")
                        val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")

                        if (adaptiveFormats != null) {
                            var bestAudioUrl: String? = null
                            var highestBitrate = 0

                            for (f in 0 until adaptiveFormats.length()) {
                                val fmt = adaptiveFormats.getJSONObject(f)
                                val mimeType = fmt.optString("mimeType", "")
                                if (mimeType.startsWith("audio/")) {
                                    val directUrl = fmt.optString("url", "")
                                    val bitrate = fmt.optInt("bitrate", 0)

                                    if (directUrl.isNotBlank() && bitrate >= highestBitrate) {
                                        highestBitrate = bitrate
                                        bestAudioUrl = directUrl
                                    }
                                }
                            }

                            if (!bestAudioUrl.isNullOrBlank()) {
                                return@withContext Result.success(bestAudioUrl)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Fallback to Cobalt API audio extraction
            try {
                val cobaltReq = Request.Builder()
                    .url("https://api.cobalt.tools/api/json")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(
                        JSONObject().apply {
                            put("url", "https://www.youtube.com/watch?v=$cleanId")
                            put("isAudioOnly", true)
                            put("aFormat", "mp3")
                        }.toString().toRequestBody("application/json".toMediaType())
                    )
                    .build()

                val cobaltRes = httpClient.newCall(cobaltReq).execute().use { it.body?.string() ?: "" }
                if (cobaltRes.isNotBlank()) {
                    val cobaltJson = JSONObject(cobaltRes)
                    val streamUrl = cobaltJson.optString("url", "")
                    if (streamUrl.isNotBlank()) {
                        return@withContext Result.success(streamUrl)
                    }
                }
            } catch (_: Exception) {}

            // 3. Fallback to Invidious audio stream endpoint
            val invidiousInstances = listOf("https://inv.tux.pizza", "https://invidious.nerdvpn.de", "https://vid.puffyan.us")
            for (instance in invidiousInstances) {
                try {
                    val invReq = Request.Builder()
                        .url("$instance/api/v1/videos/$cleanId")
                        .build()
                    val invRes = httpClient.newCall(invReq).execute().use { it.body?.string() ?: "" }
                    if (invRes.isNotBlank()) {
                        val invJson = JSONObject(invRes)
                        val formats = invJson.optJSONArray("adaptiveFormats")
                        if (formats != null) {
                            for (f in 0 until formats.length()) {
                                val fmt = formats.getJSONObject(f)
                                val type = fmt.optString("type", "")
                                if (type.startsWith("audio/")) {
                                    val streamUrl = fmt.optString("url", "")
                                    if (streamUrl.isNotBlank()) {
                                        return@withContext Result.success(streamUrl)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            Result.failure(Exception("Could not extract audio stream for YouTube video $cleanId"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download YouTube audio stream directly to file
     */
    suspend fun downloadTrack(
        videoUrlOrId: String,
        targetFile: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val streamUrlRes = getAudioStreamUrl(videoUrlOrId)
        val streamUrl = streamUrlRes.getOrElse { return@withContext Result.failure(it) }

        try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("YouTube stream download failed with code ${resp.code}"))
                }

                val body = resp.body ?: return@withContext Result.failure(Exception("Empty YouTube audio body"))
                val contentLength = body.contentLength()

                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    body.byteStream().use { bis ->
                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            if (contentLength > 0) {
                                val prog = totalDownloaded.toFloat() / contentLength.toFloat()
                                onProgress(prog, totalDownloaded, contentLength)
                            }
                        }
                    }
                    fos.flush()
                }

                onProgress(1.0f, targetFile.length(), targetFile.length())
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
