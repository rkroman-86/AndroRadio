package com.plugyourhome.androradio.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plugyourhome.androradio.R
import com.plugyourhome.androradio.data.Continent
import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/* Couleurs du cadran : volontairement fixes (bois sombre, verre rétroéclairé ambre), quel que soit le thème. */
private val DialWoodTop = Color(0xFF2A1810)
private val DialWoodBottom = Color(0xFF1A0E08)
private val DialGlow = Color(0xFF5A3A18)
private val DialAmber = Color(0xFFFFC46B)
private val DialAmberDim = Color(0x99FFC46B)
private val DialNeedle = Color(0xFFE53935)
private val DialItemWidth = 132.dp
private val DialHeight = 62.dp

/** Une graduation du cadran. */
data class DialItem(val key: String, val title: String, val subtitle: String = "")

/**
 * Tuner « Tour du monde », façon vieux poste de radio :
 * trois cadrans à faire glisser (continent, pays, région/ville) puis la liste des stations.
 */
@Composable
fun TunerPage(
    geo: GeoUi,
    currentUuid: String?,
    onContinent: (Int) -> Unit,
    onCountry: (Int) -> Unit,
    onRegion: (Int) -> Unit,
    onRetry: () -> Unit,
    onPlay: (Station) -> Unit,
    onAddFavorite: (Station) -> Unit,
    onClose: () -> Unit,
) {
    val continents = Continent.entries.map { DialItem(it.name, stringResource(it.label)) }
    val allLabel = stringResource(R.string.geo_all)

    FloatingPage(stringResource(R.string.geo_title), onClose) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            TunerDial(
                label = stringResource(R.string.geo_continent),
                items = continents,
                selected = geo.continent,
                listKey = "continents",
                onSelected = onContinent,
            )
            TunerDial(
                label = stringResource(R.string.geo_country),
                items = geo.countries?.map { DialItem(it.code, it.name, it.stations.toString()) },
                selected = geo.countryIndex,
                listKey = "c" + geo.continent + ":" + geo.countries?.size,
                onSelected = onCountry,
            )
            TunerDial(
                label = stringResource(R.string.geo_region),
                items = geo.regions?.map {
                    DialItem(it.key ?: "*", if (it.key == null) allLabel else it.name, it.stations.toString())
                },
                selected = geo.regionIndex,
                listKey = "r" + geo.selectedCountry?.code + ":" + geo.regions?.size,
                onSelected = onRegion,
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                geo.countriesError -> TunerError(onRetry, Modifier.align(Alignment.Center))
                else -> Crossfade(targetState = geo.stations, label = "geoStations") { state ->
                    when (state) {
                        LoadState.Idle, LoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        LoadState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { TunerError(onRetry) }
                        is LoadState.Loaded -> if (state.stations.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                                Text(
                                    stringResource(R.string.no_results),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // Nouvelle liste (autre pays ou région) : elle repart du haut
                            val listState = key(state.stations.firstOrNull()?.uuid, state.stations.size) {
                                rememberLazyListState()
                            }
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
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
}

@Composable
private fun TunerError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(24.dp)) {
        Text(
            stringResource(R.string.error_loading),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/**
 * Un cadran à faire glisser à gauche ou à droite. L'élément sous l'aiguille rouge est le choix ;
 * il est validé quand le cadran s'arrête (pas à chaque cran), avec un petit « tic » à chaque passage.
 * Toucher une graduation y amène l'aiguille.
 *
 * [items] null = chargement en cours. [listKey] change quand la liste est remplacée
 * (autre continent, autre pays) : le cadran est alors recréé sur [selected].
 */
@Composable
fun TunerDial(
    label: String,
    items: List<DialItem>?,
    selected: Int,
    listKey: String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(DialHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(DialWoodTop, DialWoodBottom)))
                .border(1.dp, Color(0xFF6B4A2A), RoundedCornerShape(12.dp)),
        ) {
            // Halo ambre du rétroéclairage, plus fort au centre
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to DialGlow.copy(alpha = 0.55f), 1f to Color.Transparent,
                    ),
                ),
            )
            if (items == null) {
                DialSweep()
            } else if (items.isEmpty()) {
                Text(
                    "—",
                    color = DialAmberDim,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                key(listKey) {
                    DialStrip(items, selected.coerceIn(items.indices), maxWidth, onSelected)
                }
            }
            DialNeedleOverlay()
        }
    }
}

@Composable
private fun DialStrip(items: List<DialItem>, selected: Int, width: Dp, onSelected: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val side = ((width - DialItemWidth) / 2).coerceAtLeast(0.dp)

    /** Index de la graduation la plus proche de l'aiguille. */
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: currentSelected
        }
    }

    // « Tic » à chaque cran franchi pendant le glissement
    LaunchedEffect(state) {
        snapshotFlow { centered }.drop(1).collect {
            if (state.isScrollInProgress) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // Choix validé quand le cadran s'immobilise
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.drop(1).filter { !it }.collect {
            if (centered != currentSelected) currentOnSelected(centered)
        }
    }
    // Sélection changée de l'extérieur (ex. pays du téléphone choisi au chargement)
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress && centered != selected) state.animateScrollToItem(selected)
    }

    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = side),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(DialItemWidth)
                    .fillMaxHeight()
                    .drawBehind { drawTicks() }
                    .graphicsLayer {
                        // Plus on s'éloigne de l'aiguille, plus la graduation rétrécit et s'éteint
                        val info = state.layoutInfo
                        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val me = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val distance = me?.let { abs(it.offset + it.size / 2f - center) / it.size } ?: 2f
                        val f = (1f - distance * 0.35f).coerceIn(0.55f, 1f)
                        scaleX = f
                        scaleY = f
                        alpha = (1f - distance * 0.45f).coerceIn(0.25f, 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { state.animateScrollToItem(index) } }
                    .semantics { contentDescription = item.title },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        item.title,
                        color = DialAmber,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (item.subtitle.isNotEmpty()) {
                        Text(
                            item.subtitle,
                            color = DialAmberDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Graduations du cadran : un grand trait par élément, quatre petits entre deux. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTicks() {
    val step = size.width / 5f
    for (i in 0 until 5) {
        val x = step * i + step / 2f
        val major = i == 2
        val h = if (major) size.height * 0.22f else size.height * 0.11f
        drawLine(
            color = if (major) DialAmber.copy(alpha = 0.7f) else DialAmberDim.copy(alpha = 0.35f),
            start = Offset(x, size.height - h - 3.dp.toPx()),
            end = Offset(x, size.height - 3.dp.toPx()),
            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
        )
    }
}

/** Aiguille rouge fixe au centre, avec un reflet de verre par-dessus. */
@Composable
private fun DialNeedleOverlay() {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val x = size.width / 2f
            drawLine(DialNeedle.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 6.dp.toPx())
            drawLine(DialNeedle, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            val t = 6.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(x - t, 0f)
                    lineTo(x + t, 0f)
                    lineTo(x, t * 1.3f)
                    close()
                },
                DialNeedle,
            )
            // Reflet du verre
            drawRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.45f to Color.Transparent),
            )
        },
    )
}

/** Chargement : un trait ambre balaie le cadran, comme une recherche de fréquence. */
@Composable
private fun DialSweep() {
    val transition = rememberInfiniteTransition(label = "sweep")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "sweepPos",
    )
    Box(
        Modifier.fillMaxSize().drawBehind {
            val x = size.width * (0.1f + 0.8f * p)
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, DialAmber.copy(alpha = 0.35f), Color.Transparent),
                    startX = x - 60.dp.toPx(),
                    endX = x + 60.dp.toPx(),
                ),
            )
            drawLine(DialAmber.copy(alpha = 0.8f), Offset(x, size.height * 0.25f), Offset(x, size.height * 0.75f), 1.5.dp.toPx())
        },
    )
}
