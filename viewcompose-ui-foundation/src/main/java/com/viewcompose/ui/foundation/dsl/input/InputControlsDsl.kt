package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.minHeight
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily

/**
 * Displays a labeled checkbox and reports accepted checked-state changes.
 *
 * State remains caller-owned. The current theme supplies a minimum effective height before
 * [modifier] is appended; this can enlarge the native View and accessibility bounds without
 * changing its centered platform indicator. An explicit exact height in [modifier] remains
 * authoritative.
 *
 * @sample com.viewcompose.ui.foundation.samples.compactInputTargetSample
 * @receiver active tree builder that receives the emitted Checkbox node
 * @param text visible label associated with the checkbox
 * @param checked current caller-owned checked state
 * @param onCheckedChange callback invoked synchronously on the renderer thread with the requested state
 * @param enabled whether input is accepted and enabled color roles are used
 * @param checkedColor ARGB indicator color used for the checked state
 * @param uncheckedColor ARGB indicator color used for the unchecked state
 * @param style immutable text appearance snapshot for [text]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
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
        modifier = Modifier
            .minHeight(InputControlDefaults.minimumInteractiveHeight())
            .then(modifier),
    )
}

/**
 * Displays a labeled switch and reports accepted checked-state changes.
 *
 * State remains caller-owned, and default thumb and track colors resolve from the complete checked
 * and enabled state. The current theme supplies a minimum effective height before [modifier] is
 * appended; an explicit exact height in [modifier] remains authoritative.
 *
 * @sample com.viewcompose.ui.foundation.samples.compactInputTargetSample
 * @receiver active tree builder that receives the emitted Switch node
 * @param text visible label associated with the switch
 * @param checked current caller-owned checked state
 * @param onCheckedChange callback invoked synchronously on the renderer thread with the requested state
 * @param enabled whether input is accepted and enabled color roles are used
 * @param thumbColor optional ARGB thumb color, or `null` to retain the renderer-native value
 * @param trackColor optional ARGB track color, or `null` to retain the renderer-native value
 * @param style immutable text appearance snapshot for [text]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
 */
fun UiTreeBuilder.Switch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    thumbColor: Int? = InputControlDefaults.switchThumbColor(checked, enabled),
    trackColor: Int? = InputControlDefaults.switchTrackColor(checked, enabled),
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
        modifier = Modifier
            .minHeight(InputControlDefaults.minimumInteractiveHeight())
            .then(modifier),
    )
}

/**
 * Displays a labeled radio button for a caller-owned mutually exclusive selection flow.
 *
 * This component does not manage group exclusivity. The current theme supplies a minimum effective
 * height before [modifier] is appended; an explicit exact height in [modifier] remains authoritative.
 *
 * @sample com.viewcompose.ui.foundation.samples.compactInputTargetSample
 * @receiver active tree builder that receives the emitted RadioButton node
 * @param text visible label associated with the option
 * @param checked whether this option is currently selected
 * @param onCheckedChange callback invoked synchronously on the renderer thread with the requested state
 * @param enabled whether input is accepted and enabled color roles are used
 * @param checkedColor ARGB indicator color used for the selected state
 * @param uncheckedColor ARGB indicator color used for the unselected state
 * @param style immutable text appearance snapshot for [text]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
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
        modifier = Modifier
            .minHeight(InputControlDefaults.minimumInteractiveHeight())
            .then(modifier),
    )
}

/**
 * Displays an integer-valued slider and reports accepted value changes.
 *
 * State remains caller-owned. Callers should keep [value] within the inclusive [min] to [max]
 * range; this DSL does not normalize inconsistent inputs. The current theme supplies a minimum
 * effective height before [modifier] is appended. Android Renderer keeps the native track and
 * thumb centered inside that height, while an explicit exact application or parent height remains
 * authoritative.
 *
 * @sample com.viewcompose.ui.foundation.samples.compactInputTargetSample
 * @receiver active tree builder that receives the emitted Slider node
 * @param value current caller-owned integer value
 * @param onValueChange callback invoked synchronously on the renderer thread with the requested value
 * @param min inclusive lower bound of the platform progress range
 * @param max inclusive upper bound of the platform progress range
 * @param enabled whether input is accepted and enabled color roles are used
 * @param thumbColor ARGB color applied to the slider thumb
 * @param trackColor ARGB color applied to the active slider track
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
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
        modifier = Modifier
            .minHeight(InputControlDefaults.minimumInteractiveHeight())
            .then(modifier),
    )
}
