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
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.PaddingModifierElement
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.state.LazyListConnector
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.state.LazyListStateSnapshot
import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerState
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

    check(connector.lastPage == 3)
    state.updateFromPager(currentPage = 3, pageOffset = 0.25f)
    check(state.currentPage == 3)
    check(state.pageOffset == 0.25f)
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
    var lastPage: Int? = null

    override fun scrollToPage(page: Int) {
        lastPage = page
    }
}
