package com.viewcompose.ui.foundation

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.minHeight
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.ButtonNodeProps
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

internal data class ExperimentalComponentRecipes(
    val identity: ExperimentalComponentRecipeIdentity,
    val action: ExperimentalActionRecipe,
    val surface: ExperimentalSurfaceRecipe,
    val switch: ExperimentalSwitchRecipe,
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
    val interactive = enabled && onClick != null
    val semanticModifier = Modifier
        .backgroundColor(recipe.containerColor)
        .shape(recipe.shape)
        .clip()
        .let { current ->
            if (recipe.borderWidth > UiDp.Zero) {
                current.border(recipe.borderWidth, recipe.borderColor)
            } else {
                current
            }
        }
        .let { current ->
            if (recipe.elevation > UiDp.Zero) current.elevation(recipe.elevation) else current
        }
        .let { current ->
            if (!enabled) current.alpha(recipe.disabledAlpha) else current
        }
        .let { current ->
            if (interactive) current.clickable(requireNotNull(onClick)) else current
        }
        .then(modifier)

    ProvideLocal(LocalContentColor, recipe.contentColor) {
        StateLayerBox(
            type = NodeType.Surface,
            key = key,
            contentAlignment = contentAlignment,
            rippleColor = recipe.stateLayerColors.pressedColor.takeIf { interactive },
            stateLayerColors = recipe.stateLayerColors.takeIf { interactive },
            modifier = semanticModifier,
            content = content,
        )
    }
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
