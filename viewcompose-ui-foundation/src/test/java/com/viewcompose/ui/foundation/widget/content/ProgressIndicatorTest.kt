package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core widget/content 中的 Progress Indicator 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Progress Indicator behavior in widget-core widget/content and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressIndicatorTest {
    @Test
    fun `linear progress indicator emits themed defaults`() {
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
                onSurface = 7,
                onSurfaceVariant = 8,
                outline = 6,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 30.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 16.sp),
                labelMedium = UiTextStyle(fontSizeSp = 14.sp),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                LinearProgressIndicator(progress = 0.42f)
            }
        }

        val node = tree.single()
        val spec = node.spec as ProgressIndicatorNodeProps
        val elements = node.modifier.readModifierElements()
        val width = elements.last { it is WidthModifierElement } as WidthModifierElement
        val height = elements.last { it is HeightModifierElement } as HeightModifierElement

        assertEquals(NodeType.LinearProgressIndicator, node.type)
        assertEquals(0.42f, spec.progress)
        assertEquals(customTheme.colors.primary, spec.indicatorColor)
        assertEquals(customTheme.colors.outlineVariant, spec.trackColor)
        assertEquals(customTheme.controls.progressIndicator.linearTrackThickness, spec.trackThickness)
        assertEquals(UiDimension.MatchParent, width.width)
        assertEquals(
            UiDimension.Exact(customTheme.controls.progressIndicator.linearTrackThickness),
            height.height,
        )
        assertTrue(node.spec is ProgressIndicatorNodeProps)
    }

    @Test
    fun `circular progress indicator emits themed defaults`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 11,
                surface = 12,
                surfaceVariant = 13,
                primary = 14,
                secondary = 15,
                error = 19,
                success = 20,
                warning = 21,
                info = 22,
                onSurface = 17,
                onSurfaceVariant = 18,
                outline = 16,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 28.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 16.sp),
                labelMedium = UiTextStyle(fontSizeSp = 13.sp),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                CircularProgressIndicator()
            }
        }

        val node = tree.single()
        val spec = node.spec as ProgressIndicatorNodeProps
        val size = node.modifier.readModifierElements().last { it is SizeModifierElement } as SizeModifierElement

        assertEquals(NodeType.CircularProgressIndicator, node.type)
        assertEquals(null, spec.progress)
        assertEquals(customTheme.colors.primary, spec.indicatorColor)
        assertEquals(customTheme.colors.outlineVariant, spec.trackColor)
        assertEquals(customTheme.controls.progressIndicator.circularTrackThickness, spec.trackThickness)
        assertEquals(customTheme.controls.progressIndicator.circularSize, spec.indicatorSize)
        assertEquals(UiDimension.Exact(customTheme.controls.progressIndicator.circularSize), size.width)
        assertEquals(UiDimension.Exact(customTheme.controls.progressIndicator.circularSize), size.height)
        assertTrue(node.spec is ProgressIndicatorNodeProps)
    }

    @Test
    fun `progress indicator uses color overrides`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideProgressIndicatorColors(
                    ProgressIndicatorColorOverride(
                        linearIndicator = 701,
                        linearTrack = 702,
                        circularIndicator = 703,
                        circularTrack = 704,
                    ),
                ) {
                    Column {
                        LinearProgressIndicator(progress = 0.4f)
                        CircularProgressIndicator(progress = 0.6f)
                    }
                }
            }
        }

        val linearSpec = tree.single().children[0].spec as ProgressIndicatorNodeProps
        val circularSpec = tree.single().children[1].spec as ProgressIndicatorNodeProps

        assertEquals(701, linearSpec.indicatorColor)
        assertEquals(702, linearSpec.trackColor)
        assertEquals(703, circularSpec.indicatorColor)
        assertEquals(704, circularSpec.trackColor)
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = javaClass.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }
}
