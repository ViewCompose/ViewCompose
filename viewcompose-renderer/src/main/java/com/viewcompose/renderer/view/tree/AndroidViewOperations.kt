package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.AndroidViewOperationException

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
