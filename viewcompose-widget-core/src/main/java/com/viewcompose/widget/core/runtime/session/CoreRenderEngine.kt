package com.viewcompose.widget.core

import android.view.ViewGroup
import com.viewcompose.ui.node.VNode

/**
 * host-android 在运行时注册的 Android 渲染引擎契约。
 * Android rendering engine contract registered by host-android at runtime.
 *
 * widget-core 保留该窄接口，以避免直接依赖 renderer 模块。
 * widget-core keeps this narrow contract to avoid a direct renderer dependency.
 */
interface CoreRenderEngine {
    /**
     * 将 VNode 树渲染到容器，并返回新的 mounted 节点和可选诊断/提交工作。
     * Renders the VNode tree into the container and returns new mounted nodes plus optional diagnostics/commit work.
     */
    fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame

    /**
     * 释放已经挂载的 renderer 节点。
     * Disposes renderer nodes that are already mounted.
     */
    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<Any>,
    ): List<CoreRenderCommitFailure>
}

/**
 * 一帧 renderer 输出，native commit effect 会在 composition commit 后执行。
 * Renderer output for one frame; native commit effects run after composition commit.
 */
data class CoreRenderFrame(
    val mountedNodes: List<Any>,
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
    val commitEffects: List<CoreRenderCommitEffect> = emptyList(),
    val commitFailures: List<CoreRenderCommitFailure> = emptyList(),
)

/**
 * renderer 延迟到 session commit 阶段执行的 native 操作。
 * Native operation delayed by the renderer until the session commit phase.
 */
data class CoreRenderCommitEffect(
    val operation: RenderFailureOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

/**
 * renderer 在渲染或释放过程中捕获的 native commit 失败。
 * Native commit failure captured by the renderer while rendering or disposing.
 */
data class CoreRenderCommitFailure(
    val operation: RenderFailureOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)
