package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily

/**
 * 发射带文本标签的复选框节点。
 * Emits a checkbox node with a text label.
 *
 * 该 DSL 只描述当前 checked 状态，状态持有与更新由调用方在 onCheckedChange 中完成。
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
    // controlColor 表示控件本体的基础颜色，checked/unchecked 颜色保留给平台控件的状态列表。
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
 * 发射带文本标签的开关节点。
 * Emits a switch node with a text label.
 *
 * thumbColor/trackColor 为空时交给 renderer 使用平台默认或主题派生颜色。
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
    // 开关和 checkbox/radio 共享 ToggleNodeProps，renderer 根据 NodeType 选择原生控件。
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
 * 发射互斥选择场景使用的单选按钮节点。
 * Emits a radio button node for mutually exclusive selection flows.
 *
 * 该函数不管理同组互斥关系；调用方需要用共享状态确保只有一个选项被选中。
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
    // 单选按钮使用独立默认色，便于主题在不同输入控件间做差异化处理。
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
 * 发射整数值滑块节点。
 * Emits an integer-valued slider node.
 *
 * value 应保持在 min..max 范围内；renderer 负责映射到平台进度条的实际范围。
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
