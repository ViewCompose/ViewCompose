package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily

/**
 * Emits a checkbox node with a text label.
 *
 * This DSL only describes the current checked state; callers own state storage and update it from onCheckedChange.
 */
fun UiTreeBuilder.Checkbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    checkedColor: Int = InputControlDefaults.checkboxCheckedColor(enabled),
    uncheckedColor: Int = InputControlDefaults.checkboxUncheckedColor(enabled),
    style: UiTextStyle = InputControlDefaults.labelStyle(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    // controlColor is the base control tint, while checked/unchecked colors remain for platform state lists.
    val controlColor = InputControlDefaults.checkboxControlColor(enabled)
    emit(
        type = NodeType.Checkbox,
        key = key,
        spec = ToggleNodeProps(
            text = text,
            enabled = enabled,
            checked = checked,
            controlColor = controlColor,
            checkedColor = checkedColor,
            uncheckedColor = uncheckedColor,
            onCheckedChange = onCheckedChange,
            textColor = InputControlDefaults.checkboxLabelColor(enabled),
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            rippleColor = InputControlDefaults.pressedColor(),
        ),
        modifier = modifier,
    )
}

/**
 * Emits a switch node with a text label.
 *
 * Null thumbColor/trackColor lets the renderer use platform defaults or theme-derived colors.
 */
fun UiTreeBuilder.Switch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    thumbColor: Int? = null,
    trackColor: Int? = null,
    style: UiTextStyle = InputControlDefaults.labelStyle(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    // Switch shares ToggleNodeProps with checkbox/radio; the renderer picks the native control from NodeType.
    val controlColor = InputControlDefaults.switchControlColor(enabled)
    emit(
        type = NodeType.Switch,
        key = key,
        spec = ToggleNodeProps(
            text = text,
            enabled = enabled,
            checked = checked,
            controlColor = controlColor,
            thumbColor = thumbColor,
            trackColor = trackColor,
            onCheckedChange = onCheckedChange,
            textColor = InputControlDefaults.switchLabelColor(enabled),
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            rippleColor = InputControlDefaults.pressedColor(),
        ),
        modifier = modifier,
    )
}

/**
 * Emits a radio button node for mutually exclusive selection flows.
 *
 * This function does not manage group exclusivity; callers should enforce a single selected option with shared state.
 */
fun UiTreeBuilder.RadioButton(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    checkedColor: Int = InputControlDefaults.radioButtonCheckedColor(enabled),
    uncheckedColor: Int = InputControlDefaults.radioButtonUncheckedColor(enabled),
    style: UiTextStyle = InputControlDefaults.labelStyle(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    // Radio buttons use separate defaults so themes can differentiate input controls.
    val controlColor = InputControlDefaults.radioButtonControlColor(enabled)
    emit(
        type = NodeType.RadioButton,
        key = key,
        spec = ToggleNodeProps(
            text = text,
            enabled = enabled,
            checked = checked,
            controlColor = controlColor,
            checkedColor = checkedColor,
            uncheckedColor = uncheckedColor,
            onCheckedChange = onCheckedChange,
            textColor = InputControlDefaults.radioButtonLabelColor(enabled),
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            rippleColor = InputControlDefaults.pressedColor(),
        ),
        modifier = modifier,
    )
}

/**
 * Emits an integer-valued slider node.
 *
 * value should stay within min..max; the renderer maps it onto the platform progress range.
 */
fun UiTreeBuilder.Slider(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int = 0,
    max: Int = 100,
    enabled: Boolean = true,
    thumbColor: Int = InputControlDefaults.sliderThumbColor(enabled),
    trackColor: Int = InputControlDefaults.sliderTrackColor(enabled),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.Slider,
        key = key,
        spec = SliderNodeProps(
            min = min,
            max = max,
            value = value,
            enabled = enabled,
            thumbColor = thumbColor,
            trackColor = trackColor,
            onValueChange = onValueChange,
        ),
        modifier = modifier,
    )
}
