package com.plugyourhome.androradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plugyourhome.androradio.R
import com.plugyourhome.androradio.data.Station

/** Cadre commun des pages flottantes (favoris, recherche). */
@Composable
private fun FloatingPage(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .imePadding(),
        ) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                content()
            }
        }
    }
}

/**
 * Liste complète des 20 favoris. Chaque case : Enregistrer (y place la station choisie)
 * et Supprimer. Les cases libres sont marquées « Vide ».
 */
@Composable
fun FavoritesPage(
    slots: List<Station?>,
    stationToSave: Station?,
    currentUuid: String?,
    onSave: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlay: (Station) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, onMove)
    FloatingPage(stringResource(R.string.band_favorites), onClose) {
        // Station à enregistrer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (stationToSave != null) {
                StationArt(stationToSave.favicon, Modifier.size(40.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.favorites_save_target, stationToSave.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    stringResource(R.string.favorites_no_target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.favorites_reorder_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        )
        HorizontalDivider()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().reorderable(reorder),
        ) {
            itemsIndexed(slots, key = { index, station -> station?.uuid ?: "empty-$index" }) { index, station ->
                val dragging = reorder.draggingIndex == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                        .graphicsLayer {
                            if (dragging) {
                                translationY = reorder.offset
                                shadowElevation = 12f
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .background(
                            if (dragging) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp),
                        )
                        .fillMaxWidth()
                        .clickable(enabled = station != null) { station?.let(onPlay) }
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(26.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    if (station != null) StationArt(station.favicon, Modifier.size(44.dp), corner = 8.dp)
                    else EmptyArt(Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            station?.displayName ?: stringResource(R.string.empty),
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = if (station == null) FontStyle.Italic else FontStyle.Normal,
                            color = if (station == null) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (station != null && station.details.isNotBlank()) {
                            Text(
                                station.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (station != null && station.uuid == currentUuid) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    FilledTonalIconButton(onClick = { onSave(index) }, enabled = stationToSave != null) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedIconButton(onClick = { onDelete(index) }, enabled = station != null) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

/** Recherche sur Radio Browser dans tous les critères (nom, genre, pays, langue…). */
@Composable
fun SearchPage(
    query: String,
    state: LoadState,
    busy: Boolean = false,
    currentUuid: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (Station) -> Unit,
    onAddFavorite: (Station) -> Unit,
    onClose: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state is LoadState.Idle) runCatching { focusRequester.requestFocus() } }
    val submit = {
        focusManager.clearFocus()
        onSearch()
    }

    FloatingPage(stringResource(R.string.search_title), onClose) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            trailingIcon = {
                IconButton(onClick = submit) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(focusRequester),
        )

        Box(Modifier.fillMaxSize()) {
            when (state) {
                LoadState.Idle -> CenterMessage(stringResource(R.string.search_help))
                LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LoadState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                ) {
                    Text(
                        stringResource(R.string.error_loading),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = submit) { Text(stringResource(R.string.retry)) }
                }
                is LoadState.Loaded -> if (state.stations.isEmpty()) {
                    CenterMessage(stringResource(R.string.no_results))
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // D'autres critères arrivent encore
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                        Text(
                            stringResource(R.string.results_count, state.stations.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                        LazyColumn(
                            state = listState, // conservé par le ViewModel : même position à la réouverture
                            contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.stations, key = { it.uuid }) { station ->
                                SearchResultRow(
                                    station = station,
                                    isCurrent = station.uuid == currentUuid,
                                    onPlay = { onPlay(station) },
                                    onAddFavorite = { onAddFavorite(station) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    station: Station,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onAddFavorite: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        StationArt(station.favicon, Modifier.size(52.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (station.details.isNotBlank()) {
                Text(
                    station.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val extra = (listOf(station.language).filter { it.isNotBlank() } + station.tagList.take(4))
                .joinToString(" · ")
            if (extra.isNotBlank()) {
                Text(
                    extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onAddFavorite) {
            Icon(Icons.Filled.FavoriteBorder, contentDescription = stringResource(R.string.add_favorite))
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Page « À propos » : version, sources des données, confidentialité, logiciels libres. */
@Composable
fun AboutPage(onClose: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    fun open(url: String) = runCatching { uriHandler.openUri(url) }

    FloatingPage(stringResource(R.string.about), onClose) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.about_by, com.plugyourhome.androradio.BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AboutSection(R.string.about_directory_title, R.string.about_directory_text)
                LinkButton("radio-browser.info") { open(URL_RADIO_BROWSER) }
            }
            item {
                AboutSection(R.string.about_art_title, R.string.about_art_text)
                Row {
                    LinkButton("MusicBrainz") { open(URL_MUSICBRAINZ) }
                    LinkButton("Cover Art Archive") { open(URL_COVER_ART) }
                }
            }
            item { AboutSection(R.string.about_streams_title, R.string.about_streams_text) }
            item {
                AboutSection(R.string.about_privacy_title, R.string.about_privacy_text)
                LinkButton(stringResource(R.string.about_privacy_link)) { open(URL_PRIVACY) }
            }
            item {
                AboutSection(R.string.about_opensource_title, R.string.about_opensource_text)
                LinkButton(stringResource(R.string.about_source_code)) { open(URL_SOURCE) }
            }
            item { Spacer(Modifier.size(12.dp)) }
        }
    }
}

@Composable
private fun AboutSection(title: Int, text: Int) {
    Column {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
    }
}

const val URL_RADIO_BROWSER = "https://www.radio-browser.info"
const val URL_MUSICBRAINZ = "https://musicbrainz.org"
const val URL_COVER_ART = "https://coverartarchive.org"
const val URL_SOURCE = "https://github.com/rkroman-86/AndroRadio"
const val URL_PRIVACY = "https://github.com/rkroman-86/AndroRadio/blob/main/PRIVACY.md"

/** « Diffuser vers… » : ce téléphone, ou un appareil DLNA trouvé sur le réseau local. */
@Composable
fun CastPage(
    outputs: OutputList,
    currentId: String?,
    onPhone: () -> Unit,
    onDevice: (OutputTarget) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    FloatingPage(stringResource(R.string.cast_to), onClose) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                CastRow(
                    icon = Icons.Filled.Smartphone,
                    label = stringResource(R.string.this_phone),
                    selected = currentId == null,
                    onClick = onPhone,
                )
            }
            items(outputs.targets, key = { it.id }) { target ->
                CastRow(
                    icon = if (target.kind == OutputKind.CAST) Icons.Filled.Cast else Icons.Filled.Speaker,
                    label = target.name,
                    badge = if (target.kind == OutputKind.CAST) "Cast" else "DLNA",
                    selected = currentId == target.id,
                    onClick = { onDevice(target) },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (outputs.searching) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.cast_searching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            if (outputs.targets.isEmpty()) stringResource(R.string.cast_none)
                            else stringResource(R.string.cast_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
