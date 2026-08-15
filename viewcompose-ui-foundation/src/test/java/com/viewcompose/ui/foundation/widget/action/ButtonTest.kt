package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core widget/action 中的 Button 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Button behavior in widget-core widget/action and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.ButtonNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonTest {
    @Test
    fun `button emits icon props and themed defaults`() {
        val tree = buildVNodeTree {
            Button(
                text = "Continue",
                leadingIcon = ImageSource.Resource(11),
                trailingIcon = ImageSource.Resource(12),
                size = ButtonSize.Large,
            )
        }

        val node = tree.single()
        val spec = node.spec as ButtonNodeProps

        assertEquals(NodeType.Button, node.type)
        assertEquals(ImageSource.Resource(11), spec.leadingIcon)
        assertEquals(ImageSource.Resource(12), spec.trailingIcon)
        assertEquals(ButtonDefaults.iconSize(ButtonSize.Large), spec.iconSize)
        assertEquals(ButtonDefaults.iconSpacing(ButtonSize.Large), spec.iconSpacing)
        assertEquals(ButtonDefaults.contentColor(), spec.textColor)
        assertEquals(ButtonDefaults.textStyle(ButtonSize.Large).fontSizeSp, spec.textSizeSp)
        assertEquals(ButtonDefaults.height(ButtonSize.Large), spec.minHeight)
        assertEquals(ButtonDefaults.visualHeight(ButtonSize.Large), spec.visualHeight)
        assertEquals(ButtonDefaults.stateLayerColors(), spec.stateLayerColors)
        assertTrue(node.spec is ButtonNodeProps)
    }

    @Test
    fun `button resolves variant state layers from content roles and interaction tokens`() {
        val base = UiThemeDefaults.light()
        val tokens = base.copy(
            colors = base.colors.copy(
                onPrimary = 0xFF102030.toInt(),
                onSecondaryContainer = 0xFF405060.toInt(),
                primary = 0xFF708090.toInt(),
            ),
            interactions = UiInteractionTokens(
                pressedStateLayerOpacity = 0.10f,
                focusedStateLayerOpacity = 0.20f,
                hoveredStateLayerOpacity = 0.30f,
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(tokens) {
                Button(text = "Primary")
                Button(text = "Tonal", variant = ButtonVariant.Tonal)
                Button(text = "Outlined", variant = ButtonVariant.Outlined)
            }
        }

        assertEquals(
            UiStateLayerColors(0x1A102030, 0x33102030, 0x4D102030),
            (tree[0].spec as ButtonNodeProps).stateLayerColors,
        )
        assertEquals(
            UiStateLayerColors(0x1A405060, 0x33405060, 0x4D405060),
            (tree[1].spec as ButtonNodeProps).stateLayerColors,
        )
        assertEquals(
            UiStateLayerColors(0x1A708090, 0x33708090, 0x4D708090),
            (tree[2].spec as ButtonNodeProps).stateLayerColors,
        )
    }

    @Test
    fun `instance state layers override every interaction color`() {
        val colors = UiStateLayerColors(0x33445566, 0x22445566, 0x11445566)
        val tree = buildVNodeTree {
            Button(
                text = "Override",
                overrides = ButtonOverrides(stateLayerColors = colors),
            )
        }

        val spec = tree.single().spec as ButtonNodeProps

        assertEquals(0x33445566, spec.rippleColor)
        assertEquals(colors, spec.stateLayerColors)
    }

    @Test
    fun `button emits full text style fields`() {
        val style = UiTextStyle(
            fontSizeSp = 17.sp,
            fontWeight = 700,
            letterSpacingEm = 0.06f,
            lineHeightSp = 24.sp,
            includeFontPadding = true,
        )

        val tree = buildVNodeTree {
            Button(
                text = "Styled",
                overrides = ButtonOverrides(textStyle = style),
            )
        }

        val spec = tree.single().spec as ButtonNodeProps

        assertEquals(style.fontWeight, spec.fontWeight)
        assertEquals(style.letterSpacingEm, spec.letterSpacingEm)
        assertEquals(style.lineHeightSp, spec.lineHeightSp)
        assertEquals(style.includeFontPadding, spec.includeFontPadding)
    }

    @Test
    fun `nested scopes merge fieldwise and instance overrides win`() {
        val tree = buildVNodeTree {
            ProvideButtonOverrides(
                ButtonOverrides(
                    containerColor = 101,
                    contentColor = 102,
                ),
            ) {
                Button(text = "Outer")
                ProvideButtonOverrides(
                    ButtonOverrides(
                        contentColor = 202,
                        borderColor = 203,
                        borderWidth = 2.dp,
                    ),
                ) {
                    Button(
                        text = "Inner",
                        overrides = ButtonOverrides(containerColor = 301),
                    )
                }
                Button(text = "Restored")
            }
        }

        val outer = tree[0].spec as ButtonNodeProps
        val inner = tree[1].spec as ButtonNodeProps
        val restored = tree[2].spec as ButtonNodeProps

        assertEquals(101, outer.backgroundColor)
        assertEquals(102, outer.textColor)
        assertEquals(301, inner.backgroundColor)
        assertEquals(202, inner.textColor)
        assertEquals(203, inner.borderColor)
        assertEquals(2.dp, inner.borderWidth)
        assertEquals(101, restored.backgroundColor)
        assertEquals(102, restored.textColor)
    }

    @Test
    fun `generic appearance fields apply across variants until an instance replaces them`() {
        val tree = buildVNodeTree {
            ProvideButtonOverrides(ButtonOverrides(containerColor = 101)) {
                Button(text = "Primary")
                Button(text = "Text", variant = ButtonVariant.Text)
                Button(
                    text = "Restored text",
                    variant = ButtonVariant.Text,
                    overrides = ButtonOverrides(containerColor = 202),
                )
            }
        }

        assertEquals(101, (tree[0].spec as ButtonNodeProps).backgroundColor)
        assertEquals(101, (tree[1].spec as ButtonNodeProps).backgroundColor)
        assertEquals(202, (tree[2].spec as ButtonNodeProps).backgroundColor)
    }

    @Test
    fun `empty instance patch keeps the scoped patch identity`() {
        val scoped = ButtonOverrides(containerColor = 101)

        assertSame(scoped, scoped.merge(ButtonOverrides.None))
        assertSame(scoped, ButtonOverrides.None.merge(scoped))
    }

    @Test
    fun `button overrides reject negative dimensions`() {
        assertThrows(IllegalArgumentException::class.java) {
            ButtonOverrides(borderWidth = (-1).dp)
        }
    }

    @Test
    fun `button override scope restores after content throws`() {
        val theme = UiThemeDefaults.light()
        var restoredColor = 0

        buildVNodeTree {
            UiTheme(theme) {
                runCatching {
                    ProvideButtonOverrides(ButtonOverrides(containerColor = 101)) {
                        error("failure")
                    }
                }
                restoredColor = ButtonDefaults.containerColor()
            }
        }

        assertEquals(theme.colors.primary, restoredColor)
    }
}
