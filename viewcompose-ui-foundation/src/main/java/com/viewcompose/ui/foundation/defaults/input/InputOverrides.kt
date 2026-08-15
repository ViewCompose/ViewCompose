package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces low-frequency appearance values resolved by [TextField].
 *
 * An exact error slot takes precedence while `isError` is active. If that slot is absent, the
 * current enabled or disabled slot applies before the semantic error default. A `null` property
 * inherits the nearest scoped value or [TextFieldDefaults]. Appearance fields apply every
 * [TextFieldVariant]; use an instance patch when only one variant should differ. Behavior such as
 * keyboard options, transformations, focus callbacks, and editable state is intentionally absent.
 *
 * @property containerColor enabled container ARGB color
 * @property disabledContainerColor disabled container ARGB color
 * @property errorContainerColor error container ARGB color
 * @property textColor enabled editable-text ARGB color
 * @property disabledTextColor disabled editable-text ARGB color
 * @property errorTextColor error editable-text ARGB color
 * @property placeholderColor enabled placeholder ARGB color
 * @property disabledPlaceholderColor disabled placeholder ARGB color
 * @property errorPlaceholderColor error placeholder ARGB color
 * @property labelColor enabled label ARGB color
 * @property disabledLabelColor disabled label ARGB color
 * @property errorLabelColor error label ARGB color
 * @property supportingTextColor enabled supporting-text ARGB color
 * @property disabledSupportingTextColor disabled supporting-text ARGB color
 * @property errorSupportingTextColor error supporting-text ARGB color
 * @property borderColor enabled border ARGB color
 * @property disabledBorderColor disabled border ARGB color
 * @property errorBorderColor error border ARGB color
 * @property borderWidth border thickness in dp
 * @property cursorColor cursor ARGB color
 * @property shape editable container shape
 * @property textStyle editable text typography
 * @property labelTextStyle label typography
 * @property supportingTextStyle supporting-text typography
 * @property minimumHeight minimum single-line editable area height in dp
 * @property horizontalPadding editable content padding on each horizontal edge in dp
 * @property verticalPadding editable content padding on each vertical edge in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class TextFieldOverrides(
    val containerColor: Int? = null,
    val disabledContainerColor: Int? = null,
    val errorContainerColor: Int? = null,
    val textColor: Int? = null,
    val disabledTextColor: Int? = null,
    val errorTextColor: Int? = null,
    val placeholderColor: Int? = null,
    val disabledPlaceholderColor: Int? = null,
    val errorPlaceholderColor: Int? = null,
    val labelColor: Int? = null,
    val disabledLabelColor: Int? = null,
    val errorLabelColor: Int? = null,
    val supportingTextColor: Int? = null,
    val disabledSupportingTextColor: Int? = null,
    val errorSupportingTextColor: Int? = null,
    val borderColor: Int? = null,
    val disabledBorderColor: Int? = null,
    val errorBorderColor: Int? = null,
    val borderWidth: UiDp? = null,
    val cursorColor: Int? = null,
    val shape: UiShape? = null,
    val textStyle: UiTextStyle? = null,
    val labelTextStyle: UiTextStyle? = null,
    val supportingTextStyle: UiTextStyle? = null,
    val minimumHeight: UiDp? = null,
    val horizontalPadding: UiDp? = null,
    val verticalPadding: UiDp? = null,
) {
    init {
        borderWidth.requireNonNegative("TextFieldOverrides.borderWidth")
        minimumHeight.requireNonNegative("TextFieldOverrides.minimumHeight")
        horizontalPadding.requireNonNegative("TextFieldOverrides.horizontalPadding")
        verticalPadding.requireNonNegative("TextFieldOverrides.verticalPadding")
    }

    /** Shared TextField override values. */
    companion object {
        /** Shared empty patch used by component defaults without allocating during rendering. */
        val None: TextFieldOverrides = TextFieldOverrides()
    }
}

internal fun TextFieldOverrides.merge(nearest: TextFieldOverrides): TextFieldOverrides {
    if (nearest === TextFieldOverrides.None) return this
    if (this === TextFieldOverrides.None) return nearest
    return TextFieldOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        disabledContainerColor = nearest.disabledContainerColor ?: disabledContainerColor,
        errorContainerColor = nearest.errorContainerColor ?: errorContainerColor,
        textColor = nearest.textColor ?: textColor,
        disabledTextColor = nearest.disabledTextColor ?: disabledTextColor,
        errorTextColor = nearest.errorTextColor ?: errorTextColor,
        placeholderColor = nearest.placeholderColor ?: placeholderColor,
        disabledPlaceholderColor = nearest.disabledPlaceholderColor ?: disabledPlaceholderColor,
        errorPlaceholderColor = nearest.errorPlaceholderColor ?: errorPlaceholderColor,
        labelColor = nearest.labelColor ?: labelColor,
        disabledLabelColor = nearest.disabledLabelColor ?: disabledLabelColor,
        errorLabelColor = nearest.errorLabelColor ?: errorLabelColor,
        supportingTextColor = nearest.supportingTextColor ?: supportingTextColor,
        disabledSupportingTextColor = nearest.disabledSupportingTextColor ?: disabledSupportingTextColor,
        errorSupportingTextColor = nearest.errorSupportingTextColor ?: errorSupportingTextColor,
        borderColor = nearest.borderColor ?: borderColor,
        disabledBorderColor = nearest.disabledBorderColor ?: disabledBorderColor,
        errorBorderColor = nearest.errorBorderColor ?: errorBorderColor,
        borderWidth = nearest.borderWidth ?: borderWidth,
        cursorColor = nearest.cursorColor ?: cursorColor,
        shape = nearest.shape ?: shape,
        textStyle = nearest.textStyle ?: textStyle,
        labelTextStyle = nearest.labelTextStyle ?: labelTextStyle,
        supportingTextStyle = nearest.supportingTextStyle ?: supportingTextStyle,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
        horizontalPadding = nearest.horizontalPadding ?: horizontalPadding,
        verticalPadding = nearest.verticalPadding ?: verticalPadding,
    )
}

internal val LocalTextFieldOverrides = uiLocalOf(
    debugName = "TextFieldOverrides",
    debugValueFormatter = TextFieldOverrides::toString,
) { TextFieldOverrides.None }

/**
 * Merges sparse [overrides] into TextField defaults for [content].
 *
 * Nested providers merge field by field, instance overrides retain the highest precedence, and
 * the previous scope is restored after [content] returns.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant TextField components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideTextFieldOverrides(
    overrides: TextFieldOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalTextFieldOverrides,
        value = UiLocals.current(LocalTextFieldOverrides).merge(overrides),
        content = content,
    )
}

/**
 * Selectively replaces Checkbox appearance without affecting other input-control families.
 *
 * @property labelColor enabled label ARGB color
 * @property disabledLabelColor disabled label ARGB color
 * @property checkedColor enabled checked-indicator ARGB color
 * @property uncheckedColor enabled unchecked-indicator ARGB color
 * @property disabledCheckedColor disabled checked-indicator ARGB color
 * @property disabledUncheckedColor disabled unchecked-indicator ARGB color
 * @property textStyle label typography
 * @property rippleColor interaction-feedback ARGB color
 * @property minimumHeight minimum effective control height in dp
 * @throws IllegalArgumentException when [minimumHeight] is negative
 */
data class CheckboxOverrides(
    val labelColor: Int? = null,
    val disabledLabelColor: Int? = null,
    val checkedColor: Int? = null,
    val uncheckedColor: Int? = null,
    val disabledCheckedColor: Int? = null,
    val disabledUncheckedColor: Int? = null,
    val textStyle: UiTextStyle? = null,
    val rippleColor: Int? = null,
    val minimumHeight: UiDp? = null,
) {
    init {
        minimumHeight.requireNonNegative("CheckboxOverrides.minimumHeight")
    }

    /** Shared Checkbox override values. */
    companion object {
        /** Shared empty Checkbox appearance patch. */
        val None: CheckboxOverrides = CheckboxOverrides()
    }
}

internal fun CheckboxOverrides.merge(nearest: CheckboxOverrides): CheckboxOverrides {
    if (nearest === CheckboxOverrides.None) return this
    if (this === CheckboxOverrides.None) return nearest
    return CheckboxOverrides(
        labelColor = nearest.labelColor ?: labelColor,
        disabledLabelColor = nearest.disabledLabelColor ?: disabledLabelColor,
        checkedColor = nearest.checkedColor ?: checkedColor,
        uncheckedColor = nearest.uncheckedColor ?: uncheckedColor,
        disabledCheckedColor = nearest.disabledCheckedColor ?: disabledCheckedColor,
        disabledUncheckedColor = nearest.disabledUncheckedColor ?: disabledUncheckedColor,
        textStyle = nearest.textStyle ?: textStyle,
        rippleColor = nearest.rippleColor ?: rippleColor,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
    )
}

internal val LocalCheckboxOverrides = uiLocalOf(
    debugName = "CheckboxOverrides",
    debugValueFormatter = CheckboxOverrides::toString,
) { CheckboxOverrides.None }

/**
 * Merges sparse [overrides] into Checkbox defaults for [content].
 *
 * Nested scopes merge field by field, instance patches retain the highest precedence, and the
 * previous scope is restored after synchronous construction returns or throws.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant Checkbox components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideCheckboxOverrides(
    overrides: CheckboxOverrides,
    content: UiTreeBuilder.() -> Unit,
) = provideMergedOverrides(LocalCheckboxOverrides, overrides, CheckboxOverrides::merge, content)

/**
 * Selectively replaces Switch appearance without collapsing thumb and track state roles.
 *
 * @property labelColor enabled label ARGB color
 * @property disabledLabelColor disabled label ARGB color
 * @property checkedThumbColor enabled checked thumb ARGB color
 * @property uncheckedThumbColor enabled unchecked thumb ARGB color
 * @property disabledCheckedThumbColor disabled checked thumb ARGB color
 * @property disabledUncheckedThumbColor disabled unchecked thumb ARGB color
 * @property checkedTrackColor enabled checked track ARGB color
 * @property uncheckedTrackColor enabled unchecked track ARGB color
 * @property disabledCheckedTrackColor disabled checked track ARGB color
 * @property disabledUncheckedTrackColor disabled unchecked track ARGB color
 * @property textStyle label typography
 * @property rippleColor interaction-feedback ARGB color
 * @property minimumHeight minimum effective control height in dp
 * @throws IllegalArgumentException when [minimumHeight] is negative
 */
data class SwitchOverrides(
    val labelColor: Int? = null,
    val disabledLabelColor: Int? = null,
    val checkedThumbColor: Int? = null,
    val uncheckedThumbColor: Int? = null,
    val disabledCheckedThumbColor: Int? = null,
    val disabledUncheckedThumbColor: Int? = null,
    val checkedTrackColor: Int? = null,
    val uncheckedTrackColor: Int? = null,
    val disabledCheckedTrackColor: Int? = null,
    val disabledUncheckedTrackColor: Int? = null,
    val textStyle: UiTextStyle? = null,
    val rippleColor: Int? = null,
    val minimumHeight: UiDp? = null,
) {
    init {
        minimumHeight.requireNonNegative("SwitchOverrides.minimumHeight")
    }

    /** Shared Switch override values. */
    companion object {
        /** Shared empty Switch appearance patch. */
        val None: SwitchOverrides = SwitchOverrides()
    }
}

internal fun SwitchOverrides.merge(nearest: SwitchOverrides): SwitchOverrides {
    if (nearest === SwitchOverrides.None) return this
    if (this === SwitchOverrides.None) return nearest
    return SwitchOverrides(
        labelColor = nearest.labelColor ?: labelColor,
        disabledLabelColor = nearest.disabledLabelColor ?: disabledLabelColor,
        checkedThumbColor = nearest.checkedThumbColor ?: checkedThumbColor,
        uncheckedThumbColor = nearest.uncheckedThumbColor ?: uncheckedThumbColor,
        disabledCheckedThumbColor = nearest.disabledCheckedThumbColor ?: disabledCheckedThumbColor,
        disabledUncheckedThumbColor = nearest.disabledUncheckedThumbColor ?: disabledUncheckedThumbColor,
        checkedTrackColor = nearest.checkedTrackColor ?: checkedTrackColor,
        uncheckedTrackColor = nearest.uncheckedTrackColor ?: uncheckedTrackColor,
        disabledCheckedTrackColor = nearest.disabledCheckedTrackColor ?: disabledCheckedTrackColor,
        disabledUncheckedTrackColor = nearest.disabledUncheckedTrackColor ?: disabledUncheckedTrackColor,
        textStyle = nearest.textStyle ?: textStyle,
        rippleColor = nearest.rippleColor ?: rippleColor,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
    )
}

internal val LocalSwitchOverrides = uiLocalOf(
    debugName = "SwitchOverrides",
    debugValueFormatter = SwitchOverrides::toString,
) { SwitchOverrides.None }

/**
 * Merges sparse [overrides] into Switch defaults for [content].
 *
 * Nested scopes merge field by field without collapsing thumb and track roles. Instance patches
 * retain the highest precedence and the previous scope is restored after [content] returns.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant Switch components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideSwitchOverrides(
    overrides: SwitchOverrides,
    content: UiTreeBuilder.() -> Unit,
) = provideMergedOverrides(LocalSwitchOverrides, overrides, SwitchOverrides::merge, content)

/**
 * Selectively replaces RadioButton appearance without affecting Checkbox or Switch.
 *
 * @property labelColor enabled label ARGB color
 * @property disabledLabelColor disabled label ARGB color
 * @property checkedColor enabled selected-indicator ARGB color
 * @property uncheckedColor enabled unselected-indicator ARGB color
 * @property disabledCheckedColor disabled selected-indicator ARGB color
 * @property disabledUncheckedColor disabled unselected-indicator ARGB color
 * @property textStyle label typography
 * @property rippleColor interaction-feedback ARGB color
 * @property minimumHeight minimum effective control height in dp
 * @throws IllegalArgumentException when [minimumHeight] is negative
 */
data class RadioButtonOverrides(
    val labelColor: Int? = null,
    val disabledLabelColor: Int? = null,
    val checkedColor: Int? = null,
    val uncheckedColor: Int? = null,
    val disabledCheckedColor: Int? = null,
    val disabledUncheckedColor: Int? = null,
    val textStyle: UiTextStyle? = null,
    val rippleColor: Int? = null,
    val minimumHeight: UiDp? = null,
) {
    init {
        minimumHeight.requireNonNegative("RadioButtonOverrides.minimumHeight")
    }

    /** Shared RadioButton override values. */
    companion object {
        /** Shared empty RadioButton appearance patch. */
        val None: RadioButtonOverrides = RadioButtonOverrides()
    }
}

internal fun RadioButtonOverrides.merge(nearest: RadioButtonOverrides): RadioButtonOverrides {
    if (nearest === RadioButtonOverrides.None) return this
    if (this === RadioButtonOverrides.None) return nearest
    return RadioButtonOverrides(
        labelColor = nearest.labelColor ?: labelColor,
        disabledLabelColor = nearest.disabledLabelColor ?: disabledLabelColor,
        checkedColor = nearest.checkedColor ?: checkedColor,
        uncheckedColor = nearest.uncheckedColor ?: uncheckedColor,
        disabledCheckedColor = nearest.disabledCheckedColor ?: disabledCheckedColor,
        disabledUncheckedColor = nearest.disabledUncheckedColor ?: disabledUncheckedColor,
        textStyle = nearest.textStyle ?: textStyle,
        rippleColor = nearest.rippleColor ?: rippleColor,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
    )
}

internal val LocalRadioButtonOverrides = uiLocalOf(
    debugName = "RadioButtonOverrides",
    debugValueFormatter = RadioButtonOverrides::toString,
) { RadioButtonOverrides.None }

/**
 * Merges sparse [overrides] into RadioButton defaults for [content].
 *
 * Nested scopes merge field by field, instance patches retain the highest precedence, and the
 * previous scope is restored after [content] returns.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant RadioButton components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideRadioButtonOverrides(
    overrides: RadioButtonOverrides,
    content: UiTreeBuilder.() -> Unit,
) = provideMergedOverrides(LocalRadioButtonOverrides, overrides, RadioButtonOverrides::merge, content)

/**
 * Selectively replaces Slider appearance while preserving separate thumb and track roles.
 *
 * @property thumbColor enabled thumb ARGB color
 * @property activeTrackColor enabled active-track ARGB color
 * @property inactiveTrackColor enabled inactive-track ARGB color
 * @property disabledThumbColor disabled thumb ARGB color
 * @property disabledActiveTrackColor disabled active-track ARGB color
 * @property disabledInactiveTrackColor disabled inactive-track ARGB color
 * @property minimumHeight minimum effective control height in dp
 * @throws IllegalArgumentException when [minimumHeight] is negative
 */
data class SliderOverrides(
    val thumbColor: Int? = null,
    val activeTrackColor: Int? = null,
    val inactiveTrackColor: Int? = null,
    val disabledThumbColor: Int? = null,
    val disabledActiveTrackColor: Int? = null,
    val disabledInactiveTrackColor: Int? = null,
    val minimumHeight: UiDp? = null,
) {
    init {
        minimumHeight.requireNonNegative("SliderOverrides.minimumHeight")
    }

    /** Shared Slider override values. */
    companion object {
        /** Shared empty Slider appearance patch. */
        val None: SliderOverrides = SliderOverrides()
    }
}

internal fun SliderOverrides.merge(nearest: SliderOverrides): SliderOverrides {
    if (nearest === SliderOverrides.None) return this
    if (this === SliderOverrides.None) return nearest
    return SliderOverrides(
        thumbColor = nearest.thumbColor ?: thumbColor,
        activeTrackColor = nearest.activeTrackColor ?: activeTrackColor,
        inactiveTrackColor = nearest.inactiveTrackColor ?: inactiveTrackColor,
        disabledThumbColor = nearest.disabledThumbColor ?: disabledThumbColor,
        disabledActiveTrackColor = nearest.disabledActiveTrackColor ?: disabledActiveTrackColor,
        disabledInactiveTrackColor = nearest.disabledInactiveTrackColor ?: disabledInactiveTrackColor,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
    )
}

internal val LocalSliderOverrides = uiLocalOf(
    debugName = "SliderOverrides",
    debugValueFormatter = SliderOverrides::toString,
) { SliderOverrides.None }

/**
 * Merges sparse [overrides] into Slider defaults for [content].
 *
 * Nested scopes merge field by field without collapsing thumb, active-track, and inactive-track
 * roles. Instance patches retain the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant Slider components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideSliderOverrides(
    overrides: SliderOverrides,
    content: UiTreeBuilder.() -> Unit,
) = provideMergedOverrides(LocalSliderOverrides, overrides, SliderOverrides::merge, content)

private fun <T : Any> UiTreeBuilder.provideMergedOverrides(
    local: UiLocal<T>,
    overrides: T,
    merge: T.(T) -> T,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = local,
        value = UiLocals.current(local).merge(overrides),
        content = content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
