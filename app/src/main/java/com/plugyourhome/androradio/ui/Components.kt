package com.plugyourhome.androradio.ui

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.plugyourhome.androradio.R
import com.plugyourhome.androradio.data.Station

/**
 * Images fournies directement, sans réseau (captures d'écran générées pour Google Play).
 * Dans l'appli, toujours null : les images viennent d'internet via Coil.
 */
val LocalArtOverride = staticCompositionLocalOf<((String) -> Painter?)?> { null }

/** Pochette de la station (son logo) ; icône radio tant qu'elle n'est pas chargée ou si elle manque. */
@Composable
fun StationArt(url: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val override = url?.let { LocalArtOverride.current?.invoke(it) }
    if (override != null) {
        Image(
            painter = override,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(corner)),
        )
        return
    }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(
                Icons.Filled.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Case vide (favori non attribué). */
@Composable
fun EmptyArt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.empty),
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Une bande du haut en bas de l'écran : fond arrondi commun. */
@Composable
fun Band(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(8.dp)) { content() }
    }
}

@Composable
fun BandTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

/**
 * Bande de stations défilant de gauche à droite, 4 visibles à la fois.
 * [slots] peut contenir des cases vides (null) pour les favoris.
 */
@Composable
fun StationBand(
    title: String,
    slots: List<Station?>,
    currentUuid: String?,
    isActive: Boolean,
    emptyMessage: String,
    onPlay: (Station) -> Unit,
    onLongPress: (Station) -> Unit,
    modifier: Modifier = Modifier,
    numbered: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Band(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BandTitle(title) }
                trailing?.invoke()
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (slots.isEmpty()) {
                    Text(
                        emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                    )
                } else {
                    val gap = 6.dp
                    val tileWidth = (maxWidth - gap * (VISIBLE_TILES - 1)) / VISIBLE_TILES
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(slots) { index, station ->
                            StationTile(
                                station = station,
                                number = if (numbered) index + 1 else null,
                                isCurrent = station != null && station.uuid == currentUuid,
                                isActive = isActive,
                                onPlay = onPlay,
                                onLongPress = onLongPress,
                                modifier = Modifier.width(tileWidth).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationTile(
    station: Station?,
    number: Int?,
    isCurrent: Boolean,
    isActive: Boolean,
    onPlay: (Station) -> Unit,
    onLongPress: (Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                enabled = station != null,
                onClick = { station?.let(onPlay) },
                onLongClick = { station?.let(onLongPress) },
            )
            .padding(3.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            Box(Modifier.size(side)) {
                if (station != null) StationArt(station.favicon, Modifier.fillMaxSize())
                else EmptyArt(Modifier.fillMaxSize())
                if (number != null) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape)
                            .padding(horizontal = 5.dp),
                    )
                }
                if (isCurrent && isActive) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = stringResource(R.string.band_now_playing),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(2.dp)
                            .size(14.dp),
                    )
                }
            }
        }
        Text(
            station?.displayName ?: stringResource(R.string.empty),
            style = MaterialTheme.typography.labelSmall,
            fontStyle = if (station == null) FontStyle.Italic else FontStyle.Normal,
            color = if (station == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** Choix de la langue de l'appli (Android 13+ ; avant, l'appli suit la langue du système). */
@Composable
fun LanguageMenu() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LanguageMenuApi33()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageMenuApi33() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        null to stringResource(R.string.language_system),
        "en" to "English",
        "fr" to "Français",
        "ru" to "Русский",
    )
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Language,
                contentDescription = stringResource(R.string.language),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (tag, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        context.getSystemService(LocaleManager::class.java).applicationLocales =
                            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                    },
                )
            }
        }
    }
}

const val VISIBLE_TILES = 4
