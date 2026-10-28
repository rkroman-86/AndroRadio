package com.plugyourhome.androradio.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Favoris et historique d'écoute, persistés en JSON dans le stockage privé de l'appli.
 * Aucune donnée ne quitte l'appareil.
 */
class StationStore(context: Context, private val scope: CoroutineScope) {

    private val favoritesFile = File(context.filesDir, "favorites.json")
    private val recentFile = File(context.filesDir, "recent.json")
    private val fileLock = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    private val _favorites = MutableStateFlow<List<Station>>(emptyList())
    val favorites: StateFlow<List<Station>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow<List<Station>>(emptyList())
    val recent: StateFlow<List<Station>> = _recent.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            _favorites.value = read(favoritesFile)
            _recent.value = read(recentFile)
            loaded.complete(Unit)
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    fun isFavorite(uuid: String) = _favorites.value.any { it.uuid == uuid }

    fun toggleFavorite(station: Station) {
        _favorites.update { list ->
            if (list.any { it.uuid == station.uuid }) list.filterNot { it.uuid == station.uuid }
            else list + station
        }
        persist(favoritesFile, _favorites)
    }

    fun addRecent(station: Station) {
        _recent.update { list ->
            (listOf(station) + list.filterNot { it.uuid == station.uuid }).take(MAX_RECENT)
        }
        persist(recentFile, _recent)
    }

    fun clearRecent() {
        _recent.value = emptyList()
        persist(recentFile, _recent)
    }

    private fun read(file: File): List<Station> = runCatching {
        if (file.exists()) AppJson.decodeFromString<List<Station>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    /** Écrit la valeur la plus récente du flux (pas celle du moment de l'appel). */
    private fun persist(file: File, source: StateFlow<List<Station>>) {
        scope.launch(Dispatchers.IO) {
            awaitLoaded() // ne jamais écraser le fichier avant de l'avoir lu
            fileLock.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(AppJson.encodeToString(source.value))
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        private const val MAX_RECENT = 50
    }
}
