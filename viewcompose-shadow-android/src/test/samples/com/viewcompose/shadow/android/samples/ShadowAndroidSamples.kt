package com.viewcompose.shadow.android.samples

import android.view.View
import com.viewcompose.shadow.android.InnerShadowSpecResolver
import com.viewcompose.shadow.android.ResolvedInnerShadowSpec
import com.viewcompose.shadow.android.ResolvedShadowSpec
import com.viewcompose.shadow.android.ShadowBitmapRasterizer
import com.viewcompose.shadow.android.ShadowRenderBackendDecision
import com.viewcompose.shadow.android.ShadowRenderBackendSelector
import com.viewcompose.shadow.android.ShadowRenderPolicy
import com.viewcompose.shadow.android.ShadowSpecResolver
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp

fun resolveShadowSpecSample(): ResolvedShadowSpec {
    return ShadowSpecResolver.resolve(
        elements = listOf(
            DropShadowModifierElement(
                shadows = listOf(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 12.dp,
                        offsetY = 4.dp,
                    ),
                ),
            ),
        ),
        defaultShape = UiShape.rounded(16.dp),
        density = UiDensity(density = 3f, fontScale = 1f),
    )
}

fun resolveInnerShadowSpecSample(): ResolvedInnerShadowSpec {
    return InnerShadowSpecResolver.resolve(
        elements = listOf(
            InnerShadowModifierElement(
                shadows = listOf(UiShadow(blurRadius = 6.dp, offsetY = 2.dp)),
            ),
        ),
        defaultShape = UiShape.rounded(12.dp),
        density = UiDensity(density = 3f, fontScale = 1f),
    )
}

fun rasterizeShadowSample() {
    val rasterizer = ShadowBitmapRasterizer(
        maxCacheBytes = 2 * 1024 * 1024,
        maxRasterBytes = 8 * 1024 * 1024,
    )
    val raster = rasterizer.rasterize(
        widthPx = 240,
        heightPx = 96,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        spec = resolveShadowSpecSample(),
    )
    // Draw raster?.bitmap at raster.drawOffsetXPx/drawOffsetYPx; do not recycle it.
    check(raster != null)
}

// DOCS_REGION_START(shadow-backend-selection)
fun selectShadowBackendSample(): ShadowRenderBackendDecision {
    return ShadowRenderBackendSelector.select(
        policy = ShadowRenderPolicy.RenderNodeDisplayList,
        sdkInt = 35,
        hardwareAccelerated = true,
    )
}
// DOCS_REGION_END(shadow-backend-selection)
