package com.viewcompose.renderer.interop

import com.viewcompose.ui.node.nativeContainer
import org.junit.Assert.assertSame
import org.junit.Test

class AndroidContractAdaptersTest {
    @Test
    fun `render container preserves the native owner`() {
        val nativeContainer = Any()
        val handle = nativeContainer.asRenderContainerHandle()

        assertSame(nativeContainer, handle.nativeContainer)
    }
}
