package com.viewcompose.host.android.runtime

import com.viewcompose.ui.foundation.RenderSessionInspectionPolicy
import com.viewcompose.ui.foundation.RenderSessionInspectionRegistration
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling
import com.viewcompose.ui.foundation.RenderSessionNodeInspection
import com.viewcompose.ui.foundation.RenderSessionTimingInspection
import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceCallSite
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidRenderSessionInspectionToolingDiscoveryTest {
    @Test
    fun `selects the only neutral inspection tooling service`() {
        val provider = TestInspectionTooling()

        assertSame(provider, selectSingleRenderSessionInspectionTooling(listOf(provider)))
    }

    @Test
    fun `absence and ambiguity both disable optional inspection tooling`() {
        assertNull(selectSingleRenderSessionInspectionTooling(emptyList()))
        assertNull(
            selectSingleRenderSessionInspectionTooling(
                listOf(TestInspectionTooling(), TestInspectionTooling()),
            ),
        )
    }

    private class TestInspectionTooling : RenderSessionInspectionTooling {
        override fun inspectionPolicy(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
        ): RenderSessionInspectionPolicy = RenderSessionInspectionPolicy.Ignore

        override fun register(
            container: RenderContainerHandle,
            context: RenderDiagnosticContext,
            sourceCandidates: List<List<UiSourceCallSite>>,
            nodeInspection: RenderSessionNodeInspection,
            timingInspection: RenderSessionTimingInspection,
        ): RenderSessionInspectionRegistration? = null
    }
}
