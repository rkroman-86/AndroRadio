package com.plugyourhome.androradio.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.plugyourhome.androradio.ui.CastPage
import com.plugyourhome.androradio.ui.FavoritesPage
import com.plugyourhome.androradio.ui.HomeBands
import com.plugyourhome.androradio.ui.LoadState
import com.plugyourhome.androradio.ui.LocalArtOverride
import com.plugyourhome.androradio.ui.OutputKind
import com.plugyourhome.androradio.ui.OutputList
import com.plugyourhome.androradio.ui.OutputTarget
import com.plugyourhome.androradio.ui.SearchPage
import com.plugyourhome.androradio.ui.theme.AndroRadioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Génère les captures d'écran de la fiche Google Play, dans les 3 langues,
 * avec des stations fictives (aucune marque de tiers).
 *
 * Scènes : accueil, favoris, recherche, « Diffuser vers… ».
 * Lancement : ./gradlew recordRoborazziDebug
 * Résultat :  fastlane/metadata/android/<langue>/images/<format>/
 */
abstract class StoreScreenshots(private val folder: String) {

    @get:Rule
    val compose = createComposeRule()

    private fun output(locale: String, index: Int, scene: String) =
        "../fastlane/metadata/android/$locale/images/$folder/${index}_$scene.png"

    /**
     * Affiche [content] et enregistre une capture de tout l'écran (fenêtres flottantes comprises).
     * La langue vient du @Config de chaque test : elle s'applique aussi aux fenêtres flottantes.
     */
    private fun shoot(locale: String, index: Int, scene: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalArtOverride provides DemoData::painter) {
                AndroRadioTheme(darkTheme = true, dynamicColor = false) { content() }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(output(locale, index, scene))
    }

    @Composable
    private fun Home() {
        val language = LocalConfiguration.current.locales[0]
        HomeBands(
            current = DemoData.coastJazz,
            player = DemoData.playing,
            track = DemoData.track,
            trackArt = DemoData.TRACK_ART,
            recent = DemoData.recent,
            favorites = DemoData.favorites,
            local = LoadState.Loaded(DemoData.local),
            countryName = Locale("", "FR").getDisplayCountry(language),
            onPlay = {}, onLongPress = {}, onRefreshLocal = {}, onPlayStop = {},
            onFavorites = {}, onSearch = {}, onAbout = {},
        )
    }

    @Composable
    private fun FavoritesScene() {
        Home()
        FavoritesPage(
            slots = DemoData.favorites,
            stationToSave = DemoData.vinyl,
            currentUuid = DemoData.coastJazz.uuid,
            onSave = {}, onDelete = {}, onMove = { _, _ -> }, onPlay = {}, onClose = {},
        )
    }

    @Composable
    private fun SearchScene() {
        Home()
        SearchPage(
            query = "jazz",
            state = LoadState.Loaded(DemoData.jazzResults),
            currentUuid = DemoData.coastJazz.uuid,
            onQueryChange = {}, onSearch = {}, onPlay = {}, onAddFavorite = {}, onClose = {},
        )
    }

    /** Appareils fictifs, noms génériques traduits (aucune marque). */
    @Composable
    private fun CastScene() {
        val names = when (LocalConfiguration.current.locales[0].language) {
            "fr" -> listOf("TV du salon", "Enceinte cuisine", "Ampli hi-fi", "Enceinte terrasse")
            "ru" -> listOf("ТВ в гостиной", "Колонка на кухне", "Hi-Fi усилитель", "Колонка на террасе")
            else -> listOf("Living room TV", "Kitchen speaker", "Hi-Fi amplifier", "Terrace speaker")
        }
        val targets = listOf(
            OutputTarget("cast:tv", names[0], OutputKind.CAST),
            OutputTarget("cast:kitchen", names[1], OutputKind.CAST),
            OutputTarget("dlna:amp", names[2], OutputKind.DLNA),
            OutputTarget("dlna:terrace", names[3], OutputKind.DLNA),
        )
        Home()
        CastPage(
            outputs = OutputList(searching = false, targets = targets),
            currentId = "dlna:amp",
            onPhone = {}, onDevice = {}, onRefresh = {}, onClose = {},
        )
    }

    @Test @Config(qualifiers = "+en-rUS")
    fun home_en() = shoot("en-US", 1, "home") { Home() }

    @Test @Config(qualifiers = "+fr-rFR")
    fun home_fr() = shoot("fr-FR", 1, "home") { Home() }

    @Test @Config(qualifiers = "+ru-rRU")
    fun home_ru() = shoot("ru-RU", 1, "home") { Home() }

    @Test @Config(qualifiers = "+en-rUS")
    fun favorites_en() = shoot("en-US", 2, "favorites") { FavoritesScene() }

    @Test @Config(qualifiers = "+fr-rFR")
    fun favorites_fr() = shoot("fr-FR", 2, "favorites") { FavoritesScene() }

    @Test @Config(qualifiers = "+ru-rRU")
    fun favorites_ru() = shoot("ru-RU", 2, "favorites") { FavoritesScene() }

    @Test @Config(qualifiers = "+en-rUS")
    fun search_en() = shoot("en-US", 3, "search") { SearchScene() }

    @Test @Config(qualifiers = "+fr-rFR")
    fun search_fr() = shoot("fr-FR", 3, "search") { SearchScene() }

    @Test @Config(qualifiers = "+ru-rRU")
    fun search_ru() = shoot("ru-RU", 3, "search") { SearchScene() }

    @Test @Config(qualifiers = "+en-rUS")
    fun cast_en() = shoot("en-US", 4, "cast") { CastScene() }

    @Test @Config(qualifiers = "+fr-rFR")
    fun cast_fr() = shoot("fr-FR", 4, "cast") { CastScene() }

    @Test @Config(qualifiers = "+ru-rRU")
    fun cast_ru() = shoot("ru-RU", 4, "cast") { CastScene() }
}

// Formats demandés par Google Play (rapport largeur/hauteur ≤ 2)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h820dp-xxhdpi")
class PhoneScreenshots : StoreScreenshots("phoneScreenshots")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h960dp-xhdpi")
class SevenInchScreenshots : StoreScreenshots("sevenInchScreenshots")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class TenInchScreenshots : StoreScreenshots("tenInchScreenshots")
