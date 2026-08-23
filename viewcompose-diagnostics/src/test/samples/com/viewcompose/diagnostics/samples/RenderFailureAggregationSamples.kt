package com.viewcompose.diagnostics.samples

import com.viewcompose.diagnostics.BoundedRenderFailureAggregator
import com.viewcompose.diagnostics.RenderFailureAggregationSnapshot
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel

/** Installs failure-only aggregation and forwards a sanitized immutable snapshot. */
fun boundedFailureAggregationSample(
    forward: (RenderFailureAggregationSnapshot) -> Unit,
): RenderDiagnostics {
    val aggregator = BoundedRenderFailureAggregator()
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = aggregator,
    )

    // Invoke from application-owned scheduling, outside synchronous render-sink delivery.
    forward(aggregator.snapshotAndReset())
    return diagnostics
}
