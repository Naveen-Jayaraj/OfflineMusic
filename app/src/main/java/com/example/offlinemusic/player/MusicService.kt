package com.example.offlinemusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.offlinemusic.MainActivity
import com.example.offlinemusic.R

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    companion object {
        const val CHANNEL_ID = "offline_music_playback_channel"
        const val CHANNEL_NAME = "Music Playback"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Hook up MediaNotificationProvider for status bar pill & Dynamic Island controls
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        setMediaNotificationProvider(notificationProvider)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles audio focus (phone calls, other apps)
            .setHandleAudioBecomingNoisy(true) // Pauses on headphone unplug
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keeps CPU awake for screen-off & background playback
            .build()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                // IMPORTANCE_DEFAULT allows OEM Live Alerts / Dynamic Island / Status Bar pills to treat this as an active live alert
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.playback_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Keep sound silent so audio track changes don't chime
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            // Explicitly expose standard media control commands to SystemUI / Dynamic Island pills
            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_STOP)
                .build()

            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands,
                availablePlayerCommands
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.stop()
            player.clearMediaItems()
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
