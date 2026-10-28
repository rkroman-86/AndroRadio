package com.plugyourhome.androradio.screenshots

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.plugyourhome.androradio.ui.FavoritesPage
import com.plugyourhome.androradio.ui.HomeBands
import com.plugyourhome.androradio.ui.LoadState
import com.plugyourhome.androradio.ui.LocalArtOverride
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
 * Lancement : ./gradlew recordRoborazziDebug
 * Résultat :  fastlane/metadata/android/<langue>/images/<format>/
 */
abstract class StoreScreenshots(private val folder: String) {

    @get:Rule
    val compose = createComposeRule()

    private val locales = listOf("en-US", "fr-FR", "ru-RU")

    private fun output(locale: String, index: Int, scene: String) =
        "../fastlane/metadata/android/$locale/images/$folder/${index}_$scene.png"

    /** Affiche [content] dans chaque langue et enregistre une capture de tout l'écran (fenêtres flottantes comprises). */
    private fun shoot(index: Int, scene: String, content: @Composable () -> Unit) {
        var locale by mutableStateOf(locales.first())
        compose.setContent {
            Localized(locale) {
                CompositionLocalProvider(LocalArtOverride provides DemoData::painter) {
                    AndroRadioTheme(darkTheme = true, dynamicColor = false) { content() }
                }
            }
        }
        for (tag in locales) {
            locale = tag
            compose.waitForIdle()
            captureScreenRoboImage(output(tag, index, scene))
        }
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

    @Test
    fun home() = shoot(1, "home") { Home() }

    @Test
    fun favorites() = shoot(2, "favorites") {
        Home()
        FavoritesPage(
            slots = DemoData.favorites,
            stationToSave = DemoData.vinyl,
            currentUuid = DemoData.coastJazz.uuid,
            onSave = {}, onDelete = {}, onMove = { _, _ -> }, onPlay = {}, onClose = {},
        )
    }

    @Test
    fun search() = shoot(3, "search") {
        Home()
        SearchPage(
            query = "jazz",
            state = LoadState.Loaded(DemoData.jazzResults),
            currentUuid = DemoData.coastJazz.uuid,
            onQueryChange = {}, onSearch = {}, onPlay = {}, onAddFavorite = {}, onClose = {},
        )
    }
}

/** Fournit des ressources (textes) dans la langue demandée. */
@Composable
private fun Localized(tag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    val context = base.createConfigurationContext(config)
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) { content() }
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
