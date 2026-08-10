package com.viewcompose.renderer.interop

import com.viewcompose.ui.tooling.UiSourceSessionContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidContractAdaptersTest {
    @Test
    fun `ordinary child containers default to content source role`() {
        val handle = Any().asRenderContainerHandle() as UiSourceSessionContainerHandle

        assertEquals(UiSourceSessionRole.Content, handle.sourceSessionRole)
    }

    @Test
    fun `pager child containers retain page source role`() {
        val handle = Any().asRenderContainerHandle(
            UiSourceSessionRole.Page,
        ) as UiSourceSessionContainerHandle

        assertEquals(UiSourceSessionRole.Page, handle.sourceSessionRole)
    }
}
