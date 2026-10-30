package com.plugyourhome.androradio.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.DeviceInfo
import androidx.media3.session.SessionCommand
import com.plugyourhome.androradio.dlna.DlnaCommands
import com.plugyourhome.androradio.dlna.DlnaDevice
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
    /** Lecture sur un appareil Google Cast (CastPlayer) plutôt que sur le téléphone. */
    val isCasting: Boolean = false,
    /** Volume de l'appareil Cast (0..1), si connu. */
    val castVolume: Float? = null,
    /** Appareil DLNA qui joue (géré par le service), null sinon. */
    val dlnaName: String? = null,
    val dlnaId: String? = null,
    /** Volume réel de l'appareil DLNA (0..1), si connu. */
    val dlnaVolume: Float? = null,
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
            val c = runCatching {
                MediaController.Builder(appContext, token)
                    .setListener(object : MediaController.Listener {
                        // Le service annonce ici l'appareil DLNA en cours
                        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = publish(controller)
                    })
                    .buildAsync().await()
            }
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

    /** Bascule la lecture vers un appareil DLNA (géré par le service). */
    fun connectDlna(device: DlnaDevice, station: Station?) = withController {
        sendCustomCommand(SessionCommand(DlnaCommands.CONNECT, Bundle.EMPTY), DlnaCommands.connectArgs(device, station))
    }

    /** Termine la diffusion DLNA ; [resume] relance la station sur le téléphone. */
    fun disconnectDlna(resume: Boolean) = withController {
        sendCustomCommand(
            SessionCommand(DlnaCommands.DISCONNECT, Bundle.EMPTY),
            Bundle().apply { putBoolean(DlnaCommands.KEY_RESUME, resume) },
        )
    }

    /** Volume de l'appareil DLNA (0..1), envoyé directement au service. */
    fun setDlnaVolume(value: Float) = withController {
        sendCustomCommand(
            SessionCommand(DlnaCommands.SET_VOLUME, Bundle.EMPTY),
            Bundle().apply { putFloat(DlnaCommands.KEY_VOLUME, value.coerceIn(0f, 1f)) },
        )
    }

    /** Volume de l'appareil Cast (0..1). */
    fun setCastVolume(value: Float) = withController {
        val info = deviceInfo
        if (info.maxVolume > info.minVolume) {
            val level = info.minVolume + Math.round(value.coerceIn(0f, 1f) * (info.maxVolume - info.minVolume))
            setDeviceVolume(level, 0)
        }
    }

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
            isCasting = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE,
            dlnaName = (player as? MediaController)?.sessionExtras?.getString(DlnaCommands.EXTRA_OUTPUT_NAME),
            dlnaId = (player as? MediaController)?.sessionExtras?.getString(DlnaCommands.EXTRA_OUTPUT_ID),
            dlnaVolume = (player as? MediaController)?.sessionExtras
                ?.getFloat(DlnaCommands.EXTRA_VOLUME, -1f)?.takeIf { it >= 0f },
            castVolume = player.deviceInfo.takeIf { it.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE && it.maxVolume > it.minVolume }
                ?.let { (player.deviceVolume - it.minVolume).toFloat() / (it.maxVolume - it.minVolume) },
            isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hasError = player.playerError != null,
        )
    }
}
