package com.viewcompose.widget.core

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
