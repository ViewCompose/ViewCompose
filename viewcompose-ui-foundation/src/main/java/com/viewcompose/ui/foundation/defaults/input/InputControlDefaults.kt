package com.viewcompose.ui.foundation

/**
 * Resolves Checkbox, Switch, RadioButton, and Slider colors from theme and scoped overrides.
 *
 * Each control family has an independent local override, preventing one customization from leaking
 * into other input controls.
 */
object InputControlDefaults {
    /** Returns the shared body typography used by labeled controls. */
    fun labelStyle(): UiTextStyle = TextDefaults.bodyStyle()

    /** Resolves Checkbox label color for [enabled] state. */
    fun checkboxLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: Theme.stateColors.primaryText.resolve(enabled = false)
        }
    }

    /** Resolves the primary Checkbox control color for [enabled] state. */
    fun checkboxControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves checked Checkbox color for [enabled] state. */
    fun checkboxCheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves unchecked Checkbox color for [enabled] state. */
    fun checkboxUncheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            Theme.stateColors.control.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
        }
    }

    /** Resolves Switch label color for [enabled] state. */
    fun switchLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: Theme.stateColors.primaryText.resolve(enabled = false)
        }
    }

    /** Resolves the primary Switch control color for [enabled] state. */
    fun switchControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves Switch thumb color for [checked] and [enabled] state. */
    fun switchThumbColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return when {
            !enabled -> override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
            checked -> override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
            else -> Theme.stateColors.control.resolve()
        }
    }

    /** Resolves Switch track color, applying a translucent activated tone while checked. */
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

    /** Resolves RadioButton label color for [enabled] state. */
    fun radioButtonLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: Theme.stateColors.primaryText.resolve(enabled = false)
        }
    }

    /** Resolves the primary RadioButton control color for [enabled] state. */
    fun radioButtonControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves checked RadioButton color for [enabled] state. */
    fun radioButtonCheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve(checked = true)
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves unchecked RadioButton color for [enabled] state. */
    fun radioButtonUncheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            Theme.stateColors.control.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.control.resolve(enabled = false)
        }
    }

    /** Resolves the primary Slider control color for [enabled] state. */
    fun sliderControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves Slider thumb color for [enabled] state. */
    fun sliderThumbColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.stateColors.controlActivated.resolve()
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Resolves Slider track color, applying a translucent activated tone while enabled. */
    fun sliderTrackColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            val base = override?.control ?: Theme.stateColors.controlActivated.resolve()
            (base and 0x00FFFFFF) or 0x61000000
        } else {
            override?.controlDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Returns the current pressed-state control highlight. */
    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)
}
