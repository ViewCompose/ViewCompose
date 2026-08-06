package com.viewcompose.renderer.view.lazy.adapter

/*
 * 测试职责：覆盖 renderer view/lazy/adapter 中的 Lazy Sticky Header Decoration 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy Sticky Header Decoration behavior in renderer view/lazy/adapter and guards render and patch contracts against regressions.
 */

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.nativeContainer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LazyStickyHeaderDecorationTest {
    @Test
    fun `sticky decoration pins session backed header and disposes when removed`() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = LazyListAdapter()
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(
                item("header", LazyListItemKind.StickyHeader),
                item("row-1"),
                item("row-2"),
                item("row-3"),
            ),
        )
        LazyStickyHeaderDecoration.update(recyclerView, adapter)
        layout(recyclerView)

        recyclerView.draw(
            Canvas(Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)),
        )
        assertNotNull(
            recyclerView.getTag(R.id.viewcompose_lazy_sticky_header_decoration),
        )

        adapter.submitItems(listOf(item("row-only")))
        LazyStickyHeaderDecoration.update(recyclerView, adapter)

        assertNull(recyclerView.getTag(R.id.viewcompose_lazy_sticky_header_decoration))
    }

    private fun item(
        key: Any,
        kind: LazyListItemKind = LazyListItemKind.Item,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = key,
            kind = kind,
            sessionFactory = LazyListItemSessionFactory { handle ->
                val container = handle.nativeContainer as ViewGroup
                object : LazyListItemSession {
                    override fun render() {
                        if (container.childCount == 0) {
                            container.addView(
                                TextView(container.context).apply {
                                    text = key.toString()
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        40,
                                    )
                                },
                            )
                        }
                    }

                    override fun dispose() {
                        container.removeAllViews()
                    }
                }
            },
        )
    }

    private fun layout(recyclerView: RecyclerView) {
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
        )
        recyclerView.layout(0, 0, 240, 160)
    }
}
