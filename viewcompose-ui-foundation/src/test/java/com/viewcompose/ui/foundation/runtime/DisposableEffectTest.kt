package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Disposable Effect 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Disposable Effect behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.runtime.composition.ComposerLite
import kotlinx.coroutines.Dispatchers
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
                DisposableEffect(Unit) {
                    events += "start:A"
                    onDispose {
                        events += "dispose:A"
                    }
                }
            }
        }
        harness.render {
            key("branch-B") {
                DisposableEffect(Unit) {
                    events += "start:B"
                    onDispose {
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
            DisposableEffect(Unit) { onDispose {} }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("DisposableEffect"))
        assertTrue(error?.message.orEmpty().contains("active ViewCompose composition"))
    }

    @Test
    fun `aborted candidate neither starts replacement nor disposes committed setup`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var key = 1

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return ComposerContext.withComposer(
                composer = composer,
                coroutineContext = Dispatchers.Unconfined,
            ) {
                composer.prepareRoot {
                    val current = key
                    DisposableEffect(current) {
                        events += "start:$current"
                        onDispose {
                            events += "dispose:$current"
                        }
                    }
                }
            }
        }

        prepare().commit()
        key = 2
        prepare().abort()

        assertEquals(listOf("start:1"), events)
        composer.dispose()
        assertEquals(listOf("start:1", "dispose:1"), events)
    }

    @Test
    fun `setup failure stays pending and retries for an equal key`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()
        var key = 1
        var failSetup = true

        fun render() {
            harness.render {
                val current = key
                DisposableEffect(current) {
                    events += "start:$current"
                    if (failSetup) {
                        failSetup = false
                        error("setup failed")
                    }
                    onDispose {
                        events += "dispose:$current"
                    }
                }
            }
        }

        val firstError = runCatching(::render).exceptionOrNull()
        assertTrue(firstError is IllegalStateException)

        render()
        assertEquals(listOf("start:1", "start:1"), events)

        key = 2
        render()
        harness.dispose()
        assertEquals(
            listOf("start:1", "start:1", "dispose:1", "start:2", "dispose:2"),
            events,
        )
    }

    @Test
    fun `throwing cleanup is terminal and does not block replacement setup`() {
        val harness = ComposerRuntimeHarness()
        val events = mutableListOf<String>()
        var key = 1

        fun render() {
            harness.render {
                val current = key
                DisposableEffect(current) {
                    events += "start:$current"
                    onDispose {
                        events += "dispose:$current"
                        if (current == 1) error("cleanup failed")
                    }
                }
            }
        }

        render()
        key = 2
        val error = runCatching(::render).exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("start:1", "dispose:1", "start:2"), events)

        harness.dispose()
        assertEquals(listOf("start:1", "dispose:1", "start:2", "dispose:2"), events)
    }

    @Test
    fun `empty dynamic key list is rejected`() {
        val harness = ComposerRuntimeHarness()

        val error = runCatching {
            harness.render {
                DisposableEffect(*emptyArray()) {
                    onDispose {}
                }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        harness.dispose()
    }

    @Test
    fun `disposable setup must capture local values during declaration`() {
        val harness = ComposerRuntimeHarness()

        val error = runCatching {
            harness.render {
                DisposableEffect(Unit) {
                    Theme.current
                    onDispose {}
                }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("UiLocal 'Theme'"))
        harness.dispose()
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
                onDispose {
                    events += "dispose:$label"
                }
            }
        }
    }
}
