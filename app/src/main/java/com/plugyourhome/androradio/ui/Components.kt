package com.plugyourhome.androradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.plugyourhome.androradio.R
import com.plugyourhome.androradio.data.Station
import com.plugyourhome.androradio.playback.PlayerUiState

/** Logo de la station ; icône radio tant que l'image n'est pas chargée ou si elle manque. */
@Composable
fun StationLogo(url: String, size: Dp, modifier: Modifier = Modifier) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 5))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(
                Icons.Filled.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size / 2),
            )
        }
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize().padding(size / 16),
            )
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StationCard(
    station: Station,
    isFavorite: Boolean,
    isCurrent: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        ) {
            StationLogo(station.favicon, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    station.displayName,
                    style = MaterialTheme.typography.titleMedium,
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
                if (station.tagList.isNotEmpty()) {
                    Text(
                        station.tagList.take(4).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isCurrent && isActive) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            FavoriteButton(isFavorite, onToggleFavorite)
        }
    }
}

/** Grille adaptative : une colonne sur téléphone, plusieurs sur tablette. */
@Composable
fun StationGrid(
    stations: List<Station>,
    favoriteIds: Set<String>,
    player: PlayerUiState,
    emptyText: String,
    onPlay: (Station) -> Unit,
    onToggleFavorite: (Station) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    if (stations.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            header?.let { Box(Modifier.padding(horizontal = 16.dp)) { it() } }
            MessageView(emptyText)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(horizontal = 4.dp)) { header() } }
        }
        items(stations, key = { it.uuid }) { station ->
            val current = player.station?.uuid == station.uuid
            StationCard(
                station = station,
                isFavorite = station.uuid in favoriteIds,
                isCurrent = current,
                isActive = current && player.isActive,
                onClick = { onPlay(station) },
                onToggleFavorite = { onToggleFavorite(station) },
            )
        }
    }
}

@Composable
fun MessageView(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(
            Icons.Filled.Radio,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Texte d'état sous le nom de la station : erreur, connexion, titre en cours ou infos. */
@Composable
fun playerSubtitle(state: PlayerUiState): String = when {
    state.hasError -> stringResource(R.string.playback_error)
    state.isBuffering -> stringResource(R.string.connecting)
    else -> state.nowPlaying ?: state.station?.details.orEmpty()
}

@Composable
fun PlayPauseIcon(state: PlayerUiState, modifier: Modifier = Modifier) {
    Icon(
        if (state.isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = stringResource(if (state.isActive) R.string.pause else R.string.play),
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = state.station ?: return
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (state.isBuffering) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                StationLogo(station.favicon, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        station.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playerSubtitle(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                FavoriteButton(isFavorite, onToggleFavorite)
                FilledIconButton(onClick = onTogglePlay) { PlayPauseIcon(state) }
            }
        }
    }
}
