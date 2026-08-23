package com.viewcompose.runtime.composition

import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionTimingTest {
    @Test
    fun `ordinary composition never touches a timing collector`() {
        val composer = ComposerLite()
        var callbacks = 0
        val collector = CompositionTimingCollector {
            callbacks += 1
            CompositionTimingSpan { callbacks += 1 }
        }

        composer.prepareRoot {
            composer.runGroup(signature = "node") { 1 }
        }.commit()

        assertEquals(0, callbacks)
        composer.prepareRootWithTiming(collector) {
            composer.runGroup(signature = "node") { 1 }
        }.commit()
        assertEquals(0, callbacks)
    }

    @Test
    fun `collector observes only executed scopes in nested order`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()

        composer.prepareRootWithTiming(
            collector = CompositionTimingCollector { scope ->
                events += "begin:${scope.path}:${scope.depth}"
                CompositionTimingSpan { events += "end:${scope.path}:${scope.depth}" }
            },
        ) {
            composer.runGroup(signature = "parent") {
                composer.runGroup(signature = "child") { 1 }
            }
        }.commit()

        assertEquals(6, events.size)
        assertEquals("begin:root:0", events[0])
        assertTrue(events[1].startsWith("begin:root/"))
        assertTrue(events[2].startsWith("begin:root/"))
        assertTrue(events[3].startsWith("end:root/"))
        assertTrue(events[4].startsWith("end:root/"))
        assertEquals("end:root:0", events[5])
    }

    @Test
    fun `accepted scope exposes stable identity only while its body runs`() {
        val state = mutableStateOf(0)
        val composer = ComposerLite()
        var firstIdentity: CompositionTimingNodeIdentity? = null
        var exposedAfterBody: CompositionTimingNodeIdentity? = null

        fun timedCompose() {
            composer.requestRootRecompose()
            composer.prepareRootWithTiming(
                collector = CompositionTimingCollector { CompositionTimingSpan {} },
            ) {
                composer.runGroup(signature = "node", inputs = state.value) { scope ->
                    firstIdentity = firstIdentity ?: scope.timingNodeIdentityOrNull()
                    assertEquals(firstIdentity, scope.timingNodeIdentityOrNull())
                    state.value
                }
            }.commit()
        }

        timedCompose()
        state.value = 1
        timedCompose()
        composer.prepareRoot {
            composer.runGroup(signature = "node", inputs = state.value) { scope ->
                exposedAfterBody = scope.timingNodeIdentityOrNull()
            }
        }.commit()

        assertNotNull(firstIdentity)
        assertNull(exposedAfterBody)
    }

    @Test
    fun `collector failures cannot replace application results or failures`() {
        val beginFailureComposer = ComposerLite()
        val beginResult = beginFailureComposer.prepareRootWithTiming(
            collector = CompositionTimingCollector { error("begin failed") },
        ) { "result" }
        assertEquals("result", beginResult.value)
        beginResult.commit()

        val closeFailureComposer = ComposerLite()
        val closeResult = closeFailureComposer.prepareRootWithTiming(
            collector = CompositionTimingCollector {
                CompositionTimingSpan { error("close failed") }
            },
        ) { "result" }
        assertEquals("result", closeResult.value)
        closeResult.commit()

        val applicationFailureComposer = ComposerLite()
        val failure = runCatching {
            applicationFailureComposer.prepareRootWithTiming(
                collector = CompositionTimingCollector {
                    CompositionTimingSpan { error("close failed") }
                },
            ) { error("application failed") }
        }.exceptionOrNull()
        assertEquals("application failed", failure?.message)
    }
}
