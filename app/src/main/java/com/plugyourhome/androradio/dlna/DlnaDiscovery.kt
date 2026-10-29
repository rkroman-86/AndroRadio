package com.plugyourhome.androradio.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Un lecteur DLNA / UPnP (MediaRenderer) trouvé sur le réseau local. */
data class DlnaDevice(
    val id: String,              // UDN, identifiant unique de l'appareil
    val name: String,            // friendlyName, ex. « Arylic LA50 7165be »
    val avTransportUrl: String,  // contrôle lecture : SetAVTransportURI, Play, Stop…
    val renderingControlUrl: String?, // volume (facultatif)
)

/**
 * Découverte SSDP des lecteurs DLNA (UPnP MediaRenderer) :
 * une requête M-SEARCH en multicast, puis lecture de la description XML de chaque appareil.
 */
class DlnaDiscovery(private val context: Context, private val client: OkHttpClient) {

    suspend fun discover(timeoutMs: Int = 3_000): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val locations = searchLocations(timeoutMs)
        coroutineScope {
            locations.map { url -> async { runCatching { describe(url) }.getOrNull() } }.awaitAll()
        }.filterNotNull().distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    /** Envoie M-SEARCH et récupère les adresses (LOCATION) des descriptions d'appareils. */
    private fun searchLocations(timeoutMs: Int): Set<String> {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("AndroRadio-DLNA")?.apply { setReferenceCounted(false); acquire() }
        val found = LinkedHashSet<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 400
                val group = InetAddress.getByName(SSDP_ADDRESS)
                // Deux cibles : lecteurs DLNA et, par sécurité, tous les appareils UPnP racines.
                for (target in listOf(RENDERER, "upnp:rootdevice")) {
                    val message = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $target\r\n" +
                            "USER-AGENT: Android UPnP/1.1 AndroRadio\r\n\r\n"
                        ).toByteArray()
                    repeat(2) { socket.send(DatagramPacket(message, message.size, group, SSDP_PORT)) }
                }
                val buffer = ByteArray(2048)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    val location = text.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim()
                    if (!location.isNullOrBlank()) found += location
                }
            }
        } finally {
            lock?.release()
        }
        return found
    }

    /** Lit la description de l'appareil ; null si ce n'est pas un lecteur (pas d'AVTransport). */
    private fun describe(location: String): DlnaDevice? {
        val base = location.toHttpUrlOrNull() ?: return null
        val xml = client.newCall(Request.Builder().url(base).build()).execute().use {
            if (!it.isSuccessful) return null
            it.body?.string() ?: return null
        }

        var udn: String? = null
        var name: String? = null
        var urlBase: String? = null
        var avTransport: String? = null
        var rendering: String? = null
        var isRenderer = false

        // État courant pendant la lecture d'un <service>
        var serviceType: String? = null
        var controlUrl: String? = null

        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        var tag = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "service") { serviceType = null; controlUrl = null }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text.trim()
                    if (value.isEmpty()) continue
                    when (tag) {
                        "URLBase" -> urlBase = value
                        "deviceType" -> if (value.contains("MediaRenderer")) isRenderer = true
                        "friendlyName" -> if (name == null) name = value
                        "UDN" -> if (udn == null) udn = value
                        "serviceType" -> serviceType = value
                        "controlURL" -> controlUrl = value
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "service") {
                        when {
                            serviceType?.contains("AVTransport") == true -> avTransport = controlUrl
                            serviceType?.contains("RenderingControl") == true -> rendering = controlUrl
                        }
                    }
                    tag = ""
                }
            }
        }

        val root = urlBase?.toHttpUrlOrNull() ?: base
        fun absolute(path: String?) = path?.let { root.resolve(it)?.toString() }
        val transportUrl = absolute(avTransport) ?: return null
        if (!isRenderer && avTransport == null) return null
        return DlnaDevice(
            id = udn ?: location,
            name = name ?: base.host,
            avTransportUrl = transportUrl,
            renderingControlUrl = absolute(rendering),
        )
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    }
}
