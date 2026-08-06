package com.viewcompose.ui.foundation

// --- Button ---

/**
 * Overrides selected Button-family color slots inside a local provider scope.
 *
 * Every `null` property preserves the value derived by `ButtonDefaults` from the current theme.
 *
 * @property primaryContainer enabled primary button container
 * @property primaryContent enabled primary button content
 * @property primaryDisabledContainer disabled primary button container
 * @property primaryDisabledContent disabled primary button content
 * @property secondaryContainer enabled secondary button container
 * @property secondaryContent enabled secondary button content
 * @property secondaryDisabledContainer disabled secondary button container
 * @property secondaryDisabledContent disabled secondary button content
 * @property tonalContainer enabled tonal button container
 * @property tonalContent enabled tonal button content
 * @property tonalDisabledContainer disabled tonal button container
 * @property tonalDisabledContent disabled tonal button content
 * @property outlinedContent enabled outlined button content
 * @property outlinedBorder enabled outlined button border
 * @property outlinedDisabledContent disabled outlined button content
 * @property outlinedDisabledBorder disabled outlined button border
 */
data class ButtonColorOverride(
    val primaryContainer: Int? = null,
    val primaryContent: Int? = null,
    val primaryDisabledContainer: Int? = null,
    val primaryDisabledContent: Int? = null,
    val secondaryContainer: Int? = null,
    val secondaryContent: Int? = null,
    val secondaryDisabledContainer: Int? = null,
    val secondaryDisabledContent: Int? = null,
    val tonalContainer: Int? = null,
    val tonalContent: Int? = null,
    val tonalDisabledContainer: Int? = null,
    val tonalDisabledContent: Int? = null,
    val outlinedContent: Int? = null,
    val outlinedBorder: Int? = null,
    val outlinedDisabledContent: Int? = null,
    val outlinedDisabledBorder: Int? = null,
)

internal val LocalButtonColors = uiLocalOf<ButtonColorOverride?> { null }

/** Provides [override] to Button and IconButton defaults while building [content]. */
fun UiTreeBuilder.ProvideButtonColors(
    override: ButtonColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalButtonColors, override) { content() }
}

// --- TextField ---

/**
 * Overrides selected TextField container and border slots inside a local provider scope.
 *
 * Every `null` property preserves the value derived by `TextFieldDefaults` from the current theme.
 *
 * @property filledContainer enabled filled-field container
 * @property filledDisabledContainer disabled filled-field container
 * @property filledErrorContainer error filled-field container
 * @property tonalContainer enabled tonal-field container
 * @property tonalDisabledContainer disabled tonal-field container
 * @property tonalErrorContainer error tonal-field container
 * @property outlinedBorder enabled outlined-field border
 * @property outlinedDisabledBorder disabled outlined-field border
 * @property outlinedErrorBorder error outlined-field border
 */
data class TextFieldColorOverride(
    val filledContainer: Int? = null,
    val filledDisabledContainer: Int? = null,
    val filledErrorContainer: Int? = null,
    val tonalContainer: Int? = null,
    val tonalDisabledContainer: Int? = null,
    val tonalErrorContainer: Int? = null,
    val outlinedBorder: Int? = null,
    val outlinedDisabledBorder: Int? = null,
    val outlinedErrorBorder: Int? = null,
)

internal val LocalTextFieldColors = uiLocalOf<TextFieldColorOverride?> { null }

/** Provides [override] to TextField defaults while building [content]. */
fun UiTreeBuilder.ProvideTextFieldColors(
    override: TextFieldColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalTextFieldColors, override) { content() }
}

// --- SegmentedControl ---

/**
 * Overrides selected segmented-control colors inside a local provider scope.
 *
 * Every `null` property preserves the value derived by `SegmentedControlDefaults` from the current
 * theme.
 *
 * @property background enabled track background
 * @property backgroundDisabled disabled track background
 * @property indicator enabled selected-segment indicator
 * @property indicatorDisabled disabled selected-segment indicator
 * @property text enabled unselected label
 * @property textDisabled disabled unselected label
 * @property selectedText enabled selected label
 * @property selectedTextDisabled disabled selected label
 */
data class SegmentedControlColorOverride(
    val background: Int? = null,
    val backgroundDisabled: Int? = null,
    val indicator: Int? = null,
    val indicatorDisabled: Int? = null,
    val text: Int? = null,
    val textDisabled: Int? = null,
    val selectedText: Int? = null,
    val selectedTextDisabled: Int? = null,
)

internal val LocalSegmentedControlColors = uiLocalOf<SegmentedControlColorOverride?> { null }

/** Provides [override] to SegmentedControl defaults while building [content]. */
fun UiTreeBuilder.ProvideSegmentedControlColors(
    override: SegmentedControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalSegmentedControlColors, override) { content() }
}

// --- InputControl (checkbox, switch, radio, slider) ---

/**
 * Overrides label and native-control colors for one input-control family.
 *
 * Each provider installs this model into a family-specific local, preventing Checkbox, Switch,
 * RadioButton, and Slider customization from leaking into one another.
 *
 * @property label enabled label color
 * @property labelDisabled disabled label color
 * @property control enabled control color
 * @property controlDisabled disabled control color
 */
data class InputControlColorOverride(
    val label: Int? = null,
    val labelDisabled: Int? = null,
    val control: Int? = null,
    val controlDisabled: Int? = null,
)

internal val LocalCheckboxColors = uiLocalOf<InputControlColorOverride?> { null }
internal val LocalSwitchColors = uiLocalOf<InputControlColorOverride?> { null }
internal val LocalRadioButtonColors = uiLocalOf<InputControlColorOverride?> { null }
internal val LocalSliderColors = uiLocalOf<InputControlColorOverride?> { null }

/** Provides [override] to Checkbox defaults while building [content]. */
fun UiTreeBuilder.ProvideCheckboxColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalCheckboxColors, override) { content() }
}

/** Provides [override] to Switch defaults while building [content]. */
fun UiTreeBuilder.ProvideSwitchColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalSwitchColors, override) { content() }
}

/** Provides [override] to RadioButton defaults while building [content]. */
fun UiTreeBuilder.ProvideRadioButtonColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalRadioButtonColors, override) { content() }
}

/** Provides [override] to Slider defaults while building [content]. */
fun UiTreeBuilder.ProvideSliderColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalSliderColors, override) { content() }
}

// --- ProgressIndicator ---

/**
 * Overrides selected linear and circular progress-indicator colors.
 *
 * @property linearIndicator active linear indicator color
 * @property linearTrack inactive linear track color
 * @property circularIndicator active circular indicator color
 * @property circularTrack inactive circular track color
 */
data class ProgressIndicatorColorOverride(
    val linearIndicator: Int? = null,
    val linearTrack: Int? = null,
    val circularIndicator: Int? = null,
    val circularTrack: Int? = null,
)

internal val LocalProgressIndicatorColors = uiLocalOf<ProgressIndicatorColorOverride?> { null }

/** Provides [override] to linear and circular progress defaults while building [content]. */
fun UiTreeBuilder.ProvideProgressIndicatorColors(
    override: ProgressIndicatorColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalProgressIndicatorColors, override) { content() }
}
