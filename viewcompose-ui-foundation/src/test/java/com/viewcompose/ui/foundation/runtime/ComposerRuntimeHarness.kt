package com.viewcompose.ui.foundation

/*
 * 测试工具职责：为 widget-core runtime 测试提供 Composer Runtime Harness 支撑，避免每个用例重复搭建组合运行时。
 * Test harness responsibility: provides Composer Runtime Harness support for widget-core runtime tests and avoids repeated composition runtime setup.
 */

import com.viewcompose.runtime.composition.ComposerLite
import kotlinx.coroutines.Dispatchers

internal class ComposerRuntimeHarness {
    private val composer = ComposerLite()

    fun <T> render(block: () -> T): T {
        if (!composer.hasPendingInvalidations()) {
            composer.requestRootRecompose()
        }
        val result = ComposerContext.withComposer(
            composer = composer,
            coroutineContext = Dispatchers.Unconfined,
        ) {
            composer.composeRoot(block)
        }
        composer.commitSideEffects()
        return result
    }

    fun dispose() {
        composer.dispose()
    }
}
