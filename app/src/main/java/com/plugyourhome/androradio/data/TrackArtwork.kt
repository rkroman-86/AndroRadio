package com.plugyourhome.androradio.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections

/** Morceau en cours, tiré des métadonnées ICY (« Artiste - Titre »). */
data class Track(val artist: String?, val title: String) {
    companion object {
        /** Découpe « Artiste - Titre » ; sans séparateur, tout devient le titre. */
        fun parse(raw: String?): Track? {
            val text = raw?.trim()?.takeIf { it.isNotBlank() && it != "-" } ?: return null
            val sep = listOf(" - ", " – ", " — ").firstOrNull { text.contains(it) }
            if (sep == null) return Track(null, text)
            val artist = text.substringBefore(sep).trim()
            val title = text.substringAfter(sep).trim()
            return when {
                artist.isEmpty() && title.isEmpty() -> null
                artist.isEmpty() -> Track(null, title)
                title.isEmpty() -> Track(null, artist)
                else -> Track(artist, title)
            }
        }
    }
}

/**
 * Pochette d'album du morceau en cours, via l'API publique de recherche iTunes
 * (sans clé). Résultats gardés en cache pour ne pas refaire la même requête.
 */
class TrackArtwork(private val client: OkHttpClient = RadioBrowserApi.defaultClient()) {

    @Serializable
    private data class Result(val artworkUrl100: String = "", val artistName: String = "")

    @Serializable
    private data class Response(val results: List<Result> = emptyList())

    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
    })

    /** URL d'une pochette 600×600, ou null si rien de fiable n'est trouvé. */
    suspend fun find(track: Track): String? {
        val artist = track.artist ?: return null // sans artiste, trop de fausses pochettes
        val key = "${artist.lowercase()}|${track.title.lowercase()}"
        cache[key]?.let { return it.ifEmpty { null } }
        val url = try {
            lookup(artist, track.title)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null // erreur réseau : on ne met pas en cache, on réessaiera
        }
        cache[key] = url.orEmpty()
        return url
    }

    private suspend fun lookup(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", "$artist $title")
            .addQueryParameter("media", "music")
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "5")
            .build()
        val body = client.newCall(Request.Builder().url(url).header("User-Agent", RadioBrowserApi.USER_AGENT).build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext null
        val results = AppJson.decodeFromString<Response>(body).results
        val wanted = normalize(artist)
        val match = results.firstOrNull { r ->
            val name = normalize(r.artistName)
            name.isNotEmpty() && (name.contains(wanted) || wanted.contains(name))
        } ?: return@withContext null
        match.artworkUrl100.takeIf { it.isNotBlank() }?.replace("100x100bb", "600x600bb")
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
