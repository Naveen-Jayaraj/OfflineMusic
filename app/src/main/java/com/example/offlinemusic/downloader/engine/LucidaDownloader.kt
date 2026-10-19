package com.example.offlinemusic.downloader.engine

import android.util.Base64
import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadableTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object LucidaDownloader {

    private const val BASE_URL = "https://lucida.to"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val TOKEN_REGEX = Pattern.compile("token:\\s*\"([^\"]+)\"")
    private val EXPIRY_REGEX = Pattern.compile("tokenExpiry:\\s*(\\d+)")

    /**
     * Search Lucida (Amazon Music / Lossless FLAC) for tracks
     */
    suspend fun search(query: String, limit: Int = 5): List<DownloadableTrack> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$BASE_URL/search?service=amazon&country=US&query=$encodedQuery"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()

            val html = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: ""
            }

            val tracks = mutableListOf<DownloadableTrack>()

            // Extract SvelteKit data JSON array: "const data = ["
            val marker = "const data = ["
            val startIdx = html.indexOf(marker)
            if (startIdx != -1) {
                val jsonStart = startIdx + "const data = ".length
                var bracketCount = 0
                var inString = false
                var escapeNext = false
                var endIdx = -1

                for (i in jsonStart until html.length) {
                    val c = html[i]
                    if (escapeNext) {
                        escapeNext = false
                        continue
                    }
                    if (c == '\\') {
                        escapeNext = true
                        continue
                    }
                    if (c == '"') {
                        inString = !inString
                        continue
                    }
                    if (!inString) {
                        if (c == '[' || c == '{') {
                            bracketCount++
                        } else if (c == ']' || c == '}') {
                            bracketCount--
                            if (bracketCount == 0) {
                                endIdx = i + 1
                                break
                            }
                        }
                    }
                }

                if (endIdx != -1) {
                    val jsonStr = html.substring(jsonStart, endIdx)
                    try {
                        val arr = JSONArray(jsonStr)
                        if (arr.length() > 1) {
                            val dataObj = arr.getJSONObject(1)
                            val resObj = dataObj.optJSONObject("data")?.optJSONObject("results")
                            val tracksArr = resObj?.optJSONObject("results")?.optJSONArray("tracks")

                            if (tracksArr != null) {
                                for (i in 0 until minOf(tracksArr.length(), limit)) {
                                    val t = tracksArr.getJSONObject(i)
                                    val title = t.optString("title", "Unknown Title")
                                    val albumObj = t.optJSONObject("album")
                                    val album = albumObj?.optString("title", "") ?: ""
                                    val url = t.optString("url", "")
                                    val thumb = albumObj?.optString("cover", "")?.takeIf { it.isNotBlank() }

                                    val artistsArr = t.optJSONArray("artists")
                                    val artistsList = mutableListOf<String>()
                                    if (artistsArr != null) {
                                        for (a in 0 until artistsArr.length()) {
                                            val name = artistsArr.getJSONObject(a).optString("name", "")
                                            if (name.isNotBlank()) artistsList.add(name)
                                        }
                                    }
                                    val artist = if (artistsList.isNotEmpty()) artistsList.joinToString(", ") else "Unknown Artist"

                                    if (url.isNotBlank()) {
                                        tracks.add(
                                            DownloadableTrack(
                                                id = "lucida_${url.hashCode()}",
                                                title = title,
                                                artist = artist,
                                                album = album,
                                                source = DownloadSource.LUCIDA,
                                                sourceUrl = url,
                                                quality = "Lossless FLAC (Amazon Music)",
                                                thumbnailUrl = thumb
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            tracks
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Download track directly from Lucida lossless stream pipeline
     */
    suspend fun downloadTrack(
        trackUrl: String,
        targetFile: File,
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pageUrl = "$BASE_URL/?url=$trackUrl"
            val pageReq = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .build()

            val html = httpClient.newCall(pageReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to load Lucida page, status ${resp.code}"))
                }
                resp.body?.string() ?: ""
            }

            val tokenMatcher = TOKEN_REGEX.matcher(html)
            val expiryMatcher = EXPIRY_REGEX.matcher(html)

            if (!tokenMatcher.find() || !expiryMatcher.find()) {
                return@withContext Result.failure(Exception("Could not extract authorization token from Lucida"))
            }

            val rawToken = tokenMatcher.group(1) ?: return@withContext Result.failure(Exception("Missing token"))
            val expiry = expiryMatcher.group(1)?.toLongOrNull() ?: 0L

            // Double Base64 decode to retrieve primary token
            val step1 = Base64.decode(rawToken, Base64.DEFAULT)
            val step2 = Base64.decode(step1, Base64.DEFAULT)
            val primaryToken = String(step2, Charsets.UTF_8)

            // Initialize server stream rip
            val payload = JSONObject().apply {
                put("url", trackUrl)
                put("metadata", true)
                put("compat", false)
                put("private", false)
                put("handoff", true)
                put("account", JSONObject().apply {
                    put("type", "country")
                    put("id", "auto")
                })
                put("upload", JSONObject().apply {
                    put("enabled", false)
                    put("service", "pixeldrain")
                })
                put("downscale", "original")
                put("token", JSONObject().apply {
                    put("primary", primaryToken)
                    put("expiry", expiry)
                })
            }

            val initUrl = "$BASE_URL/api/load?url=%2Fapi%2Ffetch%2Fstream%2Fv2"
            val initReq = Request.Builder()
                .url(initUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val initResStr = httpClient.newCall(initReq).execute().use { it.body?.string() ?: "" }
            val initJson = JSONObject(initResStr)

            if (initJson.optBoolean("success", false) != true) {
                val err = initJson.optString("error", "Failed to initialize server stream")
                return@withContext Result.failure(Exception("Lucida error: $err"))
            }

            val handoff = initJson.optString("handoff", "")
            val serverName = initJson.optString("name", "katze")

            if (handoff.isBlank()) {
                return@withContext Result.failure(Exception("Missing server handoff ID"))
            }

            // Poll status until completed
            val statusUrl = "$BASE_URL/api/load?url=%2Fapi%2Ffetch%2Frequest%2F$handoff&force=$serverName"
            val startTime = System.currentTimeMillis()
            val timeout = 25_000L // 25 seconds for fast fallback
            android.util.Log.d("LucidaDownloader", "Polling Lucida rip status for handoff: $handoff")

            var completed = false
            while (System.currentTimeMillis() - startTime < timeout) {
                delay(750)
                val statusReq = Request.Builder()
                    .url(statusUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$BASE_URL/")
                    .build()

                val stStr = httpClient.newCall(statusReq).execute().use { it.body?.string() ?: "" }
                val stJson = JSONObject(stStr)

                val status = stJson.optString("status", "")
                if (status == "completed") {
                    completed = true
                    android.util.Log.d("LucidaDownloader", "Lucida stream rip completed successfully")
                    break
                } else if (status == "error") {
                    val msg = stJson.optString("message", "Unknown error during stream rip")
                    android.util.Log.w("LucidaDownloader", "Lucida rip error: $msg")
                    return@withContext Result.failure(Exception("Lucida rip failed: $msg"))
                }
            }

            if (!completed) {
                android.util.Log.w("LucidaDownloader", "Lucida stream timed out after 25s")
                return@withContext Result.failure(Exception("Lucida stream timed out"))
            }

            // Download stream
            val downloadUrl = "$BASE_URL/api/load?url=%2Fapi%2Ffetch%2Frequest%2F$handoff%2Fdownload&force=$serverName&redirect=true"
            val dlReq = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "*/*")
                .build()

            httpClient.newCall(dlReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed with HTTP ${resp.code}"))
                }

                val body = resp.body ?: return@withContext Result.failure(Exception("Empty download body"))
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
