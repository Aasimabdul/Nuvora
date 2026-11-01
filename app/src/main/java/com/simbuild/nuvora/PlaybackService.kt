package com.simbuild.nuvora

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * The UI owns the queue, so the system's next/previous buttons are routed back to it through this
 * bridge. Only set while the activity is alive.
 */
object PlaybackBridge {
    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrevious: (() -> Unit)? = null
    @Volatile var applyEqualizer: ((Boolean, List<Short>) -> Unit)? = null
    @Volatile var applyLocalEqualizer: ((Boolean, List<Short>) -> Unit)? = null
}

/** Exposes next/previous to the notification and lock screen even though each item is played on its own. */
private class QueueAwarePlayer(player: Player) : ForwardingPlayer(player) {
    private val queueCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*queueCommands).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in queueCommands || super.isCommandAvailable(command)

    override fun seekToNext() { PlaybackBridge.onNext?.invoke() }
    override fun seekToNextMediaItem() { PlaybackBridge.onNext?.invoke() }
    override fun seekToPrevious() { PlaybackBridge.onPrevious?.invoke() }
    override fun seekToPreviousMediaItem() { PlaybackBridge.onPrevious?.invoke() }
}

/**
 * Owns the ExoPlayer used for Audius playback and exposes it through a Media3 MediaSession so music
 * continues in the background and can be controlled from the notification, lock screen and Bluetooth.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var equalizerSessionId: Int = 0
    private var requestedEqualizerEnabled: Boolean = false
    private var requestedEqualizerLevels: List<Short> = List(5) { 0.toShort() }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        this,
                        DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                            .setDefaultRequestProperties(mapOf("Accept" to "*/*", "Accept-Language" to "en-US,en;q=0.9"))
                            .setAllowCrossProtocolRedirects(true)
                    )
                )
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        PlaybackBridge.applyEqualizer = { enabled, requestedLevels ->
            requestedEqualizerEnabled = enabled
            requestedEqualizerLevels = requestedLevels.toList()
            applyEqualizerToActiveSession()
        }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) applyEqualizerToActiveSession()
            }
        })
        val builder = MediaSession.Builder(this, QueueAwarePlayer(player))
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        session = builder.build()
    }

    private fun applyEqualizerToActiveSession() {
        val player = exoPlayer ?: return
        val sessionId = player.audioSessionId
        if (sessionId <= 0) return
        if (equalizer == null || equalizerSessionId != sessionId) {
            runCatching { equalizer?.enabled = false }
            runCatching { equalizer?.release() }
            equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
            equalizerSessionId = if (equalizer != null) sessionId else 0
        }
        val effect = equalizer ?: return
        runCatching {
            val count = effect.numberOfBands.toInt()
            val range = effect.bandLevelRange
            for (band in 0 until count) {
                val index = if (count <= 1) 0 else band * (requestedEqualizerLevels.size - 1) / (count - 1)
                val level = requestedEqualizerLevels.getOrElse(index) { 0 }.coerceIn(range[0], range[1])
                effect.setBandLevel(band.toShort(), level)
            }
            effect.enabled = requestedEqualizerEnabled && count > 0
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PlaybackBridge.applyEqualizer = null
        runCatching { equalizer?.enabled = false }
        equalizer?.release()
        equalizer = null
        session?.release()
        session = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
