package com.plugyourhome.androradio.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

/**
 * Glisser-déposer dans une LazyColumn : appui long sur une ligne, puis glisser.
 * La liste défile toute seule quand la ligne approche du bord.
 * La LazyColumn ne doit pas avoir de contentPadding (les positions seraient décalées).
 */
class ReorderState(val listState: LazyListState, private val onMove: (Int, Int) -> Unit) {

    /** Index de la ligne déplacée, null si aucun glissement en cours. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** Décalage vertical de la ligne déplacée par rapport à sa place dans la liste. */
    var offset by mutableFloatStateOf(0f)
        private set

    private fun itemInfo(index: Int): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    fun start(y: Float): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { y.toInt() in it.offset until it.offset + it.size } ?: return false
        draggingIndex = item.index
        offset = 0f
        return true
    }

    fun drag(dy: Float) {
        offset += dy
        swapIfNeeded()
    }

    fun end() {
        draggingIndex = null
        offset = 0f
    }

    /** Si le centre de la ligne déplacée passe sur une voisine, on les échange. */
    private fun swapIfNeeded() {
        val index = draggingIndex ?: return
        val current = itemInfo(index) ?: return
        val center = current.offset + offset + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != index && center >= it.offset && center < it.offset + it.size
        } ?: return
        onMove(index, target.index)
        // la ligne prend la place de la cible : on compense pour qu'elle reste sous le doigt
        offset += current.offset - target.offset
        draggingIndex = target.index
    }

    /** Vitesse de défilement automatique (px par image) si la ligne touche un bord. */
    fun autoScrollSpeed(): Float {
        val index = draggingIndex ?: return 0f
        val current = itemInfo(index) ?: return 0f
        val top = current.offset + offset
        val bottom = top + current.size
        val info = listState.layoutInfo
        val edge = current.size * 0.6f
        return when {
            top < info.viewportStartOffset + edge && listState.canScrollBackward -> -AUTO_SCROLL_PX
            bottom > info.viewportEndOffset - edge && listState.canScrollForward -> AUTO_SCROLL_PX
            else -> 0f
        }
    }

    internal fun onScrolled(consumed: Float) {
        offset += consumed // la liste a bougé sous la ligne : on garde la ligne sous le doigt
        swapIfNeeded()
    }

    private companion object {
        const val AUTO_SCROLL_PX = 14f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (Int, Int) -> Unit): ReorderState {
    val latestOnMove by rememberUpdatedState(onMove)
    val state = remember(listState) { ReorderState(listState) { from, to -> latestOnMove(from, to) } }
    // Défilement automatique pendant le glissement
    LaunchedEffect(state.draggingIndex != null) {
        while (state.draggingIndex != null) {
            val speed = state.autoScrollSpeed()
            if (speed != 0f) {
                val consumed = state.listState.scrollBy(speed)
                state.onScrolled(consumed)
            }
            delay(16)
        }
    }
    return state
}

/** À poser sur la LazyColumn elle-même. */
@Composable
fun Modifier.reorderable(state: ReorderState): Modifier {
    val haptics = LocalHapticFeedback.current
    return this.pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { pos ->
                if (state.start(pos.y)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, amount ->
                if (state.draggingIndex != null) {
                    change.consume()
                    state.drag(amount.y)
                }
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
}
