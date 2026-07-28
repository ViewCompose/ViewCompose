package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core widget/action 中的 Icon Button 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Icon Button behavior in widget-core widget/action and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconButtonTest {
    @Test
    fun `icon button emits themed defaults`() {
        val tree = buildVNodeTree {
            IconButton(
                icon = ImageSource.Resource(42),
                contentDescription = "Action",
            )
        }

        val node = tree.single()
        val spec = node.spec as IconButtonNodeProps
        val elements = node.modifier.readModifierElements()
        val size = elements.last { it is SizeModifierElement } as SizeModifierElement

        assertEquals(NodeType.IconButton, node.type)
        assertEquals(ImageSource.Resource(42), spec.source)
        assertEquals("Action", spec.contentDescription)
        assertEquals(ImageContentScale.Inside, spec.contentScale)
        assertEquals(IconButtonDefaults.contentColor(ButtonVariant.Text), spec.tint)
        assertEquals(IconButtonDefaults.size(), size.width)
        assertEquals(IconButtonDefaults.size(), size.height)
        assertEquals(IconButtonDefaults.containerColor(ButtonVariant.Text), spec.backgroundColor)
        assertEquals(IconButtonDefaults.shape(), spec.shape)
        assertEquals(true, spec.enabled)
        assertTrue(node.spec is IconButtonNodeProps)
    }

    @Test
    fun `outlined icon button uses border tokens`() {
        val tree = buildVNodeTree {
            IconButton(
                icon = ImageSource.Resource(7),
                variant = ButtonVariant.Outlined,
                enabled = false,
            )
        }

        val node = tree.single()
        val spec = node.spec as IconButtonNodeProps

        assertEquals(false, spec.enabled)
        assertEquals(IconButtonDefaults.borderWidth(ButtonVariant.Outlined), spec.borderWidth)
        assertEquals(
            IconButtonDefaults.borderColor(ButtonVariant.Outlined, enabled = false),
            spec.borderColor,
        )
        assertEquals(
            IconButtonDefaults.contentColor(ButtonVariant.Outlined, enabled = false),
            spec.tint,
        )
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = javaClass.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }
}
