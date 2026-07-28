package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core context 中的 Local Text Style 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Local Text Style behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTextStyleTest {
    @Test
    fun `text defaults to local text style`() {
        val tree = buildVNodeTree {
            ProvideLocal(LocalTextStyle, UiTextStyle(fontSizeSp = 42)) {
                Text("hello")
            }
        }

        val spec = tree.single().spec as TextNodeProps
        assertEquals(42, spec.textSizeSp)
    }

    @Test
    fun `text style falls back to body medium when no provider`() {
        val tree = buildVNodeTree {
            Text("hello")
        }

        val spec = tree.single().spec as TextNodeProps
        assertEquals(Theme.typography.bodyMedium.fontSizeSp, spec.textSizeSp)
    }

    @Test
    fun `nested provide text style overrides outer`() {
        val tree = buildVNodeTree {
            ProvideLocal(LocalTextStyle, UiTextStyle(fontSizeSp = 20)) {
                Text("outer")
                ProvideLocal(LocalTextStyle, UiTextStyle(fontSizeSp = 12)) {
                    Text("inner")
                }
            }
        }

        val outer = tree[0].spec as TextNodeProps
        val inner = tree[1].spec as TextNodeProps

        assertEquals(20, outer.textSizeSp)
        assertEquals(12, inner.textSizeSp)
    }

    @Test
    fun `explicit style parameter overrides local text style`() {
        val tree = buildVNodeTree {
            ProvideLocal(LocalTextStyle, UiTextStyle(fontSizeSp = 42)) {
                Text("hello", style = UiTextStyle(fontSizeSp = 10))
            }
        }

        val spec = tree.single().spec as TextNodeProps
        assertEquals(10, spec.textSizeSp)
    }

    @Test
    fun `TextStyle current reads provided value`() {
        var captured = 0
        buildVNodeTree {
            ProvideLocal(LocalTextStyle, UiTextStyle(fontSizeSp = 99)) {
                captured = TextStyle.current.fontSizeSp
                Text("probe")
            }
        }

        assertEquals(99, captured)
    }
}
