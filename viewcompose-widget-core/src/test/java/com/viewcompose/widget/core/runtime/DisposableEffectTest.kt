package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core runtime 中的 Disposable Effect 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Disposable Effect behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisposableEffectTest {
    @Test
    fun `reuses effect when key is unchanged`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        renderEffect(harness, key = "stable", events = events)
        renderEffect(harness, key = "stable", events = events)

        assertEquals(
            listOf("start:stable"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `disposes and restarts effect when key changes`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        renderEffect(harness, key = "A", events = events)
        renderEffect(harness, key = "B", events = events)

        assertEquals(
            listOf("start:A", "dispose:A", "start:B"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `disposes effect when slot disappears`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        renderEffect(harness, key = "A", events = events)
        harness.render { Unit }

        assertEquals(
            listOf("start:A", "dispose:A"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `composer disposal disposes active effects`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        renderEffect(harness, key = "A", events = events)
        harness.dispose()

        assertEquals(
            listOf("start:A", "dispose:A"),
            events,
        )
    }

    @Test
    fun `reuses effect when composite keys are unchanged`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        renderEffect(
            harness = harness,
            events = events,
            keys = arrayOf("user", 1),
        )
        renderEffect(
            harness = harness,
            events = events,
            keys = arrayOf("user", 1),
        )

        assertEquals(
            listOf("start:user-1"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `restarts effect when any composite key changes`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        renderEffect(
            harness = harness,
            events = events,
            keys = arrayOf("user", 1),
        )
        renderEffect(
            harness = harness,
            events = events,
            keys = arrayOf("user", 2),
        )

        assertEquals(
            listOf("start:user-1", "dispose:user-1", "start:user-2"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `key scope contributes to effect identity`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()

        harness.render {
            key("branch-A") {
                DisposableEffect {
                    events += "start:A"
                    {
                        events += "dispose:A"
                    }
                }
            }
        }
        harness.render {
            key("branch-B") {
                DisposableEffect {
                    events += "start:B"
                    {
                        events += "dispose:B"
                    }
                }
            }
        }

        assertEquals(
            listOf("start:A", "dispose:A", "start:B"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `disposable effect outside composition fails fast`() {
        val error = runCatching {
            DisposableEffect { {} }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("DisposableEffect"))
        assertTrue(error?.message.orEmpty().contains("active ViewCompose composition"))
    }

    private fun renderEffect(
        harness: ComposerRuntimeHarness,
        key: String,
        events: MutableList<String>,
    ) {
        renderEffect(
            harness = harness,
            events = events,
            keys = arrayOf(key),
        )
    }

    private fun renderEffect(
        harness: ComposerRuntimeHarness,
        events: MutableList<String>,
        keys: Array<out Any?>,
    ) {
        val label = keys.joinToString(separator = "-")
        harness.render {
            DisposableEffect(*keys) {
                events += "start:$label"
                {
                    events += "dispose:$label"
                }
            }
        }
    }
}
