package com.viewcompose.renderer.decoration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ViewDecorationDrawingTest {
    @After
    fun resetRuntime() {
        AndroidViewDecorationRuntime.resetForTests()
    }

    @Test
    fun `host with no decorated children never calls backend during draw`() {
        val backend = CountingBackend()
        AndroidViewDecorationRuntime.install(backend)
        val host = ViewDecorationHostLayout(RuntimeEnvironment.getApplication())
        host.addView(View(host.context), FrameLayout.LayoutParams(20, 20))
        layout(host)

        host.draw(Canvas(Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)))

        assertEquals(0, backend.behindDraws)
        assertEquals(0, backend.overDraws)
    }

    @Test
    fun `parent index dispatches only active decoration planes`() {
        val backend = CountingBackend()
        AndroidViewDecorationRuntime.install(backend)
        val host = ViewDecorationHostLayout(RuntimeEnvironment.getApplication())
        val child = View(host.context)
        host.addView(child, FrameLayout.LayoutParams(20, 20))
        AndroidViewDecorationRuntime.update(
            view = child,
            request = AndroidViewDecorationRequest(
                dropShadows = listOf(
                    DropShadowModifierElement(
                        shadows = listOf(UiShadow(blurRadius = 2.dp)),
                    ),
                ),
                innerShadows = emptyList(),
                defaultShape = UiShape.rounded(0.dp),
                density = UiDensity.Default,
            ),
        )
        layout(host)

        host.draw(Canvas(Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)))

        assertEquals(1, backend.behindDraws)
        assertEquals(0, backend.overDraws)
    }

    @Test
    fun `custom child order is enabled only while a nonzero zIndex exists`() {
        val host = DrawingOrderCountingHost()
        val first = View(host.context)
        val second = View(host.context)
        host.addView(first, FrameLayout.LayoutParams(20, 20))
        host.addView(second, FrameLayout.LayoutParams(20, 20))
        layout(host)
        val canvas = Canvas(Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888))

        host.draw(canvas)
        assertEquals(0, host.drawingOrderCalls)

        DecorationChildDrawingOrder.update(first, 1f)
        host.draw(canvas)
        val activeCalls = host.drawingOrderCalls
        assertEquals(2, activeCalls)

        DecorationChildDrawingOrder.update(first, 0f)
        host.draw(canvas)
        assertEquals(activeCalls, host.drawingOrderCalls)
    }

    private fun layout(host: ViewDecorationHostLayout) {
        host.measure(
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 40, 40)
    }

    private class DrawingOrderCountingHost : ViewDecorationHostLayout(
        RuntimeEnvironment.getApplication(),
    ) {
        var drawingOrderCalls: Int = 0

        override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
            drawingOrderCalls += 1
            return super.getChildDrawingOrder(childCount, drawingPosition)
        }
    }

    private class CountingBackend : AndroidViewDecorationBackend {
        var behindDraws: Int = 0
        var overDraws: Int = 0

        override fun update(
            view: View,
            request: AndroidViewDecorationRequest,
        ): AndroidViewDecorationPresence {
            return AndroidViewDecorationPresence(
                behindChild = request.dropShadows.isNotEmpty(),
                overChild = request.innerShadows.isNotEmpty(),
            )
        }

        override fun clear(view: View) = Unit

        override fun drawBehindChild(
            canvas: Canvas,
            parent: android.view.ViewGroup,
            child: View,
        ) {
            behindDraws += 1
        }

        override fun drawOverChild(
            canvas: Canvas,
            parent: android.view.ViewGroup,
            child: View,
        ) {
            overDraws += 1
        }
    }

}
