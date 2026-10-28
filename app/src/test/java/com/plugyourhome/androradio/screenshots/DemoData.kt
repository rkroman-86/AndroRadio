package com.plugyourhome.androradio.screenshots

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.plugyourhome.androradio.data.Station
import com.plugyourhome.androradio.data.Track
import com.plugyourhome.androradio.playback.PlayerUiState

/**
 * Données fictives pour les captures Google Play : noms de stations inventés,
 * logos et pochettes dessinés pour l'occasion (src/test/resources/demo).
 * Aucune marque, aucun logo, aucune pochette de tiers.
 */
object DemoData {

    private fun station(key: String, name: String, country: String, codec: String, bitrate: Int, tags: String) =
        Station(
            uuid = "demo-$key",
            name = name,
            favicon = "demo:$key",
            country = country,
            codec = codec,
            bitrate = bitrate,
            tags = tags,
            url = "https://example.invalid/$key",
        )

    val coastJazz = station("coastjazz", "Coast Jazz", "France", "MP3", 192, "jazz,smooth jazz,lounge")
    val aurora = station("aurora", "Aurora FM", "Norway", "AAC", 128, "pop,hits")
    val lagoon = station("lagoon", "Blue Lagoon Radio", "Iceland", "MP3", 128, "chillout,ambient")
    val nightDrive = station("nightdrive", "Night Drive", "Germany", "MP3", 320, "synthwave,electronic")
    val pulse = station("pulse", "Pulse Radio", "Spain", "AAC", 96, "dance,electronic")
    val lounge = station("lounge", "Côte Lounge", "France", "MP3", 128, "lounge,jazz,downtempo")
    val vinyl = station("vinyl", "Vinyl Café", "Belgium", "MP3", 192, "soul,funk,jazz")
    val mistral = station("mistral", "Mistral Info", "France", "AAC", 64, "news,talk")
    val sunset = station("sunset", "Sunset Beats", "Portugal", "MP3", 128, "house,deep house")
    val nordic = station("nordic", "Nordic Chill", "Finland", "OGG", 160, "chillout,ambient")
    val echo = station("echo", "Echo Classic", "France", "MP3", 256, "classical,orchestra")
    val groove = station("groove", "Urban Groove", "France", "AAC", 128, "hip hop,rnb")
    val atlas = station("atlas", "Atlas World", "Morocco", "MP3", 128, "world,folk")
    val starlight = station("starlight", "Starlight", "Canada", "MP3", 128, "oldies,80s")
    val bossa = station("bossa", "Bossa Breeze", "Brazil", "MP3", 128, "bossa nova,jazz,latin")
    val retro = station("retro", "Retro 80", "Italy", "MP3", 192, "80s,synthpop")
    val focus = station("focus", "Deep Focus", "Netherlands", "AAC", 96, "ambient,study,jazz")
    val forest = station("forest", "Forest Calm", "Canada", "MP3", 128, "nature,relax")
    val harbor = station("harbor", "Harbor Soul", "France", "MP3", 128, "soul,jazz,blues")
    val metro = station("metro", "Metro Talk", "France", "AAC", 64, "talk,news")

    val recent = listOf(coastJazz, nightDrive, lagoon, vinyl, nordic, atlas, sunset, bossa, retro, echo)

    val favorites: List<Station?> = listOf(aurora, coastJazz, pulse, lounge, sunset, mistral, retro, starlight, nordic) +
        List(11) { null }

    val local = listOf(echo, groove, harbor, metro, lounge, mistral, focus, forest, vinyl, coastJazz)

    /** Résultats affichés pour la recherche « jazz ». */
    val jazzResults = listOf(coastJazz, bossa, vinyl, lounge, harbor, focus, echo)

    val track = Track(artist = "Luna Harbor", title = "Midnight Ferris Wheel")
    const val TRACK_ART = "demo:cover_harbor"

    val playing = PlayerUiState(
        station = coastJazz,
        nowPlaying = "${track.artist} - ${track.title}",
        isPlaying = true,
    )

    private val cache = HashMap<String, Painter?>()

    /** Charge « demo:clé » depuis les ressources de test, sans réseau. */
    fun painter(url: String): Painter? {
        if (!url.startsWith("demo:")) return null
        return cache.getOrPut(url) {
            val name = "demo/" + url.removePrefix("demo:") + ".png"
            javaClass.classLoader?.getResourceAsStream(name)?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()?.let(::BitmapPainter)
            }
        }
    }
}
