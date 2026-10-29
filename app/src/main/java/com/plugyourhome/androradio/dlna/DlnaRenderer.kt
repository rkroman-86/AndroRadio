package com.plugyourhome.androradio.dlna

import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** État de lecture rapporté par l'appareil DLNA. */
enum class RemoteTransport { PLAYING, LOADING, STOPPED, ERROR }

/**
 * Pilotage d'un lecteur DLNA par SOAP (services UPnP AVTransport et RenderingControl) :
 * l'appareil reçoit l'adresse du flux et le lit lui-même ; le téléphone n'est qu'une télécommande.
 */
class DlnaRenderer(val device: DlnaDevice, private val client: OkHttpClient) {

    /** Envoie la station puis lance la lecture. */
    suspend fun play(station: Station) {
        runCatching { action(AV, device.avTransportUrl, "Stop", "<InstanceID>0</InstanceID>") }
        action(
            AV, device.avTransportUrl, "SetAVTransportURI",
            "<InstanceID>0</InstanceID>" +
                "<CurrentURI>${escape(station.streamUrl)}</CurrentURI>" +
                "<CurrentURIMetaData>${escape(didl(station))}</CurrentURIMetaData>",
        )
        resume()
    }

    suspend fun resume() {
        action(AV, device.avTransportUrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun stop() {
        action(AV, device.avTransportUrl, "Stop", "<InstanceID>0</InstanceID>")
    }

    suspend fun transport(): RemoteTransport {
        val xml = action(AV, device.avTransportUrl, "GetTransportInfo", "<InstanceID>0</InstanceID>")
        return when (tag(xml, "CurrentTransportState")) {
            "PLAYING" -> RemoteTransport.PLAYING
            "TRANSITIONING" -> RemoteTransport.LOADING
            "STOPPED", "PAUSED_PLAYBACK", "NO_MEDIA_PRESENT" -> RemoteTransport.STOPPED
            else -> RemoteTransport.STOPPED
        }
    }

    /** Titre en cours vu par l'appareil (souvent « Artiste - Titre » lu dans le flux), si disponible. */
    suspend fun nowPlaying(): String? {
        val xml = action(AV, device.avTransportUrl, "GetPositionInfo", "<InstanceID>0</InstanceID>")
        val meta = unescape(tag(xml, "TrackMetaData") ?: return null)
        val title = tag(meta, "dc:title")?.let(::unescape)?.trim()
        val artist = tag(meta, "upnp:artist")?.let(::unescape)?.trim()
            ?: tag(meta, "dc:creator")?.let(::unescape)?.trim()
        return when {
            title.isNullOrBlank() -> null
            !artist.isNullOrBlank() && !title.contains(artist, ignoreCase = true) -> "$artist - $title"
            else -> title
        }
    }

    /** Volume 0..1 ; null si l'appareil ne le gère pas. */
    suspend fun volume(): Float? {
        val url = device.renderingControlUrl ?: return null
        val xml = action(RC, url, "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>")
        return tag(xml, "CurrentVolume")?.toIntOrNull()?.let { it.coerceIn(0, 100) / 100f }
    }

    suspend fun setVolume(value: Float) {
        val url = device.renderingControlUrl ?: return
        val level = (value.coerceIn(0f, 1f) * 100).toInt()
        action(
            RC, url, "SetVolume",
            "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$level</DesiredVolume>",
        )
    }

    private suspend fun action(service: String, url: String, name: String, args: String): String =
        withContext(Dispatchers.IO) {
            val envelope = """<?xml version="1.0" encoding="utf-8"?>""" +
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
                """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""" +
                """<u:$name xmlns:u="$service">$args</u:$name></s:Body></s:Envelope>"""
            val request = Request.Builder()
                .url(url)
                .header("SOAPACTION", "\"$service#$name\"")
                .post(envelope.toRequestBody(XML))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("$name: HTTP ${response.code}")
                body
            }
        }

    private fun didl(station: Station): String {
        val mime = mimeFor(station)
        val art = station.favicon.takeIf { it.startsWith("http") }
            ?.let { "<upnp:albumArtURI>${escape(it)}</upnp:albumArtURI>" }.orEmpty()
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="1" parentID="0" restricted="1">""" +
            "<dc:title>${escape(station.displayName)}</dc:title>" +
            "<upnp:class>object.item.audioItem.audioBroadcast</upnp:class>" +
            art +
            """<res protocolInfo="http-get:*:$mime:*">${escape(station.streamUrl)}</res>""" +
            "</item></DIDL-Lite>"
    }

    private fun mimeFor(station: Station): String {
        val codec = station.codec.uppercase()
        return when {
            station.hls == 1 || station.streamUrl.substringBefore('?').endsWith(".m3u8", true) ->
                "application/vnd.apple.mpegurl"
            codec.startsWith("AAC") -> "audio/aac"
            codec == "OGG" || codec == "VORBIS" -> "audio/ogg"
            codec == "OPUS" -> "audio/ogg"
            codec == "FLAC" -> "audio/flac"
            else -> "audio/mpeg"
        }
    }

    private companion object {
        const val AV = "urn:schemas-upnp-org:service:AVTransport:1"
        const val RC = "urn:schemas-upnp-org:service:RenderingControl:1"
        val XML = "text/xml; charset=\"utf-8\"".toMediaType()

        fun escape(s: String) = s
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

        fun unescape(s: String) = s
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")

        /** Contenu du premier <nom>…</nom>, avec ou sans préfixe d'espace de noms. */
        fun tag(xml: String, name: String): String? {
            val local = name.substringAfter(':')
            val regex = Regex("<(?:[\\w-]+:)?$local(?:\\s[^>]*)?>(.*?)</(?:[\\w-]+:)?$local>", RegexOption.DOT_MATCHES_ALL)
            return regex.find(xml)?.groupValues?.get(1)
        }
    }
}
