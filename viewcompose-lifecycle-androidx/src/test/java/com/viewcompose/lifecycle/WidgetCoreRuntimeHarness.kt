package com.viewcompose.lifecycle

/*
 * 测试工具职责：为 lifecycle integration 测试提供 Widget Core Runtime Harness 支撑，避免每个用例重复搭建运行时。
 * Test harness responsibility: provides Widget Core Runtime Harness support for lifecycle integration tests and avoids repeated runtime setup.
 */

import com.viewcompose.runtime.State
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.node.VNode
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlinx.coroutines.Dispatchers

internal class WidgetCoreRuntimeHarness {
    private val composer = ComposerLite()
    private val composerContextClass = Class.forName("com.viewcompose.ui.foundation.ComposerContext")
    private val composerContextInstance: Any = requireNotNull(
        composerContextClass.getField("INSTANCE").get(null),
    )
    private val withComposer = composerContextClass.findMethodPrefix(
        prefix = "withComposer",
        paramCount = 3,
    )

    fun <T> render(
        block: () -> State<T>,
    ): State<T> {
        return compose(block)
    }

    fun renderTree(
        block: UiTreeBuilder.() -> Unit,
    ) {
        compose {
            buildVNodeTree(block)
        }
    }

    fun prepareTree(
        block: UiTreeBuilder.() -> Unit,
    ): ComposerLite.PreparedComposition<List<VNode>> {
        composer.requestRootRecompose()
        return inComposerContext {
            composer.prepareRoot {
                buildVNodeTree(block)
            }
        }
    }

    fun commitSideEffects() {
        composer.commitSideEffects()
    }

    fun dispose() {
        composer.dispose()
    }

    private fun <T> compose(
        block: () -> T,
    ): T {
        if (!composer.hasPendingInvalidations()) {
            composer.requestRootRecompose()
        }
        val result = inComposerContext {
            composer.composeRoot(block)
        }
        composer.commitSideEffects()
        return result
    }

    private fun <T> inComposerContext(block: () -> T): T {
        val callback = object : kotlin.jvm.functions.Function0<T> {
            override fun invoke(): T = block()
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            withComposer.invoke(
                composerContextInstance,
                composer,
                Dispatchers.Unconfined,
                callback,
            ) as T
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun Class<*>.findMethodPrefix(
        prefix: String,
        paramCount: Int,
    ): Method {
        val method = methods.firstOrNull { candidate ->
            candidate.name.startsWith(prefix) && candidate.parameterCount == paramCount
        } ?: declaredMethods.firstOrNull { candidate ->
            candidate.name.startsWith(prefix) && candidate.parameterCount == paramCount
        } ?: error("Method with prefix '$prefix' and $paramCount params not found in $name")
        method.isAccessible = true
        return method
    }
}
