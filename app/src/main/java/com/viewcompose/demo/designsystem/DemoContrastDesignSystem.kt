package com.viewcompose

import com.viewcompose.animation.animateColorAsState
import com.viewcompose.animation.animateDpAsState
import com.viewcompose.animation.core.MotionRole
import com.viewcompose.animation.core.MotionScheme
import com.viewcompose.animation.core.ReducedMotionBehavior
import com.viewcompose.animation.core.ReducedMotionPolicy
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.graphics.core.Brush
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.BasicButton
import com.viewcompose.ui.foundation.BasicButtonStyle
import com.viewcompose.ui.foundation.BasicSurface
import com.viewcompose.ui.foundation.BasicSurfaceStyle
import com.viewcompose.ui.foundation.BasicTextField
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiColors
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiShapes
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.offset
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/** Internal recipe families used to expose accidental Material fallback in screenshots. */
internal enum class DemoDesignSystemKind(
    val id: String,
    val label: String,
) {
    RoundedReference("rounded-reference", "Rounded reference"),
    CutContrast("cut-contrast", "Cut contrast"),
    CupertinoPressure("cupertino-pressure", "Cupertino pressure"),
    ;

    companion object {
        fun fromId(id: String?): DemoDesignSystemKind {
            return entries.firstOrNull { kind -> kind.id == id } ?: CutContrast
        }
    }
}

internal enum class DemoConformanceOutcome {
    Exact,
    Equivalent,
    Degraded,
    Unsupported,
}

internal data class DemoComponentConformance(
    val component: String,
    val outcome: DemoConformanceOutcome,
    val implementation: String,
    val fallback: String = "none",
)

internal enum class DemoControlPlacement {
    Leading,
    Trailing,
}

internal data class DemoActionRecipe(
    val enabledContainerColor: Int,
    val disabledContainerColor: Int,
    val enabledContentColor: Int,
    val disabledContentColor: Int,
    val borderColor: Int,
    val borderWidth: UiDp,
    val shape: UiShape,
    val minimumHeight: UiDp,
    val visualHeight: UiDp,
    val horizontalPadding: UiDp,
    val textStyle: UiTextStyle,
    val stateLayers: UiStateLayerColors,
)

internal data class DemoSurfaceRecipe(
    val containerColor: Int,
    val contentColor: Int,
    val borderColor: Int,
    val borderWidth: UiDp,
    val shape: UiShape,
    val stateLayers: UiStateLayerColors,
)

internal data class DemoSwitchRecipe(
    val placement: DemoControlPlacement,
    val checkedTrackColor: Int,
    val uncheckedTrackColor: Int,
    val disabledTrackColor: Int,
    val checkedThumbColor: Int,
    val uncheckedThumbColor: Int,
    val disabledThumbColor: Int,
    val labelColor: Int,
    val disabledLabelColor: Int,
    val trackShape: UiShape,
    val thumbShape: UiShape,
    val trackWidth: UiDp,
    val trackHeight: UiDp,
    val trackPadding: UiDp,
    val thumbSize: UiDp,
    val labelSpacing: UiDp,
    val stateLayers: UiStateLayerColors,
)

internal data class DemoTextFieldRecipe(
    val stackedLabel: Boolean,
    val containerColor: Int,
    val errorContainerColor: Int,
    val textColor: Int,
    val hintColor: Int,
    val labelColor: Int,
    val errorColor: Int,
    val borderColor: Int,
    val borderWidth: UiDp,
    val errorBorderWidth: UiDp,
    val shape: UiShape,
    val minimumHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
    val textStyle: UiTextStyle,
    val labelStyle: UiTextStyle,
    val supportingStyle: UiTextStyle,
)

internal data class DemoNavigationRecipe(
    val containerColor: Int,
    val containerShape: UiShape,
    val selectedColor: Int,
    val unselectedColor: Int,
    val indicatorColor: Int,
    val indicatorShape: UiShape,
    val height: UiDp,
    val iconSize: UiDp,
    val selectedOnlyLabels: Boolean,
    val indicatorVisible: Boolean,
    val selectedLabelColor: Int,
    val stateLayers: UiStateLayerColors,
)

internal data class DemoSegmentedRecipe(
    val containerColor: Int,
    val selectedContainerColor: Int,
    val selectedContentColor: Int,
    val unselectedContentColor: Int,
    val containerShape: UiShape,
    val itemShape: UiShape,
    val height: UiDp,
    val borderColor: Int,
    val stateLayers: UiStateLayerColors,
)

internal data class DemoComponentRecipes(
    val action: DemoActionRecipe,
    val surface: DemoSurfaceRecipe,
    val switch: DemoSwitchRecipe,
    val textField: DemoTextFieldRecipe,
    val navigation: DemoNavigationRecipe,
    val segmented: DemoSegmentedRecipe,
)

internal data class DemoDesignSystemBundle(
    val kind: DemoDesignSystemKind,
    val tokens: UiThemeTokens,
    val recipes: DemoComponentRecipes,
    val motion: MotionScheme,
    val reducedMotionEnabled: Boolean,
    val conformance: List<DemoComponentConformance>,
)

private val LocalDemoDesignSystem = uiLocalOf<DemoDesignSystemBundle?>(
    debugName = "DemoDesignSystemBundle",
    debugValueFormatter = { bundle -> bundle?.kind?.id ?: "absent" },
) { null }

internal fun UiTreeBuilder.ProvideDemoDesignSystem(
    bundle: DemoDesignSystemBundle,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalDemoDesignSystem, bundle, content)
}

internal val DemoDesignSystem: DemoDesignSystemBundle
    get() = requireNotNull(UiLocals.current(LocalDemoDesignSystem)) {
        "Demo design-system components require ProvideDemoDesignSystem."
    }

internal object DemoDesignSystemBundles {
    fun resolve(
        kind: DemoDesignSystemKind,
        dark: Boolean,
        reducedMotionEnabled: Boolean,
    ): DemoDesignSystemBundle {
        val base = if (dark) DemoThemeTokens.dark else DemoThemeTokens.light
        val tokens = when (kind) {
            DemoDesignSystemKind.RoundedReference -> roundedTokens(base, dark)
            DemoDesignSystemKind.CutContrast -> cutTokens(base, dark)
            DemoDesignSystemKind.CupertinoPressure -> cupertinoTokens(base, dark)
        }
        return DemoDesignSystemBundle(
            kind = kind,
            tokens = tokens,
            recipes = recipes(kind, tokens.colors),
            motion = motion(kind),
            reducedMotionEnabled = reducedMotionEnabled,
            conformance = buildList {
                val sharedPrimitiveOutcome = if (kind == DemoDesignSystemKind.CupertinoPressure) {
                    DemoConformanceOutcome.Equivalent
                } else {
                    DemoConformanceOutcome.Exact
                }
                add(DemoComponentConformance("Button", sharedPrimitiveOutcome, "BasicButton"))
                add(DemoComponentConformance("Surface/Card", sharedPrimitiveOutcome, "BasicSurface"))
                add(DemoComponentConformance("Switch", DemoConformanceOutcome.Equivalent, "owned composite"))
                add(DemoComponentConformance("TextField", DemoConformanceOutcome.Equivalent, "native edit core"))
                add(
                    DemoComponentConformance(
                        "NavigationBar",
                        DemoConformanceOutcome.Equivalent,
                        "owned composite",
                    ),
                )
                if (kind == DemoDesignSystemKind.CupertinoPressure) {
                    add(
                        DemoComponentConformance(
                            "SegmentedControl",
                            DemoConformanceOutcome.Equivalent,
                            "owned composite",
                        ),
                    )
                    add(
                        DemoComponentConformance(
                            "Continuous corners",
                            DemoConformanceOutcome.Exact,
                            "framework Path",
                            "rounded rectangle",
                        ),
                    )
                    add(
                        DemoComponentConformance(
                            "Shape morph",
                            DemoConformanceOutcome.Degraded,
                            "semantic motion",
                            "discrete endpoint",
                        ),
                    )
                }
                add(
                    DemoComponentConformance(
                        "Backdrop blur",
                        DemoConformanceOutcome.Degraded,
                        "capability policy",
                        if (kind == DemoDesignSystemKind.CupertinoPressure) {
                            "tinted translucent surface"
                        } else {
                            "tinted surface"
                        },
                    ),
                )
            },
        )
    }

    private fun roundedTokens(base: UiThemeTokens, dark: Boolean): UiThemeTokens {
        val colors = if (dark) {
            base.colors.copy(
                background = 0xFF101814.toInt(),
                surface = 0xFF17221C.toInt(),
                surfaceContainer = 0xFF1E2B24.toInt(),
                onSurface = 0xFFE0F0E5.toInt(),
                onSurfaceVariant = 0xFFB8CBBE.toInt(),
                primary = 0xFF8DE3A9.toInt(),
                onPrimary = 0xFF06391B.toInt(),
                primaryContainer = 0xFF18562C.toInt(),
                onPrimaryContainer = 0xFFB0F5C2.toInt(),
                outline = 0xFF819487.toInt(),
            )
        } else {
            base.colors.copy(
                background = 0xFFF4FBF6.toInt(),
                surface = 0xFFF4FBF6.toInt(),
                surfaceContainer = 0xFFE2F0E6.toInt(),
                onSurface = 0xFF142019.toInt(),
                onSurfaceVariant = 0xFF53645A.toInt(),
                primary = 0xFF214E34.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = 0xFFC8E8D0.toInt(),
                onPrimaryContainer = 0xFF0B321C.toInt(),
                outline = 0xFF819085.toInt(),
            )
        }
        return base.copy(
            colors = colors,
            shapes = UiShapes(
                small = UiShape.rounded(14.dp),
                medium = UiShape.rounded(22.dp),
            ),
        )
    }

    private fun cutTokens(base: UiThemeTokens, dark: Boolean): UiThemeTokens {
        val colors = if (dark) {
            base.colors.copy(
                background = 0xFF20120D.toInt(),
                surface = 0xFF2A1912.toInt(),
                surfaceContainer = 0xFF382219.toInt(),
                onSurface = 0xFFFFE6DC.toInt(),
                onSurfaceVariant = 0xFFE3BCAF.toInt(),
                primary = 0xFFFFB59D.toInt(),
                onPrimary = 0xFF5B1A08.toInt(),
                primaryContainer = 0xFF7C2F18.toInt(),
                onPrimaryContainer = 0xFFFFDBCF.toInt(),
                outline = 0xFFB89A8E.toInt(),
            )
        } else {
            base.colors.copy(
                background = 0xFFFFF8F4.toInt(),
                surface = 0xFFFFF0E8.toInt(),
                surfaceContainer = 0xFFFFE2D6.toInt(),
                onSurface = 0xFF32150C.toInt(),
                onSurfaceVariant = 0xFF77574B.toInt(),
                primary = 0xFF6A2B18.toInt(),
                onPrimary = 0xFFFFF4EF.toInt(),
                primaryContainer = 0xFFFFD8C9.toInt(),
                onPrimaryContainer = 0xFF4A1507.toInt(),
                outline = 0xFF9B786B.toInt(),
            )
        }
        return base.copy(
            colors = colors,
            shapes = UiShapes(
                small = UiShape.cut(8.dp),
                medium = UiShape.cut(16.dp),
            ),
        )
    }

    private fun cupertinoTokens(base: UiThemeTokens, dark: Boolean): UiThemeTokens {
        val colors = if (dark) {
            base.colors.copy(
                background = 0xFF000000.toInt(),
                surface = 0xE61C1C1E.toInt(),
                surfaceContainer = 0xD92C2C2E.toInt(),
                surfaceContainerHigh = 0xE63A3A3C.toInt(),
                onSurface = 0xFFF5F5F7.toInt(),
                onSurfaceVariant = 0xFFAEAEB2.toInt(),
                primary = 0xFF0A84FF.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = 0xCC0A84FF.toInt(),
                onPrimaryContainer = 0xFFFFFFFF.toInt(),
                outline = 0xFF48484A.toInt(),
                outlineVariant = 0xFF38383A.toInt(),
            )
        } else {
            base.colors.copy(
                background = 0xFFF2F2F7.toInt(),
                surface = 0xE6FFFFFF.toInt(),
                surfaceContainer = 0xD9E9E9EE.toInt(),
                surfaceContainerHigh = 0xE6D1D1D6.toInt(),
                onSurface = 0xFF1C1C1E.toInt(),
                onSurfaceVariant = 0xFF636366.toInt(),
                primary = 0xFF007AFF.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = 0xCC007AFF.toInt(),
                onPrimaryContainer = 0xFFFFFFFF.toInt(),
                outline = 0xFFC6C6C8.toInt(),
                outlineVariant = 0xFFD1D1D6.toInt(),
            )
        }
        return base.copy(
            colors = colors,
            shapes = UiShapes(
                extraSmall = UiShape.continuous(6.dp),
                small = UiShape.continuous(10.dp),
                medium = UiShape.continuous(16.dp),
                large = UiShape.continuous(22.dp),
                extraLarge = UiShape.continuous(28.dp),
                full = UiShape.roundedRelative(0.5f),
            ),
        )
    }

    private fun recipes(
        kind: DemoDesignSystemKind,
        colors: UiColors,
    ): DemoComponentRecipes {
        val cut = kind == DemoDesignSystemKind.CutContrast
        val cupertino = kind == DemoDesignSystemKind.CupertinoPressure
        val actionShape = when {
            cut -> UiShape.cut(10.dp)
            cupertino -> UiShape.continuous(14.dp)
            else -> UiShape.roundedRelative(0.5f)
        }
        val surfaceShape = when {
            cut -> UiShape.cut(18.dp)
            cupertino -> UiShape.continuous(22.dp)
            else -> UiShape.rounded(20.dp)
        }
        val trackShape = if (cut) UiShape.cut(6.dp) else UiShape.roundedRelative(0.5f)
        val thumbShape = if (cut) UiShape.cut(3.dp) else UiShape.roundedRelative(0.5f)
        val textShape = when {
            cut -> UiShape.cut(10.dp)
            cupertino -> UiShape.continuous(12.dp)
            else -> UiShape.rounded(12.dp)
        }
        val navShape = when {
            cut -> UiShape.cut(12.dp)
            cupertino -> UiShape.continuous(18.dp)
            else -> UiShape.rounded(20.dp)
        }
        val indicatorShape = if (cut) UiShape.cut(5.dp) else UiShape.roundedRelative(0.5f)
        val onSurfaceLayers = stateLayers(colors.onSurface)
        return DemoComponentRecipes(
            action = DemoActionRecipe(
                enabledContainerColor = colors.primary,
                disabledContainerColor = colors.surfaceContainer,
                enabledContentColor = colors.onPrimary,
                disabledContentColor = colors.onSurfaceVariant,
                borderColor = if (cut) colors.outline else 0x00000000,
                borderWidth = if (cut) 2.dp else 0.dp,
                shape = actionShape,
                minimumHeight = if (cut) 52.dp else 48.dp,
                visualHeight = when {
                    cut -> 52.dp
                    cupertino -> 48.dp
                    else -> 40.dp
                },
                horizontalPadding = when {
                    cut -> 28.dp
                    cupertino -> 20.dp
                    else -> 24.dp
                },
                textStyle = UiTextStyle(
                    fontSizeSp = if (cupertino) 17.sp else if (cut) 16.sp else 14.sp,
                    fontWeight = if (cupertino) 600 else if (cut) 700 else 600,
                    lineHeightSp = if (cupertino) 22.sp else 20.sp,
                ),
                stateLayers = stateLayers(colors.onPrimary),
            ),
            surface = DemoSurfaceRecipe(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                borderColor = if (cut) colors.primary else colors.outline,
                borderWidth = if (cut) 2.dp else if (cupertino) 0.dp else 1.dp,
                shape = surfaceShape,
                stateLayers = onSurfaceLayers,
            ),
            switch = DemoSwitchRecipe(
                placement = if (cut || cupertino) DemoControlPlacement.Trailing else DemoControlPlacement.Leading,
                checkedTrackColor = colors.primary,
                uncheckedTrackColor = if (cupertino) colors.surfaceContainerHigh else colors.surfaceContainer,
                disabledTrackColor = colors.surfaceContainer,
                checkedThumbColor = colors.onPrimary,
                uncheckedThumbColor = if (cut) colors.primary else colors.onSurfaceVariant,
                disabledThumbColor = colors.outline,
                labelColor = colors.onSurface,
                disabledLabelColor = colors.onSurfaceVariant,
                trackShape = trackShape,
                thumbShape = thumbShape,
                trackWidth = when {
                    cut -> 52.dp
                    cupertino -> 51.dp
                    else -> 44.dp
                },
                trackHeight = if (cupertino) 31.dp else if (cut) 28.dp else 26.dp,
                trackPadding = if (cut) 4.dp else 3.dp,
                thumbSize = if (cupertino) 25.dp else 20.dp,
                labelSpacing = if (cut || cupertino) 16.dp else 12.dp,
                stateLayers = onSurfaceLayers,
            ),
            textField = DemoTextFieldRecipe(
                stackedLabel = !cut,
                containerColor = if (cupertino) colors.surfaceContainer else colors.surface,
                errorContainerColor = colors.surfaceContainer,
                textColor = colors.onSurface,
                hintColor = colors.onSurfaceVariant,
                labelColor = colors.primary,
                errorColor = colors.error,
                borderColor = colors.outline,
                borderWidth = if (cut) 2.dp else if (cupertino) 0.dp else 1.dp,
                errorBorderWidth = if (cut) 3.dp else 2.dp,
                shape = textShape,
                minimumHeight = if (cut || cupertino) 56.dp else 52.dp,
                horizontalPadding = if (cut) 20.dp else 16.dp,
                verticalPadding = if (cut) 14.dp else 12.dp,
                textStyle = UiTextStyle(
                    fontSizeSp = if (cut || cupertino) 17.sp else 16.sp,
                    lineHeightSp = 24.sp,
                ),
                labelStyle = UiTextStyle(
                    fontSizeSp = if (cupertino) 15.sp else 13.sp,
                    fontWeight = if (cupertino) 600 else 650,
                    lineHeightSp = 20.sp,
                ),
                supportingStyle = UiTextStyle(fontSizeSp = 12.sp, lineHeightSp = 16.sp),
            ),
            navigation = DemoNavigationRecipe(
                containerColor = if (cupertino) colors.surface else colors.surfaceContainer,
                containerShape = navShape,
                selectedColor = if (cupertino) colors.primary else colors.onPrimary,
                unselectedColor = colors.onSurfaceVariant,
                indicatorColor = colors.primary,
                indicatorShape = indicatorShape,
                height = when {
                    cut -> 76.dp
                    cupertino -> 64.dp
                    else -> 82.dp
                },
                iconSize = if (cut || cupertino) 22.dp else 24.dp,
                selectedOnlyLabels = cut,
                indicatorVisible = !cupertino,
                selectedLabelColor = colors.primary,
                stateLayers = onSurfaceLayers,
            ),
            segmented = DemoSegmentedRecipe(
                containerColor = if (cupertino) colors.surfaceContainer else colors.surface,
                selectedContainerColor = if (cupertino) colors.surface else colors.primaryContainer,
                selectedContentColor = if (cupertino) colors.onSurface else colors.onPrimaryContainer,
                unselectedContentColor = colors.onSurfaceVariant,
                containerShape = if (cupertino) UiShape.continuous(10.dp) else navShape,
                itemShape = if (cupertino) UiShape.continuous(8.dp) else indicatorShape,
                height = 44.dp,
                borderColor = colors.outline,
                stateLayers = onSurfaceLayers,
            ),
        )
    }

    private fun motion(kind: DemoDesignSystemKind): MotionScheme {
        val spatial = when (kind) {
            DemoDesignSystemKind.CutContrast -> tween(durationMillis = 180)
            DemoDesignSystemKind.CupertinoPressure -> {
                spring(durationMillis = 360, dampingRatio = 0.92f, stiffness = 300f)
            }
            DemoDesignSystemKind.RoundedReference -> {
                spring(durationMillis = 320, dampingRatio = 0.85f, stiffness = 260f)
            }
        }
        return MotionScheme(
            fastEffects = tween(durationMillis = 90),
            defaultEffects = tween(durationMillis = 180),
            fastSpatial = spatial,
            defaultSpatial = tween(durationMillis = 280),
            expressiveSpatial = spring(durationMillis = 480),
            reducedMotion = ReducedMotionPolicy(
                nonEssentialBehavior = ReducedMotionBehavior.Snap,
                essentialDurationScale = 0.4f,
            ),
        )
    }

    private fun stateLayers(contentColor: Int): UiStateLayerColors {
        return UiStateLayerColors(
            pressedColor = contentColor.withAlpha(0.12f),
            focusedColor = contentColor.withAlpha(0.12f),
            hoveredColor = contentColor.withAlpha(0.08f),
        )
    }
}

internal fun UiTreeBuilder.DemoDesignButton(
    text: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val recipe = DemoDesignSystem.recipes.action
    BasicButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        style = BasicButtonStyle(
            surface = BasicSurfaceStyle(
                fill = Brush.SolidColor(
                    if (enabled) recipe.enabledContainerColor else recipe.disabledContainerColor,
                ),
                shape = recipe.shape,
                borderWidth = recipe.borderWidth,
                borderColor = recipe.borderColor,
                clipContent = true,
            ),
            contentColor = if (enabled) recipe.enabledContentColor else recipe.disabledContentColor,
            textStyle = recipe.textStyle,
            stateLayerColors = recipe.stateLayers,
            minimumHeight = recipe.minimumHeight,
            visualHeight = recipe.visualHeight,
            paddingHorizontal = recipe.horizontalPadding,
            paddingVertical = 8.dp,
        ),
        modifier = modifier,
    )
}

internal fun UiTreeBuilder.DemoDesignCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    val recipe = DemoDesignSystem.recipes.surface
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(recipe.containerColor),
            shape = recipe.shape,
            borderWidth = recipe.borderWidth,
            borderColor = recipe.borderColor,
            clipContent = true,
        ),
        contentColor = recipe.contentColor,
        onClick = onClick,
        stateLayerColors = recipe.stateLayers,
        rippleColor = recipe.stateLayers.pressedColor,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

internal fun UiTreeBuilder.DemoDesignSwitch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bundle = DemoDesignSystem
    val recipe = bundle.recipes.switch
    val trackColorTarget = when {
        !enabled -> recipe.disabledTrackColor
        checked -> recipe.checkedTrackColor
        else -> recipe.uncheckedTrackColor
    }
    val thumbColorTarget = when {
        !enabled -> recipe.disabledThumbColor
        checked -> recipe.checkedThumbColor
        else -> recipe.uncheckedThumbColor
    }
    val travel = recipe.trackWidth - recipe.thumbSize - recipe.trackPadding * 2f
    val spatialSpec = bundle.motion.resolve(
        role = MotionRole.FastSpatial,
        reducedMotionEnabled = bundle.reducedMotionEnabled,
        essential = true,
    )
    val effectSpec = bundle.motion.resolve(
        role = MotionRole.FastEffects,
        reducedMotionEnabled = bundle.reducedMotionEnabled,
        essential = true,
    )
    val thumbOffsetTarget = when {
        !checked -> UiDp.Zero
        Environment.layoutDirection == UiLayoutDirection.Rtl -> UiDp.Zero - travel
        else -> travel
    }
    val thumbOffset = animateDpAsState(
        targetValue = thumbOffsetTarget,
        animationSpec = spatialSpec,
    ).value
    val trackColor = animateColorAsState(trackColorTarget, effectSpec).value
    val thumbColor = animateColorAsState(thumbColorTarget, effectSpec).value
    val controlIsFirst = when (recipe.placement) {
        DemoControlPlacement.Leading -> Environment.layoutDirection == UiLayoutDirection.Ltr
        DemoControlPlacement.Trailing -> Environment.layoutDirection == UiLayoutDirection.Rtl
    }
    val control: UiTreeBuilder.() -> Unit = {
        BasicSurface(
            style = BasicSurfaceStyle(
                fill = Brush.SolidColor(trackColor),
                shape = recipe.trackShape,
                clipContent = true,
            ),
            contentColor = thumbColor,
            modifier = Modifier
                .size(recipe.trackWidth, recipe.trackHeight)
                .padding(recipe.trackPadding),
            contentAlignment = BoxAlignment.CenterStart,
        ) {
            BasicSurface(
                style = BasicSurfaceStyle(
                    fill = Brush.SolidColor(thumbColor),
                    shape = recipe.thumbShape,
                    clipContent = true,
                ),
                contentColor = thumbColor,
                modifier = Modifier
                    .size(recipe.thumbSize, recipe.thumbSize)
                    .offset(x = thumbOffset),
            ) {}
        }
    }
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(0x00000000),
            shape = UiShape.rounded(0.dp),
        ),
        contentColor = if (enabled) recipe.labelColor else recipe.disabledLabelColor,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        stateLayerColors = recipe.stateLayers,
        minimumHeight = 48.dp,
        role = SemanticsRole.Switch,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = SemanticsRole.Switch
            this.checked = checked
            this.enabled = enabled
        },
        contentAlignment = BoxAlignment.CenterStart,
    ) {
        Row(
            spacing = recipe.labelSpacing,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            if (controlIsFirst) control()
            Text(
                text = text,
                color = if (enabled) recipe.labelColor else recipe.disabledLabelColor,
                style = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 550, lineHeightSp = 20.sp),
                modifier = Modifier.weight(1f),
            )
            if (!controlIsFirst) control()
        }
    }
}

internal fun UiTreeBuilder.DemoDesignTextField(
    state: TextFieldState,
    label: String,
    placeholder: String,
    supportingText: String,
    isError: Boolean,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val recipe = DemoDesignSystem.recipes.textField
    Column(modifier = modifier) {
        if (recipe.stackedLabel) {
            Text(
                text = label,
                color = if (isError) recipe.errorColor else recipe.labelColor,
                style = recipe.labelStyle,
                modifier = Modifier.margin(bottom = 6.dp),
            )
        }
        BasicTextField(
            state = state,
            placeholder = if (recipe.stackedLabel) placeholder else label,
            textColor = recipe.textColor,
            hintColor = recipe.hintColor,
            cursorColor = recipe.labelColor,
            textStyle = recipe.textStyle,
            backgroundColor = if (isError) recipe.errorContainerColor else recipe.containerColor,
            borderWidth = if (isError) recipe.errorBorderWidth else recipe.borderWidth,
            borderColor = if (isError) recipe.errorColor else recipe.borderColor,
            shape = recipe.shape,
            minHeight = recipe.minimumHeight,
            paddingHorizontal = recipe.horizontalPadding,
            paddingVertical = recipe.verticalPadding,
            autofillHints = autofillHints,
            modifier = Modifier.fillMaxWidth().semantics {
                if (isError) error = supportingText
            },
        )
        Text(
            text = supportingText,
            color = if (isError) recipe.errorColor else recipe.hintColor,
            style = recipe.supportingStyle,
            modifier = Modifier.margin(top = 6.dp),
        )
    }
}

internal fun UiTreeBuilder.DemoDesignNavigationBar(
    items: List<NavigationBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = DemoDesignSystem.recipes.navigation
    Row(
        arrangement = MainAxisArrangement.SpaceEvenly,
        verticalAlignment = VerticalAlignment.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        val indexedItems = items.withIndex().let { values ->
            if (Environment.layoutDirection == UiLayoutDirection.Rtl) values.reversed() else values
        }
        indexedItems.forEach { (index, item) ->
            val selected = index == selectedIndex
            BasicSurface(
                style = BasicSurfaceStyle(
                    fill = Brush.SolidColor(recipe.containerColor),
                    shape = recipe.containerShape,
                    clipContent = true,
                ),
                contentColor = if (selected) recipe.selectedColor else recipe.unselectedColor,
                onClick = { onItemSelected(index) },
                stateLayerColors = recipe.stateLayers,
                minimumHeight = recipe.height,
                role = SemanticsRole.Tab,
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        role = SemanticsRole.Tab
                        this.selected = selected
                    },
                contentAlignment = BoxAlignment.Center,
            ) {
                Column(
                    spacing = 4.dp,
                    arrangement = MainAxisArrangement.Center,
                    horizontalAlignment = HorizontalAlignment.Center,
                ) {
                    BasicSurface(
                        style = BasicSurfaceStyle(
                            fill = Brush.SolidColor(
                                if (selected && recipe.indicatorVisible) {
                                    recipe.indicatorColor
                                } else {
                                    0x00000000
                                },
                            ),
                            shape = recipe.indicatorShape,
                            clipContent = true,
                        ),
                        contentColor = if (selected) recipe.selectedColor else recipe.unselectedColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = BoxAlignment.Center,
                    ) {
                        Icon(
                            source = if (selected) item.selectedIcon ?: item.icon else item.icon,
                            contentDescription = item.label,
                            tint = if (selected) recipe.selectedColor else recipe.unselectedColor,
                            size = recipe.iconSize,
                        )
                    }
                    if (!recipe.selectedOnlyLabels || selected) {
                        Text(
                            text = item.label,
                            color = if (selected) recipe.selectedLabelColor else recipe.unselectedColor,
                            style = UiTextStyle(fontSizeSp = 12.sp, fontWeight = 600),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal fun UiTreeBuilder.DemoDesignSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(labels.size in 2..5) { "DemoDesignSegmentedControl requires between 2 and 5 labels." }
    require(selectedIndex in labels.indices) { "Selected index must reference a segment." }
    val recipe = DemoDesignSystem.recipes.segmented
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(recipe.containerColor),
            shape = recipe.containerShape,
            borderWidth = 1.dp,
            borderColor = recipe.borderColor,
            clipContent = true,
        ),
        contentColor = recipe.unselectedContentColor,
        minimumHeight = recipe.height,
        modifier = modifier.fillMaxWidth(),
        contentAlignment = BoxAlignment.Center,
    ) {
        Row(
            arrangement = MainAxisArrangement.SpaceEvenly,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth().padding(2.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                BasicSurface(
                    style = BasicSurfaceStyle(
                        fill = Brush.SolidColor(
                            if (selected) recipe.selectedContainerColor else 0x00000000,
                        ),
                        shape = recipe.itemShape,
                        clipContent = true,
                    ),
                    contentColor = if (selected) {
                        recipe.selectedContentColor
                    } else {
                        recipe.unselectedContentColor
                    },
                    onClick = { onItemSelected(index) },
                    stateLayerColors = recipe.stateLayers,
                    minimumHeight = 40.dp,
                    role = SemanticsRole.Tab,
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                        role = SemanticsRole.Tab
                        this.selected = selected
                    },
                    contentAlignment = BoxAlignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) {
                            recipe.selectedContentColor
                        } else {
                            recipe.unselectedContentColor
                        },
                        style = UiTextStyle(fontSizeSp = 13.sp, fontWeight = 600, lineHeightSp = 18.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

internal fun demoDesignNavigationItems(): List<NavigationBarItem> {
    val icon = ImageSource.Resource(R.drawable.demo_media_icon)
    return listOf(
        NavigationBarItem(key = "home", label = "Home", icon = icon),
        NavigationBarItem(key = "search", label = "Search", icon = icon),
        NavigationBarItem(key = "profile", label = "Profile", icon = icon),
    )
}

private fun Int.withAlpha(alpha: Float): Int {
    val resolvedAlpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (this and 0x00FFFFFF) or (resolvedAlpha shl 24)
}
