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
    private var cachedVisitorData: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val VIDEO_ID_PATTERN = Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})")

    fun extractVideoId(urlOrId: String): String {
        var trimmed = urlOrId.trim()
        if (trimmed.startsWith("yt_")) {
            trimmed = trimmed.substring(3)
        }
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
            // Cache live visitorData token from search response for subsequent stream queries
            root.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }?.let {
                cachedVisitorData = it
            }

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

    private data class InnerTubeClientConfig(
        val clientContext: JSONObject,
        val clientNameHeader: String,
        val clientVersionHeader: String,
        val userAgentHeader: String
    )

    /**
     * Fetch direct streaming audio URL for a YouTube video using proven InnerTube clients
     */
    suspend fun getAudioStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanId = extractVideoId(videoId)

            // Step 1: Pre-fetch watch page to obtain live VISITOR_DATA token and signature timestamp (sts)
            var visitorData: String? = cachedVisitorData
            var sts = 20740L

            try {
                val watchUrl = "https://www.youtube.com/watch?v=$cleanId"
                val watchReq = Request.Builder()
                    .url(watchUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val watchHtml = httpClient.newCall(watchReq).execute().use { it.body?.string() ?: "" }
                val visMatcher = Pattern.compile("\"VISITOR_DATA\":\\s*\"([^\"]+)\"").matcher(watchHtml)
                if (visMatcher.find()) {
                    visitorData = visMatcher.group(1)
                    cachedVisitorData = visitorData
                }
                val stsMatcher = Pattern.compile("\"sts\":\\s*(\\d+)").matcher(watchHtml)
                if (stsMatcher.find()) {
                    sts = stsMatcher.group(1)?.toLongOrNull() ?: 20740L
                }
            } catch (_: Exception) {}

            // Step 2: Fallback visitorData via lightweight search ping if still null
            if (visitorData.isNullOrBlank()) {
                try {
                    val pingPayload = JSONObject().apply {
                        put("context", JSONObject().apply {
                            put("client", JSONObject().apply {
                                put("clientName", "WEB")
                                put("clientVersion", "2.20240101.00.00")
                            })
                        })
                        put("query", cleanId)
                    }
                    val pingReq = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/search")
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/json")
                        .post(pingPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val pingBody = httpClient.newCall(pingReq).execute().use { it.body?.string() ?: "" }
                    val pingJson = JSONObject(pingBody)
                    pingJson.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }?.let {
                        visitorData = it
                        cachedVisitorData = it
                    }
                } catch (_: Exception) {}
            }

            // Step 3: Query InnerTube Player API using VISIONOS client (no JS signature decipher required)
            val playerUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

            val clients = listOf(
                // Client 1: VISIONOS (Apple Reality Device - direct AAC / Opus streams)
                InnerTubeClientConfig(
                    clientContext = JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice17,1")
                        put("userAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                        put("osName", "visionOS")
                        put("osVersion", "26.5.23O471")
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    },
                    clientNameHeader = "101",
                    clientVersionHeader = "1.02",
                    userAgentHeader = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
                ),
                // Client 2: ANDROID_VR (Oculus Quest 3 fallback)
                InnerTubeClientConfig(
                    clientContext = JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.65.10")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("androidSdkVersion", 32)
                        put("userAgent", "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip")
                        put("osName", "Android")
                        put("osVersion", "12L")
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    },
                    clientNameHeader = "28",
                    clientVersionHeader = "1.65.10",
                    userAgentHeader = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                )
            )

            for (cfg in clients) {
                val payload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", cfg.clientContext)
                    })
                    put("videoId", cleanId)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("signatureTimestamp", sts)
                        })
                    })
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                val reqBuilder = Request.Builder()
                    .url(playerUrl)
                    .header("User-Agent", cfg.userAgentHeader)
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", cfg.clientNameHeader)
                    .header("X-YouTube-Client-Version", cfg.clientVersionHeader)
                    .header("Origin", "https://www.youtube.com")

                val currentVisitor = visitorData
                if (!currentVisitor.isNullOrBlank()) {
                    reqBuilder.header("X-Goog-Visitor-Id", currentVisitor)
                }

                val req = reqBuilder.post(payload.toString().toRequestBody("application/json".toMediaType())).build()

                try {
                    val respStr = httpClient.newCall(req).execute().use { it.body?.string() ?: "" }
                    if (respStr.isNotBlank()) {
                        val playerJson = JSONObject(respStr)
                        val status = playerJson.optJSONObject("playabilityStatus")?.optString("status", "")
                        android.util.Log.d("YouTubeDownloader", "Player response status for $cleanId (${cfg.clientNameHeader}): $status")
                        if (status == "OK" || status.isNullOrBlank()) {
                            val streamingData = playerJson.optJSONObject("streamingData")
                            val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")

                            if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                                var bestAudioUrl: String? = null
                                var highestBitrate = 0

                                // First pass: Look for high quality AAC (audio/mp4)
                                for (f in 0 until adaptiveFormats.length()) {
                                    val fmt = adaptiveFormats.getJSONObject(f)
                                    val mimeType = fmt.optString("mimeType", "")
                                    if (mimeType.startsWith("audio/mp4")) {
                                        val directUrl = fmt.optString("url", "")
                                        val bitrate = fmt.optInt("bitrate", 0)
                                        if (directUrl.isNotBlank() && bitrate >= highestBitrate) {
                                            highestBitrate = bitrate
                                            bestAudioUrl = directUrl
                                        }
                                    }
                                }

                                // Second pass: Look for any audio stream (Opus / WebM)
                                if (bestAudioUrl.isNullOrBlank()) {
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
                                }

                                if (!bestAudioUrl.isNullOrBlank()) {
                                    return@withContext Result.success(bestAudioUrl)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            Result.failure(Exception("Could not extract direct audio stream for YouTube video $cleanId"))
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
