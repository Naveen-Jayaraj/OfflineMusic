package com.example.offlinemusic.downloader.engine

import android.content.Context
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadableTrack
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

internal class LucidaDownloader(context: Context) {

    private val browserUserAgent by lazy {
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault(USER_AGENT)
    }
    private val cookieManager by lazy { runCatching { CookieManager.getInstance() }.getOrNull() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (!url.isHttps || url.host != HOST || url.port != 443) {
                return@addNetworkInterceptor chain.proceed(request)
            }

            // Use the same browser identity as the in-app verification page.
            val clearance = cookieManager?.getCookie(url.toString())
                ?.split(';')
                ?.firstOrNull { it.trimStart().startsWith("cf_clearance=") }
                ?.trim()
            val verifiedRequest = request.newBuilder().header("User-Agent", browserUserAgent)
            clearance?.let { verifiedRequest.header("Cookie", it) }
            chain.proceed(verifiedRequest.build())
        }
        .build()
    private val searchClient = httpClient.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()

    class VerificationRequired : IOException("Lucida requires browser verification")

    suspend fun search(query: String, limit: Int = 5): Result<List<DownloadableTrack>> {
        if (query.isBlank() || limit <= 0) return Result.success(emptyList())

        val request = Request.Builder()
            .url(searchUrl(query))
            .header("Referer", "$BASE_URL/")
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = searchClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(runCatching { response.use { readSearchResponse(it, limit) } })
                }
            })
        }
    }

    companion object {
        private const val HOST = "lucida.to"
        private const val BASE_URL = "https://$HOST"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private val TOKEN_REGEX = Pattern.compile("\\btoken\"?\\s*:\\s*\"([^\"]+)\"")
        private val EXPIRY_REGEX = Pattern.compile("\\btokenExpiry\"?\\s*:\\s*(\\d+)")
        private val DATA_NODE = Regex("""\{\s*["']?type["']?\s*:\s*["']data["']\s*,\s*["']?data["']?\s*:\s*(?=\{)""")

        fun searchUrl(query: String) = "$BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("service", "amazon")
            .addQueryParameter("query", query.trim())
            .build()

        fun readSearchResponse(response: Response, limit: Int): List<DownloadableTrack> {
            if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) {
                throw VerificationRequired()
            }
            if (!response.isSuccessful) throw IOException("Lucida search returned HTTP ${response.code}")
            val page = response.body?.string() ?: throw IOException("Lucida returned an empty search page")
            return parseSearchPage(page, limit)
        }

        fun parseSearchPage(page: String, limit: Int): List<DownloadableTrack> {
            if (limit <= 0) return emptyList()

            // Read each SvelteKit data node independently of its index or the surrounding script.
            for (match in DATA_NODE.findAll(page)) {
                val serializedData = page.substring(match.range.last + 1)
                val data = try {
                    JsonParser.parseReader(JsonReader(StringReader(serializedData))) as? JsonObject
                } catch (_: JsonParseException) {
                    null
                } ?: continue

                val envelope = (data.get("results") as? JsonObject)
                    ?: data.takeIf { it.has("tracks") }
                    ?: continue
                val success = envelope.get("success") as? JsonPrimitive
                if (success?.isBoolean == true && !success.asBoolean) {
                    throw IOException("Lucida's Amazon Music search failed")
                }
                val results = (envelope.get("results") as? JsonObject) ?: envelope
                val tracks = results.get("tracks") as? JsonArray ?: continue
                val matches = tracks.asSequence()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull(::parseTrack)
                    .distinctBy { it.id }
                    .take(limit)
                    .toList()
                if (!tracks.isEmpty && matches.isEmpty()) {
                    throw IOException("Lucida search returned no usable track links")
                }
                return matches
            }
            throw IOException("Lucida search results were missing from the page")
        }

        private fun parseTrack(track: JsonObject): DownloadableTrack? {
            val url = track.string("url")
            if (url.toHttpUrlOrNull()?.isHttps != true) return null

            val album = track.get("album") as? JsonObject
            val artists = (track.get("artists") as? JsonArray)?.mapNotNull { artist ->
                (artist as? JsonObject)?.string("name")?.takeIf { it.isNotBlank() }
            }.orEmpty()
            return DownloadableTrack(
                id = "lucida_${url.hashCode()}",
                title = track.string("title").ifBlank { "Unknown Title" },
                artist = artists.joinToString(", ").ifBlank { "Unknown Artist" },
                album = album?.string("title").orEmpty(),
                source = DownloadSource.LUCIDA,
                sourceUrl = url,
                quality = "Lossless FLAC (Amazon Music)",
                thumbnailUrl = album?.string("cover")?.takeIf { it.isNotBlank() }
            )
        }

        private fun JsonObject.string(name: String): String =
            (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.asString.orEmpty()
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
            val pageUrl = BASE_URL.toHttpUrl().newBuilder().addQueryParameter("url", trackUrl).build()
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

            if (initJson.has("success") && !initJson.optBoolean("success")) {
                val err = initJson.optString("error", "Failed to initialize server stream")
                return@withContext Result.failure(Exception("Lucida error: $err"))
            }

            val handoff = initJson.optString("handoff", "")
            val serverName = initJson.optString("server").ifBlank { initJson.optString("name", "katze") }

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
                java.io.BufferedOutputStream(FileOutputStream(targetFile), 64 * 1024).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    java.io.BufferedInputStream(body.byteStream(), 64 * 1024).use { bis ->
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
