package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core widget/navigation 中的 Segmented Control 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Segmented Control behavior in widget-core widget/navigation and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedControlTest {
    @Test
    fun `segmented control emits themed props`() {
        var selectedIndex = -1

        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                SegmentedControl(
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = 1,
                    onSelectionChange = { selectedIndex = it },
                )
            }
        }

        val node = tree.single()
        val spec = node.spec as SegmentedControlNodeProps
        val height = node.modifier.readModifierElements()
            .last { it is HeightModifierElement } as HeightModifierElement

        assertEquals(NodeType.SegmentedControl, node.type)
        assertEquals(1, spec.selectedIndex)
        assertEquals(SegmentedControlDefaults.backgroundColor(), spec.backgroundColor)
        assertEquals(SegmentedControlDefaults.indicatorColor(), spec.indicatorColor)
        assertEquals(SegmentedControlDefaults.shape(), spec.shape)
        assertEquals(SegmentedControlDefaults.textColor(), spec.textColor)
        assertEquals(SegmentedControlDefaults.selectedTextColor(), spec.selectedTextColor)
        assertEquals(SegmentedControlDefaults.rippleColor(), spec.rippleColor)
        assertEquals(SegmentedControlDefaults.textStyle().fontSizeSp, spec.textSizeSp)
        assertEquals(3, spec.items.size)
        assertEquals("System", spec.items[0].label)
        assertEquals(UiDimension.Exact(SegmentedControlDefaults.height()), height.height)
        assertTrue(node.spec is SegmentedControlNodeProps)

        spec.onSelectionChange?.invoke(2)
        assertEquals(2, selectedIndex)
    }

    @Test
    fun `segmented control uses color override tokens`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideSegmentedControlColors(
                    SegmentedControlColorOverride(
                        background = 401,
                        indicator = 406,
                        text = 408,
                        selectedText = 410,
                    ),
                ) {
                    SegmentedControl(
                        items = listOf("A", "B"),
                        selectedIndex = 0,
                        onSelectionChange = {},
                    )
                }
            }
        }

        val spec = tree.single().spec as SegmentedControlNodeProps

        assertEquals(401, spec.backgroundColor)
        assertEquals(406, spec.indicatorColor)
        assertEquals(408, spec.textColor)
        assertEquals(410, spec.selectedTextColor)
    }

    @Test
    fun `segmented control emits full text style fields`() {
        val customTheme = UiThemeDefaults.light().copy(
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 30.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
                labelMedium = UiTextStyle(fontSizeSp = 14.sp),
                labelLarge = UiTextStyle(
                    fontSizeSp = 15.sp,
                    fontWeight = 700,
                    letterSpacingEm = 0.05f,
                    lineHeightSp = 22.sp,
                    includeFontPadding = true,
                ),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                SegmentedControl(
                    items = listOf("A", "B"),
                    selectedIndex = 0,
                    onSelectionChange = {},
                    size = SegmentedControlSize.Medium,
                )
            }
        }

        val spec = tree.single().spec as SegmentedControlNodeProps

        assertEquals(customTheme.typography.labelLarge.fontWeight, spec.fontWeight)
        assertEquals(customTheme.typography.labelLarge.letterSpacingEm, spec.letterSpacingEm)
        assertEquals(customTheme.typography.labelLarge.lineHeightSp, spec.lineHeightSp)
        assertEquals(customTheme.typography.labelLarge.includeFontPadding, spec.includeFontPadding)
    }

    @Test
    fun `segmented control uses disabled color override tokens`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideSegmentedControlColors(
                    SegmentedControlColorOverride(
                        backgroundDisabled = 502,
                        indicatorDisabled = 504,
                        textDisabled = 506,
                        selectedTextDisabled = 508,
                    ),
                ) {
                    SegmentedControl(
                        items = listOf("A", "B"),
                        selectedIndex = 0,
                        enabled = false,
                        onSelectionChange = {},
                    )
                }
            }
        }

        val spec = tree.single().spec as SegmentedControlNodeProps

        assertEquals(false, spec.enabled)
        assertEquals(502, spec.backgroundColor)
        assertEquals(504, spec.indicatorColor)
        assertEquals(506, spec.textColor)
        assertEquals(508, spec.selectedTextColor)
        assertEquals(0x00000000, spec.rippleColor)
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = javaClass.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }
}
