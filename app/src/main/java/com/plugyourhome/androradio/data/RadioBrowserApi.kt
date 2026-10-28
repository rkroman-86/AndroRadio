package com.plugyourhome.androradio.data

import com.plugyourhome.androradio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client minimal de l'API publique Radio Browser (https://api.radio-browser.info).
 * Les serveurs miroirs sont découverts au premier appel ; en cas d'échec on passe au suivant.
 */
class RadioBrowserApi(private val client: OkHttpClient = defaultClient()) {

    @Serializable
    private data class Server(val name: String = "")

    @Volatile
    private var servers: List<String> = emptyList()

    private suspend fun servers(): List<String> {
        servers.takeIf { it.isNotEmpty() }?.let { return it }
        val discovered = runCatching {
            get("https://all.api.radio-browser.info/json/servers".toHttpUrl())
                .let { AppJson.decodeFromString<List<Server>>(it) }
                .map { it.name }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
        return (discovered.ifEmpty { FALLBACK_SERVERS }).shuffled().also { servers = it }
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty body")
        }
    }

    /** Essaie chaque serveur miroir jusqu'à obtenir une réponse. */
    private suspend fun request(path: String, params: Map<String, String?>): String {
        var lastError: Exception = IOException("No server")
        for (host in servers()) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .addPathSegments(path)
                .apply { params.forEach { (k, v) -> if (!v.isNullOrBlank()) addQueryParameter(k, v) } }
                .build()
            try {
                return get(url)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
            }
        }
        throw lastError
    }

    private suspend fun stations(path: String, params: Map<String, String?>): List<Station> =
        AppJson.decodeFromString<List<Station>>(request(path, params))
            .filter { it.streamUrl.startsWith("http", ignoreCase = true) && it.name.isNotBlank() }
            .distinctBy { it.name.trim().lowercase() + "|" + it.streamUrl }

    /** Stations les plus écoutées, d'un pays (code ISO) ou du monde entier. */
    suspend fun popular(countryCode: String?, limit: Int = PAGE_SIZE): List<Station> =
        search(name = null, countryCode = countryCode, tag = null, limit = limit)

    suspend fun search(
        name: String?,
        countryCode: String?,
        tag: String?,
        limit: Int = PAGE_SIZE,
    ): List<Station> = stations(
        "json/stations/search",
        mapOf(
            "name" to name?.trim(),
            "countrycode" to countryCode,
            "tag" to tag,
            "order" to "clickcount",
            "reverse" to "true",
            "hidebroken" to "true",
            "limit" to limit.toString(),
        ),
    )

    /** Signale une écoute à Radio Browser (sert au classement communautaire). */
    suspend fun registerClick(uuid: String) {
        runCatching { request("json/url/$uuid", emptyMap()) }
    }

    companion object {
        const val PAGE_SIZE = 100
        val USER_AGENT = "AndroRadio/${BuildConfig.VERSION_NAME}"
        private val FALLBACK_SERVERS = listOf(
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "fi1.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "at1.api.radio-browser.info",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
