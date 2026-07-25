package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.ComposerLite

internal class ComposerRuntimeHarness {
    private val composer = ComposerLite()

    fun <T> render(block: () -> T): T {
        if (!composer.hasPendingInvalidations()) {
            composer.requestRootRecompose()
        }
        val result = ComposerContext.withComposer(composer) {
            composer.composeRoot(block)
        }
        composer.commitSideEffects()
        return result
    }

    fun dispose() {
        composer.dispose()
    }
}
