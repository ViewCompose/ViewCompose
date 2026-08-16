package com.viewcompose.material3

import com.viewcompose.graphics.core.Brush
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.BasicButton
import com.viewcompose.ui.foundation.BasicButtonStyle
import com.viewcompose.ui.foundation.BasicSurface
import com.viewcompose.ui.foundation.BasicSurfaceStyle
import com.viewcompose.ui.foundation.BasicTextField
import com.viewcompose.ui.foundation.BasicTextFieldStyle
import com.viewcompose.ui.foundation.BoxScope
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.NavigationBarOverrides
import com.viewcompose.ui.foundation.NavigationBarScope
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.SwitchOverrides
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiColors
import com.viewcompose.ui.foundation.UiInteractionTokens
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp

/** Stable diagnostic identities for the first-party Material 3 pressure slice. */
object Material3Reference {
    /** Stable design-system identity exported through design-system diagnostics. */
    const val designSystem: String = "viewcompose-material3"

    /** Stable five-family recipe-set identity. */
    const val recipeSet: String = "material3-pressure-v1"
}

/** Selects Material 3 filled, tonal, outlined, or text button treatment. */
enum class Material3ButtonVariant {
    Filled,
    FilledTonal,
    Outlined,
    Text,
}

/** Selects the Material 3 surface-container role. */
enum class Material3SurfaceVariant {
    Surface,
    Container,
    ContainerHigh,
}

/** Selects Material 3 elevated, filled, or outlined card treatment. */
enum class Material3CardVariant {
    Elevated,
    Filled,
    Outlined,
}

/** Selects Material 3 filled or outlined text-field decoration. */
enum class Material3TextFieldVariant {
    Filled,
    Outlined,
}

/**
 * Emits a Material 3 surface through the design-system-neutral [BasicSurface].
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver active builder inside [Material3Theme]
 * @param variant semantic surface-container role
 * @param onClick optional complete-surface action
 * @param enabled whether an action accepts input
 * @param key optional stable root identity
 * @param modifier caller configuration appended to the resolved surface
 * @param content child subtree built inside the surface
 * @throws IllegalStateException when no [Material3Theme] is active
 */
fun UiTreeBuilder.Material3Surface(
    variant: Material3SurfaceVariant = Material3SurfaceVariant.Surface,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    val recipes = material3Recipes()
    val containerColor = when (variant) {
        Material3SurfaceVariant.Surface -> recipes.colors.surface
        Material3SurfaceVariant.Container -> recipes.colors.surfaceContainer
        Material3SurfaceVariant.ContainerHigh -> recipes.colors.surfaceContainerHigh
    }
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(containerColor),
            shape = recipes.surfaceShape,
            clipContent = true,
            interactionIndication = if (onClick == null) null else {
                UiInteractionIndication.StateLayer(
                    stateLayers(recipes.colors.onSurface, recipes.interactions),
                )
            },
        ),
        contentColor = recipes.colors.onSurface,
        enabled = enabled,
        onClick = onClick,
        role = if (onClick == null) null else SemanticsRole.Button,
        key = key,
        modifier = modifier,
        content = content,
    )
}

/**
 * Emits a Material 3 card through [BasicSurface] while preserving caller-owned content structure.
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver active builder inside [Material3Theme]
 * @param variant elevated, filled, or outlined hierarchy
 * @param onClick optional complete-card action
 * @param enabled whether an action accepts input
 * @param key optional stable root identity
 * @param modifier caller configuration appended to the resolved card
 * @param content child subtree built inside the card
 * @throws IllegalStateException when no [Material3Theme] is active
 */
fun UiTreeBuilder.Material3Card(
    variant: Material3CardVariant = Material3CardVariant.Filled,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    val recipes = material3Recipes()
    val containerColor = when (variant) {
        Material3CardVariant.Elevated -> recipes.colors.surfaceContainerLow
        Material3CardVariant.Filled -> recipes.colors.surfaceContainerHighest
        Material3CardVariant.Outlined -> recipes.colors.surface
    }
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(containerColor),
            shape = recipes.cardShape,
            borderWidth = if (variant == Material3CardVariant.Outlined) 1.dp else UiDp.Zero,
            borderColor = if (variant == Material3CardVariant.Outlined) {
                recipes.colors.outlineVariant
            } else {
                0x00000000
            },
            elevation = if (variant == Material3CardVariant.Elevated) 1.dp else UiDp.Zero,
            clipContent = true,
            interactionIndication = if (onClick == null) null else {
                UiInteractionIndication.StateLayer(
                    stateLayers(recipes.colors.onSurface, recipes.interactions),
                )
            },
        ),
        contentColor = recipes.colors.onSurface,
        enabled = enabled,
        onClick = onClick,
        role = if (onClick == null) null else SemanticsRole.Button,
        key = key,
        modifier = modifier,
        content = content,
    )
}

/**
 * Emits a Material 3 text action through the shared [BasicButton] primitive.
 *
 * The effective target remains at least 48dp while standard filled, tonal, outlined, and text
 * containers use a centered 40dp visual surface.
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver active builder inside [Material3Theme]
 * @param text localized single-line action label
 * @param onClick optional action callback
 * @param variant Material hierarchy treatment
 * @param enabled whether the action accepts input
 * @param leadingIcon optional decorative leading icon
 * @param trailingIcon optional decorative trailing icon
 * @param key optional stable root identity
 * @param modifier caller configuration appended to the complete target
 * @throws IllegalStateException when no [Material3Theme] is active
 */
fun UiTreeBuilder.Material3Button(
    text: String,
    onClick: (() -> Unit)?,
    variant: Material3ButtonVariant = Material3ButtonVariant.Filled,
    enabled: Boolean = true,
    leadingIcon: ImageSource? = null,
    trailingIcon: ImageSource? = null,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipes = material3Recipes()
    val containerColor = when {
        !enabled -> recipes.colors.onSurface.withAlpha(0.12f)
        variant == Material3ButtonVariant.Filled -> recipes.colors.primary
        variant == Material3ButtonVariant.FilledTonal -> recipes.colors.secondaryContainer
        else -> 0x00000000
    }
    val contentColor = when {
        !enabled -> recipes.colors.onSurface.withAlpha(0.38f)
        variant == Material3ButtonVariant.Filled -> recipes.colors.onPrimary
        variant == Material3ButtonVariant.FilledTonal -> recipes.colors.onSecondaryContainer
        else -> recipes.colors.primary
    }
    BasicButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        key = key,
        style = BasicButtonStyle(
            surface = BasicSurfaceStyle(
                fill = Brush.SolidColor(containerColor),
                shape = recipes.fullShape,
                borderWidth = if (variant == Material3ButtonVariant.Outlined) 1.dp else UiDp.Zero,
                borderColor = if (variant == Material3ButtonVariant.Outlined) {
                    if (enabled) recipes.colors.outline else recipes.colors.onSurface.withAlpha(0.12f)
                } else {
                    0x00000000
                },
                clipContent = true,
            ),
            contentColor = contentColor,
            textStyle = recipes.buttonTextStyle,
            stateLayerColors = stateLayers(contentColor, recipes.interactions),
            minimumHeight = 48.dp,
            visualHeight = 40.dp,
            paddingHorizontal = if (variant == Material3ButtonVariant.Text) 12.dp else 24.dp,
            paddingVertical = 8.dp,
            iconSize = 18.dp,
            iconSpacing = 8.dp,
        ),
        modifier = modifier,
    )
}

/**
 * Emits a Material 3 labeled switch while retaining the native Android switch behavioral core.
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver active builder inside [Material3Theme]
 * @param text localized control label
 * @param checked caller-owned checked state
 * @param onCheckedChange callback receiving the requested replacement state
 * @param enabled whether input is accepted
 * @param key optional stable identity
 * @param modifier caller configuration appended after the 48dp effective target
 * @throws IllegalStateException when no [Material3Theme] is active
 */
fun UiTreeBuilder.Material3Switch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipes = material3Recipes()
    Switch(
        text = text,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        overrides = SwitchOverrides(
            checkedThumbColor = recipes.colors.onPrimary,
            uncheckedThumbColor = recipes.colors.outline,
            disabledCheckedThumbColor = recipes.colors.onSurface.withAlpha(0.38f),
            disabledUncheckedThumbColor = recipes.colors.onSurface.withAlpha(0.38f),
            checkedTrackColor = recipes.colors.primary,
            uncheckedTrackColor = recipes.colors.surfaceContainerHighest,
            disabledCheckedTrackColor = recipes.colors.onSurface.withAlpha(0.12f),
            disabledUncheckedTrackColor = recipes.colors.onSurface.withAlpha(0.12f),
            textStyle = recipes.bodyTextStyle,
        ),
        key = key,
        modifier = modifier,
    )
}

/**
 * Emits Material 3 field decoration around the native Android editing core.
 *
 * The caller retains text, selection, composition, and save/restore ownership through [state].
 * This component does not replace IME, autofill, selection handles, or accessibility editing.
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver active builder inside [Material3Theme]
 * @param state caller-owned text editing state
 * @param label localized label shown above the editable area
 * @param placeholder localized placeholder
 * @param supportingText optional guidance or error explanation
 * @param variant filled or outlined decoration
 * @param enabled whether editing and focus input are enabled
 * @param isError whether error colors and semantics are active
 * @param autofillHints platform autofill categories forwarded to Android
 * @param key optional stable outer-column identity
 * @param modifier caller configuration appended to the outer column
 * @throws IllegalStateException when no [Material3Theme] is active
 */
fun UiTreeBuilder.Material3TextField(
    state: TextFieldState,
    label: String,
    placeholder: String = "",
    supportingText: String = "",
    variant: Material3TextFieldVariant = Material3TextFieldVariant.Filled,
    enabled: Boolean = true,
    isError: Boolean = false,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipes = material3Recipes()
    val labelColor = when {
        !enabled -> recipes.colors.onSurface.withAlpha(0.38f)
        isError -> recipes.colors.error
        else -> recipes.colors.onSurfaceVariant
    }
    Column(key = key, modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = labelColor,
                style = recipes.labelTextStyle,
                modifier = Modifier.margin(bottom = 4.dp),
            )
        }
        BasicTextField(
            state = state,
            style = BasicTextFieldStyle(
                textColor = if (enabled) {
                    recipes.colors.onSurface
                } else {
                    recipes.colors.onSurface.withAlpha(0.38f)
                },
                placeholderColor = if (enabled) {
                    recipes.colors.onSurfaceVariant
                } else {
                    recipes.colors.onSurface.withAlpha(0.38f)
                },
                cursorColor = if (isError) recipes.colors.error else recipes.colors.primary,
                textStyle = recipes.bodyTextStyle,
                containerColor = if (variant == Material3TextFieldVariant.Filled) {
                    recipes.colors.surfaceContainerHighest
                } else {
                    0x00000000
                },
                borderWidth = when {
                    isError -> 2.dp
                    variant == Material3TextFieldVariant.Outlined -> 1.dp
                    else -> UiDp.Zero
                },
                borderColor = when {
                    isError -> recipes.colors.error
                    variant == Material3TextFieldVariant.Outlined -> recipes.colors.outline
                    else -> 0x00000000
                },
                shape = recipes.fieldShape,
                minimumHeight = 56.dp,
                horizontalPadding = 16.dp,
                verticalPadding = 8.dp,
            ),
            placeholder = placeholder,
            enabled = enabled,
            autofillHints = autofillHints,
            modifier = Modifier.fillMaxWidth().semantics {
                if (isError && supportingText.isNotBlank()) error = supportingText
            },
        )
        if (supportingText.isNotBlank()) {
            Text(
                text = supportingText,
                color = if (isError) recipes.colors.error else recipes.colors.onSurfaceVariant,
                style = recipes.supportingTextStyle,
                modifier = Modifier.margin(top = 4.dp),
            )
        }
    }
}

/**
 * Emits the Material 3 navigation-bar pressure slice through the neutral navigation renderer.
 *
 * Selection remains caller-owned and [onItemSelected] receives the requested item index.
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver active builder inside [Material3Theme]
 * @param selectedIndex caller-owned selected item index
 * @param onItemSelected callback receiving the requested index
 * @param key optional stable bar identity
 * @param modifier caller configuration appended to the complete bar
 * @param items destination declarations
 * @throws IllegalStateException when no [Material3Theme] is active
 */
fun UiTreeBuilder.Material3NavigationBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    key: Any? = null,
    modifier: Modifier = Modifier,
    items: NavigationBarScope.() -> Unit,
) {
    val recipes = material3Recipes()
    NavigationBar(
        selectedIndex = selectedIndex,
        onItemSelected = onItemSelected,
        overrides = NavigationBarOverrides(
            containerColor = recipes.colors.surfaceContainer,
            selectedIconColor = recipes.colors.onSecondaryContainer,
            unselectedIconColor = recipes.colors.onSurfaceVariant,
            selectedLabelColor = recipes.colors.onSecondaryContainer,
            unselectedLabelColor = recipes.colors.onSurfaceVariant,
            indicatorColor = recipes.colors.secondaryContainer,
            selectedStateLayerColors = stateLayers(
                recipes.colors.onSecondaryContainer,
                recipes.interactions,
            ),
            unselectedStateLayerColors = stateLayers(
                recipes.colors.onSurfaceVariant,
                recipes.interactions,
            ),
            iconSize = 24.dp,
            labelStyle = recipes.navigationTextStyle,
            badgeColor = recipes.colors.error,
            badgeTextColor = recipes.colors.onError,
        ),
        key = key,
        modifier = modifier,
        items = items,
    )
}

internal data class Material3Recipes(
    val colors: UiColors,
    val interactions: UiInteractionTokens,
    val surfaceShape: UiShape,
    val cardShape: UiShape,
    val fieldShape: UiShape,
    val fullShape: UiShape,
    val buttonTextStyle: UiTextStyle,
    val bodyTextStyle: UiTextStyle,
    val labelTextStyle: UiTextStyle,
    val supportingTextStyle: UiTextStyle,
    val navigationTextStyle: UiTextStyle,
) {
    companion object {
        fun from(tokens: UiThemeTokens): Material3Recipes = Material3Recipes(
            colors = tokens.colors,
            interactions = tokens.interactions,
            surfaceShape = tokens.shapes.medium,
            cardShape = tokens.shapes.medium,
            fieldShape = tokens.shapes.extraSmall,
            fullShape = tokens.shapes.full,
            buttonTextStyle = tokens.typography.labelLarge,
            bodyTextStyle = tokens.typography.bodyLarge,
            labelTextStyle = tokens.typography.bodySmall,
            supportingTextStyle = tokens.typography.bodySmall,
            navigationTextStyle = tokens.typography.labelSmall,
        )
    }
}

private fun stateLayers(
    contentColor: Int,
    interactions: UiInteractionTokens,
): UiStateLayerColors = UiStateLayerColors(
    pressedColor = contentColor.withAlpha(interactions.pressedStateLayerOpacity),
    focusedColor = contentColor.withAlpha(interactions.focusedStateLayerOpacity),
    hoveredColor = contentColor.withAlpha(interactions.hoveredStateLayerOpacity),
)

private fun Int.withAlpha(alpha: Float): Int {
    val resolvedAlpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (this and 0x00FFFFFF) or (resolvedAlpha shl 24)
}
