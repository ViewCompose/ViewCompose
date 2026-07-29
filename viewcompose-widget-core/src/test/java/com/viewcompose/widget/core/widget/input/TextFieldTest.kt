package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core widget/input 中的 Text Field 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Text Field behavior in widget-core widget/input and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.TextFieldType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFieldTest {
    @Test
    fun `text field emits expected props and themed defaults`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 1,
                surface = 2,
                surfaceVariant = 3,
                primary = 4,
                secondary = 5,
                error = 9,
                success = 10,
                warning = 11,
                info = 12,
                onSurface = 70,
                onSurfaceVariant = 80,
                outline = 6,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 31.sp),
                bodyMedium = UiTextStyle(
                    fontSizeSp = 19.sp,
                    fontWeight = 500,
                    letterSpacingEm = 0.03f,
                    lineHeightSp = 26.sp,
                    includeFontPadding = true,
                ),
                labelMedium = UiTextStyle(fontSizeSp = 13.sp),
            ),
        )
        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                TextField(
                    state = textState("hello"),
                    hint = "Type here",
                    label = "Display name",
                    supportingText = "Shown in profile",
                    maxLines = 3,
                    keyboardOptions = TextFieldKeyboardOptions(
                        imeAction = TextFieldImeAction.Next,
                    ),
                )
            }
        }

        val root = tree.single()
        val node = findFirstTextFieldNode(tree)
        val spec = node.spec as TextFieldNodeProps

        assertEquals(NodeType.Column, root.type)
        assertEquals(NodeType.TextField, node.type)
        assertEquals(TextFieldValue("hello"), spec.value)
        assertEquals("Type here", spec.placeholder)
        assertTrue(collectTextNodes(root).any { it.text == "Display name" })
        assertTrue(collectTextNodes(root).any { it.text == "Shown in profile" })
        assertEquals(true, spec.singleLine)
        assertEquals(TextFieldType.Text, spec.keyboardOptions.keyboardType)
        assertEquals(3, spec.maxLines)
        assertEquals(TextFieldImeAction.Next, spec.keyboardOptions.imeAction)
        assertEquals(customTheme.colors.onSurfaceVariant, spec.hintColor)
        assertEquals(customTheme.colors.onSurface, spec.textColor)
        assertEquals(customTheme.typography.bodyMedium.fontSizeSp, spec.textSizeSp)
        assertEquals(customTheme.typography.bodyMedium.fontWeight, spec.fontWeight)
        assertEquals(customTheme.typography.bodyMedium.letterSpacingEm, spec.letterSpacingEm)
        assertEquals(customTheme.typography.bodyMedium.lineHeightSp, spec.lineHeightSp)
        assertEquals(customTheme.typography.bodyMedium.includeFontPadding, spec.includeFontPadding)
        assertEquals(customTheme.colors.surface, spec.backgroundColor)
        assertEquals(customTheme.shapes.small, spec.shape)
        assertEquals(true, spec.enabled)
        assertTrue(node.spec is TextFieldNodeProps)
    }

    @Test
    fun `password field uses password input type`() {
        val tree = buildVNodeTree {
            PasswordField(
                state = textState("secret"),
                hint = "Password",
                label = "Password",
                supportingText = "At least 8 characters",
            )
        }

        val node = findFirstTextFieldNode(tree)
        val spec = node.spec as TextFieldNodeProps

        assertEquals(NodeType.TextField, node.type)
        assertEquals(TextFieldType.Password, spec.keyboardOptions.keyboardType)
        assertTrue(collectTextNodes(tree.single()).any { it.text == "Password" })
        assertTrue(collectTextNodes(tree.single()).any { it.text == "At least 8 characters" })
        assertTrue(spec.singleLine)
    }

    @Test
    fun `text area exposes read only and multiline semantics`() {
        val tree = buildVNodeTree {
            TextArea(
                state = textState("Line 1"),
                label = "Bio",
                supportingText = "Visible to collaborators",
                readOnly = true,
                minLines = 4,
                maxLines = 6,
                keyboardOptions = TextFieldKeyboardOptions(
                    imeAction = TextFieldImeAction.Done,
                ),
            )
        }

        val node = findFirstTextFieldNode(tree)
        val spec = node.spec as TextFieldNodeProps

        assertEquals(false, spec.singleLine)
        assertEquals(true, spec.readOnly)
        assertEquals(4, spec.minLines)
        assertEquals(6, spec.maxLines)
        assertEquals(TextFieldImeAction.Done, spec.keyboardOptions.imeAction)
        assertTrue(collectTextNodes(tree.single()).any { it.text == "Bio" })
        assertTrue(collectTextNodes(tree.single()).any { it.text == "Visible to collaborators" })
    }

    @Test
    fun `outlined text field uses border variant`() {
        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                TextField(
                    state = textState("hello"),
                    variant = TextFieldVariant.Outlined,
                )
            }
        }

        val spec = findFirstTextFieldNode(tree).spec as TextFieldNodeProps

        assertEquals(0x00000000, spec.backgroundColor)
        assertEquals(Theme.colors.outline, spec.borderColor)
        assertEquals(1.dp, spec.borderWidth)
    }

    @Test
    fun `compact text field applies themed height and typography`() {
        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                TextField(
                    state = textState("hello"),
                    size = TextFieldSize.Compact,
                )
            }
        }

        val textFieldNode = findFirstTextFieldNode(tree)
        val elements = textFieldNode.modifier.readModifierElements()
        val spec = textFieldNode.spec as TextFieldNodeProps

        assertFalse(elements.any { it is com.viewcompose.ui.modifier.HeightModifierElement })
        assertEquals(TextFieldDefaults.height(TextFieldSize.Compact), spec.minHeight)
        assertEquals(Theme.typography.labelSmall.fontSizeSp, spec.textSizeSp)
    }

    @Test
    fun `disabled and error text field states use color overrides`() {
        val baseTheme = UiThemeDefaults.light()

        val disabledTree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideTextFieldColors(
                    TextFieldColorOverride(
                        filledDisabledContainer = 202,
                        outlinedErrorBorder = 209,
                    ),
                ) {
                    TextField(
                        state = textState("hello"),
                        enabled = false,
                    )
                }
            }
        }
        val errorTree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideTextFieldColors(
                    TextFieldColorOverride(
                        filledDisabledContainer = 202,
                        outlinedErrorBorder = 209,
                    ),
                ) {
                    TextField(
                        state = textState("hello"),
                        variant = TextFieldVariant.Outlined,
                        isError = true,
                    )
                }
            }
        }

        val disabledSpec = findFirstTextFieldNode(disabledTree).spec as TextFieldNodeProps
        val errorSpec = findFirstTextFieldNode(errorTree).spec as TextFieldNodeProps

        assertEquals(202, disabledSpec.backgroundColor)
        assertEquals(209, errorSpec.borderColor)
        assertEquals(Theme.colors.onErrorContainer, errorSpec.textColor)
        assertEquals(Theme.colors.onErrorContainer, errorSpec.hintColor)
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = com.viewcompose.ui.modifier.Modifier::class.java.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }

    private fun textState(text: String): TextFieldState {
        return TextFieldState(TextFieldValue(text))
    }

    private fun findFirstTextFieldNode(tree: List<VNode>): VNode {
        fun visit(node: VNode): VNode? {
            if (node.type == NodeType.TextField) return node
            node.children.forEach { child ->
                val match = visit(child)
                if (match != null) return match
            }
            return null
        }
        tree.forEach { node ->
            val match = visit(node)
            if (match != null) return match
        }
        error("No TextField node found")
    }

    private fun collectTextNodes(node: VNode): List<TextNodeProps> {
        val result = mutableListOf<TextNodeProps>()
        fun visit(current: VNode) {
            val spec = current.spec
            if (spec is TextNodeProps) {
                result += spec
            }
            current.children.forEach(::visit)
        }
        visit(node)
        return result
    }
}
