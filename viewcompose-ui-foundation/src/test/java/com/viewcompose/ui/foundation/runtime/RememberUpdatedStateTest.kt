package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Remember Updated State 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Remember Updated State behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RememberUpdatedStateTest {
    @Test
    fun `reuses same state holder across renders`() {
        val harness = ComposerRuntimeHarness()

        val first = harness.render {
            rememberUpdatedState("A")
        }
        val second = harness.render {
            rememberUpdatedState("B")
        }

        assertSame(first, second)
        harness.dispose()
    }

    @Test
    fun `exposes latest value on every render`() {
        val harness = ComposerRuntimeHarness()

        val first = harness.render {
            rememberUpdatedState("A")
        }
        val second = harness.render {
            rememberUpdatedState("B")
        }

        assertSame(first, second)
        assertEquals("B", second.value)
        harness.dispose()
    }
}
