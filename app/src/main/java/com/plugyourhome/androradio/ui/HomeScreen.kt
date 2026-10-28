package com.plugyourhome.androradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plugyourhome.androradio.R
import com.plugyourhome.androradio.data.Station
import com.plugyourhome.androradio.playback.PlayerUiState

/**
 * Écran principal : 5 bandes de même hauteur, de haut en bas :
 * station actuelle, derniers écoutés, favoris, stations locales, contrôles.
 */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val current by vm.currentStation.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val favoritesSheet by vm.favoritesSheet.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()

    val currentUuid = player.station?.uuid
    val openFavoritesFor: (Station) -> Unit = { vm.openFavorites(it) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val band = Modifier.weight(1f)

            NowPlayingBand(current, player, band)

            StationBand(
                title = stringResource(R.string.band_recent),
                slots = recent,
                currentUuid = currentUuid,
                isActive = player.isActive,
                emptyMessage = stringResource(R.string.recent_empty),
                onPlay = vm::play,
                onLongPress = openFavoritesFor,
                modifier = band,
            )

            StationBand(
                title = stringResource(R.string.band_favorites),
                slots = favorites,
                currentUuid = currentUuid,
                isActive = player.isActive,
                emptyMessage = "",
                onPlay = vm::play,
                onLongPress = openFavoritesFor,
                numbered = true,
                modifier = band,
            )

            LocalBand(vm, local, currentUuid, player.isActive, openFavoritesFor, band)

            ControlsBand(
                isActive = player.isActive,
                canPlay = current != null,
                onPlayStop = vm::togglePlayStop,
                onFavorites = { vm.openFavorites() },
                onSearch = vm::openSearch,
                modifier = band,
            )
        }
    }

    favoritesSheet?.let { sheet ->
        FavoritesPage(
            slots = favorites,
            stationToSave = sheet.stationToSave,
            currentUuid = currentUuid,
            onSave = vm::saveFavorite,
            onDelete = vm::deleteFavorite,
            onPlay = {
                vm.play(it)
                vm.closeFavorites()
            },
            onClose = vm::closeFavorites,
        )
    }

    if (searchOpen) {
        val query by vm.searchQuery.collectAsStateWithLifecycle()
        val results by vm.search.collectAsStateWithLifecycle()
        SearchPage(
            query = query,
            state = results,
            currentUuid = currentUuid,
            onQueryChange = vm::setSearchQuery,
            onSearch = vm::runSearch,
            onPlay = {
                vm.play(it)
                vm.closeSearch()
            },
            onAddFavorite = openFavoritesFor,
            onClose = vm::closeSearch,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingBand(station: Station?, player: PlayerUiState, modifier: Modifier) {
    Band(modifier) {
        Box(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                if (station != null) {
                    StationArt(station.favicon, Modifier.fillMaxHeight().aspectRatio(1f), corner = 14.dp)
                } else {
                    StationArt(null, Modifier.fillMaxHeight().aspectRatio(1f), corner = 14.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(end = 28.dp)) {
                    Text(
                        stringResource(R.string.band_now_playing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        station?.displayName ?: stringResource(R.string.no_station),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (station != null) {
                        val status = when {
                            player.hasError -> stringResource(R.string.playback_error)
                            player.isBuffering -> stringResource(R.string.connecting)
                            player.isPlaying -> player.nowPlaying ?: stringResource(R.string.status_live)
                            else -> stringResource(R.string.status_stopped)
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (player.hasError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
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
                    }
                    if (player.isBuffering) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) { LanguageMenu() }
        }
    }
}

@Composable
private fun LocalBand(
    vm: MainViewModel,
    state: LoadState,
    currentUuid: String?,
    isActive: Boolean,
    onLongPress: (Station) -> Unit,
    modifier: Modifier,
) {
    val title = if (vm.countryName.isNotEmpty()) stringResource(R.string.band_local, vm.countryName)
    else stringResource(R.string.band_popular)
    when (state) {
        is LoadState.Loaded -> StationBand(
            title = title,
            slots = state.stations,
            currentUuid = currentUuid,
            isActive = isActive,
            emptyMessage = stringResource(R.string.no_results),
            onPlay = vm::play,
            onLongPress = onLongPress,
            modifier = modifier,
        )
        else -> Band(modifier) {
            Column(Modifier.fillMaxSize()) {
                BandTitle(title)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state is LoadState.Error) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.error_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            IconButton(onClick = vm::loadLocal) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.retry))
                            }
                        }
                    } else {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

/** Dernière bande : Lecture/Stop (un seul bouton), Favoris, Recherche. */
@Composable
private fun ControlsBand(
    isActive: Boolean,
    canPlay: Boolean,
    onPlayStop: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier,
) {
    Band(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val iconSize = (maxHeight * 0.38f).coerceIn(22.dp, 44.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                ControlButton(
                    icon = if (isActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    label = stringResource(if (isActive) R.string.stop else R.string.play),
                    iconSize = iconSize,
                    primary = true,
                    enabled = canPlay || isActive,
                    onClick = onPlayStop,
                )
                ControlButton(
                    icon = Icons.Filled.Favorite,
                    label = stringResource(R.string.band_favorites),
                    iconSize = iconSize,
                    onClick = onFavorites,
                )
                ControlButton(
                    icon = Icons.Filled.Search,
                    label = stringResource(R.string.search),
                    iconSize = iconSize,
                    onClick = onSearch,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
    val modifier = Modifier.weight(1f).fillMaxHeight()
    val shape = RoundedCornerShape(16.dp)
    val padding = ButtonDefaults.ContentPadding
    if (primary) {
        Button(onClick = onClick, enabled = enabled, shape = shape, contentPadding = padding, modifier = modifier, content = content)
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled, shape = shape, contentPadding = padding, modifier = modifier, content = content)
    }
}
