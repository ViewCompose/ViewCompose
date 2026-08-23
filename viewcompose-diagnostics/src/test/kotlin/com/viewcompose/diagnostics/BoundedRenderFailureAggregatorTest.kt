package com.viewcompose.diagnostics

import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.RenderFailureObserved
import com.viewcompose.ui.foundation.RenderFailureOperation
import com.viewcompose.ui.foundation.RenderFailurePhase
import com.viewcompose.ui.foundation.RenderFailureRecovery
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.foundation.RenderSessionStarted
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BoundedRenderFailureAggregatorTest {
    @Test
    fun `constructor enforces capacity and window limits`() {
        expectIllegalArgument { BoundedRenderFailureAggregator(capacity = 0) }
        expectIllegalArgument { BoundedRenderFailureAggregator(capacity = 129) }
        expectIllegalArgument {
            BoundedRenderFailureAggregator(windowDurationNanos = MINUTE_NANOS - 1L)
        }
        expectIllegalArgument {
            BoundedRenderFailureAggregator(windowDurationNanos = DAY_NANOS + 1L)
        }

        BoundedRenderFailureAggregator(capacity = 1, windowDurationNanos = MINUTE_NANOS)
        BoundedRenderFailureAggregator(capacity = 128, windowDurationNanos = DAY_NANOS)
    }

    @Test
    fun `non failure events do not read clock or create records`() {
        var clockReads = 0
        val aggregator = BoundedRenderFailureAggregator(
            capacity = 2,
            windowDurationNanos = MINUTE_NANOS,
            monotonicTimeNanos = {
                clockReads += 1
                10L
            },
        )

        aggregator.onEvent(RenderSessionStarted(context(sessionId = 1L)))

        assertEquals(0, clockReads)
        assertTrue(aggregator.snapshot().aggregates.isEmpty())
        assertEquals(1, clockReads)
    }

    @Test
    fun `fingerprint groups safe fields and excludes sensitive failure data`() {
        val clock = AtomicLong(100L)
        val aggregator = aggregator(capacity = 4, clock = clock)
        val first = failureEvent(
            sessionId = 1L,
            message = "credential=secret-token",
            nodeKey = "account-user-42",
            stackTrace = arrayOf(
                StackTraceElement("com.example.AccountScreen", "renderSecret", "Account.kt", 91),
                StackTraceElement("com.viewcompose.renderer.Tree", "bind", "Tree.kt", 11),
                StackTraceElement("com.viewcompose.runtime.Frame", "commit", "Frame.kt", 22),
                StackTraceElement("com.viewcompose.ui.Node", "emit", "Node.kt", 33),
                StackTraceElement("com.viewcompose.ui.Ignored", "fourth", "Ignored.kt", 44),
            ),
        )
        val second = failureEvent(
            sessionId = 2L,
            message = "different-private-message",
            nodeKey = "different-private-key",
            stackTrace = first.failure.cause.stackTrace,
        )

        aggregator.record(first)
        clock.incrementAndGet()
        aggregator.record(second)
        val snapshot = aggregator.snapshot()

        val aggregate = snapshot.aggregates.single()
        assertEquals(2L, aggregate.count)
        assertEquals(2L, aggregate.latestContext.sessionId.value)
        assertEquals(SensitiveFailure::class.java.name, aggregate.fingerprint.exceptionType)
        assertEquals(
            listOf("bind", "commit", "emit"),
            aggregate.fingerprint.frameworkFrames.map(RenderFailureStackFrame::methodName),
        )
        val encoded = snapshot.toString()
        assertFalse(encoded.contains("secret-token"))
        assertFalse(encoded.contains("account-user-42"))
        assertFalse(encoded.contains("AccountScreen"))
        assertFalse(encoded.contains("Tree.kt"))
        assertFalse(encoded.contains(":11"))
        assertFalse(encoded.contains("nested-cause-secret"))
    }

    @Test
    fun `fingerprint strings and framework frames stop at absolute limits`() {
        val clock = AtomicLong(100L)
        val aggregator = aggregator(capacity = 1, clock = clock)
        val oversizedClass = "com.viewcompose." + "C".repeat(300)
        val oversizedMethod = "m".repeat(300)
        aggregator.record(
            failureEvent(
                sessionId = 1L,
                stackTrace = arrayOf(
                    StackTraceElement(oversizedClass, oversizedMethod, "First.kt", 1),
                    StackTraceElement("com.viewcompose.Second", "second", "Second.kt", 2),
                    StackTraceElement("com.viewcompose.Third", "third", "Third.kt", 3),
                    StackTraceElement("com.viewcompose.Fourth", "fourth", "Fourth.kt", 4),
                ),
            ),
        )

        val frames = aggregator.snapshot().aggregates.single().fingerprint.frameworkFrames
        assertEquals(3, frames.size)
        assertEquals(256, frames.first().className.length)
        assertEquals(256, frames.first().methodName.length)
        assertEquals("third", frames.last().methodName)
    }

    @Test
    fun `capacity evicts least recently updated entry and reports loss`() {
        val clock = AtomicLong(10L)
        val aggregator = aggregator(capacity = 2, clock = clock)
        val a = failureEvent(sessionId = 1L, phase = RenderFailurePhase.CompositionPrepare)
        val b = failureEvent(sessionId = 2L, phase = RenderFailurePhase.ViewTreeRender)
        val c = failureEvent(sessionId = 3L, phase = RenderFailurePhase.CompositionCommit)

        aggregator.record(a)
        aggregator.record(b)
        aggregator.record(a)
        aggregator.record(c)
        val snapshot = aggregator.snapshot()

        assertEquals(
            listOf(RenderFailurePhase.CompositionPrepare, RenderFailurePhase.CompositionCommit),
            snapshot.aggregates.map { it.fingerprint.phase },
        )
        assertEquals(1L, snapshot.droppedFailureCount)
        assertEquals(1L, snapshot.evictedFingerprintCount)
        assertEquals(listOf(2L, 1L), snapshot.aggregates.map(RenderFailureAggregate::count))
    }

    @Test
    fun `count saturation reports the unrepresented observation`() {
        val clock = AtomicLong(10L)
        val aggregator = aggregator(capacity = 1, clock = clock)
        val event = failureEvent(sessionId = 1L)
        aggregator.record(event)
        setOnlyRecordCount(aggregator, Long.MAX_VALUE)

        aggregator.record(event)
        val snapshot = aggregator.snapshot()

        assertEquals(Long.MAX_VALUE, snapshot.aggregates.single().count)
        assertEquals(1L, snapshot.droppedFailureCount)
    }

    @Test
    fun `snapshot expiration opens an empty next window without a timer`() {
        val clock = AtomicLong(0L)
        val aggregator = aggregator(capacity = 2, clock = clock)
        aggregator.record(failureEvent(sessionId = 1L))
        val first = aggregator.snapshot()

        clock.set(MINUTE_NANOS)
        val expired = aggregator.snapshot()

        assertEquals(1L, first.windowId)
        assertEquals(2L, expired.windowId)
        assertTrue(expired.aggregates.isEmpty())
        assertEquals(MINUTE_NANOS, expired.windowStartedAtNanos)
    }

    @Test
    fun `record expiration discards the elapsed window before grouping`() {
        val clock = AtomicLong(0L)
        val aggregator = aggregator(capacity = 2, clock = clock)
        aggregator.record(failureEvent(sessionId = 1L))

        clock.set(MINUTE_NANOS)
        aggregator.record(failureEvent(sessionId = 2L))
        val current = aggregator.snapshot()

        assertEquals(2L, current.windowId)
        assertEquals(1L, current.aggregates.single().count)
        assertEquals(2L, current.aggregates.single().latestContext.sessionId.value)
        assertEquals(0L, current.droppedFailureCount)
        assertEquals(0L, current.evictedFingerprintCount)
    }

    @Test
    fun `snapshot and reset returns immutable old window and preserves live context`() {
        val clock = AtomicLong(50L)
        val aggregator = aggregator(capacity = 2, clock = clock)
        aggregator.record(failureEvent(sessionId = 41L))

        val completed = aggregator.snapshotAndReset()
        aggregator.record(failureEvent(sessionId = 42L))
        val current = aggregator.snapshot()

        assertEquals(1L, completed.windowId)
        assertEquals(41L, completed.aggregates.single().latestContext.sessionId.value)
        assertEquals(2L, current.windowId)
        assertEquals(42L, current.aggregates.single().latestContext.sessionId.value)
        assertEquals(41L, completed.aggregates.single().latestContext.sessionId.value)
    }

    @Test
    fun `snapshot collections reject mutation through Java collection views`() {
        val clock = AtomicLong(50L)
        val aggregator = aggregator(capacity = 2, clock = clock)
        aggregator.record(failureEvent(sessionId = 1L))
        val snapshot = aggregator.snapshot()

        expectUnsupportedOperation {
            (snapshot.aggregates as MutableList).clear()
        }
        expectUnsupportedOperation {
            (snapshot.aggregates.single().fingerprint.frameworkFrames as MutableList).clear()
        }
        assertEquals(1, aggregator.snapshot().aggregates.size)
    }

    @Test
    fun `high cardinality input remains bounded and deterministic`() {
        val clock = AtomicLong(1L)
        val aggregator = aggregator(capacity = 128, clock = clock)

        repeat(1_000) { index ->
            aggregator.record(
                failureEvent(
                    sessionId = index.toLong() + 1L,
                    stackTrace = arrayOf(
                        StackTraceElement(
                            "com.viewcompose.renderer.HighCardinality",
                            "operation$index",
                            "Sensitive.kt",
                            index,
                        ),
                    ),
                ),
            )
        }
        val snapshot = aggregator.snapshot()

        assertEquals(128, snapshot.aggregates.size)
        assertEquals(872L, snapshot.droppedFailureCount)
        assertEquals(872L, snapshot.evictedFingerprintCount)
        assertEquals("operation872", snapshot.aggregates.first().fingerprint.frameworkFrames.single().methodName)
        assertEquals("operation999", snapshot.aggregates.last().fingerprint.frameworkFrames.single().methodName)
    }

    @Test
    fun `concurrent session publication preserves every same fingerprint count`() {
        val clock = AtomicLong(0L)
        val aggregator = aggregator(capacity = 4, clock = clock)
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val completed = CountDownLatch(8)

        repeat(8) { worker ->
            executor.execute {
                ready.countDown()
                start.await()
                repeat(1_000) {
                    aggregator.record(failureEvent(sessionId = worker.toLong() + 1L))
                }
                completed.countDown()
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(completed.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        val snapshot = aggregator.snapshot()
        assertEquals(8_000L, snapshot.aggregates.single().count)
        assertEquals(0L, snapshot.droppedFailureCount)
    }

    @Test
    fun `export failure is not fed back and disposal remains classifiable`() {
        val clock = AtomicLong(10L)
        val aggregator = aggregator(capacity = 4, clock = clock)
        aggregator.record(
            failureEvent(
                sessionId = 1L,
                phase = RenderFailurePhase.SessionDispose,
                recovery = RenderFailureRecovery.SessionDisposed,
            ),
        )
        val beforeExport = aggregator.snapshot()

        runCatching { throw ExportFailure() }
        val afterExport = aggregator.snapshot()

        assertEquals(beforeExport.aggregates, afterExport.aggregates)
        assertEquals(RenderFailurePhase.SessionDispose, afterExport.aggregates.single().fingerprint.phase)
        assertEquals(RenderFailureRecovery.SessionDisposed, afterExport.aggregates.single().fingerprint.recovery)
    }

    @Test
    fun `new application owned instance starts a process local empty identity`() {
        val firstClock = AtomicLong(1L)
        val first = aggregator(capacity = 1, clock = firstClock)
        first.record(failureEvent(sessionId = 99L))

        val nextProcessClock = AtomicLong(1L)
        val next = aggregator(capacity = 1, clock = nextProcessClock).snapshot()

        assertEquals(1L, next.windowId)
        assertTrue(next.aggregates.isEmpty())
        assertNotEquals(first.snapshot().aggregates, next.aggregates)
    }

    private fun aggregator(
        capacity: Int,
        clock: AtomicLong,
    ): BoundedRenderFailureAggregator = BoundedRenderFailureAggregator(
        capacity = capacity,
        windowDurationNanos = MINUTE_NANOS,
        monotonicTimeNanos = clock::get,
    )

    private fun failureEvent(
        sessionId: Long,
        phase: RenderFailurePhase = RenderFailurePhase.ViewTreeRender,
        recovery: RenderFailureRecovery = RenderFailureRecovery.PreviousFrameRestored,
        message: String = "failure",
        nodeKey: Any? = null,
        stackTrace: Array<StackTraceElement> = arrayOf(
            StackTraceElement("com.viewcompose.renderer.Tree", "render", "Tree.kt", 1),
        ),
    ): RenderFailureObserved {
        val nestedCause = IllegalArgumentException("nested-cause-secret").also {
            it.stackTrace = arrayOf(
                StackTraceElement("com.example.SecretCause", "loadCredential", "Secret.kt", 7),
            )
        }
        val cause = SensitiveFailure(message, nestedCause).also { it.stackTrace = stackTrace }
        return RenderFailureObserved(
            context = context(sessionId = sessionId),
            failure = RenderFailure(
                frameId = 1L,
                phase = phase,
                recovery = recovery,
                cause = cause,
                operation = RenderFailureOperation.AndroidViewUpdate,
                nodeKey = nodeKey,
            ),
        )
    }

    private fun context(sessionId: Long): RenderDiagnosticContext {
        val constructor = RenderDiagnosticContext::class.java.declaredConstructors
            .single { it.parameterCount == 6 }
        constructor.isAccessible = true
        return constructor.newInstance(
            sessionId,
            null,
            RenderSessionRole.Host,
            1L,
            1L,
            1L,
        ) as RenderDiagnosticContext
    }

    private fun setOnlyRecordCount(
        aggregator: BoundedRenderFailureAggregator,
        count: Long,
    ) {
        val recordsField = BoundedRenderFailureAggregator::class.java.getDeclaredField("records")
        recordsField.isAccessible = true
        val records = recordsField.get(aggregator) as Map<*, *>
        val mutableAggregate = records.values.single()
        val countField = mutableAggregate!!::class.java.getDeclaredField("count")
        countField.isAccessible = true
        countField.setLong(mutableAggregate, count)
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun expectUnsupportedOperation(block: () -> Unit) {
        try {
            block()
            fail("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    private class SensitiveFailure(message: String, cause: Throwable) : RuntimeException(message, cause)

    private class ExportFailure : RuntimeException()

    private companion object {
        const val MINUTE_NANOS = 60_000_000_000L
        const val DAY_NANOS = 24L * 60L * MINUTE_NANOS
    }
}
