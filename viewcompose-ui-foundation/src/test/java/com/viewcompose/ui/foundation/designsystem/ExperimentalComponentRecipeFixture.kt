package com.viewcompose.ui.foundation

import com.viewcompose.graphics.core.Brush
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.minHeight
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/*
 * This test-only experiment deliberately keeps recipes outside UiThemeTokens. It is not a public
 * vocabulary; later phases retain only the Basic primitives proven by the five-component fixture.
 */

internal data class ExperimentalComponentRecipeIdentity(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Component recipe identity must not be blank." }
    }
}
internal data class ExperimentalActionRecipe(
    val enabledContainerColor: Int,
    val disabledContainerColor: Int,
    val enabledContentColor: Int,
    val disabledContentColor: Int,
    val enabledBorderColor: Int,
    val disabledBorderColor: Int,
    val borderWidth: UiDp,
    val shape: UiShape,
    val minHeight: UiDp,
    val visualHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
    val iconSize: UiDp,
    val iconSpacing: UiDp,
    val textStyle: UiTextStyle,
    val stateLayerColors: UiStateLayerColors,
)

internal data class ExperimentalSurfaceRecipe(
    val containerColor: Int,
    val contentColor: Int,
    val shape: UiShape,
    val borderWidth: UiDp = UiDp.Zero,
    val borderColor: Int = 0x00000000,
    val elevation: UiDp = UiDp.Zero,
    val disabledAlpha: Float = 1f,
    val stateLayerColors: UiStateLayerColors,
) {
    init {
        require(disabledAlpha.isFinite() && disabledAlpha in 0f..1f) {
            "Surface disabledAlpha must be finite and in 0f..1f."
        }
    }
}

internal enum class ExperimentalControlPlacement {
    Leading,
    Trailing,
}

/** Resolved geometry and state values for a framework-composed switch. */
internal data class ExperimentalSwitchRecipe(
    val controlPlacement: ExperimentalControlPlacement,
    val checkedTrackColor: Int,
    val uncheckedTrackColor: Int,
    val disabledTrackColor: Int,
    val checkedThumbColor: Int,
    val uncheckedThumbColor: Int,
    val disabledThumbColor: Int,
    val enabledLabelColor: Int,
    val disabledLabelColor: Int,
    val trackShape: UiShape,
    val thumbShape: UiShape,
    val trackWidth: UiDp,
    val trackHeight: UiDp,
    val trackPadding: UiDp,
    val thumbSize: UiDp,
    val minimumInteractiveHeight: UiDp,
    val labelSpacing: UiDp,
    val labelStyle: UiTextStyle,
    val stateLayerColors: UiStateLayerColors,
)

internal enum class ExperimentalTextFieldDecoration {
    StackedLabel,
    PlaceholderLabel,
}

/** Resolved decoration values wrapped around the shared native editing core. */
internal data class ExperimentalTextFieldRecipe(
    val decoration: ExperimentalTextFieldDecoration,
    val enabledContainerColor: Int,
    val disabledContainerColor: Int,
    val errorContainerColor: Int,
    val enabledTextColor: Int,
    val disabledTextColor: Int,
    val enabledHintColor: Int,
    val disabledHintColor: Int,
    val enabledLabelColor: Int,
    val disabledLabelColor: Int,
    val errorLabelColor: Int,
    val supportingTextColor: Int,
    val errorSupportingTextColor: Int,
    val enabledBorderColor: Int,
    val disabledBorderColor: Int,
    val errorBorderColor: Int,
    val borderWidth: UiDp,
    val errorBorderWidth: UiDp,
    val shape: UiShape,
    val minimumHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
    val decorationSpacing: UiDp,
    val cursorColor: Int,
    val textStyle: UiTextStyle,
    val labelStyle: UiTextStyle,
    val supportingTextStyle: UiTextStyle,
)

internal enum class ExperimentalNavigationStructure {
    SharedBarNode,
    ComposedDestinations,
}

internal enum class ExperimentalNavigationLabelPolicy {
    Always,
    SelectedOnly,
    Never,
}

/** Resolved navigation values plus the design-system-owned structural choice. */
internal data class ExperimentalNavigationRecipe(
    val structure: ExperimentalNavigationStructure,
    val labelPolicy: ExperimentalNavigationLabelPolicy,
    val containerColor: Int,
    val containerShape: UiShape,
    val selectedIconColor: Int,
    val unselectedIconColor: Int,
    val selectedLabelColor: Int,
    val unselectedLabelColor: Int,
    val indicatorColor: Int,
    val indicatorShape: UiShape,
    val selectedStateLayerColors: UiStateLayerColors,
    val unselectedStateLayerColors: UiStateLayerColors,
    val height: UiDp,
    val iconSize: UiDp,
    val indicatorHorizontalPadding: UiDp,
    val indicatorVerticalPadding: UiDp,
    val itemSpacing: UiDp,
    val labelStyle: UiTextStyle,
    val badgeColor: Int,
    val badgeTextColor: Int,
)

internal data class ExperimentalComponentRecipes(
    val identity: ExperimentalComponentRecipeIdentity,
    val action: ExperimentalActionRecipe,
    val surface: ExperimentalSurfaceRecipe,
    val switch: ExperimentalSwitchRecipe,
    val textField: ExperimentalTextFieldRecipe,
    val navigation: ExperimentalNavigationRecipe,
)

private val LocalExperimentalComponentRecipes = uiLocalOf<ExperimentalComponentRecipes?>(
    debugName = "ExperimentalComponentRecipes",
    debugValueFormatter = { recipes -> recipes?.identity?.value ?: "absent" },
) { null }

internal fun UiTreeBuilder.ProvideExperimentalComponentRecipes(
    recipes: ExperimentalComponentRecipes,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalExperimentalComponentRecipes, recipes, content)
}

private fun currentExperimentalComponentRecipes(): ExperimentalComponentRecipes {
    return requireNotNull(UiLocals.current(LocalExperimentalComponentRecipes)) {
        "Experimental recipe components require ProvideExperimentalComponentRecipes."
    }
}

internal fun UiTreeBuilder.ExperimentalRecipeAction(
    text: String,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageSource.Resource? = null,
    trailingIcon: ImageSource.Resource? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipe = currentExperimentalComponentRecipes().action
    val contentColor = if (enabled) recipe.enabledContentColor else recipe.disabledContentColor
    val containerColor = if (enabled) recipe.enabledContainerColor else recipe.disabledContainerColor
    val borderColor = if (enabled) recipe.enabledBorderColor else recipe.disabledBorderColor
    val stateLayers = recipe.stateLayerColors.takeIf { enabled }
    val style = recipe.textStyle

    emit(
        type = NodeType.Button,
        key = key,
        spec = ButtonNodeProps(
            text = text,
            enabled = enabled,
            onClick = onClick,
            textColor = contentColor,
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            backgroundColor = containerColor,
            borderWidth = recipe.borderWidth,
            borderColor = borderColor,
            shape = recipe.shape,
            rippleColor = stateLayers?.pressedColor ?: 0x00000000,
            minHeight = recipe.minHeight,
            paddingHorizontal = recipe.horizontalPadding,
            paddingVertical = recipe.verticalPadding,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            iconTint = contentColor,
            iconSize = recipe.iconSize,
            iconSpacing = recipe.iconSpacing,
            visualHeight = recipe.visualHeight,
            stateLayerColors = stateLayers,
        ),
        modifier = modifier,
    )
}

internal fun UiTreeBuilder.ExperimentalRecipeSurface(
    enabled: Boolean = true,
    contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    onClick: (() -> Unit)? = null,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    val recipe = currentExperimentalComponentRecipes().surface
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(recipe.containerColor),
            shape = recipe.shape,
            borderWidth = recipe.borderWidth,
            borderColor = recipe.borderColor,
            elevation = recipe.elevation,
            clipContent = true,
        ),
        contentColor = recipe.contentColor,
        enabled = enabled,
        onClick = onClick,
        stateLayerColors = recipe.stateLayerColors,
        key = key,
        contentAlignment = contentAlignment,
        modifier = Modifier
            .then(if (!enabled) Modifier.alpha(recipe.disabledAlpha) else Modifier)
            .then(modifier),
        content = content,
    )
}

/**
 * Builds a switch from generic Row/Box/Text nodes so geometry is not delegated to an OEM widget.
 *
 * The test fixture keeps click and checked semantics on one merged root. A production BasicToggle
 * must additionally pass device accessibility, pointer, focus, drag, and animation baselines.
 */
internal fun UiTreeBuilder.ExperimentalRecipeSwitch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipe = currentExperimentalComponentRecipes().switch
    val trackColor = when {
        !enabled -> recipe.disabledTrackColor
        checked -> recipe.checkedTrackColor
        else -> recipe.uncheckedTrackColor
    }
    val thumbColor = when {
        !enabled -> recipe.disabledThumbColor
        checked -> recipe.checkedThumbColor
        else -> recipe.uncheckedThumbColor
    }
    val labelColor = if (enabled) recipe.enabledLabelColor else recipe.disabledLabelColor
    val control: UiTreeBuilder.() -> Unit = {
        experimentalSwitchTrack(
            checked = checked,
            trackColor = trackColor,
            thumbColor = thumbColor,
            recipe = recipe,
        )
    }
    val semanticModifier = Modifier
        .minHeight(recipe.minimumInteractiveHeight)
        .let { current ->
            if (enabled) current.clickable { onCheckedChange(!checked) } else current
        }
        .semantics(mergeDescendants = true) {
            role = SemanticsRole.Switch
            this.checked = checked
            this.enabled = enabled
        }
        .then(modifier)

    StateLayerRow(
        key = key,
        spacing = recipe.labelSpacing,
        verticalAlignment = VerticalAlignment.Center,
        rippleColor = recipe.stateLayerColors.pressedColor.takeIf { enabled },
        stateLayerColors = recipe.stateLayerColors.takeIf { enabled },
        modifier = semanticModifier,
    ) {
        if (recipe.controlPlacement == ExperimentalControlPlacement.Leading) control()
        Text(
            text = text,
            style = recipe.labelStyle,
            color = labelColor,
        )
        if (recipe.controlPlacement == ExperimentalControlPlacement.Trailing) control()
    }
}

private fun UiTreeBuilder.experimentalSwitchTrack(
    checked: Boolean,
    trackColor: Int,
    thumbColor: Int,
    recipe: ExperimentalSwitchRecipe,
) {
    Box(
        contentAlignment = if (checked) BoxAlignment.CenterEnd else BoxAlignment.CenterStart,
        modifier = Modifier
            .size(width = recipe.trackWidth, height = recipe.trackHeight)
            .backgroundColor(trackColor)
            .shape(recipe.trackShape)
            .clip()
            .padding(recipe.trackPadding),
    ) {
        Box(
            modifier = Modifier
                .size(width = recipe.thumbSize, height = recipe.thumbSize)
                .backgroundColor(thumbColor)
                .shape(recipe.thumbShape)
                .clip(),
        ) {}
    }
}

/**
 * Keeps editing, selection, IME, autofill, and cursor ownership in BasicTextField while allowing
 * the design system to own the surrounding label/supporting structure and resolved decoration.
 */
internal fun UiTreeBuilder.ExperimentalRecipeTextField(
    state: TextFieldState,
    label: String = "",
    placeholder: String = "",
    supportingText: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipe = currentExperimentalComponentRecipes().textField
    val containerColor = when {
        isError -> recipe.errorContainerColor
        !enabled -> recipe.disabledContainerColor
        else -> recipe.enabledContainerColor
    }
    val textColor = if (enabled) recipe.enabledTextColor else recipe.disabledTextColor
    val hintColor = if (enabled) recipe.enabledHintColor else recipe.disabledHintColor
    val labelColor = when {
        isError -> recipe.errorLabelColor
        !enabled -> recipe.disabledLabelColor
        else -> recipe.enabledLabelColor
    }
    val supportColor = if (isError) {
        recipe.errorSupportingTextColor
    } else {
        recipe.supportingTextColor
    }
    val borderColor = when {
        isError -> recipe.errorBorderColor
        !enabled -> recipe.disabledBorderColor
        else -> recipe.enabledBorderColor
    }
    val resolvedPlaceholder = when (recipe.decoration) {
        ExperimentalTextFieldDecoration.StackedLabel -> placeholder
        ExperimentalTextFieldDecoration.PlaceholderLabel -> placeholder.ifEmpty { label }
    }

    Column(
        key = key,
        modifier = modifier,
    ) {
        if (recipe.decoration == ExperimentalTextFieldDecoration.StackedLabel && label.isNotBlank()) {
            Text(
                text = label,
                style = recipe.labelStyle,
                color = labelColor,
                modifier = Modifier.margin(bottom = recipe.decorationSpacing),
            )
        }
        BasicTextField(
            state = state,
            placeholder = resolvedPlaceholder,
            enabled = enabled,
            singleLine = singleLine,
            readOnly = readOnly,
            cursorColor = recipe.cursorColor,
            textColor = textColor,
            textStyle = recipe.textStyle,
            hintColor = hintColor,
            backgroundColor = containerColor,
            borderWidth = if (isError) recipe.errorBorderWidth else recipe.borderWidth,
            borderColor = borderColor,
            shape = recipe.shape,
            minHeight = if (singleLine) recipe.minimumHeight else UiDp.Zero,
            paddingHorizontal = recipe.horizontalPadding,
            paddingVertical = recipe.verticalPadding,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (isError && supportingText.isNotBlank()) error = supportingText
                },
        )
        if (supportingText.isNotBlank()) {
            Text(
                text = supportingText,
                style = recipe.supportingTextStyle,
                color = supportColor,
                modifier = Modifier.margin(top = recipe.decorationSpacing),
            )
        }
    }
}

/**
 * Selects either the existing fixed navigation node or a design-system-owned generic composition.
 *
 * Keeping this choice above NodeSpec resolution demonstrates that Android Renderer does not need
 * to identify the active design system even when navigation structure differs materially.
 */
internal fun UiTreeBuilder.ExperimentalRecipeNavigationBar(
    items: List<NavigationBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipe = currentExperimentalComponentRecipes().navigation
    when (recipe.structure) {
        ExperimentalNavigationStructure.SharedBarNode -> {
            val style = recipe.labelStyle
            emit(
                type = NodeType.NavigationBar,
                key = key,
                spec = NavigationBarNodeProps(
                    items = items,
                    selectedIndex = selectedIndex,
                    onItemSelected = onItemSelected,
                    containerColor = recipe.containerColor,
                    selectedIconColor = recipe.selectedIconColor,
                    unselectedIconColor = recipe.unselectedIconColor,
                    selectedLabelColor = recipe.selectedLabelColor,
                    unselectedLabelColor = recipe.unselectedLabelColor,
                    indicatorColor = recipe.indicatorColor,
                    rippleColor = recipe.unselectedStateLayerColors.pressedColor,
                    iconSize = recipe.iconSize,
                    labelSizeSp = style.fontSizeSp,
                    labelFontWeight = style.fontWeight,
                    labelFontFamily = uiFontFamily(style.fontFamily),
                    labelLetterSpacingEm = style.letterSpacingEm,
                    labelLineHeightSp = style.lineHeightSp,
                    labelIncludeFontPadding = style.includeFontPadding,
                    badgeColor = recipe.badgeColor,
                    badgeTextColor = recipe.badgeTextColor,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(recipe.height)
                    .then(modifier),
            )
        }

        ExperimentalNavigationStructure.ComposedDestinations -> {
            Row(
                key = key,
                arrangement = MainAxisArrangement.SpaceEvenly,
                verticalAlignment = VerticalAlignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(recipe.height)
                    .backgroundColor(recipe.containerColor)
                    .shape(recipe.containerShape)
                    .clip()
                    .then(modifier),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val stateLayers = if (selected) {
                        recipe.selectedStateLayerColors
                    } else {
                        recipe.unselectedStateLayerColors
                    }
                    StateLayerBox(
                        key = item.key,
                        contentAlignment = BoxAlignment.Center,
                        rippleColor = stateLayers.pressedColor,
                        stateLayerColors = stateLayers,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onItemSelected(index) }
                            .semantics(mergeDescendants = true) {
                                role = SemanticsRole.Tab
                                this.selected = selected
                                enabled = true
                            },
                    ) {
                        Column(
                            spacing = recipe.itemSpacing,
                            arrangement = MainAxisArrangement.Center,
                            horizontalAlignment = HorizontalAlignment.Center,
                        ) {
                            Box(
                                contentAlignment = BoxAlignment.Center,
                                modifier = Modifier
                                    .let { current ->
                                        if (selected) {
                                            current
                                                .backgroundColor(recipe.indicatorColor)
                                                .shape(recipe.indicatorShape)
                                                .clip()
                                        } else {
                                            current
                                        }
                                    }
                                    .padding(
                                        horizontal = recipe.indicatorHorizontalPadding,
                                        vertical = recipe.indicatorVerticalPadding,
                                    ),
                            ) {
                                Icon(
                                    source = if (selected) item.selectedIcon ?: item.icon else item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) {
                                        recipe.selectedIconColor
                                    } else {
                                        recipe.unselectedIconColor
                                    },
                                    size = recipe.iconSize,
                                )
                            }
                            val showLabel = when (recipe.labelPolicy) {
                                ExperimentalNavigationLabelPolicy.Always -> true
                                ExperimentalNavigationLabelPolicy.SelectedOnly -> selected
                                ExperimentalNavigationLabelPolicy.Never -> false
                            }
                            if (showLabel) {
                                Text(
                                    text = item.label,
                                    style = recipe.labelStyle,
                                    color = if (selected) {
                                        recipe.selectedLabelColor
                                    } else {
                                        recipe.unselectedLabelColor
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
