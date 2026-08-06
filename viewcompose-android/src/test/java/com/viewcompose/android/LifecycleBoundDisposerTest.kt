package com.viewcompose.android

/*
 * 测试职责：覆盖 Android host 中的 Lifecycle Bound Disposer 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Lifecycle Bound Disposer behavior in Android host and guards the contract against regressions.
 */

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleBoundDisposerTest {
    @Test
    fun `invokes callback when bound lifecycle is destroyed`() {
        var disposeCount = 0
        val disposer = LifecycleBoundDisposer { disposeCount += 1 }
        val owner = TestLifecycleOwner()

        owner.handle(Lifecycle.Event.ON_CREATE)
        disposer.bind(owner)
        owner.handle(Lifecycle.Event.ON_DESTROY)

        assertEquals(1, disposeCount)
    }

    @Test
    fun `rebind detaches previous lifecycle observer`() {
        var disposeCount = 0
        val disposer = LifecycleBoundDisposer { disposeCount += 1 }
        val ownerA = TestLifecycleOwner()
        val ownerB = TestLifecycleOwner()

        ownerA.handle(Lifecycle.Event.ON_CREATE)
        ownerB.handle(Lifecycle.Event.ON_CREATE)
        disposer.bind(ownerA)
        disposer.bind(ownerB)

        ownerA.handle(Lifecycle.Event.ON_DESTROY)
        assertEquals(0, disposeCount)

        ownerB.handle(Lifecycle.Event.ON_DESTROY)
        assertEquals(1, disposeCount)
    }

    @Test
    fun `clearObserver cancels disposal callback`() {
        var disposeCount = 0
        val disposer = LifecycleBoundDisposer { disposeCount += 1 }
        val owner = TestLifecycleOwner()

        owner.handle(Lifecycle.Event.ON_CREATE)
        disposer.bind(owner)
        disposer.clearObserver()
        owner.handle(Lifecycle.Event.ON_DESTROY)

        assertEquals(0, disposeCount)
    }

    @Test
    fun `binding an already destroyed lifecycle disposes immediately`() {
        var disposeCount = 0
        val disposer = LifecycleBoundDisposer { disposeCount += 1 }
        val owner = TestLifecycleOwner()
        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_DESTROY)

        disposer.bind(owner)
        disposer.clearObserver()

        assertEquals(1, disposeCount)
    }

    @Test
    fun `destroyed host is rejected before creating a render session`() {
        val owner = TestLifecycleOwner()
        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_DESTROY)

        val error = runCatching {
            requireActiveHost(
                owner = owner,
                hostName = "TestHost",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("destroyed"))
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun handle(
        event: Lifecycle.Event,
    ) {
        registry.handleLifecycleEvent(event)
    }
}
