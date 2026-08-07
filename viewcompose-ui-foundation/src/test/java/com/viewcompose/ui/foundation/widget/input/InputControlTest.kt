package com.viewcompose.ui.foundation

/*
 * Covers input-control DSL, state, and theme contracts so selection colors cannot silently fall
 * back to unrelated Android compatibility attributes.
 */

import com.viewcompose.ui.modifier.MinHeightModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputControlTest {
    @Test
    fun `checkbox emits checked props and themed text props`() {
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
                    fontWeight = 600,
                    letterSpacingEm = 0.02f,
                    lineHeightSp = 24.sp,
                    includeFontPadding = true,
                ),
                labelMedium = UiTextStyle(fontSizeSp = 13.sp),
            ),
        )
        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                Checkbox(
                    text = "Enable logs",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }

        val node = tree.single()
        val spec = node.spec as ToggleNodeProps

        assertEquals(NodeType.Checkbox, node.type)
        assertEquals("Enable logs", spec.text)
        assertEquals(true, spec.checked)
        assertEquals(true, spec.enabled)
        assertEquals(customTheme.colors.primary, spec.controlColor)
        assertEquals(customTheme.colors.onSurface, spec.textColor)
        assertEquals(customTheme.typography.bodyMedium.fontSizeSp, spec.textSizeSp)
        assertEquals(customTheme.typography.bodyMedium.fontWeight, spec.fontWeight)
        assertEquals(customTheme.typography.bodyMedium.letterSpacingEm, spec.letterSpacingEm)
        assertEquals(customTheme.typography.bodyMedium.lineHeightSp, spec.lineHeightSp)
        assertEquals(customTheme.typography.bodyMedium.includeFontPadding, spec.includeFontPadding)
        assertEquals(pressedOverlayColorFor(customTheme.colors.onSurface), spec.rippleColor)
        assertEquals(
            customTheme.controls.minimumInteractiveHeight,
            (node.modifier.elements.first() as MinHeightModifierElement).minHeight,
        )
        assertTrue(node.spec is ToggleNodeProps)
    }

    @Test
    fun `slider emits range and value props`() {
        val tree = buildVNodeTree {
            Slider(
                value = 24,
                onValueChange = {},
                min = 10,
                max = 60,
                enabled = false,
            )
        }

        val node = tree.single()
        val spec = node.spec as SliderNodeProps

        assertEquals(NodeType.Slider, node.type)
        assertEquals(24, spec.value)
        assertEquals(10, spec.min)
        assertEquals(60, spec.max)
        assertEquals(false, spec.enabled)
        assertEquals(
            UiControlSizeDefaults.default().minimumInteractiveHeight,
            (node.modifier.elements.first() as MinHeightModifierElement).minHeight,
        )
        assertTrue(node.spec is SliderNodeProps)
    }

    @Test
    fun `radio button and switch keep enabled prop`() {
        val tree = buildVNodeTree {
            Column {
                RadioButton(
                    text = "Alpha",
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
                Switch(
                    text = "Beta",
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }

        val radioSpec = tree[0].children[0].spec as ToggleNodeProps
        val switchSpec = tree[0].children[1].spec as ToggleNodeProps

        assertEquals(NodeType.RadioButton, tree[0].children[0].type)
        assertEquals(NodeType.Switch, tree[0].children[1].type)
        assertEquals(false, radioSpec.enabled)
        assertTrue(switchSpec.enabled)
        assertEquals(InputControlDefaults.pressedColor(), radioSpec.rippleColor)
        assertEquals(InputControlDefaults.pressedColor(), switchSpec.rippleColor)
    }

    @Test
    fun `input control color override supports disabled state`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideCheckboxColors(
                    InputControlColorOverride(
                        label = 101,
                        labelDisabled = 102,
                        control = 103,
                        controlDisabled = 104,
                    ),
                ) {
                    Checkbox(
                        text = "Disabled",
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                    )
                }
            }
        }

        val spec = tree.single().spec as ToggleNodeProps

        assertEquals(104, spec.controlColor)
        assertEquals(102, spec.textColor)
    }

    @Test
    fun `input controls resolve independent color overrides`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideCheckboxColors(InputControlColorOverride(control = 203)) {
                    ProvideSwitchColors(InputControlColorOverride(control = 207)) {
                        ProvideRadioButtonColors(InputControlColorOverride(control = 211)) {
                            ProvideSliderColors(InputControlColorOverride(control = 213)) {
                                Column {
                                    Checkbox(
                                        text = "Checkbox",
                                        checked = true,
                                        onCheckedChange = {},
                                    )
                                    Switch(
                                        text = "Switch",
                                        checked = true,
                                        onCheckedChange = {},
                                    )
                                    RadioButton(
                                        text = "Radio",
                                        checked = true,
                                        onCheckedChange = {},
                                    )
                                    Slider(
                                        value = 12,
                                        onValueChange = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val root = tree.single()
        val checkboxSpec = root.children[0].spec as ToggleNodeProps
        val switchSpec = root.children[1].spec as ToggleNodeProps
        val radioSpec = root.children[2].spec as ToggleNodeProps
        val sliderSpec = root.children[3].spec as SliderNodeProps

        assertEquals(203, checkboxSpec.controlColor)
        assertEquals(207, switchSpec.controlColor)
        assertEquals(211, radioSpec.controlColor)
        assertEquals(213, sliderSpec.thumbColor)
        assertEquals(213, sliderSpec.inactiveTrackColor)
    }

    @Test
    fun `selection controls use semantic primary instead of legacy activated color`() {
        val primary = 0xFF246B4A.toInt()
        val inactiveTrack = 0xFFDDE8DF.toInt()
        val legacyActivated = 0xFF8D6BA8.toInt()
        val baseTheme = UiThemeDefaults.light()
        val theme = baseTheme.copy(
            colors = baseTheme.colors.copy(
                primary = primary,
                secondaryContainer = inactiveTrack,
            ),
            stateColors = baseTheme.stateColors.copy(
                controlActivated = baseTheme.stateColors.controlActivated.copy(
                    defaultColor = legacyActivated,
                    checkedColor = legacyActivated,
                ),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(theme) {
                Column {
                    Checkbox(text = "Checkbox", checked = true, onCheckedChange = {})
                    Switch(text = "Switch", checked = true, onCheckedChange = {})
                    RadioButton(text = "Radio", checked = true, onCheckedChange = {})
                    Slider(value = 50, onValueChange = {})
                }
            }
        }

        val checkbox = tree.single().children[0].spec as ToggleNodeProps
        val switch = tree.single().children[1].spec as ToggleNodeProps
        val radio = tree.single().children[2].spec as ToggleNodeProps
        val slider = tree.single().children[3].spec as SliderNodeProps

        assertEquals(primary, checkbox.controlColor)
        assertEquals(primary, checkbox.checkedColor)
        assertEquals(primary, switch.controlColor)
        assertEquals(primary, switch.trackColor)
        assertEquals(primary, radio.controlColor)
        assertEquals(primary, radio.checkedColor)
        assertEquals(primary, slider.thumbColor)
        assertEquals(primary, slider.trackColor)
        assertEquals(inactiveTrack, slider.inactiveTrackColor)
    }
}
