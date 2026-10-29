package com.plugyourhome.androradio.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.plugyourhome.androradio.data.Track
import com.plugyourhome.androradio.playback.PlayerUiState

/**
 * Écran principal : 5 bandes de même hauteur, de haut en bas :
 * station actuelle, derniers écoutés, favoris, stations locales, contrôles.
 */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val player by vm.playerState.collectAsStateWithLifecycle()
    val remote by vm.remote.state.collectAsStateWithLifecycle()
    val current by vm.currentStation.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val favoritesSheet by vm.favoritesSheet.collectAsStateWithLifecycle()
    val searchOpen by vm.searchOpen.collectAsStateWithLifecycle()

    val currentUuid = player.station?.uuid
    val openFavoritesFor: (Station) -> Unit = { vm.openFavorites(it) }

    val track by vm.track.collectAsStateWithLifecycle()
    val trackArt by vm.trackArt.collectAsStateWithLifecycle()
    val volume by vm.volumeLevel.collectAsStateWithLifecycle()

    HomeBands(
        current = current,
        player = player,
        track = track,
        trackArt = trackArt,
        recent = recent,
        favorites = favorites,
        local = local,
        countryName = vm.countryName,
        onPlay = vm::play,
        onLongPress = openFavoritesFor,
        onRefreshLocal = vm::refreshLocal,
        onPlayStop = vm::togglePlayStop,
        onFavorites = { vm.openFavorites() },
        onSearch = vm::openSearch,
        onAbout = vm::openAbout,
        volume = volume,
        onVolumeChange = vm::setVolume,
        castName = remote.device?.name,
        onCast = vm::openCast,
    )

    val castOpen by vm.castOpen.collectAsStateWithLifecycle()
    if (castOpen) {
        val devices by vm.remote.search.collectAsStateWithLifecycle()
        CastPage(
            search = devices,
            current = remote.device,
            onPhone = vm::castToPhone,
            onDevice = vm::castTo,
            onRefresh = vm.remote::startSearch,
            onClose = vm::closeCast,
        )
    }

    favoritesSheet?.let { sheet ->
        FavoritesPage(
            slots = favorites,
            stationToSave = sheet.stationToSave,
            currentUuid = currentUuid,
            onSave = vm::saveFavorite,
            onDelete = vm::deleteFavorite,
            onMove = vm::moveFavorite,
            onPlay = {
                vm.play(it)
                vm.closeFavorites()
            },
            onClose = vm::closeFavorites,
        )
    }

    val aboutOpen by vm.aboutOpen.collectAsStateWithLifecycle()
    if (aboutOpen) AboutPage(onClose = vm::closeAbout)

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
            listState = vm.searchListState,
        )
    }
}

/**
 * Les 5 bandes de l'écran principal, sans état : tout arrive en paramètres.
 * Utilisé par l'appli (via [HomeScreen]) et par les captures d'écran générées.
 */
@Composable
fun HomeBands(
    current: Station?,
    player: PlayerUiState,
    track: Track?,
    trackArt: String?,
    recent: List<Station>,
    favorites: List<Station?>,
    local: LoadState,
    countryName: String,
    onPlay: (Station) -> Unit,
    onLongPress: (Station) -> Unit,
    onRefreshLocal: () -> Unit,
    onPlayStop: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
    volume: Float = 0.6f,
    onVolumeChange: (Float) -> Unit = {},
    castName: String? = null,
    onCast: () -> Unit = {},
) {
    val currentUuid = player.station?.uuid
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // Hauteurs relatives : en cours 1,25 · trois bandes de stations 1 · contrôles 0,5
            val band = Modifier.weight(1f)

            NowPlayingBand(current, player, track, trackArt, castName, onFavorites, onSearch, onAbout, Modifier.weight(1.25f))

            StationBand(
                title = stringResource(R.string.band_recent),
                slots = recent,
                currentUuid = currentUuid,
                isActive = player.isActive,
                emptyMessage = stringResource(R.string.recent_empty),
                onPlay = onPlay,
                onLongPress = onLongPress,
                modifier = band,
            )

            StationBand(
                title = stringResource(R.string.band_favorites),
                slots = favorites,
                currentUuid = currentUuid,
                isActive = player.isActive,
                emptyMessage = "",
                onPlay = onPlay,
                onLongPress = onLongPress,
                numbered = true,
                modifier = band,
            )

            LocalBand(countryName, local, currentUuid, player.isActive, onPlay, onLongPress, onRefreshLocal, band)

            ControlsBand(
                isActive = player.isActive,
                canPlay = current != null,
                onPlayStop = onPlayStop,
                volume = volume,
                onVolumeChange = onVolumeChange,
                casting = castName != null,
                onCast = onCast,
                modifier = Modifier.weight(0.5f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingBand(
    station: Station?,
    player: PlayerUiState,
    track: Track?,
    trackArt: String?,
    castName: String?,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier,
) {
    Band(modifier) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Pochette du morceau si trouvée, sinon logo de la station
                    Crossfade(
                        targetState = trackArt ?: station?.favicon,
                        label = "art",
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    ) { url ->
                        StationArt(url, Modifier.fillMaxSize(), corner = 14.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(end = 4.dp)) {
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
                                player.isPlaying -> stringResource(R.string.status_live)
                                else -> stringResource(R.string.status_stopped)
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (player.hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            if (castName != null) {
                                // Diffusion en cours vers un appareil du réseau
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CastConnected,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        castName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else if (station.details.isNotBlank()) {
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
                // Sous la pochette : artiste puis titre du morceau
                Spacer(Modifier.size(6.dp))
                Text(
                    track?.artist ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (track?.artist != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                Text(
                    track?.title ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (track != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
            }
            // Colonne de droite : réglages (langue + infos) en haut, favoris, recherche en bas
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight(),
            ) {
                SettingsMenu(onAbout = onAbout)
                SideIcon(Icons.Filled.Favorite, stringResource(R.string.band_favorites), onFavorites)
                SideIcon(Icons.Filled.Search, stringResource(R.string.search), onSearch)
            }
        }
    }
}

@Composable
private fun LocalBand(
    countryName: String,
    state: LoadState,
    currentUuid: String?,
    isActive: Boolean,
    onPlay: (Station) -> Unit,
    onLongPress: (Station) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val title = if (countryName.isNotEmpty()) stringResource(R.string.band_local, countryName)
    else stringResource(R.string.band_popular)
    val refresh: @Composable () -> Unit = {
        IconButton(
            onClick = onRefresh,
            enabled = state !is LoadState.Loading,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.refresh),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    when (state) {
        is LoadState.Loaded -> StationBand(
            title = title,
            slots = state.stations,
            currentUuid = currentUuid,
            isActive = isActive,
            emptyMessage = stringResource(R.string.no_results),
            onPlay = onPlay,
            onLongPress = onLongPress,
            trailing = refresh,
            modifier = modifier,
        )
        else -> Band(modifier) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { BandTitle(title) }
                    refresh()
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state is LoadState.Error) {
                        Text(
                            stringResource(R.string.error_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

/** Dernière bande (demi-hauteur) : bouton Lecture/Stop carré arrondi, puis réglage du volume. */
@Composable
private fun ControlsBand(
    isActive: Boolean,
    canPlay: Boolean,
    onPlayStop: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    casting: Boolean,
    onCast: () -> Unit,
    modifier: Modifier,
) {
    Band(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Carré arrondi : toute la hauteur de la bande
            Button(
                onClick = onPlayStop,
                enabled = canPlay || isActive,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            ) {
                Icon(
                    if (isActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isActive) R.string.stop else R.string.play),
                    modifier = Modifier.fillMaxSize(0.6f),
                )
            }
            Icon(
                when {
                    volume <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).clickable { onVolumeChange(if (volume > 0f) 0f else 0.5f) },
            )
            val volumeLabel = stringResource(R.string.volume)
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = volumeLabel },
            )
            // Diffuser vers un appareil du réseau (Arylic, TV, ampli…)
            IconButton(onClick = onCast) {
                Icon(
                    if (casting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                    contentDescription = stringResource(R.string.cast_to),
                    tint = if (casting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
