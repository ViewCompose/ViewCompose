package com.viewcompose.lifecycle

import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.host.android.renderInto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class LifecycleAndroidViewAdapterTest {
    @Test
    fun `initial commit catches up and later transitions use latest committed state`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val events = mutableListOf<String>()
        var state = AdapterState(owner = owner, value = "one")
        var adapter = RecordingAdapter(events)

        val session = renderInto(root) {
            AndroidView(adapter = adapter, state = state, key = "native")
        }

        assertEquals(
            listOf(
                "create",
                "update:one",
                "commit:one",
                "ON_CREATE:one",
                "ON_START:one",
                "ON_RESUME:one",
            ),
            events,
        )
        val mounted = root.getChildAt(0)

        state = AdapterState(owner = owner, value = "two")
        adapter = RecordingAdapter(events)
        session.render()
        assertSame(mounted, root.getChildAt(0))
        assertEquals("update:two", events[events.lastIndex - 1])
        assertEquals("commit:two", events.last())

        owner.moveTo(Lifecycle.State.CREATED)
        assertEquals(
            listOf("ON_PAUSE:two", "ON_STOP:two"),
            events.takeLast(2),
        )

        session.dispose()
        assertEquals(listOf("ON_DESTROY:two", "release"), events.takeLast(2))
    }

    @Test
    fun `owner replacement completes old cleanup before new commit and catch up`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val firstOwner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val secondOwner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.STARTED) }
        val events = mutableListOf<String>()
        var state = AdapterState(firstOwner, "first")

        val session = renderInto(root) {
            AndroidView(
                adapter = RecordingAdapter(events),
                state = state,
                key = "native",
            )
        }
        events.clear()

        state = AdapterState(secondOwner, "second")
        session.render()

        assertEquals(
            listOf(
                "update:second",
                "ON_PAUSE:first",
                "ON_STOP:first",
                "ON_DESTROY:first",
                "commit:second",
                "ON_CREATE:second",
                "ON_START:second",
            ),
            events,
        )
        firstOwner.moveTo(Lifecycle.State.CREATED)
        assertEquals("ON_START:second", events.last())

        session.dispose()
    }

    @Test
    fun `lifecycle callback failure performs bounded cleanup and detaches`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED) }
        val events = mutableListOf<String>()
        val adapter = RecordingAdapter(events, failEvent = Lifecycle.Event.ON_START)
        val session = renderInto(root) {
            AndroidView(
                adapter = adapter,
                state = AdapterState(owner, "value"),
                key = "native",
            )
        }

        assertThrows(IllegalStateException::class.java) {
            owner.moveTo(Lifecycle.State.STARTED)
        }
        assertEquals(
            listOf("ON_START:value", "ON_STOP:value", "ON_DESTROY:value"),
            events.takeLast(3),
        )
        val countAfterFailure = events.size
        owner.moveTo(Lifecycle.State.CREATED)
        owner.moveTo(Lifecycle.State.STARTED)
        assertEquals(countAfterFailure, events.size)

        session.dispose()
    }

    @Test
    fun `commit callback failure clears the preceding binding instead of leaving stale state active`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val events = mutableListOf<String>()
        var state = AdapterState(owner, "stable")
        val adapter = RecordingAdapter(events, failCommitValue = "broken")
        val session = renderInto(root) {
            AndroidView(adapter = adapter, state = state, key = "native")
        }
        events.clear()

        state = AdapterState(owner, "broken")
        session.render()

        assertEquals(
            listOf(
                "update:broken",
                "commit:broken",
                "ON_PAUSE:stable",
                "ON_STOP:stable",
                "ON_DESTROY:stable",
            ),
            events,
        )
        val eventCountAfterFailure = events.size
        owner.moveTo(Lifecycle.State.CREATED)
        owner.moveTo(Lifecycle.State.RESUMED)
        assertEquals(eventCountAfterFailure, events.size)

        session.dispose()
        assertEquals("release", events.last())
    }

    @Test
    fun `reentrant owner change is reconciled before another upward callback`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val events = mutableListOf<String>()
        val adapter = RecordingAdapter(
            events = events,
            afterEvent = { event ->
                if (event == Lifecycle.Event.ON_START) {
                    owner.moveTo(Lifecycle.State.CREATED)
                }
            },
        )

        val session = renderInto(root) {
            AndroidView(
                adapter = adapter,
                state = AdapterState(owner, "value"),
                key = "native",
            )
        }

        assertEquals(
            listOf(
                "create",
                "update:value",
                "commit:value",
                "ON_CREATE:value",
                "ON_START:value",
                "ON_STOP:value",
            ),
            events,
        )

        session.dispose()
        assertEquals(listOf("ON_DESTROY:value", "release"), events.takeLast(2))
    }

    @Test
    fun `destroyed owner is rejected before adapter commit work`() {
        val owner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.CREATED)
            moveTo(Lifecycle.State.DESTROYED)
        }
        val binding = AndroidViewLifecycleBinding()

        val error = assertThrows(IllegalStateException::class.java) {
            binding.prepareOwner(owner)
        }

        assertEquals(
            "Android View cannot bind to a destroyed LifecycleOwner.",
            error.message,
        )
    }

    private data class AdapterState(
        val owner: LifecycleOwner,
        val value: String,
    )

    private class RecordingAdapter(
        private val events: MutableList<String>,
        private val failEvent: Lifecycle.Event? = null,
        private val failCommitValue: String? = null,
        private val afterEvent: (Lifecycle.Event) -> Unit = {},
    ) : LifecycleAndroidViewAdapter<View, AdapterState>() {
        override fun lifecycleOwner(state: AdapterState): LifecycleOwner = state.owner

        override fun create(scope: AndroidViewCreateScope): View {
            events += "create"
            return View(scope.context)
        }

        override fun update(scope: AndroidViewUpdateScope<View>, state: AdapterState) {
            events += "update:${state.value}"
        }

        override fun onViewCommit(scope: AndroidViewCommitScope<View>, state: AdapterState) {
            events += "commit:${state.value}"
            if (state.value == failCommitValue) {
                error("commit failed")
            }
        }

        override fun onLifecycleEvent(
            scope: AndroidViewLifecycleEventScope<View>,
            state: AdapterState,
            event: Lifecycle.Event,
        ) {
            events += "$event:${state.value}"
            if (event == failEvent) {
                error("lifecycle failed")
            }
            afterEvent(event)
        }

        override fun onViewRelease(view: View) {
            events += "release"
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}
