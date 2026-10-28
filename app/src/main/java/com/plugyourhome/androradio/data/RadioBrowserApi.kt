package com.plugyourhome.androradio.data

import com.plugyourhome.androradio.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client de l'API publique Radio Browser (https://api.radio-browser.info).
 *
 * Les serveurs sont fournis par Radio Browser lui-même (liste officielle), mélangés au hasard.
 * Si un serveur ne répond pas, la même requête est relancée sur le suivant.
 */
class RadioBrowserApi(private val client: OkHttpClient = defaultClient()) {

    @Serializable
    private data class Server(val name: String = "")

    @Volatile
    private var servers: List<String> = emptyList()

    private suspend fun servers(): List<String> {
        servers.takeIf { it.isNotEmpty() }?.let { return it }
        val discovered = runCatching {
            AppJson.decodeFromString<List<Server>>(get("https://all.api.radio-browser.info/json/servers".toHttpUrl()))
                .map { it.name.trim() }
                .filter { it.endsWith("radio-browser.info") }
                .distinct()
        }.getOrDefault(emptyList())
        return discovered.ifEmpty { FALLBACK_SERVERS }.shuffled().also { servers = it }
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }

    /** Essaie chaque serveur jusqu'à obtenir une réponse ; le serveur qui répond passe en tête. */
    private suspend fun request(segments: List<String>, params: Map<String, String?>): String {
        var lastError: Exception = IOException("No server")
        for (host in servers()) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .apply { segments.forEach { addPathSegment(it) } }
                .apply { params.forEach { (k, v) -> if (!v.isNullOrBlank()) addQueryParameter(k, v) } }
                .build()
            try {
                val body = get(url)
                if (servers.firstOrNull() != host) servers = listOf(host) + (servers - host)
                return body
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError
    }

    private suspend fun stations(segments: List<String>, limit: Int): List<Station> =
        AppJson.decodeFromString<List<Station>>(
            request(
                segments,
                mapOf(
                    "order" to "clickcount",
                    "reverse" to "true",
                    "hidebroken" to "true",
                    "limit" to limit.toString(),
                ),
            )
        ).filter { it.isPlayable }

    /** Stations les plus écoutées d'un pays (code ISO à 2 lettres), ou du monde si vide. */
    suspend fun popular(countryCode: String?, limit: Int): List<Station> {
        val fetch = limit * 3 // marge pour les doublons retirés
        val list = if (countryCode.isNullOrBlank()) stations(listOf("json", "stations", "topclick", fetch.toString()), fetch)
        else stations(listOf("json", "stations", "bycountrycodeexact", countryCode.uppercase()), fetch)
        return list.dedupe().take(limit)
    }

    /**
     * Recherche un texte dans chaque critère de Radio Browser, l'un après l'autre :
     * nom, genre (tag), pays, code pays, langue, région, codec.
     * Les résultats sont fusionnés dans cet ordre, sans doublons.
     * Un critère qui échoue n'empêche pas les autres de répondre.
     */
    suspend fun searchEverywhere(text: String, limitPerField: Int = 60): List<Station> = coroutineScope {
        val q = text.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val fields = buildList {
            add("byname")
            add("bytag")
            add("bycountry")
            if (q.length == 2 && q.all { it.isLetter() }) add("bycountrycodeexact")
            add("bylanguage")
            add("bystate")
            add("bycodec")
        }
        val failures = AtomicInteger(0)
        val results = fields.map { field ->
            async {
                try {
                    val term = if (field == "bycountrycodeexact") q.uppercase() else q
                    stations(listOf("json", "stations", field, term), limitPerField)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.incrementAndGet()
                    emptyList()
                }
            }
        }.awaitAll()
        if (failures.get() == fields.size) throw IOException("All servers failed")
        results.flatten().dedupe()
    }

    /** Signale une écoute à Radio Browser (sert au classement communautaire). */
    suspend fun registerClick(uuid: String) {
        runCatching { request(listOf("json", "url", uuid), emptyMap()) }
    }

    companion object {
        val USER_AGENT = "AndroRadio/${BuildConfig.VERSION_NAME}"
        private val FALLBACK_SERVERS = listOf(
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "fi1.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "at1.api.radio-browser.info",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Doublons : même identifiant, même flux, ou même nom dans le même pays
 * (l'annuaire liste souvent une radio plusieurs fois). On garde la première,
 * donc la plus écoutée puisque les listes sont triées par popularité.
 */
fun List<Station>.dedupe(): List<Station> {
    val seen = HashSet<String>()
    return filter { s ->
        val name = s.name.trim().lowercase().replace(Regex("\\s+"), " ")
        val keys = listOf(
            "id:" + s.uuid,
            "url:" + s.streamUrl.trimEnd('/').lowercase(),
            "name:" + name + "|" + s.countryCode.uppercase(),
        )
        val isNew = keys.none { it in seen }
        seen.addAll(keys)
        isNew
    }
}
