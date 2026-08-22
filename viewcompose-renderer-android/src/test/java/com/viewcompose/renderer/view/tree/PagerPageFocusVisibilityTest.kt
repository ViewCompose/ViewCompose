package com.viewcompose.renderer.view.tree

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.container.PagerPageHostLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PagerPageFocusVisibilityTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `pager page stops descendant request after page-local scroll owner`() {
        val outerPagerParent = RecordingParent(context)
        val pageBoundary = PagerPageHostLayout(context)
        val pageScrollOwner = RecordingParent(context)
        val editor = ViewComposeEditText(context)
        outerPagerParent.addView(pageBoundary)
        pageBoundary.addView(pageScrollOwner)
        pageScrollOwner.addView(editor)

        val moved = editor.requestRectangleOnScreen(Rect(0, 0, 200, 900), false)

        assertTrue(moved)
        assertEquals(1, pageScrollOwner.rectangleRequestCount)
        assertEquals(0, outerPagerParent.rectangleRequestCount)
    }

    @Test
    fun `ordinary hierarchy preserves complete platform propagation`() {
        val outerParent = RecordingParent(context)
        val innerParent = RecordingParent(context)
        val editor = ViewComposeEditText(context)
        outerParent.addView(innerParent)
        innerParent.addView(editor)

        val moved = editor.requestRectangleOnScreen(Rect(0, 0, 200, 900), false)

        assertTrue(moved)
        assertEquals(1, innerParent.rectangleRequestCount)
        assertEquals(1, outerParent.rectangleRequestCount)
    }

    private class RecordingParent(context: Context) : FrameLayout(context) {
        var rectangleRequestCount: Int = 0
            private set

        override fun requestChildRectangleOnScreen(
            child: View,
            rectangle: Rect,
            immediate: Boolean,
        ): Boolean {
            rectangleRequestCount += 1
            return true
        }
    }
}
