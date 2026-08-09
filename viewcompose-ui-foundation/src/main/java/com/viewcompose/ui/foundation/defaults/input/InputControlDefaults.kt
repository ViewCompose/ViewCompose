package com.viewcompose.ui.foundation

/**
 * Resolves Checkbox, Switch, RadioButton, and Slider colors from theme and scoped overrides.
 *
 * Each control family has an independent local override, preventing one customization from leaking
 * into other input controls.
 */
object InputControlDefaults {
    /**
     * Returns the theme's minimum effective height for native compact input controls.
     *
     * A zero value preserves the native control's intrinsic measurement. A positive value is
     * applied before the caller's [com.viewcompose.ui.modifier.Modifier], so an explicit exact
     * application height or tighter parent constraint remains authoritative.
     *
     * @return immutable density-independent minimum height from the current theme snapshot
     */
    fun minimumInteractiveHeight(): com.viewcompose.ui.unit.UiDp {
        return Theme.controls.minimumInteractiveHeight
    }

    /**
     * Returns the visible geometry snapshot for a design-owned Switch composite.
     *
     * Native Android Switch rendering may retain platform geometry. Design-system recipes that
     * own their track and thumb should consume this snapshot while resolving the effective target
     * independently through [minimumInteractiveHeight].
     *
     * @sample com.viewcompose.ui.foundation.samples.switchSizingTokenSample
     * @return immutable Switch sizing from the current theme snapshot
     */
    fun switchSizing(): UiSwitchSizing = Theme.controls.switch

    /** Returns the shared body typography used by labeled controls. */
    fun labelStyle(): UiTextStyle = TextDefaults.bodyStyle()

    /** Resolves Checkbox label color for [enabled] state. */
    fun checkboxLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the semantic Checkbox indicator color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun checkboxControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the checked Checkbox indicator color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun checkboxCheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves unchecked Checkbox color for [enabled] state. */
    fun checkboxUncheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalCheckboxColors)
        return if (enabled) {
            Theme.stateColors.control.resolve()
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves Switch label color for [enabled] state. */
    fun switchLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the semantic Switch control color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun switchControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves Switch thumb color for [checked] and [enabled] state. */
    fun switchThumbColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return when {
            !enabled && checked -> override?.controlDisabled ?: Theme.colors.surface
            !enabled -> override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
            checked -> override?.control ?: Theme.colors.onPrimary
            else -> Theme.colors.outline
        }
    }

    /** Resolves the semantic Switch track color for [checked] and [enabled] state. */
    fun switchTrackColor(checked: Boolean = true, enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSwitchColors)
        return when {
            !enabled && checked -> override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.12f)
            !enabled -> override?.controlDisabled ?: colorWithAlpha(Theme.colors.surfaceContainerHighest, 0.12f)
            checked -> override?.control ?: Theme.colors.primary
            else -> Theme.colors.surfaceContainerHighest
        }
    }

    /** Resolves RadioButton label color for [enabled] state. */
    fun radioButtonLabelColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.label ?: Theme.stateColors.primaryText.resolve()
        } else {
            override?.labelDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the semantic RadioButton indicator color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun radioButtonControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the checked RadioButton indicator color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun radioButtonCheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves unchecked RadioButton color for [enabled] state. */
    fun radioButtonUncheckedColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalRadioButtonColors)
        return if (enabled) {
            Theme.stateColors.control.resolve()
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the semantic Slider control color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun sliderControlColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the Slider thumb color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled control color
     */
    fun sliderThumbColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the active Slider track color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current primary color when enabled, otherwise the disabled active-track color
     */
    fun sliderTrackColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.colors.primary
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /**
     * Resolves the inactive Slider track color for [enabled] state.
     *
     * @param enabled whether enabled or disabled component roles are selected
     * @return the current secondary-container color when enabled, otherwise the disabled
     * inactive-track color
     */
    fun sliderInactiveTrackColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSliderColors)
        return if (enabled) {
            override?.control ?: Theme.colors.secondaryContainer
        } else {
            override?.controlDisabled ?: colorWithAlpha(Theme.colors.onSurface, 0.12f)
        }
    }

    /** Returns the current pressed-state control highlight. */
    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)
}
