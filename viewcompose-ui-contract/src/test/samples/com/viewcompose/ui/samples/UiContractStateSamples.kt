package com.viewcompose.ui.samples

import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.focus.FocusRequesterConnector
import com.viewcompose.ui.focus.FocusState
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.gesture.NestedScrollDispatcherConnector
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.gesture.ScrollVelocity
import com.viewcompose.ui.modifier.BackgroundColorModifierElement
import com.viewcompose.ui.modifier.InteractionIndicationModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.PaddingModifierElement
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.aspectRatio
import com.viewcompose.ui.modifier.maxHeight
import com.viewcompose.ui.modifier.maxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.state.LazyListConnector
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.state.LazyListStateSnapshot
import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerState
import com.viewcompose.ui.state.PagerStateSnapshot
import com.viewcompose.ui.state.ScrollConnector
import com.viewcompose.ui.state.ScrollState
import com.viewcompose.ui.state.ScrollStateSnapshot
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.unit.dp

fun modifierChainSample() {
    val modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .backgroundColor(0xFF336699.toInt())
        .semantics {
            contentDescription = "Account avatar"
        }
        .testTag("avatar")

    check(modifier.elements[0] is PaddingModifierElement)
    check(modifier.elements[1] is BackgroundColorModifierElement)
    val semantics = modifier.elements[2] as SemanticsModifierElement
    check(semantics.configuration.contentDescription == "Account avatar")
}

fun interactionIndicationSample() {
    val indication = UiInteractionIndication.StateLayer(
        colors = UiStateLayerColors(
            pressedColor = 0x1F000000,
            focusedColor = 0x1A000000,
            hoveredColor = 0x14000000,
        ),
    )
    val modifier = Modifier.interactionIndication(indication)

    val element = modifier.elements.single() as InteractionIndicationModifierElement
    check(element.indication == indication)
}

fun nestedScrollDispatcherSample() {
    val dispatcher = NestedScrollDispatcher()
    val connector = RecordingNestedScrollConnector()
    dispatcher.attach(connector)

    val consumed = dispatcher.dispatchPreScroll(
        available = ScrollDelta(x = 0f, y = 24f),
        source = NestedScrollSource.UserInput,
    )

    check(consumed == ScrollDelta(x = 0f, y = 12f))
    dispatcher.detach(connector)
    check(dispatcher.dispatchPreFling(ScrollVelocity(0f, 1_000f)).isZero)
}

fun focusRequesterSample() {
    val requester = FocusRequester()
    val target = RecordingFocusConnector(restorationKey = "search-field")
    requester.attach(target)

    check(requester.requestFocus())
    check(target.lastDirection == FocusDirection.Enter)

    target.state = FocusState(isFocused = true, hasFocus = true)
    check(requester.saveFocusedChild())
    requester.detach(target)

    val remounted = RecordingFocusConnector(restorationKey = "search-field")
    requester.attach(remounted)
    check(remounted.lastDirection == FocusDirection.Enter)
}

fun lazyListStateSample() {
    val state = LazyListState(initialFirstVisibleItemIndex = 2)
    val connector = RecordingLazyListConnector()
    state.attach(connector)

    state.scrollToItem(index = 8, scrollOffset = 12)

    check(state.firstVisibleItemIndex == 8)
    check(state.firstVisibleItemScrollOffset == 12)
    check(connector.lastScroll == Triple(8, 12, false))
}

fun pagerStateSample() {
    val state = PagerState()
    val connector = RecordingPagerConnector()
    state.attach(connector)
    state.scrollToPage(3)

    check(connector.lastScroll == 3 to false)
    connector.publish(
        PagerStateSnapshot(
            currentPage = 3,
            settledPage = 2,
            targetPage = 3,
            pageOffset = 0.25f,
            pageCount = 5,
            isScrollInProgress = true,
            canScrollBackward = true,
            canScrollForward = true,
        ),
    )
    check(state.currentPage == 3)
    check(state.targetPage == 3)
    check(state.pageOffset == 0.25f)
}

fun scrollStateSample() {
    val state = ScrollState(initialValue = 12)
    val connector = RecordingScrollConnector()
    state.attach(connector)

    state.scrollTo(48)
    connector.publish(
        ScrollStateSnapshot(
            value = 48,
            maxValue = 120,
            viewportSize = 64,
            isScrollInProgress = false,
            canScrollBackward = true,
            canScrollForward = true,
            lastScrolledBackward = false,
            lastScrolledForward = true,
        ),
    )

    check(connector.lastScroll == 48 to false)
    check(state.value == 48)
    check(state.maxValue == 120)
}

fun gridPolicySample() {
    val cells: GridCells = GridCells.Adaptive(minSize = 160.dp)
    val headerSpan: GridItemSpan = GridItemSpan.FullLine
    val cardSpan: GridItemSpan = GridItemSpan.Fixed(2)

    check(cells is GridCells.Adaptive)
    check(headerSpan == GridItemSpan.FullLine)
    check(cardSpan == GridItemSpan.Fixed(2))
}

fun layoutConstraintModifierSample() {
    val modifier = Modifier
        .maxWidth(720.dp)
        .maxHeight(480.dp)
        .aspectRatio(ratio = 16f / 9f)

    check(modifier.elements.size == 3)
}

private class RecordingNestedScrollConnector : NestedScrollDispatcherConnector {
    override fun dispatchPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta = ScrollDelta(available.x / 2f, available.y / 2f)

    override fun dispatchPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta = ScrollDelta.Zero

    override fun dispatchPreFling(available: ScrollVelocity): ScrollVelocity = ScrollVelocity.Zero

    override fun dispatchPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity = ScrollVelocity.Zero
}

private class RecordingFocusConnector(
    override val restorationKey: Any,
) : FocusRequesterConnector {
    var state: FocusState = FocusState.Inactive
    var lastDirection: FocusDirection? = null

    override val focusState: FocusState
        get() = state

    override fun requestFocus(direction: FocusDirection): Boolean {
        lastDirection = direction
        return true
    }
}

private class RecordingLazyListConnector : LazyListConnector {
    var lastScroll: Triple<Int, Int, Boolean>? = null

    override fun scrollToItem(
        index: Int,
        scrollOffset: Int,
        animated: Boolean,
    ) {
        lastScroll = Triple(index, scrollOffset, animated)
    }

    override fun currentSnapshot(): LazyListStateSnapshot? = null
}

private class RecordingPagerConnector : PagerConnector {
    var lastScroll: Pair<Int, Boolean>? = null
    private var listener: ((PagerStateSnapshot) -> Unit)? = null

    override fun scrollToPage(page: Int, animated: Boolean) {
        lastScroll = page to animated
    }

    override fun setOnSnapshotChangedListener(listener: ((PagerStateSnapshot) -> Unit)?) {
        this.listener = listener
    }

    fun publish(snapshot: PagerStateSnapshot) {
        listener?.invoke(snapshot)
    }
}

private class RecordingScrollConnector : ScrollConnector {
    var lastScroll: Pair<Int, Boolean>? = null
    private var listener: ((ScrollStateSnapshot) -> Unit)? = null

    override fun scrollTo(value: Int, animated: Boolean) {
        lastScroll = value to animated
    }

    override fun setOnSnapshotChangedListener(listener: ((ScrollStateSnapshot) -> Unit)?) {
        this.listener = listener
    }

    fun publish(snapshot: ScrollStateSnapshot) {
        listener?.invoke(snapshot)
    }
}
