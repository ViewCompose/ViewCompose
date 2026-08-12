package com.viewcompose.ui.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionEffectContextTest {
    @Test
    fun `effect callback cannot consume an unrelated active provider`() {
        val local = uiLocalOf(
            debugName = "TestLocal",
            defaultFactory = { "default" },
        )
        var error: Throwable? = null

        LocalContext.provide(local.holder, "unrelated") {
            error = runCatching {
                CompositionEffectContext.run {
                    UiLocals.current(local)
                }
            }.exceptionOrNull()
        }

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("UiLocal 'TestLocal'"))
    }

    @Test
    fun `nested and throwing markers restore the calling thread`() {
        val local = uiLocalOf(
            debugName = "RestoredLocal",
            defaultFactory = { "default" },
        )

        CompositionEffectContext.run {
            CompositionEffectContext.run { Unit }
        }
        runCatching {
            CompositionEffectContext.run {
                error("callback failed")
            }
        }

        assertEquals("default", UiLocals.current(local))
    }
}
