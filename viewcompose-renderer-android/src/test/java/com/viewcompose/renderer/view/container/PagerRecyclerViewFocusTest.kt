package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PagerRecyclerViewFocusTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `idle relayout preserves focus on the selected page`() {
        val mounted = mountPager()
        val selectedEvents = mutableListOf<Int>()
        mounted.pager.viewportListener = recordingListener(selectedEvents)
        val editor = mounted.editorAt(0)
        assertTrue(editor.requestFocus())

        mounted.layout()

        assertTrue(editor.isFocused)
        assertEquals(0, editor.clearFocusCalls)
        assertEquals(emptyList<Int>(), selectedEvents)
    }

    @Test
    fun `actual page change clears focus from the outgoing page`() {
        val mounted = mountPager()
        val editor = mounted.editorAt(0)
        assertTrue(editor.requestFocus())

        mounted.pager.moveToPage(position = 1, animated = false)
        mounted.layout()

        assertTrue(editor.clearFocusCalls > 0)
        assertEquals(1, mounted.pager.currentPage)
    }

    @Test
    fun `each pager item occupies the complete viewport`() {
        val mounted = mountPager()
        val firstPage = mounted.pager.findViewHolderForAdapterPosition(0)!!.itemView

        assertEquals(VIEWPORT_WIDTH, firstPage.width)
        assertEquals(VIEWPORT_HEIGHT, firstPage.height)
    }

    @Test
    fun `explicit offscreen limit lays out the requested adjacent pages`() {
        val mounted = mountPager()

        mounted.pager.setOffscreenPageLimit(2)
        mounted.layout()

        assertNotNull(mounted.pager.findViewHolderForAdapterPosition(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero is not a valid offscreen page limit`() {
        mountPager().pager.setOffscreenPageLimit(0)
    }

    @Test
    fun `disabled user input also rejects accessibility paging`() {
        val mounted = mountPager()
        mounted.pager.setUserScrollEnabled(false)

        val handled = mounted.pager.performAccessibilityAction(
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            null,
        )

        assertTrue(!handled)
        assertEquals(0, mounted.pager.currentPage)
    }

    private fun mountPager(): MountedPager {
        val root = FrameLayout(context)
        val pager = DeclarativePagerRecyclerView(
            context = context,
            orientation = LinearLayoutManager.VERTICAL,
        )
        pager.adapter = FocusPageAdapter()
        root.addView(
            pager,
            FrameLayout.LayoutParams(VIEWPORT_WIDTH, VIEWPORT_HEIGHT),
        )
        return MountedPager(root, pager).also(MountedPager::layout)
    }

    private fun recordingListener(selectedEvents: MutableList<Int>): PagerViewportListener {
        return object : PagerViewportListener {
            override fun onPageScrolled(position: Int, offset: Float) = Unit

            override fun onPageSelected(position: Int) {
                selectedEvents += position
            }

            override fun onScrollStateChanged(state: PagerScrollState) = Unit
        }
    }

    private class MountedPager(
        val root: FrameLayout,
        val pager: DeclarativePagerRecyclerView,
    ) {
        fun layout() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
        }

        fun editorAt(position: Int): TrackingEditText {
            val page = pager.findViewHolderForAdapterPosition(position)!!.itemView as ViewGroup
            return page.getChildAt(0) as TrackingEditText
        }
    }

    private class FocusPageAdapter : RecyclerView.Adapter<FocusPageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FocusPageHolder {
            val page = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(TrackingEditText(context))
            }
            return FocusPageHolder(page)
        }

        override fun onBindViewHolder(holder: FocusPageHolder, position: Int) = Unit

        override fun getItemCount(): Int = 3
    }

    private class FocusPageHolder(page: FrameLayout) : RecyclerView.ViewHolder(page)

    private class TrackingEditText(context: Context) : EditText(context) {
        var clearFocusCalls: Int = 0
            private set

        override fun clearFocus() {
            clearFocusCalls += 1
            super.clearFocus()
        }
    }

    private companion object {
        const val VIEWPORT_WIDTH = 600
        const val VIEWPORT_HEIGHT = 800
    }
}
