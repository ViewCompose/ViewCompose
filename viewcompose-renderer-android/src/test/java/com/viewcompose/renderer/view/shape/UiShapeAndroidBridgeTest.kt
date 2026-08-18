package com.viewcompose.renderer.view.shape

/*
 * 测试职责：覆盖 renderer view/shape 中的 Ui Shape Android Bridge 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Ui Shape Android Bridge behavior in renderer view/shape and guards render and patch contracts against regressions.
 */

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    fun `uniform rounded shape uses native round rect draw and outline`() {
        val drawable = UiShapeDrawable(
            shape = UiShape.rounded(12.dp),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
        }
        val canvas = RecordingCanvas()

        drawable.draw(canvas)
        val outline = Outline()
        drawable.getOutline(outline)

        assertEquals(1, canvas.roundRects.size)
        assertEquals(0, canvas.pathDrawCount)
        assertEquals(RectF(0f, 0f, 80f, 40f), canvas.roundRects.single().frame)
        assertEquals(12f, canvas.roundRects.single().radius, 0.001f)
        assertEquals(12f, outline.radius, 0.001f)
        assertEquals(Rect(0, 0, 80, 40), Rect().also { assertTrue(outline.getRect(it)) })
        assertFalse(drawable.hasFillPathResource)
        assertFalse(drawable.hasStrokeResources)
    }

    @Test
    fun `shape drawing resources follow actual geometry and border needs`() {
        val drawable = UiShapeDrawable(
            shape = UiShape.rounded(12.dp),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
        }

        drawable.draw(RecordingCanvas())
        assertFalse(drawable.hasFillPathResource)
        assertFalse(drawable.hasStrokeResources)

        drawable.setShape(shape)
        drawable.draw(RecordingCanvas())
        assertTrue(drawable.hasFillPathResource)
        assertFalse(drawable.hasStrokeResources)

        drawable.setStroke(width = 4f, color = Color.BLACK)
        drawable.draw(RecordingCanvas())
        assertTrue(drawable.hasStrokeResources)

        drawable.setStroke(width = 0f, color = Color.BLACK)
        drawable.draw(RecordingCanvas())
        assertFalse(drawable.hasStrokeResources)
    }

    @Test
    fun `outline provider retains no drawable resources for a uniform shape`() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 80, 48)
        }
        val provider = UiShapeOutlineProvider(
            shape = UiShape.rounded(12.dp),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
            topInset = 4,
            bottomInset = 4,
        )
        val outline = Outline()

        provider.getOutline(view, outline)

        assertFalse(provider.hasPathResource)
        assertEquals(12f, outline.radius, 0.001f)
        assertEquals(Rect(0, 4, 80, 44), Rect().also { assertTrue(outline.getRect(it)) })
    }

    @Test
    fun `outline provider allocates a path only for non uniform geometry`() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 80, 48)
        }
        val provider = UiShapeOutlineProvider(
            shape = shape,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
            density = UiDensity.Default,
            topInset = 4,
            bottomInset = 4,
        )

        provider.getOutline(view, Outline())

        assertTrue(provider.hasPathResource)
    }

    @Test
    fun `public solid drawable factory preserves shape color direction and density`() {
        val expectedShape = UiShape.rounded(17.dp)
        val expectedDensity = UiDensity(density = 2f, fontScale = 1f)

        val drawable = AndroidUiShapeDrawables.solid(
            shape = expectedShape,
            color = 0xFF123456.toInt(),
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
            density = expectedDensity,
        ) as UiShapeDrawable

        assertEquals(expectedShape, drawable.currentShape)
        assertEquals(0xFF123456.toInt(), drawable.currentFillColor)
        drawable.setBounds(0, 0, 100, 60)
        val outline = Outline()
        drawable.getOutline(outline)
        assertEquals(30f, outline.radius, 0.001f)
    }

    @Test
    fun `non uniform and continuous shapes keep the generic path`() {
        val nonUniform = UiShapeDrawable(
            shape = shape,
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
        }
        val continuous = UiShapeDrawable(
            shape = UiShape.continuous(12.dp),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
        }
        val canvas = RecordingCanvas()

        nonUniform.draw(canvas)
        continuous.draw(canvas)

        assertEquals(0, canvas.roundRects.size)
        assertEquals(2, canvas.pathDrawCount)
    }

    @Test
    fun `uniform rounded stroke uses inset native round rect geometry`() {
        val drawable = UiShapeDrawable(
            shape = UiShape.roundedRelative(0.5f),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
            setStroke(width = 4f, color = Color.BLACK)
        }
        val canvas = RecordingCanvas()

        drawable.draw(canvas)

        assertEquals(2, canvas.roundRects.size)
        assertEquals(0, canvas.pathDrawCount)
        assertEquals(RectF(0f, 0f, 80f, 40f), canvas.roundRects[0].frame)
        assertEquals(20f, canvas.roundRects[0].radius, 0.001f)
        assertEquals(Paint.Style.FILL, canvas.roundRects[0].style)
        assertEquals(RectF(2f, 2f, 78f, 38f), canvas.roundRects[1].frame)
        assertEquals(18f, canvas.roundRects[1].radius, 0.001f)
        assertEquals(Paint.Style.STROKE, canvas.roundRects[1].style)
    }

    @Test
    fun `late stroke inherits drawable alpha and color filter`() {
        val colorFilter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        val drawable = UiShapeDrawable(
            shape = UiShape.rounded(12.dp),
            layoutDirection = View.LAYOUT_DIRECTION_LTR,
            density = UiDensity.Default,
        ).apply {
            setBounds(0, 0, 80, 40)
            setFillColor(Color.WHITE)
            alpha = 96
            setColorFilter(colorFilter)
            setStroke(width = 4f, color = Color.BLACK)
        }
        val canvas = RecordingCanvas()

        drawable.draw(canvas)

        assertEquals(listOf(96, 96), canvas.roundRects.map(RoundRectDraw::alpha))
        canvas.roundRects.forEach { draw -> assertSame(colorFilter, draw.colorFilter) }
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

        listOf(40 to 0, 79 to 20, 40 to 39, 0 to 20).forEach { (x, y) ->
            val alpha = Color.alpha(bitmap.getPixel(x, y))
            assertTrue("Expected opaque inset stroke at ($x, $y), alpha=$alpha", alpha >= 240)
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

    private class RecordingCanvas : Canvas() {
        val roundRects = mutableListOf<RoundRectDraw>()
        var pathDrawCount: Int = 0

        override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
            assertEquals(rx, ry, 0.001f)
            roundRects += RoundRectDraw(
                frame = RectF(rect),
                radius = rx,
                style = paint.style,
                alpha = paint.alpha,
                colorFilter = paint.colorFilter,
            )
        }

        override fun drawRoundRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            rx: Float,
            ry: Float,
            paint: Paint,
        ) {
            assertEquals(rx, ry, 0.001f)
            roundRects += RoundRectDraw(
                frame = RectF(left, top, right, bottom),
                radius = rx,
                style = paint.style,
                alpha = paint.alpha,
                colorFilter = paint.colorFilter,
            )
        }

        override fun drawPath(path: Path, paint: Paint) {
            pathDrawCount += 1
        }
    }

    private data class RoundRectDraw(
        val frame: RectF,
        val radius: Float,
        val style: Paint.Style,
        val alpha: Int,
        val colorFilter: ColorFilter?,
    )
}
