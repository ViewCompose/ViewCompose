package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.foundation.RenderSessionSourceRegistration
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
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

fun renderSessionSourceToolingSample(): RenderSessionSourceTooling {
    var renderingActive = true
    var disposed = false
    val tooling = object : RenderSessionSourceTooling {
        override fun shouldCapture(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
        ): Boolean = context.frameId == null

        override fun register(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
            sourceCandidates: List<List<UiSourceCallSite>>,
        ): RenderSessionSourceRegistration {
            check(context.eventSequence == 0L)
            check(sourceCandidates.flatten().all { source -> source.lineNumber > 0 })
            return object : RenderSessionSourceRegistration {
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
