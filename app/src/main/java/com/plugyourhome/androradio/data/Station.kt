package com.plugyourhome.androradio.data

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** JSON partagé : tolérant aux champs inconnus ou null renvoyés par Radio Browser. */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/** Une station, telle que renvoyée par l'API Radio Browser (champs utiles uniquement). */
@Serializable
data class Station(
    @SerialName("stationuuid") val uuid: String,
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val homepage: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    @SerialName("countrycode") val countryCode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Int = 0,
    @SerialName("lastcheckok") val lastCheckOk: Int = 1,
) {
    val displayName: String get() = name.trim().ifBlank { "?" }

    val streamUrl: String get() = urlResolved.ifBlank { url }.trim()

    /** Type de flux, déduit du codec annoncé par Radio Browser. */
    val mimeType: String
        get() {
            val c = codec.uppercase()
            return when {
                hls == 1 || streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                c.startsWith("AAC") -> "audio/aac" // type lisible par les récepteurs Cast
                c == "OGG" || c == "VORBIS" -> "audio/ogg"
                c == "OPUS" -> "audio/ogg"
                c == "FLAC" -> "audio/flac"
                else -> "audio/mpeg"
            }
        }

    val isPlayable: Boolean get() = name.isNotBlank() && streamUrl.startsWith("http", ignoreCase = true)

    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** « France · MP3 128 kbps » */
    val details: String
        get() = buildList {
            if (country.isNotBlank()) add(country.trim())
            val format = listOfNotNull(
                codec.takeIf { it.isNotBlank() && it != "UNKNOWN" },
                bitrate.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" ")
            if (format.isNotBlank()) add(format)
        }.joinToString(" · ")

    @OptIn(UnstableApi::class)
    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply { putString(EXTRA_STATION_JSON, AppJson.encodeToString(this@Station)) }
        val metadata = MediaMetadata.Builder()
            // Titre et artiste laissés vides exprès : ExoPlayer y place le morceau en cours
            // (ICY ou ID3). Un titre fixé ici masquerait ces métadonnées.
            .setStation(displayName)
            .setGenre(tagList.firstOrNull())
            .setArtworkUri(favicon.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()
        val uri = Uri.parse(streamUrl)
        return MediaItem.Builder()
            .setMediaId(uuid)
            .setUri(uri)
            .setMimeType(mimeType) // indispensable pour la diffusion Google Cast
            .setMediaMetadata(metadata)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
    }

    companion object {
        const val EXTRA_STATION_JSON = "com.plugyourhome.androradio.STATION_JSON"

        /** Reconstruit la station à partir des métadonnées d'un MediaItem. */
        fun fromMetadata(metadata: MediaMetadata?): Station? {
            val json = metadata?.extras?.getString(EXTRA_STATION_JSON) ?: return null
            return runCatching { AppJson.decodeFromString<Station>(json) }.getOrNull()
        }
    }
}
