package com.viewcompose.widget.core

// --- Button ---

/**
 * Button 族组件的局部颜色覆盖。
 * Scoped color overrides for Button-family components.
 *
 * null 表示保留 Theme 派生默认值，只覆盖调用方明确传入的颜色槽位。
 * null keeps the Theme-derived default, overriding only color slots explicitly supplied by the caller.
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

/**
 * 在 content 范围内覆盖 Button/IconButton 的颜色默认值。
 * Overrides Button/IconButton color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideButtonColors(
    override: ButtonColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalButtonColors, override) { content() }
}

// --- TextField ---

/**
 * TextField 容器和边框颜色的局部覆盖。
 * Scoped overrides for TextField container and border colors.
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

/**
 * 在 content 范围内覆盖 TextField 的容器和边框默认值。
 * Overrides TextField container and border defaults within the content scope.
 */
fun UiTreeBuilder.ProvideTextFieldColors(
    override: TextFieldColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalTextFieldColors, override) { content() }
}

// --- SegmentedControl ---

/**
 * SegmentedControl 背景、指示器和文本颜色的局部覆盖。
 * Scoped overrides for SegmentedControl background, indicator, and text colors.
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

/**
 * 在 content 范围内覆盖 SegmentedControl 的颜色默认值。
 * Overrides SegmentedControl color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideSegmentedControlColors(
    override: SegmentedControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalSegmentedControlColors, override) { content() }
}

// --- InputControl (checkbox, switch, radio, slider) ---

/**
 * 输入控件标签和控件本体颜色的局部覆盖。
 * Scoped overrides for input-control label and control colors.
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

/**
 * 在 content 范围内覆盖 Checkbox 的颜色默认值。
 * Overrides Checkbox color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideCheckboxColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalCheckboxColors, override) { content() }
}

/**
 * 在 content 范围内覆盖 Switch 的颜色默认值。
 * Overrides Switch color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideSwitchColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalSwitchColors, override) { content() }
}

/**
 * 在 content 范围内覆盖 RadioButton 的颜色默认值。
 * Overrides RadioButton color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideRadioButtonColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalRadioButtonColors, override) { content() }
}

/**
 * 在 content 范围内覆盖 Slider 的颜色默认值。
 * Overrides Slider color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideSliderColors(
    override: InputControlColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalSliderColors, override) { content() }
}

// --- ProgressIndicator ---

/**
 * 进度指示器颜色的局部覆盖。
 * Scoped color overrides for progress indicators.
 */
data class ProgressIndicatorColorOverride(
    val linearIndicator: Int? = null,
    val linearTrack: Int? = null,
    val circularIndicator: Int? = null,
    val circularTrack: Int? = null,
)

internal val LocalProgressIndicatorColors = uiLocalOf<ProgressIndicatorColorOverride?> { null }

/**
 * 在 content 范围内覆盖线性和圆形进度指示器的颜色默认值。
 * Overrides linear and circular progress indicator color defaults within the content scope.
 */
fun UiTreeBuilder.ProvideProgressIndicatorColors(
    override: ProgressIndicatorColorOverride,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalProgressIndicatorColors, override) { content() }
}
