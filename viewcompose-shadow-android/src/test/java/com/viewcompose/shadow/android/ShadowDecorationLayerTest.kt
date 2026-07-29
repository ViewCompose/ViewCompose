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
    fun `host draws shadow outside child before child content`() {
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
    }

    @Test
    fun `equal spec is skipped and empty spec removes decoration`() {
        val child = View(RuntimeEnvironment.getApplication())
        val spec = ShadowSpecResolver.resolve(
            elements = listOf(
                DropShadowModifierElement(
                    shadows = listOf(UiShadow(blurRadius = 2.dp)),
                ),
            ),
            defaultShape = null,
            density = UiDensity.Default,
        )

        assertTrue(ShadowDecorationLayer.update(child, spec))
        assertFalse(ShadowDecorationLayer.update(child, spec))
        assertTrue(ShadowDecorationLayer.update(child, ResolvedShadowSpec.Empty))
        assertFalse(ShadowDecorationLayer.update(child, ResolvedShadowSpec.Empty))
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
            return super.drawChild(canvas, child, drawingTime)
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
