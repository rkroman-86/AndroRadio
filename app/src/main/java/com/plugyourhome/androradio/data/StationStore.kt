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
 * Favoris (20 cases fixes, une case peut être vide) et historique d'écoute,
 * persistés en JSON dans le stockage privé de l'appli. Aucune donnée ne quitte l'appareil.
 */
class StationStore(context: Context, private val scope: CoroutineScope) {

    private val favoritesFile = File(context.filesDir, "favorite_slots.json")
    private val recentFile = File(context.filesDir, "recent.json")
    private val fileLock = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    private val _favorites = MutableStateFlow<List<Station?>>(List(FAVORITE_SLOTS) { null })
    /** Toujours exactement [FAVORITE_SLOTS] cases ; null = case vide. */
    val favorites: StateFlow<List<Station?>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow<List<Station>>(emptyList())
    val recent: StateFlow<List<Station>> = _recent.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val slots = runCatching {
                if (favoritesFile.exists()) AppJson.decodeFromString<List<Station?>>(favoritesFile.readText())
                else emptyList()
            }.getOrDefault(emptyList())
            _favorites.value = List(FAVORITE_SLOTS) { slots.getOrNull(it) }
            _recent.value = runCatching {
                if (recentFile.exists()) AppJson.decodeFromString<List<Station>>(recentFile.readText())
                else emptyList()
            }.getOrDefault(emptyList())
            loaded.complete(Unit)
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    fun isFavorite(uuid: String) = _favorites.value.any { it?.uuid == uuid }

    /** Enregistre la station dans la case [slot] (en la retirant d'une autre case si besoin). */
    fun saveFavorite(slot: Int, station: Station) {
        if (slot !in 0 until FAVORITE_SLOTS) return
        _favorites.update { list ->
            list.mapIndexed { i, s ->
                when {
                    i == slot -> station
                    s?.uuid == station.uuid -> null
                    else -> s
                }
            }
        }
        saveFavorites()
    }

    fun deleteFavorite(slot: Int) {
        if (slot !in 0 until FAVORITE_SLOTS) return
        _favorites.update { list -> list.mapIndexed { i, s -> if (i == slot) null else s } }
        saveFavorites()
    }

    /** Déplace la case [from] à la position [to] (les autres cases glissent d'un cran). */
    fun moveFavorite(from: Int, to: Int) {
        if (from == to || from !in 0 until FAVORITE_SLOTS || to !in 0 until FAVORITE_SLOTS) return
        _favorites.update { list -> list.toMutableList().apply { add(to, removeAt(from)) } }
        saveFavorites()
    }

    fun addRecent(station: Station) {
        _recent.update { list ->
            (listOf(station) + list.filterNot { it.uuid == station.uuid }).take(MAX_RECENT)
        }
        scope.launch(Dispatchers.IO) { write(recentFile) { AppJson.encodeToString(_recent.value) } }
    }

    private fun saveFavorites() {
        scope.launch(Dispatchers.IO) { write(favoritesFile) { AppJson.encodeToString(_favorites.value) } }
    }

    /** Écrit la valeur la plus récente (lue sous verrou), jamais avant le chargement initial. */
    private suspend fun write(file: File, content: () -> String) {
        awaitLoaded()
        fileLock.withLock {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(content())
            tmp.renameTo(file)
        }
    }

    companion object {
        const val FAVORITE_SLOTS = 20
        private const val MAX_RECENT = 30
    }
}
