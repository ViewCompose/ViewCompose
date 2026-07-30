package com.viewcompose.shadow.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowRenderBackendTest {
    @Test
    fun `auto keeps exact bitmap until benchmark evidence changes the default`() {
        val decision = ShadowRenderBackendSelector.select(
            policy = ShadowRenderPolicy.Auto,
            sdkInt = 36,
            hardwareAccelerated = true,
        )

        assertEquals(ShadowRenderBackend.Bitmap, decision.backend)
        assertEquals(
            ShadowRenderDecisionReason.AutoExactPendingEvidence,
            decision.reason,
        )
    }

    @Test
    fun `explicit render node requires api 29 and hardware canvas`() {
        val oldApi = ShadowRenderBackendSelector.select(
            policy = ShadowRenderPolicy.RenderNodeDisplayList,
            sdkInt = 28,
            hardwareAccelerated = true,
        )
        val software = ShadowRenderBackendSelector.select(
            policy = ShadowRenderPolicy.RenderNodeDisplayList,
            sdkInt = 36,
            hardwareAccelerated = false,
        )
        val eligible = ShadowRenderBackendSelector.select(
            policy = ShadowRenderPolicy.RenderNodeDisplayList,
            sdkInt = 36,
            hardwareAccelerated = true,
        )

        assertEquals(ShadowRenderBackend.Bitmap, oldApi.backend)
        assertEquals(
            ShadowRenderDecisionReason.RenderNodeApiUnavailable,
            oldApi.reason,
        )
        assertEquals(ShadowRenderBackend.Bitmap, software.backend)
        assertEquals(ShadowRenderDecisionReason.SoftwareCanvas, software.reason)
        assertEquals(
            ShadowRenderBackend.RenderNodeDisplayList,
            eligible.backend,
        )
        assertEquals(
            ShadowRenderDecisionReason.ExplicitRenderNode,
            eligible.reason,
        )
    }

    @Test
    fun `wire policy parser fails safe to auto`() {
        assertEquals(
            ShadowRenderPolicy.ExactBitmap,
            ShadowRenderPolicy.fromWireValue("exact_bitmap"),
        )
        assertEquals(
            ShadowRenderPolicy.RenderNodeDisplayList,
            ShadowRenderPolicy.fromWireValue("render_node"),
        )
        assertEquals(
            ShadowRenderPolicy.Auto,
            ShadowRenderPolicy.fromWireValue("unknown"),
        )
    }
}
