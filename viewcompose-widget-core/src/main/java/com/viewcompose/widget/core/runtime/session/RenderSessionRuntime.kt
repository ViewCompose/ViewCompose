package com.viewcompose.widget.core

/**
 * RenderSession 的调度运行时，决定请求渲染如何合并、延迟或立即执行。
 * Scheduling runtime for RenderSession, deciding how render requests are coalesced, deferred, or run immediately.
 */
interface RenderSessionRuntime {
    /**
     * 请求在运行时策略允许时渲染一帧。
     * Requests one frame to render when allowed by the runtime policy.
     */
    fun requestRender()

    /**
     * 立即执行一次同步渲染。
     * Runs one synchronous render immediately.
     */
    fun render()

    /**
     * 当保留的 surface 不可见时暂停帧驱动渲染。
     * Suspends frame-driven renders while a retained surface is not visible.
     *
     * invalidation 会保持 pending，并在渲染重新激活时合并成一帧。显式 [render] 仍会立即渲染。
     * Invalidations remain pending and are coalesced into one frame when rendering becomes active
     * again. Explicit [render] calls still render immediately.
     */
    fun setRenderingActive(active: Boolean) = Unit

    /**
     * 释放运行时并停止后续渲染。
     * Disposes the runtime and stops future rendering.
     */
    fun dispose()
}

/**
 * 创建 RenderSessionRuntime 的平台工厂。
 * Platform factory for creating a RenderSessionRuntime.
 */
fun interface RenderSessionRuntimeFactory {
    fun create(
        onRenderNow: () -> Unit,
        onDisposeNow: () -> Unit,
    ): RenderSessionRuntime
}
