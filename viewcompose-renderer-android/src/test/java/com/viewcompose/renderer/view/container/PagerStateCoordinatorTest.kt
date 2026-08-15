package com.viewcompose.renderer.view.container

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.viewcompose.ui.state.PagerStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PagerStateCoordinatorTest {
    @Test
    fun `drag publishes motion and callback only after settled idle`() {
        var pageCount = 4
        val pager = pager(pageCount)
        val settledCallbacks = mutableListOf<Int>()
        val snapshots = mutableListOf<PagerStateSnapshot>()
        val coordinator = PagerStateCoordinator(pager, { pageCount }, { settledCallbacks::add })
        coordinator.setOnSnapshotChangedListener(snapshots::add)
        coordinator.applyControlledPage(1)

        coordinator.pageChangeCallback.onPageScrollStateChanged(ViewPager2.SCROLL_STATE_DRAGGING)
        coordinator.pageChangeCallback.onPageScrolled(1, 0.5f, 50)
        coordinator.pageChangeCallback.onPageSelected(2)

        assertTrue(coordinator.currentSnapshot().isScrollInProgress)
        assertEquals(1, coordinator.currentSnapshot().currentPage)
        assertEquals(2, coordinator.currentSnapshot().targetPage)
        assertEquals(emptyList<Int>(), settledCallbacks)

        pager.setCurrentItem(2, false)
        coordinator.pageChangeCallback.onPageScrollStateChanged(ViewPager2.SCROLL_STATE_IDLE)
        coordinator.pageChangeCallback.onPageScrollStateChanged(ViewPager2.SCROLL_STATE_IDLE)

        assertEquals(listOf(2), settledCallbacks)
        assertEquals(2, coordinator.currentSnapshot().settledPage)
        assertFalse(coordinator.currentSnapshot().isScrollInProgress)
        assertTrue(snapshots.isNotEmpty())
    }

    @Test
    fun `controlled rebinding never feeds page callback back to caller`() {
        val pager = pager(3)
        val settledCallbacks = mutableListOf<Int>()
        val coordinator = PagerStateCoordinator(pager, { 3 }, { settledCallbacks::add })

        coordinator.applyControlledPage(2)
        coordinator.pageChangeCallback.onPageSelected(2)
        coordinator.pageChangeCallback.onPageScrollStateChanged(ViewPager2.SCROLL_STATE_IDLE)

        assertEquals(emptyList<Int>(), settledCallbacks)
        assertEquals(2, coordinator.currentSnapshot().settledPage)
    }

    @Test
    fun `page count changes clamp every published index`() {
        var pageCount = 4
        val pager = pager(pageCount)
        val coordinator = PagerStateCoordinator(pager, { pageCount }, { null })
        coordinator.applyControlledPage(3)

        pageCount = 2
        coordinator.onPageCountChanged()

        assertEquals(1, coordinator.currentSnapshot().currentPage)
        assertEquals(1, coordinator.currentSnapshot().settledPage)
        assertEquals(2, coordinator.currentSnapshot().pageCount)
    }

    private fun pager(count: Int): ViewPager2 {
        val context = RuntimeEnvironment.getApplication()
        return ViewPager2(context).apply {
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                    object : RecyclerView.ViewHolder(View(parent.context)) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

                override fun getItemCount(): Int = count
            }
        }
    }
}
