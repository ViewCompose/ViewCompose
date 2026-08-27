package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.foundation.RenderSessionInspectionPolicy
import com.viewcompose.ui.foundation.RenderSessionInspectionRegistration
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling
import com.viewcompose.ui.foundation.RenderSessionDiagnosticInspection
import com.viewcompose.ui.foundation.RenderSessionNodeInspection
import com.viewcompose.ui.foundation.RenderSessionTimingInspection
import com.viewcompose.ui.foundation.RenderNodeTimingCapture
import com.viewcompose.ui.foundation.RenderNodeTimingCaptureRequest
import com.viewcompose.ui.foundation.RenderNodeTimingPhase
import com.viewcompose.ui.foundation.RenderNodeTimingStartStatus
import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFailureObserved
import com.viewcompose.ui.foundation.RenderFrameCompleted
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.RenderSessionEnded
import com.viewcompose.ui.foundation.RenderSessionStarted
import com.viewcompose.ui.foundation.RenderSessionActivityChanged
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceCallSite

// DOCS_REGION_START(diagnostics-correlated-events)
fun renderDiagnosticsEventSample(): RenderDiagnostics {
    return RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = true,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event ->
            when (event) {
                is RenderFrameCompleted -> println(event.stats)
                is RenderFailureObserved -> println(event.failure.phase)
                is RenderSessionStarted,
                is RenderSessionActivityChanged,
                is RenderSessionEnded,
                -> println(event.context)
            }
        },
    )
}
// DOCS_REGION_END(diagnostics-correlated-events)

// DOCS_REGION_START(diagnostics-session-inspection)
fun renderSessionInspectionToolingSample(): RenderSessionInspectionTooling {
    var renderingActive = true
    var disposed = false
    val tooling = object : RenderSessionInspectionTooling {
        override fun inspectionPolicy(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
        ): RenderSessionInspectionPolicy = if (context.frameId == null) {
            RenderSessionInspectionPolicy.TrackSessionAndCaptureSources
        } else {
            RenderSessionInspectionPolicy.Ignore
        }

        override fun register(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
            sourceCandidates: List<List<UiSourceCallSite>>,
            nodeInspection: RenderSessionNodeInspection,
            diagnosticInspection: RenderSessionDiagnosticInspection,
            timingInspection: RenderSessionTimingInspection,
        ): RenderSessionInspectionRegistration {
            check(context.eventSequence == 0L)
            check(sourceCandidates.flatten().all { source -> source.lineNumber > 0 })
            // Only an explicit tooling request may traverse the mounted tree.
            check(nodeInspection.snapshot().nodes.size <= 512)
            val diagnosticSnapshot = diagnosticInspection.snapshot()
            check(diagnosticSnapshot.sessionId == context.sessionId)
            check(!diagnosticSnapshot.ended)
            return object : RenderSessionInspectionRegistration {
                override fun setRenderingActive(active: Boolean) {
                    renderingActive = active
                }

                override fun dispose() {
                    disposed = true
                }
            }
        }
    }
    check(renderingActive && !disposed)
    return tooling
}
// DOCS_REGION_END(diagnostics-session-inspection)

fun renderSessionNodeInspectionSample(nodeInspection: RenderSessionNodeInspection) {
    val snapshot = nodeInspection.snapshot()
    if (!snapshot.supported || snapshot.ended) return
    snapshot.nodes.firstOrNull()?.let { node ->
        // Resolve only synchronously on the platform render thread; retain no native target.
        val nativeTarget = node.platformTarget.resolve()
        println("${node.token.value}:${node.type}:${nativeTarget?.javaClass?.name}")
    }
}

// DOCS_REGION_START(diagnostics-node-timing)
fun renderSessionTimingInspectionSample(
    timingInspection: RenderSessionTimingInspection,
): RenderNodeTimingCapture? {
    val start = timingInspection.startCapture(
        RenderNodeTimingCaptureRequest(
            phases = setOf(
                RenderNodeTimingPhase.Composition,
                RenderNodeTimingPhase.Reconciliation,
                RenderNodeTimingPhase.Binding,
            ),
            maxFrames = 8,
        ),
    )
    if (start.status != RenderNodeTimingStartStatus.Started) return null
    return start.capture
}
// DOCS_REGION_END(diagnostics-node-timing)
