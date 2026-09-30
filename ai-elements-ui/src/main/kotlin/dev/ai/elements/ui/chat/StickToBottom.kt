package dev.ai.elements.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * "Stick to bottom" for a chat list (the idea behind AI Elements'
 * `use-stick-to-bottom`), for a normal top-to-bottom [LazyListState]:
 *
 * - While [following], every layout change that leaves content below the
 *   viewport — a streamed token, a Markdown block finishing parsing, a Mermaid
 *   or KaTeX WebView reporting its height, an image decoding, the keyboard
 *   opening — pins the list back to the bottom, instantly (no animation to
 *   fall behind the stream).
 * - The moment the user drags toward older content, following stops. Growth
 *   happens below the viewport, so what they are reading never moves.
 * - Scrolling back to the very bottom, or [jumpToLatest], resumes following.
 *
 * Anything that moves the list away from the bottom that we didn't do
 * ourselves — a drag or fling, but also accessibility scroll actions, keyboard
 * paging or app code calling `scrollToItem` — stops following, so pinning
 * never fights whoever is scrolling.
 */
@Stable
class StickToBottomState internal constructor(private val listState: LazyListState) {
    /** True while the list is pinned to the newest content. */
    var following by mutableStateOf(true)
        private set

    /** Show a "scroll to latest" affordance. */
    val showJumpToLatest: Boolean get() = !following && listState.canScrollForward

    /** Install on the list (or a parent) with `Modifier.nestedScroll(connection)`. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Positive y = finger moving down = revealing older content.
            if (source == NestedScrollSource.UserInput && available.y > 0f) following = false
            return Offset.Zero
        }
    }

    /** Pin to the bottom and follow again (e.g. the user sent a message). */
    suspend fun jumpToLatest(animated: Boolean = false) {
        following = true
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last < 0) return
        scrollOwn { if (animated) listState.animateScrollToItem(last, BOTTOM) else listState.scrollToItem(last, BOTTOM) }
    }

    /**
     * Shows [lastIndex] (the list's end) from the next measure pass and follows: call it from
     * composition's apply phase (`SideEffect`) and the frame being composed is already at the
     * bottom, with no first frame at the top and no visible scroll.
     */
    internal fun startAtBottom(lastIndex: Int) {
        following = true
        if (lastIndex < 0) return
        listState.requestScrollToItem(lastIndex, BOTTOM)
        // `ownPosition` stays: when the landing moves the list, pinning sees it at the bottom and
        // records it; when it does not (the same shape as before), no update comes to record it,
        // and a cleared position would mistake the next reader's scroll for ours to undo.
    }

    /** Scroll position after our own last scroll; any other change is someone else's. */
    private var ownPosition: Pair<Int, Int>? = null
    private var selfScrolling = false

    private val position get() = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset

    private suspend fun scrollOwn(block: suspend () -> Unit) {
        selfScrolling = true
        try {
            block()
        } finally {
            selfScrolling = false
            ownPosition = position
        }
    }

    internal suspend fun pinIfFollowing() {
        if (selfScrolling) return
        val movedByOthers = ownPosition != null && position != ownPosition
        if (following && movedByOthers) {
            // Someone else scrolled (a11y action, keyboard, app code). Mid-scroll
            // `canScrollForward` can be stale, so any in-progress foreign scroll counts.
            if (listState.isScrollInProgress || listState.canScrollForward) {
                following = false
                return
            }
            ownPosition = position // still at the bottom (e.g. content shrank)
        }
        when {
            following && listState.canScrollForward && !listState.isScrollInProgress -> {
                val last = listState.layoutInfo.totalItemsCount - 1
                if (last >= 0) {
                    // A request, applied by the next measure pass: this may run inside a layout pass
                    // (the change that triggered it), where `scrollToItem`'s forced remeasure re-enters.
                    listState.requestScrollToItem(last, BOTTOM)
                    ownPosition = null // the move is ours; re-read the position once it has landed
                }
            }
            // The user scrolled (or flung) all the way down: follow again.
            !following && !listState.canScrollForward && !listState.isScrollInProgress -> {
                following = true
                ownPosition = position
            }
            following -> ownPosition = position
        }
    }

    private companion object {
        /** Past the end of any item; LazyList clamps it to the true bottom. */
        const val BOTTOM = 1_000_000
    }
}

/** Remembers a [StickToBottomState] for [listState]; install its `connection` with `Modifier.nestedScroll`. */
@Composable
fun rememberStickToBottomState(listState: LazyListState): StickToBottomState {
    val state = remember(listState) { StickToBottomState(listState) }
    LaunchedEffect(state) {
        // Any change to the list's end — items added, the last item growing, the
        // viewport shrinking — or the end of a scroll gesture re-evaluates pinning.
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            listOf(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                info.totalItemsCount,
                last?.index ?: -1,
                (last?.offset ?: 0) + (last?.size ?: 0),
                info.viewportEndOffset,
                if (listState.isScrollInProgress) 1 else 0,
                if (state.following) 1 else 0,
            )
        }.collect { state.pinIfFollowing() }
    }
    return state
}
