package com.viewcompose.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.ui.foundation.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleEffectsTest {
    @Test
    fun `start effect follows repeated start stop and composition disposal`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()

        owner.handle(Lifecycle.Event.ON_CREATE)
        harness.renderTree {
            LifecycleStartEffect("tracker", lifecycleOwner = owner) {
                events += "start"
                onStopOrDispose {
                    events += "stop"
                }
            }
        }
        assertTrue(events.isEmpty())

        owner.handle(Lifecycle.Event.ON_START)
        owner.handle(Lifecycle.Event.ON_STOP)
        owner.handle(Lifecycle.Event.ON_START)
        harness.dispose()

        assertEquals(listOf("start", "stop", "start", "stop"), events)
    }

    @Test
    fun `start effect key replacement cleans up before new setup`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()
        var key = 1

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)

        fun render() {
            harness.renderTree {
                val current = key
                LifecycleStartEffect(current, lifecycleOwner = owner) {
                    events += "start:$current"
                    onStopOrDispose {
                        events += "stop:$current"
                    }
                }
            }
        }

        render()
        render()
        key = 2
        render()

        assertEquals(listOf("start:1", "stop:1", "start:2"), events)
        harness.dispose()
    }

    @Test
    fun `start effect owner replacement detaches old lifecycle`() {
        val harness = WidgetCoreRuntimeHarness()
        val firstOwner = TestLifecycleOwner()
        val secondOwner = TestLifecycleOwner()
        val events = mutableListOf<String>()
        var owner: LifecycleOwner = firstOwner

        firstOwner.handle(Lifecycle.Event.ON_CREATE)
        firstOwner.handle(Lifecycle.Event.ON_START)
        secondOwner.handle(Lifecycle.Event.ON_CREATE)
        secondOwner.handle(Lifecycle.Event.ON_START)

        fun render() {
            val currentOwner = owner
            harness.renderTree {
                LifecycleStartEffect("stable", lifecycleOwner = currentOwner) {
                    events += "start:${if (currentOwner === firstOwner) 1 else 2}"
                    onStopOrDispose {
                        events += "stop:${if (currentOwner === firstOwner) 1 else 2}"
                    }
                }
            }
        }

        render()
        owner = secondOwner
        render()
        firstOwner.handle(Lifecycle.Event.ON_STOP)

        assertEquals(listOf("start:1", "stop:1", "start:2"), events)
        harness.dispose()
    }

    @Test
    fun `aborted lifecycle replacement leaves the committed observer active`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()
        var key = 1

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        fun content(): com.viewcompose.ui.foundation.UiTreeBuilder.() -> Unit = {
            val current = key
            LifecycleStartEffect(current, lifecycleOwner = owner) {
                events += "start:$current"
                onStopOrDispose {
                    events += "stop:$current"
                }
            }
        }

        harness.renderTree(content())
        key = 2
        harness.prepareTree(content()).abort()
        owner.handle(Lifecycle.Event.ON_STOP)
        owner.handle(Lifecycle.Event.ON_START)

        assertEquals(listOf("start:1", "stop:1", "start:1"), events)

        key = 3
        harness.prepareTree(content()).commit()
        harness.commitSideEffects()
        assertEquals(
            listOf("start:1", "stop:1", "start:1", "stop:1", "start:3"),
            events,
        )
        harness.dispose()
    }

    @Test
    fun `resume effect pairs every resume with pause or disposal`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        harness.renderTree {
            LifecycleResumeEffect("camera", lifecycleOwner = owner) {
                events += "resume"
                onPauseOrDispose {
                    events += "pause"
                }
            }
        }

        owner.handle(Lifecycle.Event.ON_RESUME)
        owner.handle(Lifecycle.Event.ON_PAUSE)
        owner.handle(Lifecycle.Event.ON_RESUME)
        owner.handle(Lifecycle.Event.ON_DESTROY)

        assertEquals(listOf("resume", "pause", "resume", "pause"), events)
        harness.dispose()
    }

    @Test
    fun `throwing lifecycle setup stays inactive until effect identity is replaced`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        var attempts = 0
        var key = 1

        owner.handle(Lifecycle.Event.ON_CREATE)
        fun render() {
            val current = key
            harness.renderTree {
                LifecycleStartEffect(current, lifecycleOwner = owner) {
                    attempts += 1
                    if (current == 1) error("setup failed")
                    onStopOrDispose {}
                }
            }
        }
        render()

        val firstError = runCatching {
            owner.handle(Lifecycle.Event.ON_START)
        }.exceptionOrNull()
        assertTrue(firstError is IllegalStateException)

        owner.handle(Lifecycle.Event.ON_STOP)
        owner.handle(Lifecycle.Event.ON_START)
        assertEquals(1, attempts)

        key = 2
        render()
        assertEquals(2, attempts)
        harness.dispose()
    }

    @Test
    fun `setup failure during initial active commit retries on the next commit`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        var attempts = 0
        var failSetup = true

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        fun render(): Throwable? = runCatching {
            harness.renderTree {
                LifecycleStartEffect(Unit, lifecycleOwner = owner) {
                    attempts += 1
                    if (failSetup) {
                        failSetup = false
                        error("setup failed")
                    }
                    onStopOrDispose {}
                }
            }
        }.exceptionOrNull()

        assertTrue(render() is IllegalStateException)
        assertEquals(1, attempts)
        assertNull(render())
        assertEquals(2, attempts)
        harness.dispose()
    }

    @Test
    fun `throwing lifecycle cleanup is terminal and detaches the observer`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        harness.renderTree {
            LifecycleStartEffect("tracker", lifecycleOwner = owner) {
                events += "start"
                onStopOrDispose {
                    events += "stop"
                    error("cleanup failed")
                }
            }
        }

        val error = runCatching {
            owner.handle(Lifecycle.Event.ON_STOP)
        }.exceptionOrNull()
        owner.handle(Lifecycle.Event.ON_START)
        harness.dispose()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("start", "stop"), events)
    }

    @Test
    fun `cleanup failure during key replacement does not skip replacement setup`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()
        var key = 1

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        fun render(): Throwable? = runCatching {
            harness.renderTree {
                val current = key
                LifecycleStartEffect(current, lifecycleOwner = owner) {
                    events += "start:$current"
                    onStopOrDispose {
                        events += "stop:$current"
                        if (current == 1) error("cleanup failed")
                    }
                }
            }
        }.exceptionOrNull()

        assertNull(render())
        key = 2
        val error = render()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("start:1", "stop:1", "start:2"), events)
        owner.handle(Lifecycle.Event.ON_STOP)
        assertEquals(listOf("start:1", "stop:1", "start:2", "stop:2"), events)
        harness.dispose()
    }

    @Test
    fun `lifecycle callbacks must capture local values during declaration`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()

        owner.handle(Lifecycle.Event.ON_CREATE)
        harness.renderTree {
            LifecycleStartEffect("tracker", lifecycleOwner = owner) {
                Theme.current
                onStopOrDispose {}
            }
        }

        val error = runCatching {
            owner.handle(Lifecycle.Event.ON_START)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("UiLocal 'Theme'"))
        harness.dispose()
    }

    @Test
    fun `current state as state is stable and stops after disposal`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()

        owner.handle(Lifecycle.Event.ON_CREATE)
        val first = harness.render {
            owner.lifecycle.currentStateAsState()
        }
        val second = harness.render {
            owner.lifecycle.currentStateAsState()
        }

        assertSame(first, second)
        assertEquals(Lifecycle.State.CREATED, first.value)

        owner.handle(Lifecycle.Event.ON_START)
        assertEquals(Lifecycle.State.STARTED, first.value)

        harness.dispose()
        owner.handle(Lifecycle.Event.ON_RESUME)
        assertEquals(Lifecycle.State.STARTED, first.value)
    }

    @Test
    fun `lifecycle effects reject an empty dynamic key list`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val keys = emptyArray<Any?>()

        val startError = runCatching {
            harness.renderTree {
                LifecycleStartEffect(*keys, lifecycleOwner = owner) {
                    onStopOrDispose {}
                }
            }
        }.exceptionOrNull()
        val resumeError = runCatching {
            harness.renderTree {
                LifecycleResumeEffect(*keys, lifecycleOwner = owner) {
                    onPauseOrDispose {}
                }
            }
        }.exceptionOrNull()

        assertTrue(startError is IllegalArgumentException)
        assertTrue(resumeError is IllegalArgumentException)
        harness.dispose()
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
