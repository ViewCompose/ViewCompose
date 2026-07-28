package com.viewcompose.runtime

/*
 * 测试职责：覆盖 runtime 中的 Derived State 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Derived State behavior in runtime and guards the contract against regressions.
 */

import com.viewcompose.runtime.observation.RuntimeObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class DerivedStateTest {
    @Test
    fun `derived state recalculates after source changes`() {
        val count = mutableStateOf(0)
        val summary = derivedStateOf { "count=${count.value}" }

        assertEquals("count=0", summary.value)

        count.value = 1

        assertEquals("count=1", summary.value)
    }

    @Test
    fun `derived state invalidates observers when dependency changes`() {
        val count = mutableStateOf(0)
        val summary = derivedStateOf { "count=${count.value}" }
        var invalidations = 0

        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            summary.value
        }

        count.value = 1

        assertEquals(1, invalidations)
        observation.dispose()
    }
}
