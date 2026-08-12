package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Coroutine Effects 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Coroutine Effects behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.runtime.composition.ComposerLite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
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
    fun `remember coroutine scope returns failed scope for a detached job`() = runBlocking {
        val composer = ComposerLite()
        lateinit var scope: CoroutineScope

        ComposerContext.withComposer(
            composer = composer,
            coroutineContext = coroutineContext,
        ) {
            composer.prepareRoot {
                scope = rememberCoroutineScope {
                    Job()
                }
            }.commit()
        }

        var ran = false
        val child = scope.launch {
            ran = true
        }
        child.join()

        assertFalse(scope.isActive)
        assertFalse(ran)
        composer.dispose()
    }

    @Test
    fun `child failure cancels remembered scope but not session supervisor`() = runBlocking {
        val composer = ComposerLite()
        val sessionJob = SupervisorJob()
        val failure = CompletableDeferred<Throwable>()
        val context =
            sessionJob + Dispatchers.Unconfined + CoroutineExceptionHandler { _, error ->
                failure.complete(error)
            }
        lateinit var scope: CoroutineScope

        ComposerContext.withComposer(
            composer = composer,
            coroutineContext = context,
        ) {
            composer.prepareRoot {
                scope = rememberCoroutineScope()
            }.commit()
        }

        scope.launch {
            error("child failed")
        }.join()

        assertEquals("child failed", failure.await().message)
        assertFalse(scope.isActive)
        assertTrue(sessionJob.isActive)

        composer.dispose()
        sessionJob.cancel()
    }

    @Test
    fun `launched effect must capture local values during declaration`() = runBlocking {
        val composer = ComposerLite()
        val result = CompletableDeferred<Throwable?>()

        ComposerContext.withComposer(
            composer = composer,
            coroutineContext = coroutineContext,
        ) {
            composer.prepareRoot {
                LaunchedEffect(Unit) {
                    result.complete(runCatching { Theme.current }.exceptionOrNull())
                }
            }.commit()
        }

        val error = result.await()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("UiLocal 'Theme'"))
        composer.dispose()
    }

    @Test
    fun `running launched effect observes only committed updated state`() = runBlocking {
        val composer = ComposerLite()
        val requests = Channel<Unit>(capacity = Channel.RENDEZVOUS)
        val values = Channel<String>(capacity = Channel.RENDEZVOUS)
        var input = "initial"

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return ComposerContext.withComposer(
                composer = composer,
                coroutineContext = coroutineContext,
            ) {
                composer.prepareRoot {
                    val latest = rememberUpdatedState(input)
                    LaunchedEffect(Unit) {
                        for (request in requests) {
                            values.send(latest.value)
                        }
                    }
                }
            }
        }

        prepare().commit()
        yield()

        input = "candidate"
        val candidate = prepare()
        requests.send(Unit)
        assertEquals("initial", values.receive())
        candidate.abort()

        input = "committed"
        prepare().commit()
        requests.send(Unit)
        assertEquals("committed", values.receive())

        composer.dispose()
    }
}
