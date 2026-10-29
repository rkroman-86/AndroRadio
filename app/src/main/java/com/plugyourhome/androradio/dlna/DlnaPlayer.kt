package com.plugyourhome.androradio.dlna

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lecteur « distant » qui pilote un appareil DLNA, branché sur la MediaSession à la place
 * du lecteur du téléphone. Ainsi la notification, l'écran verrouillé, le widget système,
 * les boutons ⏮ ⏭ et les touches de volume pilotent l'appareil DLNA, pas le téléphone.
 */
@OptIn(UnstableApi::class)
class DlnaPlayer(
    private val output: RemoteOutput,
    scope: CoroutineScope,
    private val onStationStarted: (Station) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** Liste parcourue par ⏮ ⏭ (les favoris autour de la station en cours). */
    private var queue: List<Station> = emptyList()
    private var index = 0

    private val watcher: Job = scope.launch {
        output.state.collect { invalidateState() }
    }

    val device: DlnaDevice? get() = output.state.value.device

    fun start(stations: List<Station>, startIndex: Int) {
        queue = stations
        index = startIndex.coerceIn(0, (stations.size - 1).coerceAtLeast(0))
        invalidateState()
    }

    override fun getState(): State {
        val remote = output.state.value
        val current = remote.station
        // La station en cours doit figurer dans la liste (cas d'une station lancée hors favoris)
        if (current != null && queue.none { it.uuid == current.uuid }) {
            queue = listOf(current) + queue
            index = 0
        } else if (current != null) {
            index = queue.indexOfFirst { it.uuid == current.uuid }
        }

        val playing = remote.transport == RemoteTransport.PLAYING
        val loading = remote.transport == RemoteTransport.LOADING
        val items = queue.mapIndexed { i, station ->
            val base = station.toMediaItem()
            val metadata = if (i == index && current != null) {
                base.mediaMetadata.buildUpon()
                    .setTitle(remote.nowPlaying ?: station.displayName)
                    .setArtist(station.displayName)
                    .build()
            } else base.mediaMetadata
            MediaItemData.Builder(station.uuid)
                .setMediaItem(base)
                .setMediaMetadata(metadata)
                .setIsSeekable(false)
                .setIsDynamic(true)
                .setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
                .setDefaultPositionUs(0)
                .setDurationUs(C.TIME_UNSET)
                .build()
        }

        return State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(if (items.isEmpty()) C.INDEX_UNSET else index)
            .setPlaybackState(
                when {
                    items.isEmpty() -> Player.STATE_IDLE
                    loading -> Player.STATE_BUFFERING
                    else -> Player.STATE_READY
                }
            )
            .setPlayWhenReady(playing || loading, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setIsLoading(loading)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setDeviceInfo(DEVICE_INFO)
            .setDeviceVolume(((remote.volume ?: 0.5f) * MAX_VOLUME).roundToInt())
            .setContentPositionMs(0)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) output.resume() else output.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        output.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val stations = mediaItems.mapNotNull { Station.fromMetadata(it.mediaMetadata) }
        if (stations.isNotEmpty()) {
            queue = stations
            index = startIndex.coerceIn(0, stations.size - 1).takeIf { startIndex != C.INDEX_UNSET } ?: 0
            playAt(index)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex in queue.indices && mediaItemIndex != index) playAt(mediaItemIndex)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        output.setVolume(deviceVolume.coerceIn(0, MAX_VOLUME) / MAX_VOLUME.toFloat())
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(currentVolume() + VOLUME_STEP, flags)

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(currentVolume() - VOLUME_STEP, flags)

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(if (muted) 0 else MAX_VOLUME / 2, flags)

    override fun handleRelease(): ListenableFuture<*> {
        watcher.cancel()
        return Futures.immediateVoidFuture()
    }

    private fun currentVolume() = ((output.state.value.volume ?: 0.5f) * MAX_VOLUME).roundToInt()

    private fun playAt(i: Int) {
        index = i
        val station = queue[i]
        output.play(station)
        onStationStarted(station)
        invalidateState()
    }

    private companion object {
        const val MAX_VOLUME = 100
        const val VOLUME_STEP = 5

        val DEVICE_INFO = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(MAX_VOLUME)
            .build()

        val COMMANDS = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_RELEASE,
        ).build()
    }
}
