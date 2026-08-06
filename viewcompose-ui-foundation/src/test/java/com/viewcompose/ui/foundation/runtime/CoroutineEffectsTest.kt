package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Coroutine Effects 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Coroutine Effects behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.runtime.composition.ComposerLite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutineEffectsTest {
    @Test
    fun `launched effect starts only after commit and restarts for changed key`() = runBlocking {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var key = 1

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return ComposerContext.withComposer(
                composer = composer,
                coroutineContext = coroutineContext,
            ) {
                composer.prepareRoot {
                    val launchedKey = key
                    LaunchedEffect(launchedKey) {
                        events += "start:$launchedKey"
                        try {
                            awaitCancellation()
                        } finally {
                            events += "cancel:$launchedKey"
                        }
                    }
                }
            }
        }

        prepare().abort()
        yield()
        assertTrue(events.isEmpty())

        prepare().commit()
        yield()
        assertEquals(listOf("start:1"), events)

        prepare().commit()
        yield()
        assertEquals(listOf("start:1"), events)

        key = 2
        prepare().commit()
        yield()
        assertEquals(
            listOf(
                "start:1",
                "cancel:1",
                "start:2",
            ),
            events,
        )

        composer.dispose()
        yield()
        assertEquals("cancel:2", events.last())
    }

    @Test
    fun `remember coroutine scope is stable and cancelled when forgotten`() = runBlocking {
        val composer = ComposerLite()
        var first: CoroutineScope? = null
        lateinit var second: CoroutineScope
        var includeScope = true

        fun compose() {
            composer.requestRootRecompose()
            ComposerContext.withComposer(
                composer = composer,
                coroutineContext = coroutineContext,
            ) {
                composer.prepareRoot {
                    if (includeScope) {
                        val scope = rememberCoroutineScope()
                        if (first == null) {
                            first = scope
                        } else {
                            second = scope
                        }
                    }
                }.commit()
            }
        }

        compose()
        compose()
        val rememberedScope = checkNotNull(first)
        assertSame(rememberedScope, second)

        val gate = CompletableDeferred<Unit>()
        val child = rememberedScope.launch {
            gate.await()
        }
        yield()
        assertTrue(child.isActive)

        includeScope = false
        compose()
        child.join()

        assertFalse(child.isActive)
        assertFalse(rememberedScope.isActive)
    }

    @Test
    fun `remember coroutine scope rejects a detached job`() = runBlocking {
        val composer = ComposerLite()

        val error = runCatching {
            ComposerContext.withComposer(
                composer = composer,
                coroutineContext = coroutineContext,
            ) {
                composer.prepareRoot {
                    rememberCoroutineScope {
                        Job()
                    }
                }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
