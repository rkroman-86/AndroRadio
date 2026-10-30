package com.plugyourhome.androradio.dlna

import android.os.Bundle
import com.plugyourhome.androradio.data.AppJson
import com.plugyourhome.androradio.data.Station
import kotlinx.serialization.encodeToString

/** Commandes échangées entre l'appli et le service de lecture pour la diffusion DLNA. */
object DlnaCommands {
    const val CONNECT = "com.plugyourhome.androradio.DLNA_CONNECT"
    const val DISCONNECT = "com.plugyourhome.androradio.DLNA_DISCONNECT"
    const val SET_VOLUME = "com.plugyourhome.androradio.DLNA_SET_VOLUME"
    const val KEY_VOLUME = "volume"

    const val KEY_RESUME = "resume"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_AV = "av"
    private const val KEY_RC = "rc"
    private const val KEY_STATION = "station"

    /** Extras de session : nom et identifiant de l'appareil DLNA en cours. */
    const val EXTRA_OUTPUT_NAME = "output_name"
    const val EXTRA_OUTPUT_ID = "output_id"
    /** Volume réel de l'appareil DLNA (0..1), -1 si inconnu. */
    const val EXTRA_VOLUME = "output_volume"

    fun connectArgs(device: DlnaDevice, station: Station?) = Bundle().apply {
        putString(KEY_ID, device.id)
        putString(KEY_NAME, device.name)
        putString(KEY_AV, device.avTransportUrl)
        putString(KEY_RC, device.renderingControlUrl)
        station?.let { putString(KEY_STATION, AppJson.encodeToString(it)) }
    }

    fun deviceFrom(args: Bundle): DlnaDevice? {
        val id = args.getString(KEY_ID) ?: return null
        val av = args.getString(KEY_AV) ?: return null
        return DlnaDevice(id, args.getString(KEY_NAME) ?: id, av, args.getString(KEY_RC))
    }

    fun stationFrom(args: Bundle): Station? =
        args.getString(KEY_STATION)?.let { runCatching { AppJson.decodeFromString<Station>(it) }.getOrNull() }

    fun extrasFor(device: DlnaDevice, volume: Float?) = Bundle().apply {
        putString(EXTRA_OUTPUT_NAME, device.name)
        putString(EXTRA_OUTPUT_ID, device.id)
        putFloat(EXTRA_VOLUME, volume ?: -1f)
    }
}
