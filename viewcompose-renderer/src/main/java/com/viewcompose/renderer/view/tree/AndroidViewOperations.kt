package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException

/**
 * 统一执行 AndroidView 的 factory/update/reset/release 回调，并把异常标记回对应 VNode。
 * Runs AndroidView factory/update/reset/release callbacks consistently and tags failures with the
 * owning VNode for diagnostics.
 */
internal inline fun <T> VNode.runAndroidViewOperation(
    operation: AndroidViewOperation,
    block: () -> T,
): T {
    return try {
        block()
    } catch (error: Exception) {
        if (error is AndroidViewOperationException) {
            throw error
        }
        throw AndroidViewOperationException(
            operation = operation,
            nodeKey = key,
            cause = error,
        )
    }
}

/**
 * 将 AndroidView 回调中的异常展开为渲染提交失败列表，保留 suppressed/cause 链中的节点上下文。
 * Expands AndroidView callback errors into render-tree commit failures while preserving node context
 * from suppressed and causal exception chains.
 */
internal fun Throwable.toRenderTreeCommitFailures(
    fallbackNodeKey: Any?,
): List<RenderTreeCommitFailure> {
    val failures = mutableListOf<RenderTreeCommitFailure>()

    fun collect(error: Throwable) {
        val operationError = error as? AndroidViewOperationException
        failures += RenderTreeCommitFailure(
            operation = operationError?.operation,
            nodeKey = operationError?.nodeKey ?: fallbackNodeKey,
            cause = error,
        )
        error.suppressed.forEach(::collect)
    }

    collect(this)
    return failures
}
