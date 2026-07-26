package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.backgroundDrawableRes
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.renderer.modifier.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModifierNodeStyleResolverTest {
    @Test
    fun `node style keeps drawable resource when drawable and color both set`() {
        val node = buttonVNode(
            modifier = Modifier
                .backgroundColor(0xFF0000AA.toInt())
                .backgroundDrawableRes(99),
        )

        val style = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = node.modifier.resolve(),
            defaultRippleColor = 0xFF00FF00.toInt(),
        )

        assertEquals(99, style.backgroundDrawableResId)
        assertNotNull(style.backgroundColor)
        assertEquals(0xFF0000AA.toInt(), style.backgroundColor)
    }

    private fun buttonVNode(modifier: Modifier): VNode {
        return VNode(
            type = NodeType.Button,
            spec = ButtonNodeProps(
                text = "Button",
                enabled = true,
                onClick = null,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14,
                backgroundColor = 0xFFE0E0E0.toInt(),
                borderWidth = 0,
                borderColor = 0,
                shape = UiShape.rounded(0),
                rippleColor = 0x33000000,
                minHeight = 0,
                paddingHorizontal = 0,
                paddingVertical = 0,
                leadingIcon = null,
                trailingIcon = null,
                iconTint = 0,
                iconSize = 0,
                iconSpacing = 0,
            ),
            modifier = modifier,
            children = emptyList(),
        )
    }
}
