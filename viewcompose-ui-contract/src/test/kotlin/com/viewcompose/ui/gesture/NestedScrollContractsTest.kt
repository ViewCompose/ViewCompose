package com.viewcompose.ui.gesture

/*
 * 测试职责：覆盖 UI contract 中的 Nested Scroll Contracts 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Nested Scroll Contracts behavior in UI contract and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class NestedScrollContractsTest {
    @Test
    fun `dispatcher is inert while detached and forwards all phases when attached`() {
        val dispatcher = NestedScrollDispatcher()
        val connector = RecordingConnector()

        assertEquals(
            ScrollDelta.Zero,
            dispatcher.dispatchPreScroll(ScrollDelta(4f, 8f)),
        )

        dispatcher.attach(connector)

        assertEquals(
            ScrollDelta(2f, 4f),
            dispatcher.dispatchPreScroll(
                available = ScrollDelta(4f, 8f),
                source = NestedScrollSource.UserInput,
            ),
        )
        assertEquals(
            ScrollDelta(1f, 2f),
            dispatcher.dispatchPostScroll(
                consumed = ScrollDelta(2f, 4f),
                available = ScrollDelta(2f, 4f),
            ),
        )
        assertEquals(
            ScrollVelocity(50f, 100f),
            dispatcher.dispatchPreFling(ScrollVelocity(100f, 200f)),
        )
        assertEquals(
            ScrollVelocity(25f, 50f),
            dispatcher.dispatchPostFling(
                consumed = ScrollVelocity(50f, 100f),
                available = ScrollVelocity(50f, 100f),
            ),
        )

        dispatcher.detach(connector)

        assertEquals(
            ScrollVelocity.Zero,
            dispatcher.dispatchPreFling(ScrollVelocity(100f, 200f)),
        )
    }

    @Test
    fun `late detach from old host keeps newer connector installed`() {
        val dispatcher = NestedScrollDispatcher()
        val oldConnector = RecordingConnector()
        val newConnector = RecordingConnector()
        dispatcher.attach(oldConnector)
        dispatcher.attach(newConnector)

        dispatcher.detach(oldConnector)

        assertEquals(
            ScrollDelta(2f, 4f),
            dispatcher.dispatchPreScroll(ScrollDelta(4f, 8f)),
        )
    }

    private class RecordingConnector : NestedScrollDispatcherConnector {
        override fun dispatchPreScroll(
            available: ScrollDelta,
            source: NestedScrollSource,
        ): ScrollDelta = ScrollDelta(
            x = available.x / 2f,
            y = available.y / 2f,
        )

        override fun dispatchPostScroll(
            consumed: ScrollDelta,
            available: ScrollDelta,
            source: NestedScrollSource,
        ): ScrollDelta = ScrollDelta(
            x = available.x / 2f,
            y = available.y / 2f,
        )

        override fun dispatchPreFling(
            available: ScrollVelocity,
        ): ScrollVelocity = ScrollVelocity(
            x = available.x / 2f,
            y = available.y / 2f,
        )

        override fun dispatchPostFling(
            consumed: ScrollVelocity,
            available: ScrollVelocity,
        ): ScrollVelocity = ScrollVelocity(
            x = available.x / 2f,
            y = available.y / 2f,
        )
    }
}
