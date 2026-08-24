package com.viewcompose.host.android.runtime

import com.viewcompose.ui.foundation.RenderSessionInspectionPolicy
import com.viewcompose.ui.foundation.RenderSessionInspectionRegistration
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling
import com.viewcompose.ui.foundation.RenderSessionDiagnosticInspection
import com.viewcompose.ui.foundation.RenderSessionNodeInspection
import com.viewcompose.ui.foundation.RenderSessionTimingInspection
import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceCallSite
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidRenderSessionInspectionToolingRegistryTest {
    @Test
    fun `selects one installed implementation without discovery`() {
        val slot = AndroidRenderSessionInspectionToolingSlot()
        val provider = TestInspectionTooling()

        slot.install(provider)

        assertSame(provider, slot.resolve())
        assertSame(provider, slot.resolve())
    }

    @Test
    fun `absence freezes to no tooling and rejects later installation`() {
        val warnings = mutableListOf<String>()
        val slot = AndroidRenderSessionInspectionToolingSlot(warnings::add)

        assertNull(slot.resolve())
        slot.install(TestInspectionTooling())

        assertNull(slot.resolve())
        assertTrue(warnings.single().contains("after the runtime selection had been frozen"))
    }

    @Test
    fun `same instance is idempotent and distinct implementations disable the port`() {
        val warnings = mutableListOf<String>()
        val slot = AndroidRenderSessionInspectionToolingSlot(warnings::add)
        val first = TestInspectionTooling()

        slot.install(first)
        slot.install(first)
        slot.install(TestInspectionTooling())

        assertNull(slot.resolve())
        assertTrue(warnings.single().contains("disabling all of them"))
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
            diagnosticInspection: RenderSessionDiagnosticInspection,
            timingInspection: RenderSessionTimingInspection,
        ): RenderSessionInspectionRegistration? = null
    }
}
