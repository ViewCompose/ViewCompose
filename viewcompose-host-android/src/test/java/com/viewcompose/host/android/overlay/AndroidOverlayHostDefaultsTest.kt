package com.viewcompose.host.android.overlay

import com.viewcompose.ui.foundation.OverlayHostDefaults
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOverlayHostDefaultsTest {
    @Test
    fun `zero providers selects no integration`() {
        assertNull(selectSingleAndroidOverlayHostProvider(emptyList()))
    }

    @Test
    fun `one provider is selected without classpath ordering`() {
        val provider = provider()

        assertSame(provider, selectSingleAndroidOverlayHostProvider(listOf(provider)))
    }

    @Test
    fun `duplicate providers fail with both backend identities`() {
        val first = provider()
        val second = provider()

        val failure = runCatching {
            selectSingleAndroidOverlayHostProvider(listOf(first, second))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("Multiple Android overlay host providers"))
    }

    private fun provider(): AndroidOverlayHostFactoryProvider {
        return AndroidOverlayHostFactoryProvider { OverlayHostDefaults.noOp }
    }
}
