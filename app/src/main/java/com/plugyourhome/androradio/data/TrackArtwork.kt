package com.plugyourhome.androradio.data

import com.plugyourhome.androradio.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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
 * Pochette d'album du morceau en cours :
 * 1. d'abord des bases ouvertes :
 *    - MusicBrainz (https://musicbrainz.org) pour identifier l'album à partir d'« artiste + titre » ;
 *    - Cover Art Archive (https://coverartarchive.org) pour l'image elle-même ;
 * 2. si rien n'est trouvé (ou si MusicBrainz ne répond pas), l'API de recherche iTunes (Apple),
 *    plus complète pour les morceaux récents.
 *
 * Règles MusicBrainz respectées : User-Agent avec nom, version et contact ;
 * au plus une requête par seconde ; résultats gardés en cache.
 */
class TrackArtwork(client: OkHttpClient = RadioBrowserApi.defaultClient()) {

    private val client = client.newBuilder().build()
    private val noRedirectClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    @Serializable
    private data class SearchResponse(val recordings: List<Recording> = emptyList())

    @Serializable
    private data class Recording(
        val score: Int = 0,
        @SerialName("artist-credit") val artistCredit: List<ArtistCredit> = emptyList(),
        val releases: List<Release> = emptyList(),
    )

    @Serializable
    private data class ArtistCredit(val name: String = "")

    @Serializable
    private data class Release(
        val id: String = "",
        val status: String = "",
        @SerialName("release-group") val releaseGroup: ReleaseGroup? = null,
    )

    @Serializable
    private data class ReleaseGroup(
        val id: String = "",
        @SerialName("primary-type") val primaryType: String = "",
        @SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
    )

    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 300
    })

    private val throttle = Mutex()
    private var lastMusicBrainzCall = 0L

    /** URL d'une pochette (500 px), ou null si rien de fiable n'est trouvé. */
    suspend fun find(track: Track): String? {
        val artist = track.artist ?: return null // sans artiste, trop de fausses pochettes
        val key = "${artist.lowercase()}|${track.title.lowercase()}"
        cache[key]?.let { return it.ifEmpty { null } }
        val url = try {
            lookupOpenData(artist, track.title) ?: lookupApple(artist, track.title)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null // erreur réseau : pas de cache, on réessaiera au prochain morceau
        }
        cache[key] = url.orEmpty()
        return url
    }

    @Serializable
    private data class AppleResponse(val results: List<AppleResult> = emptyList())

    @Serializable
    private data class AppleResult(val artworkUrl100: String = "", val artistName: String = "")

    /** Recherche iTunes (secours) ; erreur ou rien de fiable → null (on garde le logo de la station). */
    private suspend fun lookupApple(artist: String, title: String): String? = runCatching {
        withContext(Dispatchers.IO) {
            val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", "$artist $title")
                .addQueryParameter("media", "music")
                .addQueryParameter("entity", "song")
                .addQueryParameter("limit", "5")
                .build()
            val body = client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
                .execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext null
            val wanted = normalize(artist)
            AppJson.decodeFromString<AppleResponse>(body).results
                .firstOrNull { r ->
                    val name = normalize(r.artistName)
                    name.isNotEmpty() && (name.contains(wanted) || wanted.contains(name))
                }
                ?.artworkUrl100?.takeIf { it.isNotBlank() }
                ?.replace("100x100bb", "600x600bb")
        }
    }.getOrNull()

    /** MusicBrainz + Cover Art Archive ; une erreur réseau donne null pour laisser sa chance à Apple. */
    private suspend fun lookupOpenData(artist: String, title: String): String? = try {
        lookupMusicBrainz(artist, title)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun lookupMusicBrainz(artist: String, title: String): String? {
        val recordings = searchRecordings(artist, title)
        val wanted = normalize(artist)
        val releases = recordings
            .filter { it.score >= MIN_SCORE }
            .filter { rec ->
                val credited = normalize(rec.artistCredit.joinToString(" ") { it.name })
                credited.isNotEmpty() && (credited.contains(wanted) || wanted.contains(credited))
            }
            .flatMap { it.releases }

        // D'abord les albums / singles officiels, ensuite le reste (compilations, live…)
        val ranked = releases.sortedBy { r ->
            val group = r.releaseGroup
            var rank = 0
            if (!r.status.equals("Official", ignoreCase = true)) rank += 2
            if (group?.secondaryTypes?.isNotEmpty() == true) rank += 4
            if (group?.primaryType.orEmpty() !in setOf("Album", "Single", "EP")) rank += 1
            rank
        }
        val candidates = buildList {
            ranked.mapNotNull { it.releaseGroup?.id?.takeIf(String::isNotBlank) }.distinct().take(4)
                .forEach { add("$COVER_ART/release-group/$it/front-500") }
            ranked.map { it.id }.filter(String::isNotBlank).distinct().take(2)
                .forEach { add("$COVER_ART/release/$it/front-500") }
        }
        return candidates.firstOrNull { exists(it) }
    }

    private suspend fun searchRecordings(artist: String, title: String): List<Recording> {
        val query = "recording:\"${escape(title)}\" AND artist:\"${escape(artist)}\""
        val url = "https://musicbrainz.org/ws/2/recording".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "10")
            .build()
        return throttle.withLock {
            // MusicBrainz : environ une requête par seconde au maximum
            val wait = lastMusicBrainzCall + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                withContext<List<Recording>>(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
                        .execute().use { response ->
                            if (!response.isSuccessful) return@withContext emptyList()
                            val body = response.body?.string() ?: return@withContext emptyList()
                            AppJson.decodeFromString<SearchResponse>(body).recordings
                        }
                }
            } finally {
                lastMusicBrainzCall = System.currentTimeMillis()
            }
        }
    }

    /** Cover Art Archive répond 307 (redirection vers l'image) si une pochette existe, 404 sinon. */
    private suspend fun exists(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).head().header("User-Agent", USER_AGENT).build()
            noRedirectClient.newCall(request).execute().use { it.code in 200..399 }
        }.getOrDefault(false)
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        const val COVER_ART = "https://coverartarchive.org"
        const val MIN_SCORE = 80
        const val MIN_INTERVAL_MS = 1_100L

        /** Format demandé par MusicBrainz : « Nom/version ( contact ) ». */
        val USER_AGENT = "AndroRadio/${BuildConfig.VERSION_NAME} ( https://github.com/rkroman-86/AndroRadio )"
    }
}
