package com.viewcompose.animation

/*
 * 测试职责：覆盖 animation DSL 中的 Transition 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Transition behavior in animation DSL and guards the contract against regressions.
 */

import com.viewcompose.runtime.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionTest {
    @Test
    fun `multiple channels can register duration inside one composition snapshot`() {
        val transition = Transition(
            initialState = false,
            label = "test",
        )
        transition.updateTarget(true)

        Snapshot.takeSnapshot().use { snapshot ->
            snapshot.enter {
                transition.registerChannelDuration(100L)
                transition.registerChannelDuration(300L)
                transition.registerChannelDuration(200L)
            }
        }

        assertEquals(300L, transition.segmentDurationNanos)
    }
}
