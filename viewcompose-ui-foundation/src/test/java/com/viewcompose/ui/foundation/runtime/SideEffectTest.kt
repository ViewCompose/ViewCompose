package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Side Effect 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Side Effect behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun `keyed side effect runs only for a new key list`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()
        var key = 1

        fun render() {
            harness.render {
                val current = key
                SideEffect(current) {
                    events += "key:$current"
                }
            }
        }

        render()
        render()
        key = 2
        render()

        assertEquals(listOf("key:1", "key:2"), events)
        harness.dispose()
    }

    @Test
    fun `keyed side effect rejects an empty dynamic key list`() {
        val harness = ComposerRuntimeHarness()

        val error = runCatching {
            harness.render {
                SideEffect(*emptyArray()) {}
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
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

    @Test
    fun `side effect must capture local values during declaration`() {
        val harness = ComposerRuntimeHarness()

        val error = runCatching {
            harness.render {
                SideEffect {
                    Theme.current
                }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("UiLocal 'Theme'"))
        assertTrue(error?.message.orEmpty().contains("capture"))
        harness.dispose()
    }

    @Test
    fun `side effect can use a theme value captured during declaration`() {
        val harness = ComposerRuntimeHarness()
        val expected = UiThemeDefaults.dark()
        var observed: UiThemeTokens? = null

        harness.render {
            buildVNodeTree {
                UiTheme(expected) {
                    val captured = Theme.current
                    SideEffect {
                        observed = captured
                    }
                }
            }
        }

        assertSame(expected, observed)
        harness.dispose()
    }
}
