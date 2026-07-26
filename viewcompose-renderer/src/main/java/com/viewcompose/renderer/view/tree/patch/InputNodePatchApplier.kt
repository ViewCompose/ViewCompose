package com.viewcompose.renderer.view.tree.patch

import android.content.res.ColorStateList
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Switch
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.tree.ContentViewBinder
import com.viewcompose.renderer.view.tree.InputViewBinder
import com.viewcompose.renderer.view.tree.SliderNodePatch
import com.viewcompose.renderer.view.tree.TextFieldNodePatch
import com.viewcompose.renderer.view.tree.ToggleNodePatch
import com.viewcompose.renderer.view.tree.ViewModifierApplier
import com.viewcompose.renderer.view.tree.ViewComposeEditText
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps

internal object InputNodePatchApplier {
    fun applyTextFieldPatch(
        view: ViewComposeEditText,
        patch: TextFieldNodePatch,
    ) {
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
                textSizeSp = next.textSizeSp,
                fontWeight = next.fontWeight,
                fontFamily = next.fontFamily,
                letterSpacingEm = next.letterSpacingEm,
                lineHeightSp = next.lineHeightSp,
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
            view.minimumHeight = next.minHeight
        }
        if (
            previous.paddingHorizontal != next.paddingHorizontal ||
            previous.paddingVertical != next.paddingVertical
        ) {
            view.setPadding(
                next.paddingHorizontal,
                next.paddingVertical,
                next.paddingHorizontal,
                next.paddingVertical,
            )
        }
    }

    fun applyTogglePatch(
        view: CompoundButton,
        patch: ToggleNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        view.setOnCheckedChangeListener(null)
        if (previous.text != next.text) {
            view.text = next.text
        }
        if (previous.enabled != next.enabled) {
            view.isEnabled = next.enabled
        }
        if (previous.checked != next.checked) {
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
        view.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != next.checked) {
                next.onCheckedChange?.invoke(isChecked)
            }
        }
        if (hasTextAppearanceChange(previous, next)) {
            ContentViewBinder.applyTextAppearance(
                view = view,
                textColor = next.textColor,
                textSizeSp = next.textSizeSp,
                fontWeight = next.fontWeight,
                fontFamily = next.fontFamily,
                letterSpacingEm = next.letterSpacingEm,
                lineHeightSp = next.lineHeightSp,
                includeFontPadding = next.includeFontPadding,
            )
        }
    }

    fun applySliderPatch(
        view: SeekBar,
        patch: SliderNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        val listener = view.getTag(R.id.viewcompose_seek_listener) as? SeekBar.OnSeekBarChangeListener
        if (listener != null) {
            view.setOnSeekBarChangeListener(null)
        }
        val resolvedValue = next.value.coerceIn(next.min, next.max)
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
        val nextListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val nextValue = next.min + progress
                if (fromUser && nextValue != resolvedValue) {
                    next.onValueChange?.invoke(nextValue)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        view.setOnSeekBarChangeListener(nextListener)
        view.setTag(R.id.viewcompose_seek_listener, nextListener)
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
