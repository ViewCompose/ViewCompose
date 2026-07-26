package com.viewcompose.renderer.view.lazy.state

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.state.LazyListOrientation
import com.viewcompose.ui.state.LazyListStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UiLazyListConnectorTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `snapshot reports visible items viewport padding and scroll capabilities`() {
        val recyclerView = createRecyclerView()
        val connector = UiLazyListConnector(
            recyclerView = recyclerView,
            mainAxisItemSpacing = 6,
        )

        val snapshot = connector.currentSnapshot()

        assertNotNull(snapshot)
        checkNotNull(snapshot)
        assertEquals(20, snapshot.layoutInfo.totalItemsCount)
        assertEquals(LazyListOrientation.Vertical, snapshot.layoutInfo.orientation)
        assertEquals(8, snapshot.layoutInfo.beforeContentPadding)
        assertEquals(12, snapshot.layoutInfo.afterContentPadding)
        assertEquals(6, snapshot.layoutInfo.mainAxisItemSpacing)
        assertTrue(snapshot.layoutInfo.visibleItemsInfo.isNotEmpty())
        assertEquals(0, snapshot.firstVisibleItemIndex)
        assertFalse(snapshot.canScrollBackward)
        assertTrue(snapshot.canScrollForward)
    }

    @Test
    fun `connector publishes scrolling snapshots and detaches observers`() {
        val recyclerView = createRecyclerView()
        val connector = UiLazyListConnector(recyclerView)
        val snapshots = mutableListOf<LazyListStateSnapshot>()

        connector.setOnSnapshotChangedListener { snapshot -> snapshots += snapshot }
        recyclerView.scrollBy(0, 30)
        val publishedCount = snapshots.size
        connector.setOnSnapshotChangedListener(null)
        recyclerView.scrollBy(0, 30)

        assertTrue(publishedCount > 1)
        assertEquals(publishedCount, snapshots.size)
        assertTrue(snapshots.any { it.lastScrolledForward })
    }

    @Test
    fun `immediate scroll uses item offset and stop delegates safely`() {
        val recyclerView = createRecyclerView()
        val connector = UiLazyListConnector(recyclerView)

        connector.scrollToItem(
            index = 5,
            scrollOffset = 7,
            animated = false,
        )
        layout(recyclerView)
        connector.stopScroll()

        val snapshot = checkNotNull(connector.currentSnapshot())
        assertEquals(5, snapshot.firstVisibleItemIndex)
        assertEquals(7, snapshot.firstVisibleItemScrollOffset)
    }

    private fun createRecyclerView(): RecyclerView {
        return RecyclerView(context).apply {
            setPadding(0, 8, 0, 12)
            clipToPadding = false
            layoutManager = LinearLayoutManager(context)
            adapter = FixedSizeAdapter(count = 20)
            layout(this)
        }
    }

    private fun layout(view: RecyclerView) {
        val width = 240
        val height = 180
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private class FixedSizeAdapter(
        private val count: Int,
    ) : RecyclerView.Adapter<FixedSizeViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): FixedSizeViewHolder {
            return FixedSizeViewHolder(
                TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        40,
                    )
                },
            )
        }

        override fun onBindViewHolder(
            holder: FixedSizeViewHolder,
            position: Int,
        ) {
            holder.textView.text = position.toString()
        }

        override fun getItemCount(): Int = count
    }

    private class FixedSizeViewHolder(
        val textView: TextView,
    ) : RecyclerView.ViewHolder(textView)
}
