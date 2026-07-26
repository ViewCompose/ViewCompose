package com.viewcompose.renderer.view.container

import android.view.View
import androidx.core.view.ViewCompat
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.gesture.ScrollVelocity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NestedScrollHostLayoutTest {
    @Test
    fun `nested hosts dispatch pre outside in and post inside out`() {
        val context = RuntimeEnvironment.getApplication()
        val outer = DeclarativeNestedScrollHostLayout(context)
        val inner = DeclarativeNestedScrollHostLayout(context)
        val dispatcher = NestedScrollDispatcher()
        val calls = mutableListOf<String>()
        outer.addView(inner)
        outer.update(
            connection = recordingConnection(
                label = "outer",
                calls = calls,
            ),
            dispatcher = null,
        )
        inner.update(
            connection = recordingConnection(
                label = "inner",
                calls = calls,
            ),
            dispatcher = dispatcher,
        )

        assertEquals(
            ScrollDelta(0f, 2f),
            dispatcher.dispatchPreScroll(
                available = ScrollDelta(0f, 10f),
                source = NestedScrollSource.UserInput,
            ),
        )
        assertEquals(listOf("outer-pre", "inner-pre"), calls)

        calls.clear()
        assertEquals(
            ScrollDelta(0f, 2f),
            dispatcher.dispatchPostScroll(
                consumed = ScrollDelta(0f, 4f),
                available = ScrollDelta(0f, 6f),
                source = NestedScrollSource.UserInput,
            ),
        )
        assertEquals(listOf("inner-post", "outer-post"), calls)
    }

    @Test
    fun `dispatcher clamps invalid over-consumption and detaches on dispose`() {
        val dispatcher = NestedScrollDispatcher()
        val host = DeclarativeNestedScrollHostLayout(
            RuntimeEnvironment.getApplication(),
        )
        host.update(
            connection = object : NestedScrollConnection {
                override fun onPreScroll(
                    available: ScrollDelta,
                    source: NestedScrollSource,
                ): ScrollDelta = ScrollDelta(
                    x = 100f,
                    y = -100f,
                )

                override fun onPreFling(
                    available: ScrollVelocity,
                ): ScrollVelocity = ScrollVelocity(
                    x = Float.NaN,
                    y = -1_000f,
                )
            },
            dispatcher = dispatcher,
        )

        assertEquals(
            ScrollDelta(10f, -5f),
            dispatcher.dispatchPreScroll(
                available = ScrollDelta(10f, -5f),
                source = NestedScrollSource.UserInput,
            ),
        )
        assertEquals(
            ScrollVelocity(0f, -20f),
            dispatcher.dispatchPreFling(
                ScrollVelocity(10f, -20f),
            ),
        )

        host.dispose()

        assertEquals(
            ScrollDelta.Zero,
            dispatcher.dispatchPreScroll(ScrollDelta(10f, -5f)),
        )
    }

    @Test
    fun `native parent callbacks report pre and post consumption`() {
        val host = DeclarativeNestedScrollHostLayout(
            RuntimeEnvironment.getApplication(),
        )
        val target = View(host.context)
        host.update(
            connection = object : NestedScrollConnection {
                override fun onPreScroll(
                    available: ScrollDelta,
                    source: NestedScrollSource,
                ): ScrollDelta = ScrollDelta(
                    x = available.x / 2f,
                    y = available.y / 2f,
                )

                override fun onPostScroll(
                    consumed: ScrollDelta,
                    available: ScrollDelta,
                    source: NestedScrollSource,
                ): ScrollDelta = available
            },
            dispatcher = null,
        )
        val preConsumed = IntArray(2)

        host.onNestedPreScroll(
            target = target,
            dx = 8,
            dy = 10,
            consumed = preConsumed,
            type = ViewCompat.TYPE_TOUCH,
        )

        assertEquals(4, preConsumed[0])
        assertEquals(5, preConsumed[1])

        val postConsumed = IntArray(2)
        host.onNestedScroll(
            target = target,
            dxConsumed = 4,
            dyConsumed = 5,
            dxUnconsumed = 4,
            dyUnconsumed = 5,
            type = ViewCompat.TYPE_TOUCH,
            consumed = postConsumed,
        )

        assertEquals(4, postConsumed[0])
        assertEquals(5, postConsumed[1])
    }

    private fun recordingConnection(
        label: String,
        calls: MutableList<String>,
    ): NestedScrollConnection {
        return object : NestedScrollConnection {
            override fun onPreScroll(
                available: ScrollDelta,
                source: NestedScrollSource,
            ): ScrollDelta {
                calls += "$label-pre"
                return ScrollDelta(x = 0f, y = 1f)
            }

            override fun onPostScroll(
                consumed: ScrollDelta,
                available: ScrollDelta,
                source: NestedScrollSource,
            ): ScrollDelta {
                calls += "$label-post"
                return ScrollDelta(x = 0f, y = 1f)
            }
        }
    }
}
