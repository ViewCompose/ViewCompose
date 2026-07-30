package com.viewcompose.shadow.android

import android.graphics.Color
import android.view.View
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InnerShadowBitmapRasterizerTest {
    @Test
    fun `inner shadow stays content sized and leaves center transparent`() {
        val rasterizer = InnerShadowBitmapRasterizer()
        val result = rasterizer.rasterize(
            widthPx = 40,
            heightPx = 30,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            spec = resolve(
                UiShadow(
                    color = Color.RED,
                    blurRadius = 0.dp,
                    spreadRadius = 4.dp,
                ),
            ),
        )

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(40, result.bitmap.width)
        assertEquals(30, result.bitmap.height)
        assertEquals(Color.RED, result.bitmap.getPixel(1, 15))
        assertEquals(Color.TRANSPARENT, result.bitmap.getPixel(20, 15))
    }

    @Test
    fun `positive horizontal offset places inner shadow on leading edge`() {
        val rasterizer = InnerShadowBitmapRasterizer()
        val result = requireNotNull(
            rasterizer.rasterize(
                widthPx = 40,
                heightPx = 30,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
                spec = resolve(
                    UiShadow(
                        color = Color.BLUE,
                        blurRadius = 0.dp,
                        offsetX = 5.dp,
                    ),
                ),
            ),
        )

        assertEquals(Color.BLUE, result.bitmap.getPixel(2, 15))
        assertEquals(Color.TRANSPARENT, result.bitmap.getPixel(37, 15))
    }

    @Test
    fun `same immutable inner shadow request is cached`() {
        val rasterizer = InnerShadowBitmapRasterizer()
        val spec = resolve(
            UiShadow(
                color = 0x44000000,
                blurRadius = 8.dp,
                offsetY = 3.dp,
            ),
        )

        val first = rasterizer.rasterize(80, 40, View.LAYOUT_DIRECTION_LTR, spec)
        val second = rasterizer.rasterize(80, 40, View.LAYOUT_DIRECTION_LTR, spec)

        assertSame(first, second)
        assertEquals(1, rasterizer.stats().misses)
        assertEquals(1, rasterizer.stats().hits)
    }

    @Test
    fun `invalid and oversized inner shadow rasters are skipped`() {
        val rasterizer = InnerShadowBitmapRasterizer(
            maxCacheBytes = 1024,
            maxRasterBytes = 4096,
        )
        val spec = resolve(UiShadow(blurRadius = 4.dp))

        assertNull(rasterizer.rasterize(0, 20, View.LAYOUT_DIRECTION_LTR, spec))
        assertNull(rasterizer.rasterize(100, 100, View.LAYOUT_DIRECTION_LTR, spec))
        assertEquals(1, rasterizer.stats().oversizedSkips)
    }

    private fun resolve(vararg shadows: UiShadow): ResolvedInnerShadowSpec {
        return InnerShadowSpecResolver.resolve(
            elements = listOf(
                InnerShadowModifierElement(
                    shadows = shadows.toList(),
                ),
            ),
            defaultShape = UiShape.rounded(0.dp),
            density = UiDensity.Default,
        )
    }
}
