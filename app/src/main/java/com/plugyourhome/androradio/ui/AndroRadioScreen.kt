package com.plugyourhome.androradio.ui

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plugyourhome.androradio.R
import com.plugyourhome.androradio.data.Station
import com.plugyourhome.androradio.playback.PlayerUiState

private data class TabSpec(val tab: Tab, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(Tab.Discover, R.string.tab_discover, Icons.Filled.Explore),
    TabSpec(Tab.Favorites, R.string.tab_favorites, Icons.Filled.Favorite),
    TabSpec(Tab.Recent, R.string.tab_recent, Icons.Filled.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroRadioScreen(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.map { it.uuid }.toSet() }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = { LanguageMenu() },
                )
            },
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = player,
                        isFavorite = player.station?.uuid?.let { it in favoriteIds } == true,
                        onOpen = { showNowPlaying = true },
                        onTogglePlay = vm::togglePlayPause,
                        onToggleFavorite = { player.station?.let(vm::toggleFavorite) },
                        modifier = if (wide) Modifier.navigationBarsPadding() else Modifier,
                    )
                    if (!wide) {
                        NavigationBar {
                            TABS.forEach { spec ->
                                NavigationBarItem(
                                    selected = tab == spec.tab,
                                    onClick = { vm.selectTab(spec.tab) },
                                    icon = { Icon(spec.icon, contentDescription = null) },
                                    label = { Text(stringResource(spec.label)) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail(windowInsets = WindowInsets(0)) {
                        TABS.forEach { spec ->
                            NavigationRailItem(
                                selected = tab == spec.tab,
                                onClick = { vm.selectTab(spec.tab) },
                                icon = { Icon(spec.icon, contentDescription = null) },
                                label = { Text(stringResource(spec.label)) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (tab) {
                        Tab.Discover -> DiscoverTab(vm, player, favoriteIds)
                        Tab.Favorites -> StationGrid(
                            stations = favorites,
                            favoriteIds = favoriteIds,
                            player = player,
                            emptyText = stringResource(R.string.empty_favorites),
                            onPlay = vm::play,
                            onToggleFavorite = vm::toggleFavorite,
                        )
                        Tab.Recent -> RecentTab(vm, player, favoriteIds)
                    }
                }
            }
        }
    }

    val current = player.station
    if (showNowPlaying && current != null) {
        NowPlayingSheet(
            station = current,
            state = player,
            isFavorite = current.uuid in favoriteIds,
            onDismiss = { showNowPlaying = false },
            onTogglePlay = vm::togglePlayPause,
            onStop = {
                vm.stop()
                showNowPlaying = false
            },
            onToggleFavorite = { vm.toggleFavorite(current) },
        )
    }
}

@Composable
private fun DiscoverTab(vm: MainViewModel, player: PlayerUiState, favoriteIds: Set<String>) {
    val query by vm.query.collectAsStateWithLifecycle()
    val genre by vm.genre.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.countryCode.isNotEmpty()) {
                item {
                    FilterChip(
                        selected = scope == Scope.Country,
                        onClick = { vm.setScope(Scope.Country) },
                        label = { Text(vm.countryName) },
                        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    )
                }
            }
            item {
                FilterChip(
                    selected = scope == Scope.World,
                    onClick = { vm.setScope(Scope.World) },
                    label = { Text(stringResource(R.string.worldwide)) },
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                )
            }
            items(MainViewModel.GENRES) { g ->
                FilterChip(
                    selected = genre == g,
                    onClick = { vm.setGenre(if (genre == g) null else g) },
                    label = { Text(g.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        when (val state = discover) {
            DiscoverState.Loading -> LoadingView()
            DiscoverState.Error -> MessageView(
                text = stringResource(R.string.error_loading),
                actionLabel = stringResource(R.string.retry),
                onAction = vm::retry,
            )
            is DiscoverState.Loaded -> {
                val title = when {
                    query.isNotBlank() || genre != null ->
                        stringResource(R.string.results, state.stations.size)
                    scope == Scope.Country && vm.countryName.isNotEmpty() ->
                        stringResource(R.string.popular_in, vm.countryName)
                    else -> stringResource(R.string.popular_worldwide)
                }
                StationGrid(
                    stations = state.stations,
                    favoriteIds = favoriteIds,
                    player = player,
                    emptyText = stringResource(R.string.empty_results),
                    onPlay = {
                        focusManager.clearFocus()
                        vm.play(it)
                    },
                    onToggleFavorite = vm::toggleFavorite,
                    header = { SectionTitle(title) },
                )
            }
        }
    }
}

@Composable
private fun RecentTab(vm: MainViewModel, player: PlayerUiState, favoriteIds: Set<String>) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    StationGrid(
        stations = recent,
        favoriteIds = favoriteIds,
        player = player,
        emptyText = stringResource(R.string.empty_recent),
        onPlay = vm::play,
        onToggleFavorite = vm::toggleFavorite,
        header = if (recent.isEmpty()) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.tab_recent)) }
                    TextButton(onClick = vm::clearRecent) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.clear_history))
                    }
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Choix de la langue de l'appli (Android 13+ ; avant, l'appli suit la langue du système). */
@Composable
private fun LanguageMenu() {
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
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.language))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingSheet(
    station: Station,
    state: PlayerUiState,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            StationLogo(station.favicon, 168.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                station.displayName,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                playerSubtitle(state),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            if (state.nowPlaying != null && station.details.isNotBlank()) {
                Text(
                    station.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (station.tagList.isNotEmpty()) {
                Text(
                    station.tagList.take(6).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OutlinedIconButton(onClick = onToggleFavorite, modifier = Modifier.size(52.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.remove_favorite else R.string.add_favorite,
                        ),
                    )
                }
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(80.dp)) {
                    PlayPauseIcon(state, Modifier.size(40.dp))
                }
                OutlinedIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop))
                }
            }
            if (station.homepage.startsWith("http")) {
                TextButton(onClick = { runCatching { uriHandler.openUri(station.homepage) } }) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.open_website))
                }
            }
        }
    }
}
