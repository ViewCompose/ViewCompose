package com.viewcompose.renderer.view.tree.patch

import android.content.res.ColorStateList
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Switch
import com.viewcompose.renderer.view.tree.ContentViewBinder
import com.viewcompose.renderer.view.tree.InputViewBinder
import com.viewcompose.renderer.view.tree.SliderNodePatch
import com.viewcompose.renderer.view.tree.TextFieldNodePatch
import com.viewcompose.renderer.view.tree.ToggleNodePatch
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.toPx
import com.viewcompose.renderer.view.tree.ViewModifierApplier
import com.viewcompose.renderer.view.tree.ViewComposeEditText
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.toPx

/**
 * Targeted patch applier for input nodes.
 * Fine-grained patch applier for input nodes.
 */
internal object InputNodePatchApplier {
    /**
     * Updates a text input View's keyboard, text controller, styling, and autofill configuration.
     * Updates keyboard, text controller, styling, and autofill configuration for a text input View.
     */
    fun applyTextFieldPatch(
        view: ViewComposeEditText,
        patch: TextFieldNodePatch,
    ) {
        val environment = view.requireUiEnvironment()
        val previous = patch.previous
        val next = patch.next
        val nextSpec = InputViewBinder.readTextFieldSpec(next)
        if (previous.placeholder != next.placeholder) {
            view.hint = next.placeholder
        }
        if (previous.enabled != next.enabled) {
            view.isEnabled = next.enabled
        }
        if (previous.singleLine != next.singleLine) {
            view.isSingleLine = next.singleLine
        }
        if (
            previous.singleLine != next.singleLine ||
            previous.minLines != next.minLines
        ) {
            view.minLines = if (next.singleLine) 1 else next.minLines
        }
        if (
            previous.singleLine != next.singleLine ||
            previous.maxLines != next.maxLines
        ) {
            view.maxLines = if (next.singleLine) 1 else next.maxLines
        }
        if (
            previous.keyboardOptions != next.keyboardOptions ||
            previous.singleLine != next.singleLine
        ) {
            // textController owns input type and IME action so native EditText state cannot diverge from TextFieldState.
            // Input type and IME action are managed by textController to keep native EditText state aligned with TextFieldState.
            view.textController.updateEditorConfiguration(
                inputType = nextSpec.inputType,
                editorOptions = nextSpec.editorOptions,
            )
        }
        if (previous.hintColor != next.hintColor) {
            view.setHintTextColor(next.hintColor)
        }
        if (previous.cursorColor != next.cursorColor && next.cursorColor != 0) {
            InputViewBinder.applyCursorColor(view, next.cursorColor)
        }
        if (previous.readOnly != next.readOnly) {
            InputViewBinder.applyReadOnly(view, next.readOnly)
        }
        if (previous.autofillHints != next.autofillHints) {
            InputViewBinder.applyAutofillHints(view, next.autofillHints)
        }
        view.textController.bind(nextSpec)
        if (hasTextAppearanceChange(previous, next)) {
            ContentViewBinder.applyTextAppearance(
                view = view,
                textColor = next.textColor,
                textSizePx = environment.toPx(next.textSizeSp),
                fontWeight = next.fontWeight,
                fontFamily = next.fontFamily,
                letterSpacingEm = next.letterSpacingEm,
                lineHeightPx = next.lineHeightSp?.let(environment.density::roundToPx),
                includeFontPadding = next.includeFontPadding,
            )
        }
        if (
            previous.backgroundColor != next.backgroundColor ||
            previous.borderWidth != next.borderWidth ||
            previous.borderColor != next.borderColor ||
            previous.shape != next.shape
        ) {
            ViewModifierApplier.applyStylePatch(
                view = view,
                backgroundColor = next.backgroundColor,
                borderWidth = next.borderWidth,
                borderColor = next.borderColor,
                shape = next.shape,
                rippleColor = 0,
                clickable = false,
            )
        }
        if (previous.minHeight != next.minHeight) {
            view.minimumHeight = environment.roundToPx(next.minHeight)
        }
        if (
            previous.paddingHorizontal != next.paddingHorizontal ||
            previous.paddingVertical != next.paddingVertical
        ) {
            view.setPadding(
                environment.roundToPx(next.paddingHorizontal),
                environment.roundToPx(next.paddingVertical),
                environment.roundToPx(next.paddingHorizontal),
                environment.roundToPx(next.paddingVertical),
            )
        }
    }

    /**
     * Updates checkbox, switch, or radio selection, listeners, colors, and text style.
     * Updates checked state, listener, colors, and text style for checkbox/switch/radio.
     */
    fun applyTogglePatch(
        view: CompoundButton,
        patch: ToggleNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        // Rebind the listener before assigning isChecked so programmatic synchronization cannot invoke an old callback.
        // Rebind the listener before setting isChecked so programmatic state sync cannot invoke a stale callback.
        InputViewBinder.updateToggleListener(
            view = view,
            expectedChecked = next.checked,
            onCheckedChange = next.onCheckedChange,
        )
        if (previous.text != next.text) {
            view.text = next.text
        }
        if (previous.enabled != next.enabled) {
            view.isEnabled = next.enabled
        }
        if (previous.checked != next.checked && view.isChecked != next.checked) {
            // A native CompoundButton commits user input before the controlled callback returns.
            // Avoid restarting its in-flight thumb animation when caller state accepts that value.
            view.isChecked = next.checked
        }
        if (previous.controlColor != next.controlColor ||
            previous.checkedColor != next.checkedColor ||
            previous.uncheckedColor != next.uncheckedColor
        ) {
            if (view is Switch) {
                view.buttonTintList = ColorStateList.valueOf(next.controlColor)
                view.thumbTintList = ColorStateList.valueOf(next.thumbColor ?: next.controlColor)
                view.trackTintList = ColorStateList.valueOf(next.trackColor ?: next.controlColor)
            } else if (next.checkedColor != null || next.uncheckedColor != null) {
                val checked = next.checkedColor ?: next.controlColor
                val unchecked = next.uncheckedColor ?: next.controlColor
                view.buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked),
                    ),
                    intArrayOf(checked, unchecked),
                )
            } else {
                view.buttonTintList = ColorStateList.valueOf(next.controlColor)
            }
        } else if (view is Switch) {
            if (previous.thumbColor != next.thumbColor) {
                view.thumbTintList = ColorStateList.valueOf(next.thumbColor ?: next.controlColor)
            }
            if (previous.trackColor != next.trackColor) {
                view.trackTintList = ColorStateList.valueOf(next.trackColor ?: next.controlColor)
            }
        }
        if (hasTextAppearanceChange(previous, next)) {
            val environment = view.requireUiEnvironment()
            ContentViewBinder.applyTextAppearance(
                view = view,
                textColor = next.textColor,
                textSizePx = environment.toPx(next.textSizeSp),
                fontWeight = next.fontWeight,
                fontFamily = next.fontFamily,
                letterSpacingEm = next.letterSpacingEm,
                lineHeightPx = next.lineHeightSp?.let(environment.density::roundToPx),
                includeFontPadding = next.includeFontPadding,
            )
        }
    }

    /**
     * Updates SeekBar range, progress, listener, and tint.
     * Updates SeekBar range, progress, listener, and tint.
     */
    fun applySliderPatch(
        view: SeekBar,
        patch: SliderNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        val resolvedValue = next.value.coerceIn(next.min, next.max)
        // Represent progress as value minus min so the DSL can expose arbitrary integer ranges.
        // progress is represented as value - min so the DSL can expose any integer range.
        InputViewBinder.updateSliderListener(
            view = view,
            min = next.min,
            expectedValue = resolvedValue,
            onValueChange = next.onValueChange,
        )
        if (previous.min != next.min || previous.max != next.max) {
            view.max = (next.max - next.min).coerceAtLeast(0)
        }
        if (previous.value != next.value || previous.min != next.min || previous.max != next.max) {
            view.progress = resolvedValue - next.min
        }
        if (previous.enabled != next.enabled) {
            view.isEnabled = next.enabled
        }
        if (previous.thumbColor != next.thumbColor) {
            view.thumbTintList = ColorStateList.valueOf(next.thumbColor)
        }
        if (previous.trackColor != next.trackColor) {
            view.progressTintList = ColorStateList.valueOf(next.trackColor)
        }
        if (previous.inactiveTrackColor != next.inactiveTrackColor) {
            view.progressBackgroundTintList = ColorStateList.valueOf(next.inactiveTrackColor)
        }
    }

    private fun hasTextAppearanceChange(
        previous: TextFieldNodeProps,
        next: TextFieldNodeProps,
    ): Boolean {
        return previous.textColor != next.textColor ||
            previous.textSizeSp != next.textSizeSp ||
            previous.fontWeight != next.fontWeight ||
            previous.fontFamily != next.fontFamily ||
            previous.letterSpacingEm != next.letterSpacingEm ||
            previous.lineHeightSp != next.lineHeightSp ||
            previous.includeFontPadding != next.includeFontPadding
    }

    private fun hasTextAppearanceChange(
        previous: ToggleNodeProps,
        next: ToggleNodeProps,
    ): Boolean {
        return previous.textColor != next.textColor ||
            previous.textSizeSp != next.textSizeSp ||
            previous.fontWeight != next.fontWeight ||
            previous.fontFamily != next.fontFamily ||
            previous.letterSpacingEm != next.letterSpacingEm ||
            previous.lineHeightSp != next.lineHeightSp ||
            previous.includeFontPadding != next.includeFontPadding
    }
}
