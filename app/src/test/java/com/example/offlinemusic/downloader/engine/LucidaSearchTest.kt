package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadableTrack
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LucidaSearchTest {

    @Test
    fun formatsDurationCorrectly() {
        assertEquals("3:45", LucidaDownloader.formatDuration(225000L))
        assertEquals("0:05", LucidaDownloader.formatDuration(5000L))
        assertEquals("4:06", LucidaDownloader.formatDuration(246382L))
    }

    @Test
    fun parsesLucidaBridgeTrackJson() {
        val json = """
            [
              {
                "id": "lucida_soundcloud_88335161",
                "title": "Get Lucky [Original Version] - Daft Punk",
                "artist": "DJ KB",
                "album": "",
                "durationMs": 246382,
                "duration": "4:06",
                "source": "LUCIDA",
                "sourceUrl": "https://soundcloud.com/eldjkb/get-lucky-original-version",
                "quality": "Soundcloud (Lucida)",
                "thumbnailUrl": "https://i1.sndcdn.com/artworks-000045765973-c2x8ko-large.jpg",
                "module": "soundcloud"
              }
            ]
        """.trimIndent()

        val array = JsonParser.parseString(json).asJsonArray
        val obj = array[0].asJsonObject

        val track = DownloadableTrack(
            id = obj.get("id").asString,
            title = obj.get("title").asString,
            artist = obj.get("artist").asString,
            album = obj.get("album").asString,
            durationMs = obj.get("durationMs").asLong,
            duration = obj.get("duration").asString,
            source = DownloadSource.LUCIDA,
            sourceUrl = obj.get("sourceUrl").asString,
            quality = obj.get("quality").asString,
            thumbnailUrl = obj.get("thumbnailUrl").asString
        )

        assertEquals("lucida_soundcloud_88335161", track.id)
        assertEquals("Get Lucky [Original Version] - Daft Punk", track.title)
        assertEquals("DJ KB", track.artist)
        assertEquals(DownloadSource.LUCIDA, track.source)
        assertEquals("https://soundcloud.com/eldjkb/get-lucky-original-version", track.sourceUrl)
        assertEquals("Soundcloud (Lucida)", track.quality)
        assertNotNull(track.thumbnailUrl)
    }

    @Test
    fun parsesSoundcloudCollectionItem() {
        val itemJson = """
            {
              "kind": "track",
              "id": 88335161,
              "title": "Around The World",
              "permalink_url": "https://soundcloud.com/daft-punk/around-the-world",
              "duration": 239000,
              "artwork_url": "https://i1.sndcdn.com/artworks-sample-large.jpg",
              "user": {
                "username": "Daft Punk"
              }
            }
        """.trimIndent()

        val obj = JsonParser.parseString(itemJson).asJsonObject
        assertEquals("track", obj.get("kind").asString)
        assertEquals(88335161L, obj.get("id").asLong)
        assertEquals("Around The World", obj.get("title").asString)
        assertEquals("Daft Punk", obj.getAsJsonObject("user").get("username").asString)
    }
}
