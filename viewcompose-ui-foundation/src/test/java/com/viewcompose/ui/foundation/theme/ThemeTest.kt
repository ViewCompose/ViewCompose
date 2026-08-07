package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core theme 中的 Theme 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Theme behavior in widget-core theme and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.modifier.AlphaModifierElement
import com.viewcompose.ui.modifier.BackgroundColorModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.ShapeModifierElement
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.shape.UiShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `text uses current theme text color by default`() {
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
                bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
                labelMedium = UiTextStyle(fontSizeSp = 12.sp),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                Text("Hello")
            }
        }

        val spec = tree.single().spec as TextNodeProps
        assertEquals(7, spec.textColor)
        assertEquals(18.sp, spec.textSizeSp)
    }

    @Test
    fun `divider uses current theme divider color by default`() {
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
                outline = 42,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 30.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
                labelMedium = UiTextStyle(fontSizeSp = 12.sp),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                Divider()
            }
        }

        val spec = tree.single().spec as DividerNodeProps
        assertEquals(42, spec.color)
    }

    @Test
    fun `nested theme overrides parent colors`() {
        val outerTheme = UiThemeDefaults.light()
        val innerTheme = UiThemeDefaults.dark()
        var outerColor = 0
        var innerColor = 0
        var outerSize = 0.sp
        var innerSize = 0.sp

        buildVNodeTree {
            UiTheme(outerTheme) {
                outerColor = Theme.colors.onSurface
                outerSize = Theme.typography.bodyMedium.fontSizeSp
                UiTheme(innerTheme) {
                    innerColor = Theme.colors.onSurface
                    innerSize = Theme.typography.bodyMedium.fontSizeSp
                }
            }
        }

        assertEquals(outerTheme.colors.onSurface, outerColor)
        assertEquals(innerTheme.colors.onSurface, innerColor)
        assertEquals(outerTheme.typography.bodyMedium.fontSizeSp, outerSize)
        assertEquals(innerTheme.typography.bodyMedium.fontSizeSp, innerSize)
    }

    @Test
    fun `theme override rebases derived domains when colors change`() {
        val baseTheme = UiThemeDefaults.light()
        var primary = 0
        var activatedControl = 0
        var bodySize = 0.sp

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    colors = baseTheme.colors.copy(primary = 0xFF225577.toInt()),
                ) {
                    primary = Theme.colors.primary
                    activatedControl = Theme.stateColors.controlActivated.defaultColor
                    bodySize = Theme.typography.bodyMedium.fontSizeSp
                }
            }
        }

        assertEquals(0xFF225577.toInt(), primary)
        assertEquals(0xFF225577.toInt(), activatedControl)
        assertEquals(baseTheme.typography.bodyMedium.fontSizeSp, bodySize)
    }

    @Test
    fun `state color resolution follows Android state precedence`() {
        val colors = UiStateColor(
            defaultColor = 1,
            disabledColor = 2,
            pressedColor = 3,
            focusedColor = 4,
            checkedColor = 5,
            selectedColor = 6,
        )

        assertEquals(1, colors.resolve())
        assertEquals(2, colors.resolve(enabled = false, pressed = true))
        assertEquals(3, colors.resolve(pressed = true, focused = true))
        assertEquals(4, colors.resolve(focused = true, checked = true))
        assertEquals(5, colors.resolve(checked = true, selected = true))
        assertEquals(6, colors.resolve(selected = true))
    }

    @Test
    fun `defaults derive from colors and follow color override`() {
        val baseTheme = UiThemeDefaults.light()
        var buttonContainer = 0
        var progressIndicator = 0
        var segmentedIndicator = 0

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    colors = baseTheme.colors.copy(primary = 0xFF225577.toInt()),
                ) {
                    buttonContainer = ButtonDefaults.containerColor(ButtonVariant.Primary)
                    progressIndicator = ProgressIndicatorDefaults.linearIndicatorColor()
                    segmentedIndicator = SegmentedControlDefaults.indicatorColor()
                }
            }
        }

        assertEquals(0xFF225577.toInt(), buttonContainer)
        assertEquals(0xFF225577.toInt(), progressIndicator)
        assertEquals(baseTheme.colors.secondaryContainer, segmentedIndicator)
    }

    @Test
    fun `nested theme override prefers innermost values`() {
        val baseTheme = UiThemeDefaults.light()
        var resolvedPrimary = 0

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    colors = baseTheme.colors.copy(primary = 0xFF111111.toInt()),
                ) {
                    UiThemeOverride(
                        colors = Theme.colors.copy(primary = 0xFF222222.toInt()),
                    ) {
                        resolvedPrimary = Theme.colors.primary
                    }
                }
            }
        }

        assertEquals(0xFF222222.toInt(), resolvedPrimary)
    }

    @Test
    fun `theme override scope restores parent theme after exit`() {
        val baseTheme = UiThemeDefaults.light()
        var insidePrimary = 0
        var outsidePrimary = 0

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    colors = baseTheme.colors.copy(primary = 0xFF335577.toInt()),
                ) {
                    insidePrimary = Theme.colors.primary
                }
                outsidePrimary = Theme.colors.primary
            }
        }

        assertEquals(0xFF335577.toInt(), insidePrimary)
        assertEquals(baseTheme.colors.primary, outsidePrimary)
    }

    @Test
    fun `builder theme override updates selected domains`() {
        val baseTheme = UiThemeDefaults.light()
        var resolvedPrimary = 0
        var resolvedShape = UiShape.rounded(0.dp)
        var unchangedBody = 0.sp

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    colors = { copy(primary = 0xFF445566.toInt()) },
                    shapes = { copy(small = UiShape.rounded(77.dp)) },
                ) {
                    resolvedPrimary = Theme.colors.primary
                    resolvedShape = Theme.shapes.small
                    unchangedBody = Theme.typography.bodyMedium.fontSizeSp
                }
            }
        }

        assertEquals(0xFF445566.toInt(), resolvedPrimary)
        assertEquals(UiShape.rounded(77.dp), resolvedShape)
        assertEquals(baseTheme.typography.bodyMedium.fontSizeSp, unchangedBody)
    }

    @Test
    fun `builder theme override composes with object override`() {
        val baseTheme = UiThemeDefaults.light()
        var resolvedPrimary = 0

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    colors = baseTheme.colors.copy(primary = 0xFF111111.toInt()),
                ) {
                    UiThemeOverride(
                        colors = { copy(primary = 0xFF222222.toInt()) },
                    ) {
                        resolvedPrimary = Theme.colors.primary
                    }
                }
            }
        }

        assertEquals(0xFF222222.toInt(), resolvedPrimary)
    }

    @Test
    fun `theme override preserves explicit shape overrides while changing colors`() {
        val baseTheme = UiThemeDefaults.light()
        var shape = UiShape.rounded(0.dp)

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    shapes = { copy(small = UiShape.rounded(99.dp)) },
                ) {
                    UiThemeOverride(
                        colors = { copy(primary = 0xFF778899.toInt()) },
                    ) {
                        shape = Theme.shapes.small
                    }
                }
            }
        }

        assertEquals(UiShape.rounded(99.dp), shape)
    }

    @Test
    fun `button uses themed container and readable content colors`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 1,
                surface = 2,
                surfaceVariant = 3,
                primary = 0xFF102030.toInt(),
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
                bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
                labelMedium = UiTextStyle(fontSizeSp = 12.sp),
            ),
        )

        val tree = buildVNodeTree {
            UiTheme(customTheme) {
                Button("Tap")
            }
        }

        val spec = tree.single().spec as ButtonNodeProps
        assertEquals(customTheme.colors.primary, spec.backgroundColor)
        assertEquals(customTheme.shapes.full, spec.shape)
        assertEquals(pressedOverlayColorFor(customTheme.colors.onSurface), spec.rippleColor)
        assertEquals(0xFFFFFFFF.toInt(), spec.textColor)
        assertEquals(customTheme.typography.labelLarge.fontSizeSp, spec.textSizeSp)
        assertEquals(customTheme.controls.button.mediumHeight, ButtonDefaults.height())
        assertEquals(customTheme.controls.button.mediumHeight, spec.minHeight)
    }

    @Test
    fun `outlined button uses transparent container and semantic outline border`() {
        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                Button(
                    text = "Outline",
                    variant = ButtonVariant.Outlined,
                )
            }
        }

        val spec = tree.single().spec as ButtonNodeProps
        assertEquals(0x00000000, spec.backgroundColor)
        assertEquals(Theme.colors.outline, spec.borderColor)
        assertEquals(1.dp, spec.borderWidth)
        assertEquals(Theme.colors.primary, spec.textColor)
    }

    @Test
    fun `disabled button emits enabled prop and themed disabled styles`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                Button(
                    text = "Disabled",
                    enabled = false,
                )
            }
        }

        val spec = tree.single().spec as ButtonNodeProps

        assertEquals(false, spec.enabled)
        assertEquals(colorWithAlpha(baseTheme.colors.onSurface, 0.12f), spec.backgroundColor)
        assertEquals(colorWithAlpha(baseTheme.colors.onSurface, 0.38f), spec.textColor)
    }

    @Test
    fun `button color override changes targeted colors`() {
        val baseTheme = UiThemeDefaults.light()

        val tree = buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideButtonColors(
                    ButtonColorOverride(
                        primaryContainer = 0xFF556677.toInt(),
                        primaryDisabledContainer = 103,
                        primaryDisabledContent = 104,
                    ),
                ) {
                    Button(text = "Custom", enabled = false)
                }
            }
        }

        val spec = tree.single().spec as ButtonNodeProps

        assertEquals(103, spec.backgroundColor)
        assertEquals(104, spec.textColor)
    }

    @Test
    fun `explicit button modifier overrides theme override background`() {
        val overriddenPrimary = 0xFF123456.toInt()
        val explicitBackground = 0xFF654321.toInt()

        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                UiThemeOverride(
                    colors = Theme.colors.copy(primary = overriddenPrimary),
                ) {
                    Button(
                        text = "Override",
                        modifier = Modifier
                            .backgroundColor(explicitBackground),
                    )
                }
            }
        }

        val elements = tree.single().modifier.readModifierElements()
        val background = elements.last { it is BackgroundColorModifierElement } as BackgroundColorModifierElement

        assertEquals(explicitBackground, background.color)
    }

    @Test
    fun `button sizing defaults reflect current theme`() {
        val baseTheme = UiThemeDefaults.light()
        val customTheme = UiThemeTokens(
            colors = baseTheme.colors,
            typography = baseTheme.typography,
            controls = UiControlSizing(
                button = UiButtonSizing(
                    compactHeight = 31.dp,
                    mediumHeight = 41.dp,
                    largeHeight = 51.dp,
                    compactHorizontalPadding = 11.dp,
                    mediumHorizontalPadding = 21.dp,
                    largeHorizontalPadding = 31.dp,
                    compactVerticalPadding = 6.dp,
                    mediumVerticalPadding = 8.dp,
                    largeVerticalPadding = 10.dp,
                ),
                textField = UiControlSizeDefaults.default().textField,
                segmentedControl = UiControlSizeDefaults.default().segmentedControl,
                progressIndicator = UiControlSizeDefaults.default().progressIndicator,
            ),
        )
        var compactHeight = 0.dp
        var largeHorizontalPadding = 0.dp
        var largeStyle = 0.sp

        buildVNodeTree {
            UiTheme(customTheme) {
                compactHeight = ButtonDefaults.height(ButtonSize.Compact)
                largeHorizontalPadding = ButtonDefaults.horizontalPadding(ButtonSize.Large)
                largeStyle = ButtonDefaults.textStyle(ButtonSize.Large).fontSizeSp
            }
        }

        assertEquals(31.dp, compactHeight)
        assertEquals(31.dp, largeHorizontalPadding)
        assertEquals(customTheme.typography.bodyLarge.fontSizeSp, largeStyle)
    }

    @Test
    fun `component defaults derive from theme colors`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 1,
                surface = 2,
                surfaceVariant = 3,
                primary = 0xFF112233.toInt(),
                secondary = 0xFF445566.toInt(),
                error = 0xFFB3261E.toInt(),
                success = 0xFF2E7D32.toInt(),
                warning = 0xFFF57C00.toInt(),
                info = 0xFF1565C0.toInt(),
                onSurface = 0xFFAABBCC.toInt(),
                onSurfaceVariant = 0xFFDDEEFF.toInt(),
                outline = 0xFF778899.toInt(),
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 30.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
                labelMedium = UiTextStyle(fontSizeSp = 12.sp),
            ),
        )
        var buttonTonal = 0
        var outlinedBorder = 0
        var disabledPrimary = 0
        var linearProgressTrack = 0
        var segmentedIndicator = 0

        buildVNodeTree {
            UiTheme(customTheme) {
                buttonTonal = ButtonDefaults.containerColor(ButtonVariant.Tonal)
                outlinedBorder = ButtonDefaults.borderColor(ButtonVariant.Outlined)
                disabledPrimary = ButtonDefaults.containerColor(ButtonVariant.Primary, enabled = false)
                linearProgressTrack = ProgressIndicatorDefaults.linearTrackColor()
                segmentedIndicator = SegmentedControlDefaults.indicatorColor()
            }
        }

        assertEquals(customTheme.colors.secondaryContainer, buttonTonal)
        assertEquals(customTheme.colors.outline, outlinedBorder)
        assertEquals(colorWithAlpha(customTheme.colors.onSurface, 0.12f), disabledPrimary)
        assertEquals(customTheme.colors.secondaryContainer, linearProgressTrack)
        assertEquals(customTheme.colors.secondaryContainer, segmentedIndicator)
    }

    @Test
    fun `text defaults reflect current theme`() {
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
                bodyMedium = UiTextStyle(fontSizeSp = 19.sp),
                labelMedium = UiTextStyle(fontSizeSp = 13.sp),
            ),
        )
        var primaryColor = 0
        var secondaryColor = 0
        var titleSize = 0.sp

        buildVNodeTree {
            UiTheme(customTheme) {
                primaryColor = TextDefaults.primaryColor()
                secondaryColor = TextDefaults.secondaryColor()
                titleSize = TextDefaults.titleStyle().fontSizeSp
            }
        }

        assertEquals(customTheme.colors.onSurface, primaryColor)
        assertEquals(customTheme.colors.onSurfaceVariant, secondaryColor)
        assertEquals(customTheme.typography.titleMedium.fontSizeSp, titleSize)
    }

    @Test
    fun `surface and divider defaults reflect current theme`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 1,
                surface = 22,
                surfaceVariant = 33,
                primary = 4,
                secondary = 5,
                error = 9,
                success = 10,
                warning = 11,
                info = 12,
                onSurface = 70,
                onSurfaceVariant = 80,
                outline = 66,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 31.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 19.sp),
                labelMedium = UiTextStyle(fontSizeSp = 13.sp),
            ),
        )
        var surface = 0
        var surfaceVariant = 0
        var divider = 0

        buildVNodeTree {
            UiTheme(customTheme) {
                surface = SurfaceDefaults.backgroundColor()
                surfaceVariant = SurfaceDefaults.variantBackgroundColor()
                divider = DividerDefaults.color()
            }
        }

        assertEquals(customTheme.colors.surface, surface)
        assertEquals(customTheme.colors.surfaceVariant, surfaceVariant)
        assertEquals(customTheme.colors.outlineVariant, divider)
    }

    @Test
    fun `surface emits semantic node with themed defaults`() {
        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                Surface(
                    variant = SurfaceVariant.Variant,
                    enabled = false,
                ) {
                    Text("Inside")
                }
            }
        }

        val surface = tree.single()
        val elements = surface.modifier.readModifierElements()
        val background = elements.last { it is BackgroundColorModifierElement } as BackgroundColorModifierElement
        val shape = elements.last { it is ShapeModifierElement } as ShapeModifierElement
        val alpha = elements.last { it is AlphaModifierElement } as AlphaModifierElement
        val spec = surface.spec as BoxNodeProps

        assertEquals(NodeType.Surface, surface.type)
        assertEquals(SurfaceDefaults.variantBackgroundColor(), background.color)
        assertEquals(SurfaceDefaults.shape(), shape.shape)
        assertEquals(SurfaceDefaults.pressedColor(), spec.rippleColor)
        assertEquals(SurfaceDefaults.disabledAlpha(), alpha.alpha)
        assertTrue(surface.spec is BoxNodeProps)
    }

    @Test
    fun `surface provides local content color to nested text`() {
        val tree = buildVNodeTree {
            UiTheme(UiThemeDefaults.light()) {
                Surface(
                    variant = SurfaceVariant.Variant,
                ) {
                    Text("Inside")
                }
            }
        }

        val text = tree.single().children.single()
        val spec = text.spec as TextNodeProps

        assertEquals(SurfaceDefaults.variantContentColor(), spec.textColor)
    }

    @Test
    fun `input defaults reflect current theme`() {
        val customTheme = UiThemeTokens(
            colors = UiColors(
                background = 1,
                surface = 22,
                surfaceVariant = 33,
                primary = 44,
                secondary = 55,
                error = 9,
                success = 10,
                warning = 11,
                info = 12,
                onSurface = 77,
                onSurfaceVariant = 88,
                outline = 66,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 31.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 19.sp),
                labelMedium = UiTextStyle(fontSizeSp = 13.sp),
            ),
        )
        var container = 0
        var disabledContainer = 0
        var errorColor = 0
        var controlColor = 0
        var surfaceShape = UiShape.rounded(0.dp)
        var pressedColor = 0

        buildVNodeTree {
            UiTheme(customTheme) {
                container = TextFieldDefaults.containerColor()
                disabledContainer = TextFieldDefaults.containerColor(enabled = false)
                errorColor = TextFieldDefaults.hintColor(isError = true)
                controlColor = InputControlDefaults.checkboxControlColor()
                surfaceShape = SurfaceDefaults.shape()
                pressedColor = SurfaceDefaults.pressedColor()
            }
        }

        assertEquals(customTheme.colors.surfaceContainerHighest, container)
        assertEquals(customTheme.colors.surfaceContainerHighest, disabledContainer)
        assertEquals(customTheme.colors.onSurfaceVariant, errorColor)
        assertEquals(customTheme.colors.primary, controlColor)
        assertEquals(customTheme.shapes.medium, surfaceShape)
        assertEquals(pressedOverlayColorFor(customTheme.colors.onSurface), pressedColor)
    }

    @Test
    fun `text field sizing defaults reflect current theme`() {
        val baseTheme = UiThemeDefaults.light()
        val customTheme = UiThemeTokens(
            colors = baseTheme.colors,
            typography = baseTheme.typography,
            controls = UiControlSizing(
                button = UiControlSizeDefaults.default().button,
                textField = UiTextFieldSizing(
                    compactHeight = 35.dp,
                    mediumHeight = 45.dp,
                    largeHeight = 55.dp,
                    compactHorizontalPadding = 12.dp,
                    mediumHorizontalPadding = 16.dp,
                    largeHorizontalPadding = 20.dp,
                    compactVerticalPadding = 5.dp,
                    mediumVerticalPadding = 7.dp,
                    largeVerticalPadding = 9.dp,
                ),
                segmentedControl = UiControlSizeDefaults.default().segmentedControl,
                progressIndicator = UiControlSizeDefaults.default().progressIndicator,
            ),
        )
        var compactHeight = 0.dp
        var mediumVerticalPadding = 0.dp
        var compactStyle = 0.sp

        buildVNodeTree {
            UiTheme(customTheme) {
                compactHeight = TextFieldDefaults.height(TextFieldSize.Compact)
                mediumVerticalPadding = TextFieldDefaults.verticalPadding(TextFieldSize.Medium)
                compactStyle = TextFieldDefaults.textStyle(TextFieldSize.Compact).fontSizeSp
            }
        }

        assertEquals(35.dp, compactHeight)
        assertEquals(7.dp, mediumVerticalPadding)
        assertEquals(customTheme.typography.labelSmall.fontSizeSp, compactStyle)
    }

    @Test
    fun `theme current exposes fully overridden tokens`() {
        val baseTheme = UiThemeDefaults.light()
        val overrideShapes = baseTheme.shapes.copy(small = UiShape.rounded(99.dp))
        var current: UiThemeTokens? = null

        buildVNodeTree {
            UiTheme(baseTheme) {
                UiThemeOverride(
                    shapes = overrideShapes,
                ) {
                    current = Theme.current
                }
            }
        }

        assertEquals(overrideShapes, current?.shapes)
        assertEquals(baseTheme.colors, current?.colors)
    }

    @Test
    fun `color override via ProvideButtonColors changes targeted defaults`() {
        val baseTheme = UiThemeDefaults.light()
        var buttonPrimary = 0
        var segmentedIndicator = 0
        var disabledControl = 0
        var baseTextField = 0

        buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideButtonColors(
                    ButtonColorOverride(
                        primaryContainer = 0xFF778899.toInt(),
                    ),
                ) {
                    buttonPrimary = ButtonDefaults.containerColor(ButtonVariant.Primary)
                }
                ProvideSegmentedControlColors(
                    SegmentedControlColorOverride(
                        indicator = 0xFF998877.toInt(),
                    ),
                ) {
                    segmentedIndicator = SegmentedControlDefaults.indicatorColor()
                }
                ProvideCheckboxColors(
                    InputControlColorOverride(
                        controlDisabled = 0xFF556677.toInt(),
                    ),
                ) {
                    disabledControl = InputControlDefaults.checkboxControlColor(enabled = false)
                }
                baseTextField = TextFieldDefaults.containerColor(TextFieldVariant.Filled)
            }
        }

        assertEquals(0xFF778899.toInt(), buttonPrimary)
        assertEquals(0xFF998877.toInt(), segmentedIndicator)
        assertEquals(0xFF556677.toInt(), disabledControl)
        assertEquals(baseTheme.colors.surfaceContainerHighest, baseTextField)
    }

    @Test
    fun `color override supports disabled button and text field states`() {
        val baseTheme = UiThemeDefaults.light()
        var disabledButton = 0
        var disabledField = 0
        var errorField = 0

        buildVNodeTree {
            UiTheme(baseTheme) {
                ProvideButtonColors(
                    ButtonColorOverride(
                        primaryDisabledContainer = 0xFF111122.toInt(),
                    ),
                ) {
                    disabledButton = ButtonDefaults.containerColor(
                        variant = ButtonVariant.Primary,
                        enabled = false,
                    )
                }
                ProvideTextFieldColors(
                    TextFieldColorOverride(
                        filledDisabledContainer = 0xFF222233.toInt(),
                        outlinedErrorBorder = 0xFF333344.toInt(),
                    ),
                ) {
                    disabledField = TextFieldDefaults.containerColor(
                        variant = TextFieldVariant.Filled,
                        enabled = false,
                    )
                    errorField = TextFieldDefaults.borderColor(
                        variant = TextFieldVariant.Outlined,
                        isError = true,
                    )
                }
            }
        }

        assertEquals(0xFF111122.toInt(), disabledButton)
        assertEquals(0xFF222233.toInt(), disabledField)
        assertEquals(0xFF333344.toInt(), errorField)
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = com.viewcompose.ui.modifier.Modifier::class.java.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }
}
