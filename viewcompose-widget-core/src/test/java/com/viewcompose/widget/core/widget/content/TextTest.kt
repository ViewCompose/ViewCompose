package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core widget/content 中的 Text 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Text behavior in widget-core widget/content and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.textDocument
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {
    @Test
    fun `text emits max lines overflow and alignment props`() {
        val tree = buildVNodeTree {
            Text(
                text = "Hello",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        val node = tree.single()
        val spec = node.spec as TextNodeProps

        assertEquals(NodeType.Text, node.type)
        assertEquals(2, spec.maxLines)
        assertEquals(TextOverflow.Ellipsis, spec.overflow)
        assertEquals(TextAlign.Center, spec.textAlign)
        assertTrue(node.spec is TextNodeProps)
    }

    @Test
    fun `text inherits content color from ProvideContentColor`() {
        val tree = buildVNodeTree {
            ProvideLocal(LocalContentColor, 0xFFABCDEF.toInt()) {
                Text("colored")
            }
        }

        val spec = tree.single().spec as TextNodeProps
        assertEquals(0xFFABCDEF.toInt(), spec.textColor)
    }

    @Test
    fun `text defaults to ContentColor current`() {
        val tree = buildVNodeTree {
            Text("default color")
        }

        val spec = tree.single().spec as TextNodeProps
        assertEquals(Theme.colors.onSurface, spec.textColor)
    }

    @Test
    fun `rich text emits document without flattening annotations`() {
        val document = textDocument {
            append("Rich", TextSpanStyle(fontWeight = 700))
            append(" text")
        }
        val tree = buildVNodeTree {
            RichText(document)
        }

        val spec = tree.single().spec as TextNodeProps

        assertEquals(document, spec.document)
        assertEquals("Rich text", spec.text)
        assertEquals(1, spec.document.spanStyles.size)
    }
}
