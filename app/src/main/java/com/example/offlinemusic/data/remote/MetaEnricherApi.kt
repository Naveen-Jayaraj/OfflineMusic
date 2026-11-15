package com.example.offlinemusic.data.remote

import com.example.offlinemusic.data.database.entities.TrackEntity
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

interface MetaEnricherApi {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    // 1. Enrich a single song
    @PUT("song")
    suspend fun enrichSong(
        @Body track: TrackEntity,
        @Query("youtubeId") youtubeId: String? = null
    ): SingleSongResponse

    // 2. Batch enrich multiple songs
    @PUT("songs")
    suspend fun enrichSongsBatch(@Body tracks: List<TrackEntity>): BatchSongResponse

    // 3. Fetch line-synced LRC lyrics
    @PUT("lyric")
    suspend fun getLyrics(@Body query: LyricQuery): LyricResponse

    // 4. Batch fetch lyrics
    @PUT("lyrics")
    suspend fun getLyricsBatch(@Body queries: List<LyricQuery>): List<LyricResponse>

    // 5. Download raw .lrc file directly to disk
    @Streaming
    @GET("lyric/file")
    suspend fun downloadLrcFile(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("key") apiKey: String = "metaenricher_secret_key"
    ): ResponseBody
}
