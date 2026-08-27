package com.viewcompose.diagnostics.samples

import com.viewcompose.diagnostics.BoundedRenderFailureAggregator
import com.viewcompose.diagnostics.RenderFailureAggregationSnapshot
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel

/** Installs failure-only aggregation and returns its application-owned sink. */
// DOCS_REGION_START(diagnostics-failure-aggregation)
fun boundedFailureAggregationSample(
    install: (RenderDiagnostics) -> Unit,
): BoundedRenderFailureAggregator {
    val aggregator = BoundedRenderFailureAggregator()
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = aggregator,
    )
    install(diagnostics)
    return aggregator
}
// DOCS_REGION_END(diagnostics-failure-aggregation)

/** Copies and resets one safe window outside synchronous render-sink delivery. */
// DOCS_REGION_START(diagnostics-snapshot-export)
fun exportFailureAggregationSnapshotSample(
    aggregator: BoundedRenderFailureAggregator,
    forward: (RenderFailureAggregationSnapshot) -> Unit,
) {
    val completedWindow = aggregator.snapshotAndReset()
    forward(completedWindow)
}
// DOCS_REGION_END(diagnostics-snapshot-export)
