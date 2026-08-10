package com.viewcompose.renderer.view.shape

/*
 * 测试职责：覆盖 renderer view/shape 中的 Ui Shape Android Bridge 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Ui Shape Android Bridge behavior in renderer view/shape and guards render and patch contracts against regressions.
 */

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.ColorStop
import com.viewcompose.graphics.core.Offset
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiShapeDrawableTest {
    private val bounds = RectF(0f, 0f, 200f, 100f)
    private val shape = UiShape(
        topStart = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(12.dp)),
        topEnd = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Relative(0.5f)),
        bottomEnd = UiCorner(UiCornerFamily.Cut, UiCornerSize.Absolute(20.dp)),
        bottomStart = UiCorner(UiCornerFamily.Rounded, UiCornerSize.Absolute(4.dp)),
    )

    @Test
    fun `resolves logical shape in ltr`() {
        val corners = shape.resolveCorners(View.LAYOUT_DIRECTION_LTR, UiDensity.Default, bounds)

        assertEquals(UiCornerFamily.Cut, corners.topLeft.family)
        assertEquals(12f, corners.topLeft.size, 0.001f)
        assertEquals(UiCornerFamily.Rounded, corners.topRight.family)
        assertEquals(50f, corners.topRight.size, 0.001f)
        assertEquals(UiCornerFamily.Cut, corners.bottomRight.family)
        assertEquals(20f, corners.bottomRight.size, 0.001f)
        assertEquals(4f, corners.bottomLeft.size, 0.001f)
    }

    @Test
    fun `swaps logical start and end in rtl`() {
        val corners = shape.resolveCorners(View.LAYOUT_DIRECTION_RTL, UiDensity.Default, bounds)

        assertEquals(UiCornerFamily.Rounded, corners.topLeft.family)
        assertEquals(50f, corners.topLeft.size, 0.001f)
        assertEquals(UiCornerFamily.Cut, corners.topRight.family)
        assertEquals(12f, corners.topRight.size, 0.001f)
        assertEquals(4f, corners.bottomRight.size, 0.001f)
        assertEquals(20f, corners.bottomLeft.size, 0.001f)
    }

    @Test
    fun `relative full shape renders circular quarter arcs`() {
        val bitmap = drawShape(
            shape = UiShape.roundedRelative(0.5f),
            width = 80,
            height = 40,
        ) { drawable ->
            drawable.setFillColor(Color.WHITE)
        }

        val cornerAlpha = Color.alpha(bitmap.getPixel(5, 5))
        assertTrue("Expected circular corner coverage, alpha=$cornerAlpha", cornerAlpha <= 64)
        assertEquals(255, Color.alpha(bitmap.getPixel(40, 1)))
    }

    @Test
    fun `stroke remains fully inside drawable bounds`() {
        val bitmap = drawShape(
            shape = UiShape.roundedRelative(0.5f),
            width = 80,
            height = 40,
        ) { drawable ->
            drawable.setFillColor(Color.TRANSPARENT)
            drawable.setStroke(width = 1f, color = Color.BLACK)
        }

        listOf(
            bitmap.getPixel(40, 0),
            bitmap.getPixel(79, 20),
            bitmap.getPixel(40, 39),
            bitmap.getPixel(0, 20),
        ).forEach { pixel ->
            assertTrue(Color.alpha(pixel) >= 240)
        }
    }

    @Test
    fun `changing shape rebuilds drawable geometry`() {
        val drawable = UiShapeDrawable(
            shape = UiShape.rounded(0.dp),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
        }
        drawable.draw(Canvas(Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)))

        drawable.setShape(UiShape.roundedRelative(0.5f))
        val next = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(next))

        val cornerAlpha = Color.alpha(next.getPixel(5, 5))
        assertTrue("Expected rebuilt circular corner, alpha=$cornerAlpha", cornerAlpha <= 64)
    }

    @Test
    fun `continuous corner keeps center filled and corner outside`() {
        val bitmap = drawShape(
            shape = UiShape.continuous(20.dp),
            width = 80,
            height = 40,
        ) { drawable ->
            drawable.setFillColor(Color.WHITE)
        }

        assertTrue(Color.alpha(bitmap.getPixel(1, 1)) <= 32)
        assertEquals(255, Color.alpha(bitmap.getPixel(40, 1)))
        assertEquals(255, Color.alpha(bitmap.getPixel(40, 20)))
    }

    @Test
    fun `gradient fill is cached against drawable bounds and clipped by shape`() {
        val bitmap = drawShape(
            shape = UiShape.continuous(12.dp),
            width = 80,
            height = 40,
        ) { drawable ->
            drawable.setFill(
                Brush.LinearGradient(
                    from = Offset(0f, 0f),
                    to = Offset(80f, 0f),
                    colorStops = listOf(
                        ColorStop(0f, Color.RED),
                        ColorStop(1f, Color.BLUE),
                    ),
                ),
            )
        }

        val left = bitmap.getPixel(12, 20)
        val right = bitmap.getPixel(68, 20)
        assertTrue(
            "Expected red-dominant left pixel, value=${Integer.toHexString(left)}",
            Color.red(left) > Color.blue(left),
        )
        assertTrue(
            "Expected blue-dominant right pixel, value=${Integer.toHexString(right)}",
            Color.blue(right) > Color.red(right),
        )
        assertTrue(Color.alpha(bitmap.getPixel(1, 1)) <= 32)
    }

    private fun drawShape(
        shape: UiShape,
        width: Int,
        height: Int,
        configure: (UiShapeDrawable) -> Unit,
    ): Bitmap {
        val drawable = UiShapeDrawable(
            shape = shape,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, width, height)
            configure(this)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.draw(Canvas(bitmap))
        }
    }
}
