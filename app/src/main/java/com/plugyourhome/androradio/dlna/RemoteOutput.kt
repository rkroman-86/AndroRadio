package com.plugyourhome.androradio.dlna

import android.content.Context
import com.plugyourhome.androradio.data.RadioBrowserApi
import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Ce que l'interface doit savoir de la diffusion vers un appareil DLNA. */
data class RemoteState(
    val device: DlnaDevice? = null,       // null = lecture sur ce téléphone
    val station: Station? = null,
    val transport: RemoteTransport = RemoteTransport.STOPPED,
    val nowPlaying: String? = null,
    val volume: Float? = null,
    val unreachable: Boolean = false,
) {
    val isActive: Boolean get() = device != null
}

/** Recherche d'appareils pour la fenêtre « Diffuser vers… ». */
data class DeviceSearch(val searching: Boolean = false, val devices: List<DlnaDevice> = emptyList())

/**
 * Diffusion vers un appareil DLNA : choix de l'appareil, envoi des stations,
 * lecture / arrêt / volume, et suivi régulier de son état.
 */
class RemoteOutput(context: Context, private val scope: CoroutineScope) {

    private val client = RadioBrowserApi.defaultClient().newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    private val discovery = DlnaDiscovery(context, client)

    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _search = MutableStateFlow(DeviceSearch())
    val search: StateFlow<DeviceSearch> = _search.asStateFlow()

    private var renderer: DlnaRenderer? = null
    private var pollJob: Job? = null
    private var searchJob: Job? = null

    fun startSearch() {
        searchJob?.cancel()
        searchJob = scope.launch {
            _search.update { it.copy(searching = true) }
            val found = runCatching { discovery.discover() }.getOrDefault(emptyList())
            _search.value = DeviceSearch(searching = false, devices = found)
        }
    }

    /** Passe la lecture sur [device] et y lance [station] si fournie. */
    fun connect(device: DlnaDevice, station: Station?) {
        val r = DlnaRenderer(device, client)
        renderer = r
        _state.value = RemoteState(device = device, station = station, transport = RemoteTransport.LOADING)
        scope.launch {
            if (station != null) guarded { r.play(station) } else refresh()
        }
        startPolling()
    }

    /** Revient sur le téléphone ; arrête l'appareil distant. Rend la station qui y jouait. */
    fun disconnect(): Station? {
        val r = renderer ?: return null
        val station = _state.value.station
        pollJob?.cancel()
        renderer = null
        _state.value = RemoteState()
        scope.launch { runCatching { r.stop() } }
        return station
    }

    fun play(station: Station) {
        val r = renderer ?: return
        _state.update { it.copy(station = station, transport = RemoteTransport.LOADING, nowPlaying = null) }
        scope.launch { guarded { r.play(station) } }
    }

    fun resume() {
        val r = renderer ?: return
        _state.update { it.copy(transport = RemoteTransport.LOADING) }
        scope.launch {
            val station = _state.value.station
            // Après un arrêt, certains appareils ont oublié le flux : on le renvoie.
            guarded { if (station != null) r.play(station) else r.resume() }
        }
    }

    fun stop() {
        val r = renderer ?: return
        _state.update { it.copy(transport = RemoteTransport.STOPPED, nowPlaying = null) }
        scope.launch { guarded { r.stop() } }
    }

    fun setVolume(value: Float) {
        val r = renderer ?: return
        _state.update { it.copy(volume = value) }
        volumeJob?.cancel()
        volumeJob = scope.launch {
            delay(120) // regroupe les mouvements rapides du curseur
            runCatching { r.setVolume(value) }
        }
    }
    private var volumeJob: Job? = null

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun refresh() {
        val r = renderer ?: return
        try {
            val transport = r.transport()
            val title = runCatching { r.nowPlaying() }.getOrNull()
            val volume = if (volumeJob?.isActive == true) null else runCatching { r.volume() }.getOrNull()
            if (renderer !== r) return
            _state.update {
                it.copy(
                    transport = transport,
                    nowPlaying = if (transport == RemoteTransport.STOPPED) null else title,
                    volume = volume ?: it.volume,
                    unreachable = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (renderer === r) _state.update { it.copy(unreachable = true, transport = RemoteTransport.ERROR) }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
            delay(1_500) // laisse l'appareil se connecter au flux avant de relire son état
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(transport = RemoteTransport.ERROR, unreachable = true) }
        }
    }

    private companion object {
        const val POLL_MS = 4_000L
    }
}
