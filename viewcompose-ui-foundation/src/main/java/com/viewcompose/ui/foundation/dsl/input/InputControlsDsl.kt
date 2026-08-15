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
 * @param overrides sparse instance appearance applied after scoped [ProvideCheckboxOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
 */
fun UiTreeBuilder.Checkbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    overrides: CheckboxOverrides = CheckboxOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = InputControlDefaults.resolveCheckbox(enabled, overrides)
    emit(
        type = NodeType.Checkbox,
        key = key,
        spec = ToggleNodeProps(
            text = text,
            enabled = enabled,
            checked = checked,
            controlColor = appearance.controlColor,
            checkedColor = appearance.checkedColor,
            uncheckedColor = appearance.uncheckedColor,
            onCheckedChange = onCheckedChange,
            textColor = appearance.labelColor,
            textSizeSp = appearance.textStyle.fontSizeSp,
            fontWeight = appearance.textStyle.fontWeight,
            fontFamily = uiFontFamily(appearance.textStyle.fontFamily),
            letterSpacingEm = appearance.textStyle.letterSpacingEm,
            lineHeightSp = appearance.textStyle.lineHeightSp,
            includeFontPadding = appearance.textStyle.includeFontPadding,
            rippleColor = appearance.rippleColor,
        ),
        modifier = Modifier
            .minHeight(appearance.minimumHeight)
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
 * @param overrides sparse instance appearance applied after scoped [ProvideSwitchOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
 */
fun UiTreeBuilder.Switch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    overrides: SwitchOverrides = SwitchOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = InputControlDefaults.resolveSwitch(checked, enabled, overrides)
    emit(
        type = NodeType.Switch,
        key = key,
        spec = ToggleNodeProps(
            text = text,
            enabled = enabled,
            checked = checked,
            controlColor = appearance.controlColor,
            thumbColor = appearance.thumbColor,
            trackColor = appearance.trackColor,
            onCheckedChange = onCheckedChange,
            textColor = appearance.labelColor,
            textSizeSp = appearance.textStyle.fontSizeSp,
            fontWeight = appearance.textStyle.fontWeight,
            fontFamily = uiFontFamily(appearance.textStyle.fontFamily),
            letterSpacingEm = appearance.textStyle.letterSpacingEm,
            lineHeightSp = appearance.textStyle.lineHeightSp,
            includeFontPadding = appearance.textStyle.includeFontPadding,
            rippleColor = appearance.rippleColor,
        ),
        modifier = Modifier
            .minHeight(appearance.minimumHeight)
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
 * @param overrides sparse instance appearance applied after scoped [ProvideRadioButtonOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
 */
fun UiTreeBuilder.RadioButton(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    overrides: RadioButtonOverrides = RadioButtonOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = InputControlDefaults.resolveRadioButton(enabled, overrides)
    emit(
        type = NodeType.RadioButton,
        key = key,
        spec = ToggleNodeProps(
            text = text,
            enabled = enabled,
            checked = checked,
            controlColor = appearance.controlColor,
            checkedColor = appearance.checkedColor,
            uncheckedColor = appearance.uncheckedColor,
            onCheckedChange = onCheckedChange,
            textColor = appearance.labelColor,
            textSizeSp = appearance.textStyle.fontSizeSp,
            fontWeight = appearance.textStyle.fontWeight,
            fontFamily = uiFontFamily(appearance.textStyle.fontFamily),
            letterSpacingEm = appearance.textStyle.letterSpacingEm,
            lineHeightSp = appearance.textStyle.lineHeightSp,
            includeFontPadding = appearance.textStyle.includeFontPadding,
            rippleColor = appearance.rippleColor,
        ),
        modifier = Modifier
            .minHeight(appearance.minimumHeight)
            .then(modifier),
    )
}

/**
 * Displays an integer-valued slider and reports accepted value changes.
 *
 * State remains caller-owned. [value] and the inclusive [min] to [max] range must align exactly to
 * [step]. A user interaction invokes [onValueChangeStarted] before its first change, reports each
 * accepted stepped value through [onValueChange], and then invokes [onValueChangeFinished].
 * Declarative binding never invokes these callbacks.
 *
 * The current theme supplies a minimum effective height before [modifier] is appended. Android
 * Renderer keeps the native track and thumb centered inside that height, while an exact
 * application or parent height remains authoritative.
 *
 * @sample com.viewcompose.ui.foundation.samples.sliderInteractionSample
 * @receiver active tree builder that receives the emitted Slider node
 * @param value current caller-owned integer value
 * @param onValueChange callback invoked synchronously on the renderer thread with the requested value
 * @param min inclusive lower bound of the platform progress range
 * @param max inclusive upper bound of the platform progress range
 * @param step positive interval between adjacent accepted values
 * @param onValueChangeStarted optional callback invoked before a touch, key, or accessibility interaction
 * @param onValueChangeFinished optional callback invoked after that interaction completes
 * @param enabled whether input is accepted and enabled color roles are used
 * @param overrides sparse instance appearance applied after scoped [ProvideSliderOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the themed minimum effective height
 * @throws IllegalArgumentException when the range, value, or step cannot form exact step indexes
 */
fun UiTreeBuilder.Slider(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int = 0,
    max: Int = 100,
    step: Int = 1,
    onValueChangeStarted: (() -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    overrides: SliderOverrides = SliderOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = InputControlDefaults.resolveSlider(enabled, overrides)
    emit(
        type = NodeType.Slider,
        key = key,
        spec = SliderNodeProps(
            min = min,
            max = max,
            value = value,
            enabled = enabled,
            thumbColor = appearance.thumbColor,
            trackColor = appearance.activeTrackColor,
            onValueChange = onValueChange,
            inactiveTrackColor = appearance.inactiveTrackColor,
            step = step,
            onValueChangeStarted = onValueChangeStarted,
            onValueChangeFinished = onValueChangeFinished,
        ),
        modifier = Modifier
            .minHeight(appearance.minimumHeight)
            .then(modifier),
    )
}
