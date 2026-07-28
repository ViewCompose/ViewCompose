package com.viewcompose.widget.core

interface RenderSessionRuntime {
    fun requestRender()

    fun render()

    /**
     * Suspends frame-driven renders while a retained surface is not visible.
     *
     * Invalidations remain pending and are coalesced into one frame when rendering becomes active
     * again. Explicit [render] calls still render immediately.
     */
    fun setRenderingActive(active: Boolean) = Unit

    fun dispose()
}

fun interface RenderSessionRuntimeFactory {
    fun create(
        onRenderNow: () -> Unit,
        onDisposeNow: () -> Unit,
    ): RenderSessionRuntime
}
