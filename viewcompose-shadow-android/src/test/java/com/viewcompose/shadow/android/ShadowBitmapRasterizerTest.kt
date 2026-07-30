package com.viewcompose.shadow.android

import android.view.View
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShadowBitmapRasterizerTest {
    private val density = UiDensity(
        density = 2f,
        fontScale = 1f,
    )

    @Test
    fun `same immutable request reuses the rasterized bitmap`() {
        val rasterizer = ShadowBitmapRasterizer()
        val spec = resolve(
            UiShadow(
                color = 0x33000000,
                blurRadius = 8.dp,
                offsetY = 4.dp,
            ),
        )

        val first = rasterizer.rasterize(
            widthPx = 120,
            heightPx = 60,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            spec = spec,
        )
        val second = rasterizer.rasterize(
            widthPx = 120,
            heightPx = 60,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            spec = spec,
        )

        assertNotNull(first)
        assertSame(first, second)
        assertEquals(1, rasterizer.stats().misses)
        assertEquals(1, rasterizer.stats().hits)
    }

    @Test
    fun `multi layer raster reserves overflow without changing content size`() {
        val rasterizer = ShadowBitmapRasterizer()
        val spec = ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = listOf(
                        UiShadow(
                            color = 0x22000000,
                            blurRadius = 4.dp,
                            offsetY = 2.dp,
                        ),
                        UiShadow(
                            color = 0x18000000,
                            blurRadius = 12.dp,
                            spreadRadius = 2.dp,
                            offsetY = 8.dp,
                        ),
                    ),
                ),
            ),
            defaultShape = UiShape.rounded(12.dp),
            density = density,
        )

        val result = rasterizer.rasterize(
            widthPx = 100,
            heightPx = 50,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            spec = spec,
        )

        assertNotNull(result)
        requireNotNull(result)
        assertTrue(result.bitmap.width > 100)
        assertTrue(result.bitmap.height > 50)
        assertTrue(result.drawOffsetXPx < 0f)
        assertTrue(result.drawOffsetYPx < 0f)
        assertEquals(2, spec.layerCount)
    }

    @Test
    fun `invalid bounds and oversized raster are skipped deterministically`() {
        val rasterizer = ShadowBitmapRasterizer(
            maxCacheBytes = 1024,
            maxRasterBytes = 4096,
        )
        val spec = resolve(UiShadow(blurRadius = 16.dp))

        assertNull(
            rasterizer.rasterize(
                widthPx = 0,
                heightPx = 50,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
                spec = spec,
            ),
        )
        assertNull(
            rasterizer.rasterize(
                widthPx = 200,
                heightPx = 200,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
                spec = spec,
            ),
        )
        assertEquals(1, rasterizer.stats().oversizedSkips)
    }

    private fun resolve(vararg shadows: UiShadow): ResolvedShadowSpec {
        return ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = shadows.toList(),
                ),
            ),
            defaultShape = UiShape.rounded(10.dp),
            density = density,
        )
    }
}
