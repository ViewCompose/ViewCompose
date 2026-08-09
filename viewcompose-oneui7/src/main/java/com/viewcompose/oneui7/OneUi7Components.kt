package com.viewcompose.oneui7

import com.viewcompose.animation.animateDpAsState
import com.viewcompose.animation.core.tween
import com.viewcompose.graphics.core.Brush
import com.viewcompose.gesture.rememberToggleDragState
import com.viewcompose.gesture.toggleDraggable
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.BasicButton
import com.viewcompose.ui.foundation.BasicButtonStyle
import com.viewcompose.ui.foundation.BasicSurface
import com.viewcompose.ui.foundation.BasicSurfaceStyle
import com.viewcompose.ui.foundation.BasicTextField
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DesignSystemAttributionProvider
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiButtonSizing
import com.viewcompose.ui.foundation.UiComponentAttribution
import com.viewcompose.ui.foundation.UiComponentBackend
import com.viewcompose.ui.foundation.UiColors
import com.viewcompose.ui.foundation.UiInteractionTokens
import com.viewcompose.ui.foundation.UiDesignConformance
import com.viewcompose.ui.foundation.UiDesignSystemAttribution
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiNavigationBarSizing
import com.viewcompose.ui.foundation.UiOverlays
import com.viewcompose.ui.foundation.UiShapes
import com.viewcompose.ui.foundation.UiStateColorDefaults
import com.viewcompose.ui.foundation.UiTextFieldSizing
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeDefaults
import com.viewcompose.ui.foundation.UiThemeMetadata
import com.viewcompose.ui.foundation.UiThemeOrigin
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTokenProvenance
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.UiTypography
import com.viewcompose.ui.foundation.uiLocalOf
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.SemanticsCollectionInfo
import com.viewcompose.ui.modifier.SemanticsCollectionItemInfo
import com.viewcompose.ui.modifier.SemanticsCollectionSelectionMode
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.offset
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/**
 * Identifies the public Samsung reference line and the deliberately bounded alpha component set.
 *
 * These strings are diagnostic identifiers, not a claim that Samsung publishes or endorses this
 * artifact. The implementation follows the public One UI 7 design story and the Samsung Developer
 * One UI guidelines accessed on 2026-08-09.
 */
object OneUi7Reference {
    /** Public design reference targeted by this artifact. */
    const val targetVersion: String = "One UI 7"

    /** Stable diagnostic identity for this five-component alpha slice. */
    const val componentSet: String = "one-ui-7-five-component-alpha"
}

/** Selects the filled primary or lower-emphasis neutral One UI 7 alpha button recipe. */
enum class OneUi7ButtonVariant {
    /** Uses the primary accent container and its paired content color. */
    Primary,

    /** Uses the neutral surface container and standard surface content color. */
    Neutral,
}

/**
 * Defines one text-only destination in [OneUi7NavigationBar].
 *
 * @property key stable caller-owned identity used for reconciliation
 * @property label localized destination label; blank labels are rejected because this alpha
 * component intentionally follows Samsung's text-only bottom-navigation guidance
 * @throws IllegalArgumentException if [label] is blank
 */
data class OneUi7NavigationItem(
    val key: Any,
    val label: String,
) {
    init {
        require(label.isNotBlank()) { "OneUi7NavigationItem label must not be blank." }
    }
}

/** Creates static One UI 7 alpha token snapshots without reading Android theme resources. */
object OneUi7ThemeDefaults {
    /**
     * Returns a new light token snapshot for the five-component alpha set.
     *
     * The colors and dimensions are ViewCompose-owned interpretations of Samsung's public guidance,
     * not extracted Samsung internal design tokens. Callers may copy and replace semantic values
     * before passing the result to [OneUi7Theme].
     *
     * @return a new immutable custom-origin light token snapshot
     */
    fun light(): UiThemeTokens = createTokens(isDark = false)

    /**
     * Returns a new dark token snapshot for the five-component alpha set.
     *
     * The snapshot preserves the same semantic roles, component geometry, and accessibility target
     * sizes as [light] while replacing the color scheme.
     *
     * @return a new immutable custom-origin dark token snapshot
     */
    fun dark(): UiThemeTokens = createTokens(isDark = true)

    private fun createTokens(isDark: Boolean): UiThemeTokens {
        val base = if (isDark) UiThemeDefaults.dark() else UiThemeDefaults.light()
        val colors = if (isDark) darkColors() else lightColors()
        return UiThemeTokens(
            colors = colors,
            typography = typography(),
            stateColors = UiStateColorDefaults.from(colors),
            shapes = UiShapes(
                extraSmall = UiShape.rounded(10.dp),
                small = UiShape.rounded(14.dp),
                medium = UiShape.rounded(20.dp),
                large = UiShape.rounded(26.dp),
                extraLarge = UiShape.rounded(32.dp),
                full = UiShape.roundedRelative(0.5f),
            ),
            controls = base.controls.copy(
                button = UiButtonSizing(
                    compactHeight = 44.dp,
                    mediumHeight = 48.dp,
                    largeHeight = 56.dp,
                    compactHorizontalPadding = 20.dp,
                    mediumHorizontalPadding = 24.dp,
                    largeHorizontalPadding = 28.dp,
                    compactVerticalPadding = 8.dp,
                    mediumVerticalPadding = 10.dp,
                    largeVerticalPadding = 12.dp,
                ),
                textField = UiTextFieldSizing(
                    compactHeight = 48.dp,
                    mediumHeight = 56.dp,
                    largeHeight = 64.dp,
                    compactHorizontalPadding = 16.dp,
                    mediumHorizontalPadding = 18.dp,
                    largeHorizontalPadding = 20.dp,
                    compactVerticalPadding = 10.dp,
                    mediumVerticalPadding = 12.dp,
                    largeVerticalPadding = 14.dp,
                ),
                navigationBar = UiNavigationBarSizing(
                    height = 68.dp,
                    iconSize = 0.dp,
                    labelSizeSp = 13.sp,
                ),
                minimumInteractiveHeight = 48.dp,
            ),
            interactions = UiInteractionTokens(
                pressedStateLayerOpacity = 0.12f,
                focusedStateLayerOpacity = 0.12f,
                hoveredStateLayerOpacity = 0.08f,
            ),
            overlays = UiOverlays(scrimOpacity = 0.40f),
            metadata = UiThemeMetadata(
                origin = UiThemeOrigin.FrameworkDefault,
                isDark = isDark,
                provenance = UiTokenProvenance(
                    sourceId = "viewcompose-oneui7/static",
                    defaultOrigin = UiThemeOrigin.FrameworkDefault,
                ),
            ),
        )
    }

    private fun typography(): UiTypography = UiTypography(
        displayLarge = UiTextStyle(fontSizeSp = 48.sp, fontWeight = 700, lineHeightSp = 56.sp),
        displayMedium = UiTextStyle(fontSizeSp = 40.sp, fontWeight = 700, lineHeightSp = 48.sp),
        displaySmall = UiTextStyle(fontSizeSp = 34.sp, fontWeight = 700, lineHeightSp = 42.sp),
        headlineLarge = UiTextStyle(fontSizeSp = 32.sp, fontWeight = 700, lineHeightSp = 40.sp),
        headlineMedium = UiTextStyle(fontSizeSp = 28.sp, fontWeight = 700, lineHeightSp = 36.sp),
        headlineSmall = UiTextStyle(fontSizeSp = 24.sp, fontWeight = 700, lineHeightSp = 32.sp),
        titleLarge = UiTextStyle(fontSizeSp = 22.sp, fontWeight = 650, lineHeightSp = 28.sp),
        titleMedium = UiTextStyle(fontSizeSp = 18.sp, fontWeight = 650, lineHeightSp = 24.sp),
        titleSmall = UiTextStyle(fontSizeSp = 16.sp, fontWeight = 650, lineHeightSp = 22.sp),
        bodyLarge = UiTextStyle(fontSizeSp = 17.sp, fontWeight = 400, lineHeightSp = 24.sp),
        bodyMedium = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 400, lineHeightSp = 22.sp),
        bodySmall = UiTextStyle(fontSizeSp = 13.sp, fontWeight = 400, lineHeightSp = 18.sp),
        labelLarge = UiTextStyle(fontSizeSp = 15.sp, fontWeight = 650, lineHeightSp = 20.sp),
        labelMedium = UiTextStyle(fontSizeSp = 13.sp, fontWeight = 650, lineHeightSp = 18.sp),
        labelSmall = UiTextStyle(fontSizeSp = 11.sp, fontWeight = 650, lineHeightSp = 16.sp),
    )

    private fun lightColors(): UiColors = UiColors(
        background = 0xFFF6F7F9.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        surfaceVariant = 0xFFF0F1F3.toInt(),
        surfaceContainerLow = 0xFFF8F8FA.toInt(),
        surfaceContainer = 0xFFF0F1F3.toInt(),
        surfaceContainerHigh = 0xFFE8E9EC.toInt(),
        onSurface = 0xFF17181A.toInt(),
        onSurfaceVariant = 0xFF5D6168.toInt(),
        primary = 0xFF006FFD.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        primaryContainer = 0xFFD9E8FF.toInt(),
        onPrimaryContainer = 0xFF003A7A.toInt(),
        secondary = 0xFF5B6573.toInt(),
        onSecondary = 0xFFFFFFFF.toInt(),
        secondaryContainer = 0xFFE7EAF0.toInt(),
        onSecondaryContainer = 0xFF303640.toInt(),
        error = 0xFFD9272E.toInt(),
        onError = 0xFFFFFFFF.toInt(),
        errorContainer = 0xFFFFE3E5.toInt(),
        onErrorContainer = 0xFF740008.toInt(),
        success = 0xFF168844.toInt(),
        warning = 0xFFB66A00.toInt(),
        info = 0xFF006FFD.toInt(),
        outline = 0xFFB7BAC0.toInt(),
        outlineVariant = 0xFFE0E2E6.toInt(),
        inverseSurface = 0xFF292A2D.toInt(),
        inverseOnSurface = 0xFFF7F7F8.toInt(),
        ripple = 0x1F17181A,
    )

    private fun darkColors(): UiColors = UiColors(
        background = 0xFF08090A.toInt(),
        surface = 0xFF17181A.toInt(),
        surfaceVariant = 0xFF25272A.toInt(),
        surfaceContainerLow = 0xFF111214.toInt(),
        surfaceContainer = 0xFF202225.toInt(),
        surfaceContainerHigh = 0xFF2B2D31.toInt(),
        onSurface = 0xFFF5F6F7.toInt(),
        onSurfaceVariant = 0xFFB6BAC1.toInt(),
        primary = 0xFF5FA2FF.toInt(),
        onPrimary = 0xFF002E67.toInt(),
        primaryContainer = 0xFF153E6F.toInt(),
        onPrimaryContainer = 0xFFD8E8FF.toInt(),
        secondary = 0xFFADB7C6.toInt(),
        onSecondary = 0xFF1F2731.toInt(),
        secondaryContainer = 0xFF343B45.toInt(),
        onSecondaryContainer = 0xFFE6EAF0.toInt(),
        error = 0xFFFF8589.toInt(),
        onError = 0xFF5E0005.toInt(),
        errorContainer = 0xFF6E151A.toInt(),
        onErrorContainer = 0xFFFFDADB.toInt(),
        success = 0xFF65D58A.toInt(),
        warning = 0xFFFFB95E.toInt(),
        info = 0xFF82B6FF.toInt(),
        outline = 0xFF696D74.toInt(),
        outlineVariant = 0xFF34373B.toInt(),
        inverseSurface = 0xFFE7E8EA.toInt(),
        inverseOnSurface = 0xFF25272A.toInt(),
        ripple = 0x29F5F6F7,
    )
}

private val LocalOneUi7Recipes = uiLocalOf<OneUi7Recipes?>(
    debugName = "OneUi7Recipes",
    debugValueFormatter = { if (it == null) "missing" else OneUi7Reference.componentSet },
    defaultFactory = { null },
)

/**
 * Installs one coherent One UI 7 alpha token and component-recipe snapshot for [content].
 *
 * Resolution is synchronous and platform independent. Nested providers restore the previous
 * design-system snapshot after [content] returns. Replace the root provider or host session to
 * switch design systems; do not mutate a token object in place. This provider does not read Samsung
 * resources, install a renderer branch, or change Android window/overlay semantics.
 *
 * @sample com.viewcompose.oneui7.samples.oneUi7ComponentsSample
 * @receiver active tree builder receiving the design-system scope
 * @param tokens immutable semantic values used to derive the five component recipes
 * @param content subtree built synchronously with the same token and recipe snapshot
 */
fun UiTreeBuilder.OneUi7Theme(
    tokens: UiThemeTokens = OneUi7ThemeDefaults.light(),
    content: UiTreeBuilder.() -> Unit,
) {
    UiTheme(tokens) {
        DesignSystemAttributionProvider(OneUi7Attribution) {
            ProvideLocal(LocalOneUi7Recipes, OneUi7Recipes.from(tokens)) {
                content()
            }
        }
    }
}

private val OneUi7Attribution = UiDesignSystemAttribution(
    designSystemId = "viewcompose-oneui7",
    recipeSetId = OneUi7Reference.componentSet,
    components = listOf(
        UiComponentAttribution(
            familyId = "surface-card",
            recipeId = "one-ui7-surface-v1",
            backend = UiComponentBackend.DslComposite,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "basic-surface",
        ),
        UiComponentAttribution(
            familyId = "button",
            recipeId = "one-ui7-button-v1",
            backend = UiComponentBackend.DslComposite,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "basic-button",
        ),
        UiComponentAttribution(
            familyId = "switch",
            recipeId = "one-ui7-switch-v1",
            backend = UiComponentBackend.DslComposite,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "anchored-drag-composite",
        ),
        UiComponentAttribution(
            familyId = "text-field",
            recipeId = "one-ui7-text-field-v1",
            backend = UiComponentBackend.NativeBehavioralCore,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "android-edit-text",
        ),
        UiComponentAttribution(
            familyId = "navigation-bar",
            recipeId = "one-ui7-navigation-v1",
            backend = UiComponentBackend.DslComposite,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "text-destination-composite",
        ),
    ),
)

/**
 * Emits a One UI 7 alpha text action through the shared design-system-neutral [BasicButton].
 *
 * The complete effective surface exposes Button semantics and a minimum 48dp target. [onClick] is
 * invoked synchronously on the Android main thread after an enabled click is accepted. Disabled
 * buttons preserve layout and semantics but install no click or transient state layer.
 *
 * @sample com.viewcompose.oneui7.samples.oneUi7ComponentsSample
 * @receiver active builder inside [OneUi7Theme]
 * @param text localized single-line action label
 * @param onClick optional action callback; `null` emits a non-interactive semantic button
 * @param variant primary or neutral container treatment
 * @param enabled whether click, focus state, and enabled semantics are active
 * @param key optional stable root identity
 * @param modifier caller configuration appended to the root action
 * @throws IllegalStateException when no [OneUi7Theme] is active
 */
fun UiTreeBuilder.OneUi7Button(
    text: String,
    onClick: (() -> Unit)?,
    variant: OneUi7ButtonVariant = OneUi7ButtonVariant.Primary,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipes = oneUi7Recipes()
    val containerColor = when {
        !enabled -> recipes.disabledContainerColor
        variant == OneUi7ButtonVariant.Primary -> recipes.colors.primary
        else -> recipes.colors.surfaceContainer
    }
    val contentColor = when {
        !enabled -> recipes.disabledContentColor
        variant == OneUi7ButtonVariant.Primary -> recipes.colors.onPrimary
        else -> recipes.colors.onSurface
    }
    BasicButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        key = key,
        style = BasicButtonStyle(
            surface = BasicSurfaceStyle(
                fill = Brush.SolidColor(containerColor),
                shape = recipes.actionShape,
                clipContent = true,
            ),
            contentColor = contentColor,
            textStyle = recipes.buttonTextStyle,
            stateLayerColors = stateLayers(contentColor, recipes.interactions),
            minimumHeight = 48.dp,
            visualHeight = 48.dp,
            paddingHorizontal = 24.dp,
            paddingVertical = 10.dp,
        ),
        modifier = modifier,
    )
}

/**
 * Emits a rounded One UI 7 alpha card surface through [BasicSurface].
 *
 * Content is clipped to the resolved shape and padded by 18dp. When [onClick] is non-null, the
 * complete surface becomes one merged Button-semantic target and invokes the callback synchronously
 * on the Android main thread. The surface owns no retained state.
 *
 * @sample com.viewcompose.oneui7.samples.oneUi7ComponentsSample
 * @receiver active builder inside [OneUi7Theme]
 * @param onClick optional callback that makes the complete surface interactive
 * @param enabled whether an interactive surface accepts input
 * @param key optional stable root identity
 * @param modifier caller configuration appended to the root surface
 * @param content child subtree built synchronously inside the padded surface
 * @throws IllegalStateException when no [OneUi7Theme] is active
 */
fun UiTreeBuilder.OneUi7Surface(
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    val recipes = oneUi7Recipes()
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(recipes.colors.surface),
            shape = recipes.surfaceShape,
            borderWidth = 1.dp,
            borderColor = recipes.colors.outlineVariant,
            clipContent = true,
        ),
        contentColor = recipes.colors.onSurface,
        enabled = enabled,
        onClick = onClick,
        stateLayerColors = stateLayers(recipes.colors.onSurface, recipes.interactions),
        role = if (onClick == null) null else SemanticsRole.Button,
        key = key,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

/**
 * Emits a One UI 7 alpha labeled switch as a design-system-owned composite.
 *
 * The caller owns [checked] and must publish the replacement value from [onCheckedChange]. The
 * complete 48dp-high row is one Switch-semantic target. Clicking any enabled part invokes the
 * callback synchronously with `!checked`; horizontal dragging follows the pointer and settles by
 * position or velocity without also firing the click action. Visual geometry and drag anchors are
 * renderer-neutral and follow layout direction, while Android retains focus and accessibility
 * action ownership.
 *
 * @sample com.viewcompose.oneui7.samples.oneUi7ComponentsSample
 * @receiver active builder inside [OneUi7Theme]
 * @param text localized label merged into the switch accessibility node
 * @param checked current caller-owned selection state
 * @param onCheckedChange callback receiving the requested replacement state
 * @param enabled whether input and enabled semantics are active
 * @param key optional stable root identity
 * @param modifier caller configuration appended to the complete target
 * @throws IllegalStateException when no [OneUi7Theme] is active
 */
fun UiTreeBuilder.OneUi7Switch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipes = oneUi7Recipes()
    val trackColor = when {
        !enabled -> recipes.colors.surfaceContainerHigh
        checked -> recipes.colors.primary
        else -> recipes.colors.surfaceContainerHigh
    }
    val thumbColor = when {
        !enabled -> recipes.disabledContentColor
        checked -> recipes.colors.onPrimary
        else -> recipes.colors.onSurfaceVariant
    }
    val travel = 20.dp
    val checkedOffset = if (Environment.layoutDirection == UiLayoutDirection.Rtl) {
        UiDp.Zero - travel
    } else {
        travel
    }
    val dragState = rememberToggleDragState(
        checked = checked,
        checkedAnchorOffsetPx = Environment.density.toPx(checkedOffset),
        onCheckedChange = onCheckedChange,
    )
    val idleThumbOffset = animateDpAsState(
        targetValue = if (checked) checkedOffset else UiDp.Zero,
        animationSpec = tween(durationMillis = 180),
    ).value
    val thumbOffset = if (dragState.isDragging.value) {
        UiDp(checkedOffset.value * dragState.progress.value)
    } else {
        idleThumbOffset
    }
    val control: UiTreeBuilder.() -> Unit = {
        BasicSurface(
            style = BasicSurfaceStyle(
                fill = Brush.SolidColor(trackColor),
                shape = recipes.fullShape,
                clipContent = true,
            ),
            contentColor = thumbColor,
            key = "one-ui7-switch-track-$enabled-$checked",
            modifier = Modifier.size(width = 48.dp, height = 28.dp).padding(3.dp),
            contentAlignment = BoxAlignment.CenterStart,
        ) {
            BasicSurface(
                style = BasicSurfaceStyle(
                    fill = Brush.SolidColor(thumbColor),
                    shape = recipes.fullShape,
                    clipContent = true,
                ),
                contentColor = thumbColor,
                key = "one-ui7-switch-thumb-$enabled-$checked",
                modifier = Modifier
                    .size(22.dp, 22.dp)
                    .offset(x = thumbOffset),
            ) {}
        }
    }
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(0x00000000),
            shape = UiShape.rounded(UiDp.Zero),
        ),
        contentColor = if (enabled) recipes.colors.onSurface else recipes.disabledContentColor,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        stateLayerColors = stateLayers(recipes.colors.onSurface, recipes.interactions),
        minimumHeight = 48.dp,
        role = SemanticsRole.Switch,
        key = key,
        modifier = modifier
            .toggleDraggable(state = dragState, enabled = enabled)
            .semantics(mergeDescendants = true) {
                role = SemanticsRole.Switch
                this.checked = checked
                this.enabled = enabled
            },
        contentAlignment = BoxAlignment.CenterStart,
    ) {
        Row(
            spacing = 14.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            if (Environment.layoutDirection == UiLayoutDirection.Rtl) control()
            Text(
                text = text,
                color = if (enabled) recipes.colors.onSurface else recipes.disabledContentColor,
                style = recipes.bodyTextStyle,
                modifier = Modifier.weight(1f),
            )
            if (Environment.layoutDirection == UiLayoutDirection.Ltr) control()
        }
    }
}

/**
 * Emits a One UI 7 alpha field decoration around the framework's native editing core.
 *
 * The caller owns [state], including text, selection, and composition. ViewCompose forwards the
 * same state plus [autofillHints] to the Android `EditText` bridge, so this composite does not
 * replace IME, selection, autofill, or saved-state behavior. Error text is exposed through
 * semantics and the supporting label; empty supporting text emits no extra label.
 *
 * @sample com.viewcompose.oneui7.samples.oneUi7ComponentsSample
 * @receiver active builder inside [OneUi7Theme]
 * @param state caller-owned native editing state
 * @param label localized field label shown above the editing surface
 * @param placeholder localized hint shown while the field is empty
 * @param supportingText optional localized guidance or error explanation
 * @param enabled whether the native field accepts editing and focus input
 * @param isError whether error color, border, and accessibility error semantics are active
 * @param autofillHints platform semantic categories forwarded to Android autofill
 * @param key optional stable identity for the outer field column
 * @param modifier caller configuration appended to the outer field column
 * @throws IllegalStateException when no [OneUi7Theme] is active
 */
fun UiTreeBuilder.OneUi7TextField(
    state: TextFieldState,
    label: String,
    placeholder: String = "",
    supportingText: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val recipes = oneUi7Recipes()
    Column(key = key, modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = when {
                    !enabled -> recipes.disabledContentColor
                    isError -> recipes.colors.error
                    else -> recipes.colors.onSurface
                },
                style = recipes.labelTextStyle,
                modifier = Modifier.margin(bottom = 7.dp),
            )
        }
        BasicTextField(
            state = state,
            placeholder = placeholder,
            enabled = enabled,
            textColor = if (enabled) recipes.colors.onSurface else recipes.disabledContentColor,
            hintColor = recipes.colors.onSurfaceVariant,
            cursorColor = if (isError) recipes.colors.error else recipes.colors.primary,
            textStyle = recipes.bodyTextStyle,
            backgroundColor = if (isError) recipes.colors.errorContainer else recipes.colors.surfaceVariant,
            borderWidth = if (isError) 1.dp else UiDp.Zero,
            borderColor = if (isError) recipes.colors.error else 0x00000000,
            shape = recipes.fieldShape,
            minHeight = 56.dp,
            paddingHorizontal = 18.dp,
            paddingVertical = 12.dp,
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
                modifier = Modifier.margin(top = 6.dp),
            )
        }
    }
}

/**
 * Emits the One UI 7 alpha text-only bottom navigation component.
 *
 * This implementation follows Samsung's public preference for fewer than four text tabs and
 * accepts two through five destinations as the published maximum. [selectedIndex] is caller-owned;
 * clicking an enabled destination invokes [onItemSelected] synchronously with its original list
 * index. RTL presentation reverses visual order without changing callback indices or keys.
 * Accessibility exposes one single-selection row plus each destination's logical column, selected
 * state, and tab role; logical positions also remain stable in RTL.
 *
 * @sample com.viewcompose.oneui7.samples.oneUi7ComponentsSample
 * @receiver active builder inside [OneUi7Theme]
 * @param items ordered destination snapshots with stable keys and non-blank labels
 * @param selectedIndex selected index in [items]
 * @param onItemSelected callback receiving the requested index
 * @param enabled whether destination targets accept input
 * @param key optional stable identity for the complete bar
 * @param modifier caller configuration appended to the complete bar
 * @throws IllegalArgumentException if item count is outside `2..5` or [selectedIndex] is invalid
 * @throws IllegalStateException when no [OneUi7Theme] is active
 */
fun UiTreeBuilder.OneUi7NavigationBar(
    items: List<OneUi7NavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    require(items.size in 2..5) { "OneUi7NavigationBar requires between 2 and 5 items." }
    require(selectedIndex in items.indices) { "OneUi7NavigationBar selectedIndex must reference an item." }
    val recipes = oneUi7Recipes()
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(recipes.colors.surface),
            shape = recipes.navigationShape,
            borderWidth = 1.dp,
            borderColor = recipes.colors.outlineVariant,
            clipContent = true,
        ),
        contentColor = recipes.colors.onSurface,
        minimumHeight = 68.dp,
        key = key,
        modifier = modifier.fillMaxWidth().semantics {
            collectionInfo = SemanticsCollectionInfo(
                rowCount = 1,
                columnCount = items.size,
                selectionMode = SemanticsCollectionSelectionMode.Single,
            )
        },
        contentAlignment = BoxAlignment.Center,
    ) {
        Row(
            arrangement = MainAxisArrangement.SpaceEvenly,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth().padding(4.dp),
        ) {
            val indexedItems = items.withIndex().let { values ->
                if (Environment.layoutDirection == UiLayoutDirection.Rtl) values.reversed() else values
            }
            indexedItems.forEach { (index, item) ->
                val selected = index == selectedIndex
                val contentColor = when {
                    !enabled -> recipes.disabledContentColor
                    selected -> recipes.colors.primary
                    else -> recipes.colors.onSurfaceVariant
                }
                BasicSurface(
                    style = BasicSurfaceStyle(
                        fill = Brush.SolidColor(
                            if (selected) recipes.colors.primaryContainer else 0x00000000,
                        ),
                        shape = recipes.fullShape,
                        clipContent = true,
                    ),
                    contentColor = contentColor,
                    enabled = enabled,
                    onClick = { onItemSelected(index) },
                    stateLayerColors = stateLayers(contentColor, recipes.interactions),
                    minimumHeight = 52.dp,
                    role = SemanticsRole.Tab,
                    key = item.key,
                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                        role = SemanticsRole.Tab
                        this.selected = selected
                        this.enabled = enabled
                        collectionItemInfo = SemanticsCollectionItemInfo(
                            rowIndex = 0,
                            columnIndex = index,
                        )
                    },
                    contentAlignment = BoxAlignment.Center,
                ) {
                    Text(
                        text = item.label,
                        color = contentColor,
                        style = if (selected) recipes.navigationSelectedTextStyle else recipes.navigationTextStyle,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private data class OneUi7Recipes(
    val colors: UiColors,
    val interactions: UiInteractionTokens,
    val actionShape: UiShape,
    val surfaceShape: UiShape,
    val fieldShape: UiShape,
    val navigationShape: UiShape,
    val fullShape: UiShape,
    val buttonTextStyle: UiTextStyle,
    val bodyTextStyle: UiTextStyle,
    val labelTextStyle: UiTextStyle,
    val supportingTextStyle: UiTextStyle,
    val navigationTextStyle: UiTextStyle,
    val navigationSelectedTextStyle: UiTextStyle,
    val disabledContainerColor: Int,
    val disabledContentColor: Int,
) {
    companion object {
        fun from(tokens: UiThemeTokens): OneUi7Recipes = OneUi7Recipes(
            colors = tokens.colors,
            interactions = tokens.interactions,
            actionShape = tokens.shapes.full,
            surfaceShape = tokens.shapes.large,
            fieldShape = tokens.shapes.medium,
            navigationShape = tokens.shapes.large,
            fullShape = tokens.shapes.full,
            buttonTextStyle = tokens.typography.labelLarge,
            bodyTextStyle = tokens.typography.bodyMedium,
            labelTextStyle = tokens.typography.labelMedium,
            supportingTextStyle = tokens.typography.bodySmall,
            navigationTextStyle = tokens.typography.labelMedium,
            navigationSelectedTextStyle = tokens.typography.labelMedium.copy(fontWeight = 750),
            disabledContainerColor = tokens.colors.surfaceContainerHigh,
            disabledContentColor = tokens.colors.onSurface.withAlpha(0.38f),
        )
    }
}

private fun oneUi7Recipes(): OneUi7Recipes = checkNotNull(UiLocals.current(LocalOneUi7Recipes)) {
    "One UI 7 alpha components require an active OneUi7Theme provider."
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
