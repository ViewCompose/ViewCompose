package com.viewcompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGraphicsPageModelTest {
    @Test
    fun `outer and inner shadow fixtures expose only their focused sections`() {
        assertEquals(
            listOf(
                "shadow_outer_single",
                "shadow_outer_multi",
                "shadow_outer_spread",
                "shadow_outer_shape",
            ),
            GraphicsOuterShadowItems,
        )
        assertEquals(
            listOf(
                "shadow_inner_interop",
                "shadow_inner_single",
                "shadow_inner_multi",
            ),
            GraphicsInnerShadowItems,
        )
    }

    @Test
    fun `shadow list models exactly one thousand stable lazy items`() {
        assertEquals(GRAPHICS_SHADOW_LAZY_ITEM_COUNT, GraphicsShadowLazyItems.size)
        assertEquals(GRAPHICS_SHADOW_LAZY_ITEM_COUNT, GraphicsShadowLazyItems.distinct().size)
        assertEquals("shadow_lazy_item_0", GraphicsShadowLazyItems.first())
        assertEquals("shadow_lazy_item_999", GraphicsShadowLazyItems.last())
        assertTrue(
            GraphicsShadowLazyItems.all { item ->
                graphicsShadowContentType(item) == GRAPHICS_SHADOW_LAZY_ITEM_PREFIX
            },
        )
        assertEquals(GraphicsShadowListItems.size, GraphicsShadowListItems.distinct().size)
    }
}
