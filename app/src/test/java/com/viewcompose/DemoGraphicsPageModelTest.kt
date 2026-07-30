package com.viewcompose

/*
 * 测试职责：覆盖 Graphics Demo 的分页与 Lazy 阴影项目模型，防止页面切换或压力场景退化。
 * Test responsibility: covers Graphics Demo paging and lazy-shadow item modeling.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGraphicsPageModelTest {
    @Test
    fun `every graphics page keeps overview and page switcher at stable leading positions`() {
        (GRAPHICS_PAGE_DRAWING..GRAPHICS_PAGE_SHADOW_DIAGNOSTICS).forEach { selectedPage ->
            assertEquals(
                listOf("overview", "page_filter"),
                graphicsPageItems(selectedPage).take(2),
            )
        }
    }

    @Test
    fun `advanced shadow pages expose focused manual verification sections`() {
        assertEquals(
            listOf(
                "overview",
                "page_filter",
                "shadow_outer_single",
                "shadow_outer_multi",
                "shadow_outer_spread",
                "shadow_outer_shape",
                "verify",
            ),
            graphicsPageItems(GRAPHICS_PAGE_OUTER_SHADOWS),
        )
        assertEquals(
            listOf(
                "overview",
                "page_filter",
                "shadow_inner_single",
                "shadow_inner_multi",
                "shadow_inner_interop",
                "verify",
            ),
            graphicsPageItems(GRAPHICS_PAGE_INNER_SHADOWS),
        )
    }

    @Test
    fun `shadow diagnostics models exactly one thousand stable lazy items`() {
        val items = graphicsPageItems(GRAPHICS_PAGE_SHADOW_DIAGNOSTICS)
        val lazyItems = items.filter { it.startsWith("shadow_lazy_item_") }

        assertEquals(GRAPHICS_SHADOW_LAZY_ITEM_COUNT, lazyItems.size)
        assertEquals(GRAPHICS_SHADOW_LAZY_ITEM_COUNT, lazyItems.distinct().size)
        assertEquals("shadow_lazy_item_0", lazyItems.first())
        assertEquals("shadow_lazy_item_999", lazyItems.last())
        assertTrue(lazyItems.all { graphicsPageContentType(it) == "shadow_lazy_item_" })
        assertEquals(items.size, items.distinct().size)
    }
}
