package com.plugyourhome.androradio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plugyourhome.androradio.AndroRadioApp
import com.plugyourhome.androradio.data.Station
import com.plugyourhome.androradio.playback.PlayerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.Locale

enum class Tab { Discover, Favorites, Recent }

/** Portée de la découverte : pays de l'appareil ou monde entier. */
enum class Scope { Country, World }

sealed interface DiscoverState {
    data object Loading : DiscoverState
    data object Error : DiscoverState
    data class Loaded(val stations: List<Station>) : DiscoverState
}

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AndroRadioApp
    private val api = app.api
    private val store = app.store

    val player = PlayerConnection(application, viewModelScope)

    val favorites: StateFlow<List<Station>> = store.favorites
    val recent: StateFlow<List<Station>> = store.recent

    /** Code pays de l'appareil (ex. « FR »), vide si inconnu. */
    val countryCode: String = Locale.getDefault().country.uppercase().takeIf { it.length == 2 } ?: ""
    val countryName: String = if (countryCode.isEmpty()) "" else Locale("", countryCode).displayCountry

    private val _tab = MutableStateFlow(Tab.Discover)
    val tab = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _genre = MutableStateFlow<String?>(null)
    val genre = _genre.asStateFlow()

    private val _scope = MutableStateFlow(if (countryCode.isEmpty()) Scope.World else Scope.Country)
    val scope = _scope.asStateFlow()

    private val _discover = MutableStateFlow<DiscoverState>(DiscoverState.Loading)
    val discover = _discover.asStateFlow()

    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(_query.debounce(SEARCH_DEBOUNCE_MS), _genre, _scope, reload) { q, g, s, _ ->
                Triple(q.trim(), g, s)
            }.collectLatest { (q, g, s) ->
                _discover.value = DiscoverState.Loading
                val country = if (s == Scope.Country) countryCode else null
                _discover.value = try {
                    DiscoverState.Loaded(
                        if (q.isEmpty() && g == null) api.popular(country)
                        else api.search(name = q.ifEmpty { null }, countryCode = country, tag = g)
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DiscoverState.Error
                }
            }
        }
    }

    fun selectTab(tab: Tab) { _tab.value = tab }
    fun setQuery(value: String) { _query.value = value }
    fun setGenre(value: String?) { _genre.value = value }
    fun setScope(value: Scope) { _scope.value = value }
    fun retry() { reload.value++ }

    fun play(station: Station) {
        player.play(station)
        store.addRecent(station)
        viewModelScope.launch { api.registerClick(station.uuid) }
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun stop() = player.stop()

    fun toggleFavorite(station: Station) = store.toggleFavorite(station)
    fun clearRecent() = store.clearRecent()

    override fun onCleared() {
        player.release()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 450L

        /** Genres proposés en raccourci (étiquettes Radio Browser, en anglais). */
        val GENRES = listOf(
            "pop", "rock", "jazz", "classical", "news", "electronic",
            "chillout", "dance", "hits", "talk", "lounge", "80s", "oldies", "reggae",
        )
    }
}
