package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.unit.UiDp

/** Resolves native input-control appearance from theme tokens and family-specific overrides. */
object InputControlDefaults {
    /** Returns the theme minimum effective height shared by compact native input controls. */
    fun minimumInteractiveHeight(): UiDp = Theme.controls.minimumInteractiveHeight

    /** Returns the visible geometry snapshot for a design-owned Switch composite. */
    fun switchSizing(): UiSwitchSizing = Theme.controls.switch

    /** Returns the shared body typography used by labeled controls. */
    fun labelStyle(): UiTextStyle = TextDefaults.bodyStyle()

    /** Resolves Checkbox label color for [enabled] state. */
    fun checkboxLabelColor(enabled: Boolean = true): Int {
        val overrides = checkboxOverrides()
        return resolveStateValue(
            enabled,
            overrides.labelColor,
            overrides.disabledLabelColor,
            Theme.stateColors.primaryText.resolve(),
            disabledContentColor(),
        )
    }

    /** Resolves the Checkbox base control color for [enabled] state. */
    fun checkboxControlColor(enabled: Boolean = true): Int = checkboxCheckedColor(enabled)

    /** Resolves checked Checkbox color for [enabled] state. */
    fun checkboxCheckedColor(enabled: Boolean = true): Int {
        val overrides = checkboxOverrides()
        return resolveStateValue(
            enabled,
            overrides.checkedColor,
            overrides.disabledCheckedColor,
            Theme.colors.primary,
            disabledContentColor(),
        )
    }

    /** Resolves unchecked Checkbox color for [enabled] state. */
    fun checkboxUncheckedColor(enabled: Boolean = true): Int {
        val overrides = checkboxOverrides()
        return resolveStateValue(
            enabled,
            overrides.uncheckedColor,
            overrides.disabledUncheckedColor,
            Theme.stateColors.control.resolve(),
            disabledContentColor(),
        )
    }

    /** Resolves Switch label color for [enabled] state. */
    fun switchLabelColor(enabled: Boolean = true): Int {
        val overrides = switchOverrides()
        return resolveStateValue(
            enabled,
            overrides.labelColor,
            overrides.disabledLabelColor,
            Theme.stateColors.primaryText.resolve(),
            disabledContentColor(),
        )
    }

    /** Resolves the Switch base control color for [checked] and [enabled] state. */
    fun switchControlColor(checked: Boolean = true, enabled: Boolean = true): Int =
        switchTrackColor(checked, enabled)

    /** Resolves Switch thumb color for [checked] and [enabled] state. */
    fun switchThumbColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val overrides = switchOverrides()
        return when {
            enabled && checked -> overrides.checkedThumbColor ?: Theme.colors.onPrimary
            enabled -> overrides.uncheckedThumbColor ?: Theme.colors.outline
            checked -> overrides.disabledCheckedThumbColor ?: Theme.colors.surface
            else -> overrides.disabledUncheckedThumbColor ?: disabledContentColor()
        }
    }

    /** Resolves Switch track color for [checked] and [enabled] state. */
    fun switchTrackColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val overrides = switchOverrides()
        return when {
            enabled && checked -> overrides.checkedTrackColor ?: Theme.colors.primary
            enabled -> overrides.uncheckedTrackColor ?: Theme.colors.surfaceContainerHighest
            checked -> overrides.disabledCheckedTrackColor ?: disabledContainerColor()
            else -> overrides.disabledUncheckedTrackColor
                ?: colorWithAlpha(Theme.colors.surfaceContainerHighest, 0.12f)
        }
    }

    /** Resolves RadioButton label color for [enabled] state. */
    fun radioButtonLabelColor(enabled: Boolean = true): Int {
        val overrides = radioOverrides()
        return resolveStateValue(
            enabled,
            overrides.labelColor,
            overrides.disabledLabelColor,
            Theme.stateColors.primaryText.resolve(),
            disabledContentColor(),
        )
    }

    /** Resolves the RadioButton base control color for [enabled] state. */
    fun radioButtonControlColor(enabled: Boolean = true): Int = radioButtonCheckedColor(enabled)

    /** Resolves checked RadioButton color for [enabled] state. */
    fun radioButtonCheckedColor(enabled: Boolean = true): Int {
        val overrides = radioOverrides()
        return resolveStateValue(
            enabled,
            overrides.checkedColor,
            overrides.disabledCheckedColor,
            Theme.colors.primary,
            disabledContentColor(),
        )
    }

    /** Resolves unchecked RadioButton color for [enabled] state. */
    fun radioButtonUncheckedColor(enabled: Boolean = true): Int {
        val overrides = radioOverrides()
        return resolveStateValue(
            enabled,
            overrides.uncheckedColor,
            overrides.disabledUncheckedColor,
            Theme.stateColors.control.resolve(),
            disabledContentColor(),
        )
    }

    /** Resolves the Slider base control color for [enabled] state. */
    fun sliderControlColor(enabled: Boolean = true): Int = sliderThumbColor(enabled)

    /** Resolves Slider thumb color for [enabled] state. */
    fun sliderThumbColor(enabled: Boolean = true): Int {
        val overrides = sliderOverrides()
        return resolveStateValue(
            enabled,
            overrides.thumbColor,
            overrides.disabledThumbColor,
            Theme.colors.primary,
            disabledContentColor(),
        )
    }

    /** Resolves Slider active-track color for [enabled] state. */
    fun sliderTrackColor(enabled: Boolean = true): Int {
        val overrides = sliderOverrides()
        return resolveStateValue(
            enabled,
            overrides.activeTrackColor,
            overrides.disabledActiveTrackColor,
            Theme.colors.primary,
            disabledContentColor(),
        )
    }

    /** Resolves Slider inactive-track color for [enabled] state. */
    fun sliderInactiveTrackColor(enabled: Boolean = true): Int {
        val overrides = sliderOverrides()
        return resolveStateValue(
            enabled,
            overrides.inactiveTrackColor,
            overrides.disabledInactiveTrackColor,
            Theme.colors.secondaryContainer,
            disabledContainerColor(),
        )
    }

    internal fun resolveCheckbox(
        enabled: Boolean,
        instance: CheckboxOverrides,
    ): ResolvedToggleAppearance {
        val overrides = checkboxOverrides().merge(instance)
        val checked = resolveStateValue(
            enabled,
            overrides.checkedColor,
            overrides.disabledCheckedColor,
            Theme.colors.primary,
            disabledContentColor(),
        )
        return ResolvedToggleAppearance(
            labelColor = resolveStateValue(
                enabled,
                overrides.labelColor,
                overrides.disabledLabelColor,
                Theme.stateColors.primaryText.resolve(),
                disabledContentColor(),
            ),
            controlColor = checked,
            checkedColor = checked,
            uncheckedColor = resolveStateValue(
                enabled,
                overrides.uncheckedColor,
                overrides.disabledUncheckedColor,
                Theme.stateColors.control.resolve(),
                disabledContentColor(),
            ),
            textStyle = overrides.textStyle ?: TextDefaults.bodyStyle(),
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(checked),
            minimumHeight = overrides.minimumHeight ?: minimumInteractiveHeight(),
        )
    }

    internal fun resolveSwitch(
        checked: Boolean,
        enabled: Boolean,
        instance: SwitchOverrides,
    ): ResolvedSwitchAppearance {
        val overrides = switchOverrides().merge(instance)
        val thumbColor = when {
            enabled && checked -> overrides.checkedThumbColor ?: Theme.colors.onPrimary
            enabled -> overrides.uncheckedThumbColor ?: Theme.colors.outline
            checked -> overrides.disabledCheckedThumbColor ?: Theme.colors.surface
            else -> overrides.disabledUncheckedThumbColor ?: disabledContentColor()
        }
        val trackColor = when {
            enabled && checked -> overrides.checkedTrackColor ?: Theme.colors.primary
            enabled -> overrides.uncheckedTrackColor ?: Theme.colors.surfaceContainerHighest
            checked -> overrides.disabledCheckedTrackColor ?: disabledContainerColor()
            else -> overrides.disabledUncheckedTrackColor
                ?: colorWithAlpha(Theme.colors.surfaceContainerHighest, 0.12f)
        }
        return ResolvedSwitchAppearance(
            labelColor = resolveStateValue(
                enabled,
                overrides.labelColor,
                overrides.disabledLabelColor,
                Theme.stateColors.primaryText.resolve(),
                disabledContentColor(),
            ),
            controlColor = trackColor,
            thumbColor = thumbColor,
            trackColor = trackColor,
            textStyle = overrides.textStyle ?: TextDefaults.bodyStyle(),
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(trackColor),
            minimumHeight = overrides.minimumHeight ?: minimumInteractiveHeight(),
        )
    }

    internal fun resolveRadioButton(
        enabled: Boolean,
        instance: RadioButtonOverrides,
    ): ResolvedToggleAppearance {
        val overrides = radioOverrides().merge(instance)
        val checked = resolveStateValue(
            enabled,
            overrides.checkedColor,
            overrides.disabledCheckedColor,
            Theme.colors.primary,
            disabledContentColor(),
        )
        return ResolvedToggleAppearance(
            labelColor = resolveStateValue(
                enabled,
                overrides.labelColor,
                overrides.disabledLabelColor,
                Theme.stateColors.primaryText.resolve(),
                disabledContentColor(),
            ),
            controlColor = checked,
            checkedColor = checked,
            uncheckedColor = resolveStateValue(
                enabled,
                overrides.uncheckedColor,
                overrides.disabledUncheckedColor,
                Theme.stateColors.control.resolve(),
                disabledContentColor(),
            ),
            textStyle = overrides.textStyle ?: TextDefaults.bodyStyle(),
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(checked),
            minimumHeight = overrides.minimumHeight ?: minimumInteractiveHeight(),
        )
    }

    internal fun resolveSlider(enabled: Boolean, instance: SliderOverrides): ResolvedSliderAppearance {
        val overrides = sliderOverrides().merge(instance)
        return ResolvedSliderAppearance(
            thumbColor = resolveStateValue(
                enabled,
                overrides.thumbColor,
                overrides.disabledThumbColor,
                Theme.colors.primary,
                disabledContentColor(),
            ),
            activeTrackColor = resolveStateValue(
                enabled,
                overrides.activeTrackColor,
                overrides.disabledActiveTrackColor,
                Theme.colors.primary,
                disabledContentColor(),
            ),
            inactiveTrackColor = resolveStateValue(
                enabled,
                overrides.inactiveTrackColor,
                overrides.disabledInactiveTrackColor,
                Theme.colors.secondaryContainer,
                disabledContainerColor(),
            ),
            minimumHeight = overrides.minimumHeight ?: minimumInteractiveHeight(),
        )
    }

    private fun checkboxOverrides() = UiLocals.current(LocalCheckboxOverrides)
    private fun switchOverrides() = UiLocals.current(LocalSwitchOverrides)
    private fun radioOverrides() = UiLocals.current(LocalRadioButtonOverrides)
    private fun sliderOverrides() = UiLocals.current(LocalSliderOverrides)
}

internal data class ResolvedToggleAppearance(
    val labelColor: Int,
    val controlColor: Int,
    val checkedColor: Int,
    val uncheckedColor: Int,
    val textStyle: UiTextStyle,
    val stateLayerColors: UiStateLayerColors,
    val minimumHeight: UiDp,
)

internal data class ResolvedSwitchAppearance(
    val labelColor: Int,
    val controlColor: Int,
    val thumbColor: Int,
    val trackColor: Int,
    val textStyle: UiTextStyle,
    val stateLayerColors: UiStateLayerColors,
    val minimumHeight: UiDp,
)

internal data class ResolvedSliderAppearance(
    val thumbColor: Int,
    val activeTrackColor: Int,
    val inactiveTrackColor: Int,
    val minimumHeight: UiDp,
)

private fun disabledContentColor(): Int = colorWithAlpha(Theme.colors.onSurface, 0.38f)

private fun disabledContainerColor(): Int = colorWithAlpha(Theme.colors.onSurface, 0.12f)
