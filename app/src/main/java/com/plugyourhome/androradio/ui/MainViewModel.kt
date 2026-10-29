package com.plugyourhome.androradio.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plugyourhome.androradio.AndroRadioApp
import com.plugyourhome.androradio.data.Station
import com.plugyourhome.androradio.data.Track
import com.plugyourhome.androradio.cast.CastDevice
import com.plugyourhome.androradio.cast.CastDevices
import com.plugyourhome.androradio.dlna.DlnaDevice
import com.plugyourhome.androradio.dlna.RemoteOutput
import com.plugyourhome.androradio.dlna.RemoteTransport
import com.plugyourhome.androradio.playback.PlayerConnection
import com.plugyourhome.androradio.playback.PlayerUiState
import com.plugyourhome.androradio.playback.VolumeControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** État d'une liste chargée depuis le réseau. */
sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data object Error : LoadState
    data class Loaded(val stations: List<Station>) : LoadState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AndroRadioApp
    private val api = app.api
    private val store = app.store

    val player = PlayerConnection(application, viewModelScope)

    /** Volume multimédia du téléphone (0..1), réglé depuis la bande du bas. */
    val volume = VolumeControl(application)

    /** Diffusion vers un appareil DLNA (Arylic, WiiM, TV…). */
    val remote = RemoteOutput(application, viewModelScope)

    /** État affiché : celui de l'appareil distant pendant une diffusion, sinon celui du téléphone. */
    val playerState: StateFlow<PlayerUiState> = combine(player.state, remote.state) { local, r ->
        if (!r.isActive) local
        else PlayerUiState(
            station = r.station,
            nowPlaying = r.nowPlaying,
            isPlaying = r.transport == RemoteTransport.PLAYING,
            isBuffering = r.transport == RemoteTransport.LOADING,
            hasError = r.transport == RemoteTransport.ERROR,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    /** Appareils Google Cast (Chromecast, Google TV…). */
    val cast = CastDevices(application)

    /** Nom de l'appareil qui joue (DLNA ou Cast), null si c'est le téléphone. */
    val outputName: StateFlow<String?> = combine(remote.state, cast.connected, player.state) { r, c, p ->
        r.device?.name ?: c?.takeIf { p.isCasting || cast.isConnected }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Volume du curseur : celui de l'appareil distant pendant une diffusion. */
    val volumeLevel: StateFlow<Float> = combine(volume.level, remote.state, player.state) { v, r, p ->
        when {
            r.isActive -> r.volume ?: 0.5f
            p.isCasting -> p.castVolume ?: 0.5f
            else -> v
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, volume.level.value)

    fun setVolume(value: Float) {
        when {
            remote.state.value.isActive -> remote.setVolume(value)
            player.state.value.isCasting -> player.setCastVolume(value)
            else -> volume.set(value)
        }
    }

    /**
     * Touches de volume du téléphone pendant une diffusion : elles règlent l'appareil distant.
     * Renvoie false hors diffusion (le téléphone garde alors son comportement normal).
     */
    fun onVolumeKey(direction: Int): Boolean {
        if (!remote.state.value.isActive) return false
        val next = when (direction) {
            0 -> 0f // touche « muet »
            else -> (volumeLevel.value + direction * VOLUME_STEP).coerceIn(0f, 1f)
        }
        remote.setVolume(next)
        return true
    }

    // --- Fenêtre « Diffuser vers… » ---

    private val _castOpen = MutableStateFlow(false)
    val castOpen = _castOpen.asStateFlow()

    /** Liste « Diffuser vers… » : appareils Cast puis DLNA (un même appareil peut apparaître deux fois). */
    val outputs: StateFlow<OutputList> = combine(cast.devices, remote.search) { c, d ->
        OutputList(
            searching = d.searching,
            targets = c.map { OutputTarget("cast:" + it.id, it.name, OutputKind.CAST) } +
                d.devices.map { OutputTarget("dlna:" + it.id, it.name, OutputKind.DLNA) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OutputList())

    /** Identifiant de la cible active dans la liste, null = ce téléphone. */
    val currentOutputId: StateFlow<String?> = combine(remote.state, cast.connected, cast.devices) { r, c, list ->
        r.device?.let { "dlna:" + it.id } ?: c?.let { name -> list.firstOrNull { it.name == name }?.let { "cast:" + it.id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openCast() {
        _castOpen.value = true
        remote.startSearch()
        cast.startDiscovery()
    }

    fun closeCast() {
        _castOpen.value = false
        cast.stopDiscovery()
    }

    fun castTo(target: OutputTarget) {
        when (target.kind) {
            OutputKind.DLNA -> remote.search.value.devices
                .firstOrNull { "dlna:" + it.id == target.id }?.let(::castToDlna)
            OutputKind.CAST -> cast.devices.value
                .firstOrNull { "cast:" + it.id == target.id }?.let(::castToCast)
        }
        closeCast()
    }

    /** DLNA : la station en cours (ou la dernière écoutée) part vers l'appareil choisi. */
    private fun castToDlna(device: DlnaDevice) {
        val station = currentStation.value
        if (cast.isConnected) cast.disconnect()
        player.stop()
        remote.connect(device, station)
        station?.let { s -> viewModelScope.launch { api.registerClick(s.uuid) } }
    }

    /** Google Cast : CastPlayer reprend sur l'appareil ce que joue le téléphone. */
    private fun castToCast(device: CastDevice) {
        val fromDlna = remote.disconnect()
        val station = fromDlna ?: currentStation.value
        // Le téléphone doit jouer la station pour qu'elle soit transférée à l'appareil Cast.
        if (station != null && (fromDlna != null || !player.state.value.isActive)) play(station)
        cast.select(device)
    }

    /** Revient sur le téléphone ; la station reprend ici si elle jouait ailleurs. */
    fun castToPhone() {
        if (cast.isConnected) cast.disconnect() // CastPlayer repasse sur le lecteur local
        val wasPlaying = playerState.value.isActive
        val station = remote.disconnect()
        if (wasPlaying && station != null) player.play(station)
        closeCast()
    }

    /** Code pays de l'appareil (ex. « FR »), vide si inconnu. */
    // Région du système (et non de l'appli) : choisir une autre langue dans l'appli
    // ne doit pas faire perdre le pays des stations locales.
    val countryCode: String = android.content.res.Resources.getSystem().configuration.locales[0]
        .country.uppercase().takeIf { it.length == 2 } ?: ""
    val countryName: String = if (countryCode.isEmpty()) "" else Locale("", countryCode).displayCountry

    /** 10 dernières stations écoutées. */
    val recent: StateFlow<List<Station>> = store.recent
        .map { it.take(RECENT_COUNT) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 20 cases de favoris (null = vide). */
    val favorites: StateFlow<List<Station?>> = store.favorites

    /** Station affichée en haut : celle du lecteur, sinon la dernière écoutée. */
    val currentStation: StateFlow<Station?> = combine(playerState, store.recent) { p, r ->
        p.station ?: r.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Morceau en cours (artiste / titre), seulement pendant la lecture. */
    val track: StateFlow<Track?> = playerState
        .map { if (it.isActive) Track.parse(it.nowPlaying) else null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Pochette du morceau en cours ; null = utiliser le logo de la station. */
    val trackArt: StateFlow<String?> = track
        .mapLatest { t -> t?.let { app.artwork.find(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _local = MutableStateFlow<LoadState>(LoadState.Loading)
    val local = _local.asStateFlow()

    /** Réserve de stations locales populaires ; « Rafraîchir » en tire 10 autres au hasard. */
    private var localPool: List<Station> = emptyList()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _search = MutableStateFlow<LoadState>(LoadState.Idle)
    val search = _search.asStateFlow()
    private var searchJob: Job? = null

    /**
     * Position de défilement des résultats de recherche, gardée ici (et non dans la page)
     * pour retrouver le même endroit quand on ferme puis rouvre la recherche.
     */
    val searchListState = LazyListState()

    /** Page flottante des favoris : null = fermée ; sinon la station à enregistrer (peut être absente). */
    private val _favoritesSheet = MutableStateFlow<FavoritesSheet?>(null)
    val favoritesSheet = _favoritesSheet.asStateFlow()

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen = _searchOpen.asStateFlow()

    init {
        loadLocal()
    }

    private fun loadLocal() {
        viewModelScope.launch {
            _local.value = LoadState.Loading
            _local.value = load {
                localPool = api.popular(countryCode, LOCAL_POOL)
                localPool.take(LOCAL_COUNT) // au départ : les 10 plus écoutées
            }
        }
    }

    /** Propose 10 autres stations locales, différentes de celles affichées. */
    fun refreshLocal() {
        val shown = (_local.value as? LoadState.Loaded)?.stations.orEmpty().map { it.uuid }.toSet()
        if (localPool.size <= LOCAL_COUNT) {
            loadLocal()
            return
        }
        val fresh = localPool.filterNot { it.uuid in shown }.shuffled()
        val pick = (fresh + localPool.filter { it.uuid in shown }.shuffled()).take(LOCAL_COUNT)
        _local.value = LoadState.Loaded(pick)
    }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun runSearch() {
        val q = _searchQuery.value.trim()
        searchJob?.cancel()
        if (q.isEmpty()) {
            _search.value = LoadState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            _search.value = LoadState.Loading
            _search.value = load { api.searchEverywhere(q) }
            searchListState.requestScrollToItem(0) // nouvelle recherche : on repart du haut
        }
    }

    private suspend fun load(block: suspend () -> List<Station>): LoadState = try {
        LoadState.Loaded(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LoadState.Error
    }

    fun play(station: Station) {
        if (remote.state.value.isActive) remote.play(station) else player.play(station)
        store.addRecent(station)
        viewModelScope.launch { api.registerClick(station.uuid) }
    }

    /** Bouton unique Lecture / Stop. */
    fun togglePlayStop() {
        val remoteOn = remote.state.value.isActive
        when {
            playerState.value.isActive -> if (remoteOn) remote.stop() else player.stop()
            remoteOn && remote.state.value.station != null -> remote.resume()
            else -> currentStation.value?.let(::play)
        }
    }

    fun openFavorites(stationToSave: Station? = currentStation.value) {
        _favoritesSheet.value = FavoritesSheet(stationToSave)
    }

    fun closeFavorites() {
        _favoritesSheet.value = null
    }

    fun saveFavorite(slot: Int) {
        _favoritesSheet.value?.stationToSave?.let { store.saveFavorite(slot, it) }
    }

    fun deleteFavorite(slot: Int) = store.deleteFavorite(slot)

    fun moveFavorite(from: Int, to: Int) = store.moveFavorite(from, to)

    private val _aboutOpen = MutableStateFlow(false)
    val aboutOpen = _aboutOpen.asStateFlow()

    fun openAbout() {
        _aboutOpen.value = true
    }

    fun closeAbout() {
        _aboutOpen.value = false
    }

    fun openSearch() {
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
    }

    override fun onCleared() {
        cast.release()
        player.release()
        volume.release()
    }

    companion object {
        const val RECENT_COUNT = 10
        const val VOLUME_STEP = 0.05f
        const val LOCAL_COUNT = 10
        const val LOCAL_POOL = 60
    }
}

data class FavoritesSheet(val stationToSave: Station?)

enum class OutputKind { CAST, DLNA }

/** Un appareil de la liste « Diffuser vers… ». */
data class OutputTarget(val id: String, val name: String, val kind: OutputKind)

data class OutputList(val searching: Boolean = false, val targets: List<OutputTarget> = emptyList())
