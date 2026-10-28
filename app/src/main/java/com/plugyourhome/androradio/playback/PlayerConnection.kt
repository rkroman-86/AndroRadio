package com.plugyourhome.androradio.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Ce que l'interface doit savoir du lecteur. */
data class PlayerUiState(
    val station: Station? = null,
    val nowPlaying: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasError: Boolean = false,
) {
    val isActive: Boolean get() = isPlaying || isBuffering
}

/**
 * Pont entre l'interface et PlaybackService via un MediaController.
 * L'interface ne parle jamais directement à ExoPlayer.
 */
@OptIn(UnstableApi::class)
class PlayerConnection(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private var pending: (MediaController.() -> Unit)? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    init {
        scope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            val c = runCatching { MediaController.Builder(appContext, token).buildAsync().await() }
                .getOrNull() ?: return@launch
            controller = c
            c.addListener(listener)
            publish(c)
            pending?.invoke(c)
            pending = null
        }
    }

    private fun withController(action: MediaController.() -> Unit) {
        controller?.action() ?: run { pending = action }
    }

    fun play(station: Station) = withController {
        setMediaItem(station.toMediaItem())
        prepare()
        this.play()
    }

    fun togglePlayPause() = withController {
        when {
            isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING) -> pause()
            mediaItemCount == 0 -> Unit
            else -> {
                // Une radio est en direct : on repart du direct plutôt que du tampon.
                if (playbackState == Player.STATE_IDLE || playerError != null) prepare()
                seekToDefaultPosition()
                play()
            }
        }
    }

    /** Arrête le flux mais garde la station chargée (elle reste affichée comme station actuelle). */
    fun stop() = withController { this.stop() }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun publish(player: Player) {
        val metadata: MediaMetadata = player.mediaMetadata
        val station = Station.fromMetadata(player.currentMediaItem?.mediaMetadata)
        val name = station?.displayName
        fun clean(v: CharSequence?) = v?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "-" && it != name }
        val songTitle = clean(metadata.title)
        val songArtist = clean(metadata.artist)
        // ICY donne « Artiste - Titre » dans le titre ; les flux HLS (ID3) donnent les deux séparément.
        val title = when {
            songTitle != null && songArtist != null -> "$songArtist - $songTitle"
            else -> songTitle
        }
        _state.value = PlayerUiState(
            station = station,
            nowPlaying = title,
            isPlaying = player.isPlaying,
            isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hasError = player.playerError != null,
        )
    }
}
