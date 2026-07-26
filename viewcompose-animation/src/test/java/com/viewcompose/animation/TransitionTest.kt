package com.viewcompose.animation

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
