package com.viewcompose.renderer.view.tree

import android.content.res.ColorStateList
import android.text.InputType
import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import com.viewcompose.renderer.R
import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.TextFieldCapitalization
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.TextFieldType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps

internal object InputViewBinder {
    data class TextFieldSpec(
        val state: TextFieldState,
        val value: TextFieldValue,
        val placeholder: String,
        val enabled: Boolean,
        val singleLine: Boolean,
        val minLines: Int,
        val maxLines: Int,
        val inputType: Int,
        val editorOptions: Int,
        val hintColor: Int,
        val readOnly: Boolean,
        val inputTransformation: InputTransformation?,
        val onKeyboardAction: ((TextFieldImeAction) -> Boolean)?,
        val imeAction: TextFieldImeAction,
        val onFocusChange: ((Boolean) -> Unit)?,
        val autofillHints: Set<TextFieldAutofillHint>,
        val cursorColor: Int = 0,
        val receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    )

    data class ToggleSpec(
        val text: CharSequence?,
        val enabled: Boolean,
        val checked: Boolean,
        val controlColor: Int,
        val thumbColor: Int? = null,
        val trackColor: Int? = null,
        val checkedColor: Int? = null,
        val uncheckedColor: Int? = null,
        val onCheckedChange: ((Boolean) -> Unit)?,
    )

    data class SliderSpec(
        val min: Int,
        val max: Int,
        val value: Int,
        val enabled: Boolean,
        val thumbColor: Int,
        val trackColor: Int,
        val onValueChange: ((Int) -> Unit)?,
    )

    fun bindTextField(
        view: ViewComposeEditText,
        spec: TextFieldSpec,
    ) {
        view.hint = spec.placeholder
        view.isEnabled = spec.enabled
        view.isSingleLine = spec.singleLine
        view.minLines = if (spec.singleLine) 1 else spec.minLines
        view.maxLines = if (spec.singleLine) 1 else spec.maxLines
        view.textController.updateEditorConfiguration(
            inputType = spec.inputType,
            editorOptions = spec.editorOptions,
        )
        view.setHintTextColor(spec.hintColor)
        applyCursorColor(view, spec.cursorColor)
        applyReadOnly(view, spec.readOnly)
        applyAutofillHints(view, spec.autofillHints)
        view.textController.bind(spec)
    }

    fun bindCheckbox(
        view: CheckBox,
        spec: ToggleSpec,
    ) {
        bindCompoundButton(
            view = view,
            spec = spec,
        )
    }

    fun bindSwitch(
        view: Switch,
        spec: ToggleSpec,
    ) {
        bindCompoundButton(
            view = view,
            spec = spec,
        )
    }

    fun bindRadioButton(
        view: RadioButton,
        spec: ToggleSpec,
    ) {
        bindCompoundButton(
            view = view,
            spec = spec,
        )
    }

    fun bindSlider(
        view: SeekBar,
        spec: SliderSpec,
    ) {
        val resolvedValue = spec.value.coerceIn(spec.min, spec.max)
        updateSliderListener(
            view = view,
            min = spec.min,
            expectedValue = resolvedValue,
            onValueChange = spec.onValueChange,
        )
        view.max = (spec.max - spec.min).coerceAtLeast(0)
        view.progress = resolvedValue - spec.min
        view.isEnabled = spec.enabled
        view.progressTintList = ColorStateList.valueOf(spec.trackColor)
        view.thumbTintList = ColorStateList.valueOf(spec.thumbColor)
    }

    private fun bindCompoundButton(
        view: CompoundButton,
        spec: ToggleSpec,
    ) {
        updateToggleListener(
            view = view,
            expectedChecked = spec.checked,
            onCheckedChange = spec.onCheckedChange,
        )
        view.text = spec.text
        view.isEnabled = spec.enabled
        view.isChecked = spec.checked
        if (view is Switch) {
            view.buttonTintList = ColorStateList.valueOf(spec.controlColor)
            view.thumbTintList = ColorStateList.valueOf(spec.thumbColor ?: spec.controlColor)
            view.trackTintList = ColorStateList.valueOf(spec.trackColor ?: spec.controlColor)
        } else if (spec.checkedColor != null || spec.uncheckedColor != null) {
            val checked = spec.checkedColor ?: spec.controlColor
            val unchecked = spec.uncheckedColor ?: spec.controlColor
            view.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked),
                ),
                intArrayOf(checked, unchecked),
            )
        } else {
            view.buttonTintList = ColorStateList.valueOf(spec.controlColor)
        }
    }

    internal fun updateToggleListener(
        view: CompoundButton,
        expectedChecked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
    ) {
        val binding = (view.getTag(R.id.viewcompose_toggle_listener) as? ToggleListenerBinding)
            ?: ToggleListenerBinding().also {
                view.setTag(R.id.viewcompose_toggle_listener, it)
                view.setOnCheckedChangeListener(it)
            }
        binding.expectedChecked = expectedChecked
        binding.onCheckedChange = onCheckedChange
    }

    internal fun updateSliderListener(
        view: SeekBar,
        min: Int,
        expectedValue: Int,
        onValueChange: ((Int) -> Unit)?,
    ) {
        val binding = (view.getTag(R.id.viewcompose_seek_listener) as? SliderListenerBinding)
            ?: SliderListenerBinding().also {
                view.setTag(R.id.viewcompose_seek_listener, it)
                view.setOnSeekBarChangeListener(it)
            }
        binding.min = min
        binding.expectedValue = expectedValue
        binding.onValueChange = onValueChange
    }

    fun readTextFieldSpec(node: VNode): TextFieldSpec {
        return readTextFieldSpec(node.requireSpec())
    }

    fun readTextFieldSpec(spec: TextFieldNodeProps): TextFieldSpec {
        return TextFieldSpec(
            state = spec.state,
            value = spec.value,
            placeholder = spec.placeholder,
            enabled = spec.enabled,
            singleLine = spec.singleLine,
            minLines = spec.minLines,
            maxLines = spec.maxLines,
            inputType = resolveInputType(
                options = spec.keyboardOptions,
                singleLine = spec.singleLine,
            ),
            editorOptions = resolveEditorOptions(spec.keyboardOptions),
            hintColor = spec.hintColor,
            readOnly = spec.readOnly,
            inputTransformation = spec.inputTransformation,
            onKeyboardAction = spec.onKeyboardAction,
            imeAction = spec.keyboardOptions.imeAction,
            onFocusChange = spec.onFocusChange,
            autofillHints = spec.autofillHints,
            cursorColor = spec.cursorColor,
            receiveContent = spec.receiveContent,
        )
    }

    fun readToggleSpec(node: VNode): ToggleSpec {
        val spec = node.requireSpec<ToggleNodeProps>()
        return ToggleSpec(
            text = spec.text,
            enabled = spec.enabled,
            checked = spec.checked,
            controlColor = spec.controlColor,
            thumbColor = spec.thumbColor,
            trackColor = spec.trackColor,
            checkedColor = spec.checkedColor,
            uncheckedColor = spec.uncheckedColor,
            onCheckedChange = spec.onCheckedChange,
        )
    }

    fun readSliderSpec(node: VNode): SliderSpec {
        val spec = node.requireSpec<SliderNodeProps>()
        return SliderSpec(
            min = spec.min,
            max = spec.max,
            value = spec.value,
            enabled = spec.enabled,
            thumbColor = spec.thumbColor,
            trackColor = spec.trackColor,
            onValueChange = spec.onValueChange,
        )
    }

    internal fun resolveInputType(
        options: TextFieldKeyboardOptions,
        singleLine: Boolean,
    ): Int {
        val baseType = when (options.keyboardType) {
            TextFieldType.Text -> InputType.TYPE_CLASS_TEXT
            TextFieldType.Ascii -> {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            TextFieldType.Password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            TextFieldType.Email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            TextFieldType.Number -> InputType.TYPE_CLASS_NUMBER
            TextFieldType.Decimal -> {
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            TextFieldType.Phone -> InputType.TYPE_CLASS_PHONE
            TextFieldType.Uri -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val capitalization = when (options.capitalization) {
            TextFieldCapitalization.None -> 0
            TextFieldCapitalization.Characters -> InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            TextFieldCapitalization.Words -> InputType.TYPE_TEXT_FLAG_CAP_WORDS
            TextFieldCapitalization.Sentences -> InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val correction = when (options.autoCorrectEnabled) {
            true -> InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            false -> InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            null -> 0
        }
        val multiline = if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
        return baseType or capitalization or correction or multiline
    }

    internal fun resolveEditorOptions(options: TextFieldKeyboardOptions): Int {
        val forceAscii = if (options.keyboardType == TextFieldType.Ascii) {
            EditorInfo.IME_FLAG_FORCE_ASCII
        } else {
            0
        }
        return options.imeAction.toEditorAction() or forceAscii
    }

    internal fun applyReadOnly(
        view: ViewComposeEditText,
        readOnly: Boolean,
    ) {
        updateReadOnly(
            view = view,
            readOnly = readOnly,
        )
    }

    private fun TextFieldImeAction.toEditorAction(): Int {
        return when (this) {
            TextFieldImeAction.Default -> EditorInfo.IME_ACTION_UNSPECIFIED
            TextFieldImeAction.None -> EditorInfo.IME_ACTION_NONE
            TextFieldImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
            TextFieldImeAction.Next -> EditorInfo.IME_ACTION_NEXT
            TextFieldImeAction.Done -> EditorInfo.IME_ACTION_DONE
            TextFieldImeAction.Go -> EditorInfo.IME_ACTION_GO
            TextFieldImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
            TextFieldImeAction.Send -> EditorInfo.IME_ACTION_SEND
        }
    }

    internal fun readTextFieldValue(view: ViewComposeEditText): TextFieldValue {
        val editable = view.text
        val text = editable?.toString().orEmpty()
        val selectionStart = view.selectionStart.coerceIn(0, text.length)
        val selectionEnd = view.selectionEnd.coerceIn(0, text.length)
        val composingStart = editable?.let(BaseInputConnection::getComposingSpanStart) ?: -1
        val composingEnd = editable?.let(BaseInputConnection::getComposingSpanEnd) ?: -1
        val composition = if (
            composingStart >= 0 &&
            composingEnd >= 0 &&
            composingStart <= text.length &&
            composingEnd <= text.length
        ) {
            TextRange(composingStart, composingEnd)
        } else {
            null
        }
        return TextFieldValue(
            document = AndroidTextDocumentAdapter.fromCharSequence(editable ?: ""),
            selection = TextRange(selectionStart, selectionEnd),
            composition = composition,
        )
    }

    internal fun applyTextFieldValue(
        view: ViewComposeEditText,
        value: TextFieldValue,
    ) {
        if (view.text?.toString() != value.text) {
            val editable = view.editableText
            val oldText = editable.toString()
            val prefixLength = commonPrefixLength(oldText, value.text)
            val suffixLength = commonSuffixLength(
                oldText = oldText,
                newText = value.text,
                prefixLength = prefixLength,
            )
            editable.replace(
                prefixLength,
                oldText.length - suffixLength,
                value.text,
                prefixLength,
                value.text.length - suffixLength,
            )
        }
        val editable = view.text ?: return
        AndroidTextDocumentAdapter.applyToEditable(
            view = view,
            editable = editable,
            document = value.document,
        )
        BaseInputConnection.removeComposingSpans(editable)
        value.composition?.let { range ->
            BaseInputConnection.setComposingSpans(editable)
            editable.getSpans(0, editable.length, Any::class.java)
                .filter { span ->
                    editable.getSpanFlags(span) and android.text.Spanned.SPAN_COMPOSING != 0
                }
                .forEach { span ->
                    editable.setSpan(
                        span,
                        range.min,
                        range.max,
                        editable.getSpanFlags(span),
                    )
                }
        }
        Selection.setSelection(
            editable,
            value.selection.start,
            value.selection.end,
        )
    }

    internal fun applyAutofillHints(
        view: ViewComposeEditText,
        hints: Set<TextFieldAutofillHint>,
    ) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        view.setAutofillHints(
            *hints.map { hint ->
                when (hint) {
                    TextFieldAutofillHint.Username -> View.AUTOFILL_HINT_USERNAME
                    TextFieldAutofillHint.Password -> View.AUTOFILL_HINT_PASSWORD
                    TextFieldAutofillHint.EmailAddress -> View.AUTOFILL_HINT_EMAIL_ADDRESS
                    TextFieldAutofillHint.PhoneNumber -> View.AUTOFILL_HINT_PHONE
                    TextFieldAutofillHint.PersonName -> View.AUTOFILL_HINT_NAME
                    TextFieldAutofillHint.PostalAddress -> View.AUTOFILL_HINT_POSTAL_ADDRESS
                    TextFieldAutofillHint.PostalCode -> View.AUTOFILL_HINT_POSTAL_CODE
                    TextFieldAutofillHint.CreditCardNumber -> View.AUTOFILL_HINT_CREDIT_CARD_NUMBER
                    TextFieldAutofillHint.CreditCardSecurityCode -> {
                        View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE
                    }
                }
            }.toTypedArray(),
        )
    }

    internal fun applyCursorColor(
        view: ViewComposeEditText,
        cursorColor: Int,
    ) {
        if (cursorColor == 0 || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return
        }
        view.textCursorDrawable = view.textCursorDrawable?.mutate()?.apply {
            setTint(cursorColor)
        }
    }

    private fun updateReadOnly(
        view: ViewComposeEditText,
        readOnly: Boolean,
    ) {
        view.setReadOnlyMode(readOnly)
        view.setTextIsSelectable(readOnly)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.isCursorVisible = !readOnly
        view.isLongClickable = true
    }

    private fun commonPrefixLength(
        oldText: String,
        newText: String,
    ): Int {
        val limit = minOf(oldText.length, newText.length)
        var index = 0
        while (index < limit && oldText[index] == newText[index]) {
            index += 1
        }
        return index
    }

    private fun commonSuffixLength(
        oldText: String,
        newText: String,
        prefixLength: Int,
    ): Int {
        val limit = minOf(oldText.length, newText.length) - prefixLength
        var count = 0
        while (
            count < limit &&
            oldText[oldText.length - 1 - count] == newText[newText.length - 1 - count]
        ) {
            count += 1
        }
        return count
    }
}

private class ToggleListenerBinding : CompoundButton.OnCheckedChangeListener {
    var expectedChecked: Boolean = false
    var onCheckedChange: ((Boolean) -> Unit)? = null

    override fun onCheckedChanged(
        buttonView: CompoundButton,
        isChecked: Boolean,
    ) {
        if (isChecked != expectedChecked) {
            onCheckedChange?.invoke(isChecked)
        }
    }
}

private class SliderListenerBinding : SeekBar.OnSeekBarChangeListener {
    var min: Int = 0
    var expectedValue: Int = 0
    var onValueChange: ((Int) -> Unit)? = null

    override fun onProgressChanged(
        seekBar: SeekBar?,
        progress: Int,
        fromUser: Boolean,
    ) {
        val nextValue = min + progress
        if (fromUser && nextValue != expectedValue) {
            onValueChange?.invoke(nextValue)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
}
