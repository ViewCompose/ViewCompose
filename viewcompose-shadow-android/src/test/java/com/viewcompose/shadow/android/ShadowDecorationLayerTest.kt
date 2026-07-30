package com.viewcompose.shadow.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ShadowDecorationLayerTest {
    @Test
    fun `decoration host draws overlapping children by declarative zIndex`() {
        val context = RuntimeEnvironment.getApplication()
        val host = ShadowDecorationHostLayout(context)
        val higher = SolidColorView(context, Color.RED)
        val lower = SolidColorView(context, Color.BLUE)
        host.addView(higher, FrameLayout.LayoutParams(20, 20))
        host.addView(lower, FrameLayout.LayoutParams(20, 20))
        DecorationChildDrawingOrder.update(higher, 2f)
        DecorationChildDrawingOrder.update(lower, 1f)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 20, 20)

        val raisedBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(raisedBitmap))

        assertEquals(Color.RED, raisedBitmap.getPixel(10, 10))

        DecorationChildDrawingOrder.update(higher, 0f)
        val loweredBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(loweredBitmap))

        assertEquals(Color.BLUE, loweredBitmap.getPixel(10, 10))
    }

    @Test
    fun `host draws shadow outside child before child content`() {
        ShadowDecorationLayer.clearCache()
        val context = RuntimeEnvironment.getApplication()
        val host = TestDecorationHost(context).apply {
            clipChildren = false
        }
        val child = SolidColorView(context, Color.BLUE)
        host.addView(
            child,
            FrameLayout.LayoutParams(20, 20).apply {
                leftMargin = 20
                topMargin = 20
            },
        )
        val spec = ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = listOf(
                        UiShadow(
                            color = Color.RED,
                            blurRadius = 0.dp,
                            spreadRadius = 4.dp,
                        ),
                    ),
                ),
            ),
            defaultShape = UiShape.rounded(0.dp),
            density = UiDensity.Default,
        )
        ShadowDecorationLayer.update(child, spec)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 80, 80)

        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        host.drawChildren(Canvas(bitmap))

        assertEquals(Color.RED, bitmap.getPixel(18, 30))
        assertEquals(Color.BLUE, bitmap.getPixel(30, 30))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(10, 30))

        val statsAfterFirstDraw = ShadowDecorationLayer.cacheStats()
        child.translationX = 10f
        val translatedBitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        host.drawChildren(Canvas(translatedBitmap))
        val statsAfterTranslation = ShadowDecorationLayer.cacheStats()

        assertEquals(Color.TRANSPARENT, translatedBitmap.getPixel(18, 30))
        assertEquals(Color.RED, translatedBitmap.getPixel(28, 30))
        assertEquals(Color.BLUE, translatedBitmap.getPixel(40, 30))
        assertEquals(statsAfterFirstDraw.misses, statsAfterTranslation.misses)
        assertTrue(statsAfterTranslation.hits > statsAfterFirstDraw.hits)
    }

    @Test
    fun `host draws inner shadow over child content and follows transforms without rerasterizing`() {
        ShadowDecorationLayer.clearCache()
        val context = RuntimeEnvironment.getApplication()
        val host = TestDecorationHost(context).apply {
            clipChildren = false
        }
        val child = SolidColorView(context, Color.BLUE)
        host.addView(
            child,
            FrameLayout.LayoutParams(20, 20).apply {
                leftMargin = 20
                topMargin = 20
            },
        )
        val spec = InnerShadowSpecResolver.resolve(
            elements = listOf(
                InnerShadowModifierElement(
                    shadows = listOf(
                        UiShadow(
                            color = Color.RED,
                            blurRadius = 0.dp,
                            spreadRadius = 4.dp,
                        ),
                    ),
                ),
            ),
            defaultShape = UiShape.rounded(0.dp),
            density = UiDensity.Default,
        )
        ShadowDecorationLayer.updateInner(child, spec)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 80, 80)

        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        host.drawChildren(Canvas(bitmap))

        assertEquals(Color.TRANSPARENT, bitmap.getPixel(18, 30))
        assertEquals(Color.RED, bitmap.getPixel(21, 30))
        assertEquals(Color.BLUE, bitmap.getPixel(30, 30))

        val statsAfterFirstDraw = ShadowDecorationLayer.innerCacheStats()
        child.translationX = 10f
        val translatedBitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        host.drawChildren(Canvas(translatedBitmap))
        val statsAfterTranslation = ShadowDecorationLayer.innerCacheStats()

        assertEquals(Color.TRANSPARENT, translatedBitmap.getPixel(21, 30))
        assertEquals(Color.RED, translatedBitmap.getPixel(31, 30))
        assertEquals(Color.BLUE, translatedBitmap.getPixel(40, 30))
        assertEquals(statsAfterFirstDraw.misses, statsAfterTranslation.misses)
        assertTrue(statsAfterTranslation.hits > statsAfterFirstDraw.hits)
    }

    @Test
    fun `equal specs are skipped and empty specs remove decorations`() {
        val child = View(RuntimeEnvironment.getApplication())
        val outerSpec = ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = listOf(UiShadow(blurRadius = 2.dp)),
                ),
            ),
            defaultShape = null,
            density = UiDensity.Default,
        )
        val innerSpec = InnerShadowSpecResolver.resolve(
            elements = listOf(
                InnerShadowModifierElement(
                    shadows = listOf(UiShadow(blurRadius = 2.dp)),
                ),
            ),
            defaultShape = null,
            density = UiDensity.Default,
        )

        assertTrue(ShadowDecorationLayer.update(child, outerSpec))
        assertFalse(ShadowDecorationLayer.update(child, outerSpec))
        assertTrue(ShadowDecorationLayer.update(child, ResolvedShadowSpec.Empty))
        assertFalse(ShadowDecorationLayer.update(child, ResolvedShadowSpec.Empty))
        assertTrue(ShadowDecorationLayer.updateInner(child, innerSpec))
        assertFalse(ShadowDecorationLayer.updateInner(child, innerSpec))
        assertTrue(ShadowDecorationLayer.updateInner(child, ResolvedInnerShadowSpec.Empty))
        assertFalse(ShadowDecorationLayer.updateInner(child, ResolvedInnerShadowSpec.Empty))
    }

    private class TestDecorationHost(
        context: Context,
    ) : FrameLayout(context) {
        fun drawChildren(canvas: Canvas) {
            dispatchDraw(canvas)
        }

        override fun drawChild(
            canvas: Canvas,
            child: View,
            drawingTime: Long,
        ): Boolean {
            ShadowDecorationLayer.drawBehindChild(
                canvas = canvas,
                parent = this,
                child = child,
            )
            val drawn = super.drawChild(canvas, child, drawingTime)
            ShadowDecorationLayer.drawOverChild(
                canvas = canvas,
                parent = this,
                child = child,
            )
            return drawn
        }
    }

    private class SolidColorView(
        context: Context,
        private val color: Int,
    ) : View(context) {
        private val paint = Paint().apply {
            color = this@SolidColorView.color
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                paint,
            )
        }
    }
}
