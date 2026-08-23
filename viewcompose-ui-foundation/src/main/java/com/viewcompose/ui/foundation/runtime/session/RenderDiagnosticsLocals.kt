package com.viewcompose.ui.foundation

/** Private logical owner propagated only through immutable Local snapshots. */
internal data class RenderDiagnosticParent(
    val sessionId: RenderSessionTraceId,
    val diagnostics: RenderDiagnostics?,
)

internal val LocalRenderDiagnosticParent = LocalValue<RenderDiagnosticParent>(
    debugName = "RenderDiagnosticParent",
    debugValueFormatter = { parent -> "session=${parent.sessionId.value}" },
) {
    error("RenderDiagnosticParent has no default value.")
}

internal fun LocalSnapshot.renderDiagnosticParentOrNull(): RenderDiagnosticParent? {
    @Suppress("UNCHECKED_CAST")
    return values[LocalRenderDiagnosticParent] as? RenderDiagnosticParent
}

internal fun UiLocalSnapshot.renderDiagnosticParentOrNull(): RenderDiagnosticParent? {
    return delegate.renderDiagnosticParentOrNull()
}
