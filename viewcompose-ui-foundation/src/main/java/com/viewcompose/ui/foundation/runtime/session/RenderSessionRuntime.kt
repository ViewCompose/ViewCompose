package com.viewcompose.ui.foundation

/** Scheduling policy that coalesces, defers, or immediately runs [RenderSession] requests. */
interface RenderSessionRuntime {
    /**
     * Requests one frame to render when allowed by the runtime policy.
     */
    fun requestRender()

    /**
     * Runs one synchronous render immediately.
     */
    fun render()

    /**
     * Suspends frame-driven renders while a retained surface is not visible.
     *
     * Invalidations remain pending and are coalesced into one frame when rendering becomes active
     * again. Explicit [render] calls still render immediately.
     */
    fun setRenderingActive(active: Boolean) = Unit

    /**
     * Disposes the runtime and stops future rendering.
     */
    fun dispose()
}

/** Platform factory for creating one scheduling runtime per [RenderSession]. */
fun interface RenderSessionRuntimeFactory {
    /**
     * Creates a runtime that delegates synchronous work to [onRenderNow] and cleanup to [onDisposeNow].
     *
     * Implementations must call [onDisposeNow] at most once and must not call [onRenderNow] after
     * disposal completes.
     */
    fun create(
        onRenderNow: () -> Unit,
        onDisposeNow: () -> Unit,
    ): RenderSessionRuntime
}
