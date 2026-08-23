package com.viewcompose.host.android.runtime

import com.viewcompose.ui.foundation.RenderSessionSourceRegistration
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceCallSite
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidRenderSessionSourceToolingDiscoveryTest {
    @Test
    fun `selects the only neutral source tooling service`() {
        val provider = TestSourceTooling()

        assertSame(provider, selectSingleRenderSessionSourceTooling(listOf(provider)))
    }

    @Test
    fun `absence and ambiguity both disable optional source tooling`() {
        assertNull(selectSingleRenderSessionSourceTooling(emptyList()))
        assertNull(
            selectSingleRenderSessionSourceTooling(
                listOf(TestSourceTooling(), TestSourceTooling()),
            ),
        )
    }

    private class TestSourceTooling : RenderSessionSourceTooling {
        override fun shouldCapture(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
        ): Boolean = false

        override fun register(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
            sourceCandidates: List<List<UiSourceCallSite>>,
        ): RenderSessionSourceRegistration? = null
    }
}
