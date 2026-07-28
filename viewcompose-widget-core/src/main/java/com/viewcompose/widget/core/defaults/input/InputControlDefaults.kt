package com.viewcompose.widget.core

/**
 * Checkbox、Switch、RadioButton 与 Slider 的默认输入控件 token。
 * Default input-control tokens for Checkbox, Switch, RadioButton, and Slider.
 *
 * 各控件拥有独立 local override，避免一个控件的定制意外影响其他输入控件。
 * Each control has its own local override to prevent one control customization from leaking into other input controls.
 */
object InputControlDefaults {
    fun labelStyle(): UiTextStyle = TextDefaults.bodyStyle()

    fun checkboxLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: Theme.stateColors.primaryText.resolve(enabled = false)
        }
    }

    fun checkboxControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun checkboxCheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun checkboxUncheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            Theme.stateColors.control.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
        }
    }

    fun switchLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: Theme.stateColors.primaryText.resolve(enabled = false)
        }
    }

    fun switchControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun switchThumbColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return when {
            !enabled -> override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
            checked -> override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
            else -> Theme.stateColors.control.resolve()
        }
    }

    fun switchTrackColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return when {
            !enabled -> override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
            checked -> {
                val base = override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
                (base and 0x00FFFFFF) or 0x61000000
            }
            else -> Theme.stateColors.control.resolve()
        }
    }

    fun radioButtonLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: Theme.stateColors.primaryText.resolve(enabled = false)
        }
    }

    fun radioButtonControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun radioButtonCheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun radioButtonUncheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            Theme.stateColors.control.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
        }
    }

    fun sliderControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun sliderThumbColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun sliderTrackColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            val base = override?.control ?: Theme.stateColors.controlActivated.resolve()
            (base and 0x00FFFFFF) or 0x61000000
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)
}
