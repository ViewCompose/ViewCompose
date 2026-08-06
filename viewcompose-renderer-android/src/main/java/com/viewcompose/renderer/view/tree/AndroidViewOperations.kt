package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException

/**
 * Centralizes AndroidView factory, update, reset, and release callbacks and associates failures with their VNode.
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
 * Expands AndroidView callback errors into render-commit failures while preserving node context across suppressed and cause chains.
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
