package com.viewcompose.renderer.layout

/*
 * 测试职责：覆盖 renderer layout 中的 Layout Param Defaults Resolver 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Layout Param Defaults Resolver behavior in renderer layout and guards render and patch contracts against regressions.
 */

import android.view.ViewGroup
import android.widget.LinearLayout
import com.viewcompose.ui.node.NodeType
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutParamDefaultsResolverTest {
    @Test
    fun `surface wraps content inside horizontal row`() {
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultWidth(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.HORIZONTAL,
            ),
        )
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultHeight(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.HORIZONTAL,
            ),
        )
    }

    @Test
    fun `surface wraps content inside vertical column`() {
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultWidth(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.VERTICAL,
            ),
        )
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultHeight(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.VERTICAL,
            ),
        )
    }

    @Test
    fun `surface still fills width in generic parents`() {
        assertEquals(
            ViewGroup.LayoutParams.MATCH_PARENT,
            LayoutParamDefaultsResolver.defaultWidth(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = false,
            ),
        )
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultHeight(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = false,
            ),
        )
    }

    @Test
    fun `surface wraps content in flow row defaults`() {
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultWidth(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.HORIZONTAL,
            ),
        )
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultHeight(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.HORIZONTAL,
            ),
        )
    }

    @Test
    fun `surface wraps content in flow column defaults`() {
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultWidth(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.VERTICAL,
            ),
        )
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LayoutParamDefaultsResolver.defaultHeight(
                nodeType = NodeType.Surface,
                useLinearLikeDefaults = true,
                linearOrientation = LinearLayout.VERTICAL,
            ),
        )
    }
}
