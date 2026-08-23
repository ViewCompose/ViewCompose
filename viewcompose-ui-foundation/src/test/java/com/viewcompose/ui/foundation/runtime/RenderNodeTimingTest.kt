package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.tooling.UiNodeTooling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderNodeTimingTest {
    @Test
    fun `nested timing produces inclusive self and direct records with one token`() {
        val clock = IncrementingClock()
        val capture = capture(clock, maxFrames = 1)
        assertTrue(capture.beginFrame(frameId = 7L))
        val tree = composeOneNode(capture)
        val identity = checkNotNull(UiNodeTooling.timingIdentityOf(tree.single()))
        val binding = checkNotNull(capture.beginInterval(
            subject = CoreRenderTimingSubject(
                nodeIdentity = identity,
                nodeType = NodeType.Text,
                depth = 1,
                synthetic = false,
            ),
            phase = CoreRenderTimingPhase.Binding,
        ))
        binding.close()
        capture.completeFrame()

        val result = capture.snapshot()
        assertTrue(result.complete)
        assertEquals(RenderNodeTimingEndReason.FrameLimit, result.endReason)
        assertEquals(6, result.attemptedClockReads)
        assertEquals(6, result.retainedClockReads)
        assertEquals(5, result.records.size)
        val parentInclusive = result.records.single { record ->
            record.nodeType == null &&
                record.depth == 0 &&
                record.phase == RenderNodeTimingPhase.Composition &&
                record.inclusion == RenderNodeTimingInclusion.Inclusive
        }
        val parentSelf = result.records.single { record ->
            record.nodeToken == parentInclusive.nodeToken &&
                record.inclusion == RenderNodeTimingInclusion.Self
        }
        assertTrue(parentInclusive.durationNanos > parentSelf.durationNanos)
        val childComposition = result.records.first { record ->
            record.depth == 1 && record.phase == RenderNodeTimingPhase.Composition
        }
        val childBinding = result.records.single { record ->
            record.phase == RenderNodeTimingPhase.Binding
        }
        assertEquals(childComposition.nodeToken, childBinding.nodeToken)
        assertEquals(parentInclusive.nodeToken, childBinding.parentNodeToken)
        assertEquals(NodeType.Text, childBinding.nodeType)
        assertEquals(RenderNodeTimingInclusion.Direct, childBinding.inclusion)
        assertEquals(10L, result.emptyPairOverheadNanos)
    }

    @Test
    fun `phase filter exposes composition identity without composition clock reads`() {
        val clock = IncrementingClock()
        val capture = capture(
            clock = clock,
            maxFrames = 1,
            phases = setOf(RenderNodeTimingPhase.Binding),
        )
        capture.beginFrame(1L)

        val tree = composeOneNode(capture)
        val identity = checkNotNull(UiNodeTooling.timingIdentityOf(tree.single()))
        val binding = checkNotNull(capture.beginInterval(
            subject = CoreRenderTimingSubject(identity, NodeType.Text, 1, synthetic = false),
            phase = CoreRenderTimingPhase.Binding,
        ))
        binding.close()
        capture.completeFrame()

        val result = capture.snapshot()
        assertEquals(2L, result.attemptedClockReads)
        assertEquals(2L, result.retainedClockReads)
        assertEquals(listOf(RenderNodeTimingPhase.Binding), result.records.map { it.phase })
    }

    @Test
    fun `observed node emission carries its active composition timing identity`() {
        val capture = capture(IncrementingClock(), maxFrames = 1)
        capture.beginFrame(1L)

        val tree = ComposerContext.withComposer(ComposerLite()) {
            val composer = checkNotNull(ComposerContext.currentComposer())
            val prepared = composer.prepareRootWithTiming(capture) {
                buildVNodeTree {
                    emit(
                        type = NodeType.Text,
                        spec = observedNodeSpec(inputs = emptyList()) { EmptyNodeSpec },
                    )
                }
            }
            prepared.commit()
            prepared.value
        }
        val identity = checkNotNull(UiNodeTooling.timingIdentityOf(tree.single()))
        capture.beginInterval(
            subject = CoreRenderTimingSubject(identity, NodeType.Text, 1, synthetic = false),
            phase = CoreRenderTimingPhase.Binding,
        )?.close()
        capture.completeFrame()

        val result = capture.snapshot()
        val observedComposition = result.records.first { record ->
            record.depth == 1 && record.phase == RenderNodeTimingPhase.Composition
        }
        val binding = result.records.single { record ->
            record.phase == RenderNodeTimingPhase.Binding
        }
        assertEquals(observedComposition.nodeToken, binding.nodeToken)
    }

    @Test
    fun `node and record limits truncate deterministically before extra node clocks`() {
        val clock = IncrementingClock()
        val capture = capture(clock, maxFrames = 8)
        repeat(8) { frame ->
            capture.beginFrame(frame.toLong() + 1L)
            repeat(65) { node ->
                capture.beginInterval(
                    subject = CoreRenderTimingSubject(
                        nodeIdentity = frame * 100L + node + 1L,
                        nodeType = NodeType.Text,
                        depth = 0,
                        synthetic = false,
                    ),
                    phase = CoreRenderTimingPhase.Reconciliation,
                )?.close()
            }
            capture.completeFrame()
        }

        val result = capture.snapshot()
        assertEquals(512, result.records.size)
        assertEquals(8, result.droppedTimedNodes)
        assertEquals(512, result.droppedRecords)
        assertEquals(1_024L, result.attemptedClockReads)
        assertEquals(512L, result.retainedClockReads)
        assertTrue(result.truncated)
    }

    @Test
    fun `snapshot auto stops after monotonic duration`() {
        var now = 0L
        val capture = capture(
            clock = { now },
            maxFrames = 8,
            maxDurationNanos = 100L,
        )
        assertTrue(capture.beginFrame(1L))
        capture.completeFrame()
        now = 100L

        val result = capture.snapshot()

        assertTrue(result.complete)
        assertEquals(RenderNodeTimingEndReason.DurationLimit, result.endReason)
        assertFalse(capture.beginFrame(2L))
    }

    private fun capture(
        clock: () -> Long,
        maxFrames: Int,
        maxDurationNanos: Long = MAX_TIMING_DURATION_NANOS,
        phases: Set<RenderNodeTimingPhase> = RenderNodeTimingPhase.entries.toSet(),
    ): ActiveRenderNodeTimingCapture = ActiveRenderNodeTimingCapture(
        request = RenderNodeTimingCaptureRequest(
            phases = phases,
            maxFrames = maxFrames,
            maxDurationNanos = maxDurationNanos,
        ),
        context = RenderDiagnosticContext(
            sessionId = RenderSessionTraceId(1L),
            parentSessionId = null,
            role = RenderSessionRole.Host,
            frameId = null,
            eventSequence = 0L,
            monotonicTimestampNanos = 0L,
        ),
        clock = clock,
        onFinished = null,
    )

    private fun composeOneNode(capture: ActiveRenderNodeTimingCapture) =
        ComposerContext.withComposer(ComposerLite()) {
            val composer = checkNotNull(ComposerContext.currentComposer())
            val prepared = composer.prepareRootWithTiming(capture) {
                buildVNodeTree {
                    emit(
                        type = NodeType.Text,
                        spec = EmptyNodeSpec,
                    )
                }
            }
            prepared.commit()
            prepared.value
        }

    private class IncrementingClock : () -> Long {
        private var value = 0L

        override fun invoke(): Long = value.also { value += 10L }
    }
}
