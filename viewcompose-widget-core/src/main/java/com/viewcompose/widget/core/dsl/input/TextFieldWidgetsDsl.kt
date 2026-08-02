package com.viewcompose.widget.core

import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Emits the low-level text input node without outer label or supporting text.
 *
 * BasicTextField passes TextFieldState plus keyboard/transformation/content receiving options directly to the renderer,
 * making it suitable for higher-level composite widgets.
 */
fun UiTreeBuilder.BasicTextField(
    state: TextFieldState,
    hint: String = "",
    placeholder: String = hint,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = if (singleLine) 1 else 1,
    keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(),
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    cursorColor: Int = TextFieldDefaults.cursorColor(),
    textColor: Int = TextFieldDefaults.textColor(enabled),
    textStyle: UiTextStyle = TextFieldDefaults.textStyle(),
    hintColor: Int = TextFieldDefaults.hintColor(enabled = enabled),
    backgroundColor: Int = 0x00000000,
    borderWidth: UiDp = UiDp.Zero,
    borderColor: Int = 0x00000000,
    shape: UiShape = UiShape.rounded(UiDp.Zero),
    minHeight: UiDp = UiDp.Zero,
    paddingHorizontal: UiDp = UiDp.Zero,
    paddingVertical: UiDp = UiDp.Zero,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.TextField,
        key = key,
        spec = basicTextFieldSpec(
            state = state,
            placeholder = placeholder.ifEmpty { hint },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            inputTransformation = inputTransformation,
            receiveContent = receiveContent,
            onKeyboardAction = onKeyboardAction,
            onFocusChange = onFocusChange,
            autofillHints = autofillHints,
            hintColor = hintColor,
            readOnly = readOnly,
            textColor = textColor,
            textStyle = textStyle,
            backgroundColor = backgroundColor,
            borderWidth = borderWidth,
            borderColor = borderColor,
            shape = shape,
            minHeight = minHeight,
            paddingHorizontal = paddingHorizontal,
            paddingVertical = paddingVertical,
            cursorColor = cursorColor,
        ),
        modifier = modifier,
    )
}

/**
 * Emits a standard text field composite with label, placeholder, and supporting text.
 *
 * The outer Column only lays out helper text; the editable area is still produced by BasicTextField as the same TextField node type.
 */
fun UiTreeBuilder.TextField(
    state: TextFieldState,
    hint: String = "",
    label: String = "",
    placeholder: String = hint,
    supportingText: String = "",
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = if (singleLine) 1 else 3,
    keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(),
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    variant: TextFieldVariant = TextFieldVariant.Filled,
    size: TextFieldSize = TextFieldSize.Medium,
    enabled: Boolean = true,
    isError: Boolean = false,
    cursorColor: Int = TextFieldDefaults.cursorColor(),
    style: UiTextStyle = TextFieldDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val hintColor = TextFieldDefaults.hintColor(
        enabled = enabled,
        isError = isError,
    )
    val labelColor = TextFieldDefaults.labelColor(
        enabled = enabled,
        isError = isError,
    )
    val supportingTextColor = TextFieldDefaults.supportingTextColor(
        enabled = enabled,
        isError = isError,
    )
    // Visual structure stays in the composite layer so the renderer only binds the editable field.
    Column(
        key = key,
        modifier = modifier,
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = TextFieldDefaults.labelTextStyle(),
                color = labelColor,
                modifier = Modifier.margin(bottom = 4.dp),
            )
        }
        BasicTextField(
            state = state,
            hint = hint,
            placeholder = placeholder.ifEmpty { hint },
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            inputTransformation = inputTransformation,
            receiveContent = receiveContent,
            onKeyboardAction = onKeyboardAction,
            onFocusChange = onFocusChange,
            autofillHints = autofillHints,
            hintColor = hintColor,
            readOnly = readOnly,
            textColor = TextFieldDefaults.textColor(
                enabled = enabled,
                isError = isError,
            ),
            textStyle = style,
            backgroundColor = TextFieldDefaults.containerColor(
                variant = variant,
                enabled = enabled,
                isError = isError,
            ),
            borderWidth = TextFieldDefaults.borderWidth(variant),
            borderColor = TextFieldDefaults.borderColor(
                variant = variant,
                enabled = enabled,
                isError = isError,
            ),
            shape = TextFieldDefaults.shape(),
            minHeight = if (singleLine) TextFieldDefaults.height(size) else UiDp.Zero,
            paddingHorizontal = TextFieldDefaults.horizontalPadding(size),
            paddingVertical = TextFieldDefaults.verticalPadding(size),
            cursorColor = cursorColor,
            modifier = Modifier.fillMaxWidth(),
        )
        if (supportingText.isNotBlank()) {
            Text(
                text = supportingText,
                style = TextFieldDefaults.supportingTextStyle(),
                color = supportingTextColor,
                modifier = Modifier.margin(top = 4.dp),
            )
        }
    }
}

/**
 * Normalizes DSL parameters into TextFieldNodeProps consumed by the renderer.
 */
private fun basicTextFieldSpec(
    state: TextFieldState,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    keyboardOptions: TextFieldKeyboardOptions,
    inputTransformation: InputTransformation?,
    receiveContent: ReceiveContentConfiguration,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)?,
    onFocusChange: ((Boolean) -> Unit)?,
    autofillHints: Set<TextFieldAutofillHint>,
    hintColor: Int,
    readOnly: Boolean,
    textColor: Int,
    textStyle: UiTextStyle,
    backgroundColor: Int,
    borderWidth: UiDp,
    borderColor: Int,
    shape: UiShape,
    minHeight: UiDp,
    paddingHorizontal: UiDp,
    paddingVertical: UiDp,
    cursorColor: Int,
): TextFieldNodeProps {
    // TextFieldState is the single source of truth; value is snapshotted for diff/patch comparison.
    return TextFieldNodeProps(
        state = state,
        value = state.value,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        inputTransformation = inputTransformation,
        receiveContent = receiveContent,
        onKeyboardAction = onKeyboardAction,
        onFocusChange = onFocusChange,
        autofillHints = autofillHints,
        hintColor = hintColor,
        readOnly = readOnly,
        textColor = textColor,
        textSizeSp = textStyle.fontSizeSp,
        fontWeight = textStyle.fontWeight,
        fontFamily = uiFontFamily(textStyle.fontFamily),
        letterSpacingEm = textStyle.letterSpacingEm,
        lineHeightSp = textStyle.lineHeightSp,
        includeFontPadding = textStyle.includeFontPadding,
        backgroundColor = backgroundColor,
        borderWidth = borderWidth,
        borderColor = borderColor,
        shape = shape,
        minHeight = minHeight,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        cursorColor = cursorColor,
    )
}

/**
 * Emits a single-line text field configured for password input.
 *
 * Auto-correct is disabled and the Password autofill hint is declared by default; callers can still override keyboardOptions.
 */
fun UiTreeBuilder.PasswordField(
    state: TextFieldState,
    hint: String = "",
    label: String = "",
    supportingText: String = "",
    keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(
        keyboardType = com.viewcompose.ui.node.TextFieldType.Password,
        autoCorrectEnabled = false,
    ),
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    autofillHints: Set<TextFieldAutofillHint> = setOf(TextFieldAutofillHint.Password),
    variant: TextFieldVariant = TextFieldVariant.Filled,
    size: TextFieldSize = TextFieldSize.Medium,
    enabled: Boolean = true,
    isError: Boolean = false,
    style: UiTextStyle = TextFieldDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        hint = hint,
        label = label,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        inputTransformation = inputTransformation,
        receiveContent = receiveContent,
        onKeyboardAction = onKeyboardAction,
        onFocusChange = onFocusChange,
        autofillHints = autofillHints,
        variant = variant,
        size = size,
        enabled = enabled,
        isError = isError,
        style = style,
        key = key,
        modifier = modifier,
    )
}

/**
 * Emits a single-line text field configured for email input.
 *
 * The default keyboard type and autofill hint target email flows while preserving the standard TextField styling.
 */
fun UiTreeBuilder.EmailField(
    state: TextFieldState,
    hint: String = "",
    label: String = "",
    supportingText: String = "",
    keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(
        keyboardType = com.viewcompose.ui.node.TextFieldType.Email,
    ),
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    autofillHints: Set<TextFieldAutofillHint> = setOf(TextFieldAutofillHint.EmailAddress),
    variant: TextFieldVariant = TextFieldVariant.Filled,
    size: TextFieldSize = TextFieldSize.Medium,
    enabled: Boolean = true,
    style: UiTextStyle = TextFieldDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        hint = hint,
        label = label,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        inputTransformation = inputTransformation,
        receiveContent = receiveContent,
        onKeyboardAction = onKeyboardAction,
        onFocusChange = onFocusChange,
        autofillHints = autofillHints,
        variant = variant,
        size = size,
        enabled = enabled,
        style = style,
        key = key,
        modifier = modifier,
    )
}

/**
 * Emits a single-line text field configured for numeric input.
 *
 * Auto-correct is disabled by default to keep numeric input independent from suggestion or spelling logic.
 */
fun UiTreeBuilder.NumberField(
    state: TextFieldState,
    hint: String = "",
    label: String = "",
    supportingText: String = "",
    keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(
        keyboardType = com.viewcompose.ui.node.TextFieldType.Number,
        autoCorrectEnabled = false,
    ),
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    variant: TextFieldVariant = TextFieldVariant.Filled,
    size: TextFieldSize = TextFieldSize.Medium,
    enabled: Boolean = true,
    style: UiTextStyle = TextFieldDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        hint = hint,
        label = label,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        inputTransformation = inputTransformation,
        receiveContent = receiveContent,
        onKeyboardAction = onKeyboardAction,
        onFocusChange = onFocusChange,
        autofillHints = autofillHints,
        variant = variant,
        size = size,
        enabled = enabled,
        style = style,
        key = key,
        modifier = modifier,
    )
}

/**
 * Emits a multi-line text input area.
 *
 * TextArea reuses TextField visual rules, only disabling singleLine and applying multi-line line constraints.
 */
fun UiTreeBuilder.TextArea(
    state: TextFieldState,
    hint: String = "",
    label: String = "",
    placeholder: String = hint,
    supportingText: String = "",
    variant: TextFieldVariant = TextFieldVariant.Filled,
    size: TextFieldSize = TextFieldSize.Medium,
    enabled: Boolean = true,
    isError: Boolean = false,
    readOnly: Boolean = false,
    minLines: Int = 3,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(),
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    autofillHints: Set<TextFieldAutofillHint> = emptySet(),
    style: UiTextStyle = TextFieldDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = state,
        hint = hint,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        singleLine = false,
        readOnly = readOnly,
        maxLines = maxLines,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        inputTransformation = inputTransformation,
        receiveContent = receiveContent,
        onKeyboardAction = onKeyboardAction,
        onFocusChange = onFocusChange,
        autofillHints = autofillHints,
        variant = variant,
        size = size,
        enabled = enabled,
        isError = isError,
        style = style,
        key = key,
        modifier = modifier,
    )
}
