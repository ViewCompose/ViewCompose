package com.viewcompose.viewmodel

/*
 * 测试工具职责：为 viewmodel integration 测试提供 Widget Core Runtime Harness 支撑，避免每个用例重复搭建运行时。
 * Test harness responsibility: provides Widget Core Runtime Harness support for viewmodel integration tests and avoids repeated runtime setup.
 */

import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.buildVNodeTree
import java.lang.reflect.Method
import kotlin.coroutines.EmptyCoroutineContext

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

    fun <T> render(block: () -> T): T = compose(block)

    fun renderTree(block: UiTreeBuilder.() -> Unit) {
        compose {
            buildVNodeTree(block)
        }
    }

    fun dispose() {
        composer.dispose()
    }

    private fun <T> compose(block: () -> T): T {
        if (!composer.hasPendingInvalidations()) {
            composer.requestRootRecompose()
        }
        val callback = object : kotlin.jvm.functions.Function0<T> {
            override fun invoke(): T = composer.composeRoot(block)
        }
        @Suppress("UNCHECKED_CAST")
        val result = withComposer.invoke(
            composerContextInstance,
            composer,
            EmptyCoroutineContext,
            callback,
        ) as T
        composer.commitSideEffects()
        return result
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
