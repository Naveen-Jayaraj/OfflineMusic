package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.DownloadSource
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LucidaSearchTest {
    @Test
    fun readsTracksFromMinifiedSearchPage() {
        val page = """
            <script>
            const data=[{"type":"data","data":{"settings":{}}},null,
              {"type":"data","data":{"results":{"success":true,"results":{"tracks":[
                null,
                {"title":"Missing URL"},
                {"title":"Around the World", "url":"https://music.amazon.com/tracks/B00137",
                 "artists":[{"name":"Daft Punk"}],
                 "album":{"title":"Homework","cover":"https://images.example.com/homework.jpg"}},
                {"title":"Da Funk","url":"https://music.amazon.com/tracks/B00138"}
              ]}}},"uses":{"url":1}}];
            </script>
        """.trimIndent()

        val track = response(200, page).use { LucidaDownloader.readSearchResponse(it, limit = 1) }.single()
        assertEquals("Around the World", track.title)
        assertEquals("Daft Punk", track.artist)
        assertEquals("Homework", track.album)
        assertEquals("https://images.example.com/homework.jpg", track.thumbnailUrl)
        assertEquals(DownloadSource.LUCIDA, track.source)
        assertEquals("https://music.amazon.com/tracks/B00137", track.sourceUrl)
    }

    @Test
    fun readsSearchPageWithUnquotedKeysAndNoMatches() {
        val page = "<script>const data = [{type:'data',data:{results:{success:true,results:{tracks:[]}}}}];</script>"

        assertTrue(LucidaDownloader.parseSearchPage(page, limit = 5).isEmpty())
    }

    @Test
    fun identifiesCloudflareChallenge() {
        val response = response(403, "<title>Just a moment...</title>", cloudflareChallenge = true)

        response.use {
            assertThrows(LucidaDownloader.VerificationRequired::class.java) {
                LucidaDownloader.readSearchResponse(it, limit = 5)
            }
        }
    }

    @Test
    fun reportsUpstreamFailureRatherThanEmptyResults() {
        val page = "<script>const data=[{type:'data',data:{results:{success:false,error:'Unavailable'}}}];</script>"

        assertThrows(IOException::class.java) { LucidaDownloader.parseSearchPage(page, limit = 5) }
    }

    @Test
    fun encodesTheEntireSearchTermAsOneParameter() {
        val query = "Beyoncé & Jay-Z + live"
        val url = LucidaDownloader.searchUrl(" $query ")

        assertEquals("amazon", url.queryParameter("service"))
        assertEquals(query, url.queryParameter("query"))
    }

    private fun response(code: Int, body: String, cloudflareChallenge: Boolean = false): Response =
        Response.Builder()
            .request(Request.Builder().url(LucidaDownloader.searchUrl("Daft Punk")).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Lucida search response")
            .body(body.toResponseBody())
            .apply { if (cloudflareChallenge) header("cf-mitigated", "challenge") }
            .build()
}
