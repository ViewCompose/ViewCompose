package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core runtime 中的 Side Effect 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Side Effect behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideEffectTest {
    @Test
    fun `runs all registered effects on commit`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        harness.render {
            SideEffect { events += "first" }
            SideEffect { events += "second" }
        }

        assertEquals(
            listOf("first", "second"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `runs side effects again on next render`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        harness.render {
            SideEffect { events += "render-1" }
        }
        harness.render {
            SideEffect { events += "render-2" }
        }

        assertEquals(
            listOf("render-1", "render-2"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `side effect outside composition fails fast`() {
        val events = mutableListOf<String>()

        val error = runCatching {
            SideEffect { events += "outside" }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("SideEffect"))
        assertTrue(error?.message.orEmpty().contains("active ViewCompose composition"))
        assertEquals(emptyList<String>(), events)
    }
}
