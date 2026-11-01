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
        equalizer = runCatching { Equalizer(0, player.audioSessionId) }.getOrNull()
        PlaybackBridge.applyEqualizer = equalizerApply@ { enabled, requestedLevels ->
            val effect = equalizer ?: return@equalizerApply
            runCatching {
                val count = effect.numberOfBands.toInt()
                val range = effect.bandLevelRange
                for (band in 0 until count) {
                    val index = if (count <= 1) 0 else band * (requestedLevels.size - 1) / (count - 1)
                    val level = requestedLevels.getOrElse(index) { 0 }.coerceIn(range[0], range[1])
                    effect.setBandLevel(band.toShort(), level)
                }
                effect.enabled = enabled
            }
        }
        val builder = MediaSession.Builder(this, QueueAwarePlayer(player))
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        session = builder.build()
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
