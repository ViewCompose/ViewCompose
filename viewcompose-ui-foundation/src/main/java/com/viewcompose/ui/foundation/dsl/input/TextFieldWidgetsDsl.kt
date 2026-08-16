package com.viewcompose.ui.foundation

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
 * Defines the complete resolved appearance consumed by [BasicTextField].
 *
 * This Q2 snapshot contains no theme, variant, or scoped-override lookup. Design-system recipes
 * and high-level components resolve every value before constructing it.
 *
 * @property cursorColor cursor ARGB color
 * @property textColor editable-text ARGB color
 * @property textStyle editable text typography
 * @property placeholderColor placeholder ARGB color
 * @property containerColor editable container ARGB color
 * @property borderWidth border thickness in dp
 * @property borderColor border ARGB color
 * @property shape editable container shape
 * @property minimumHeight minimum editable-area height in dp
 * @property horizontalPadding content padding on each horizontal edge in dp
 * @property verticalPadding content padding on each vertical edge in dp
 * @throws IllegalArgumentException when a dimension is negative
 */
data class BasicTextFieldStyle(
    val cursorColor: Int,
    val textColor: Int,
    val textStyle: UiTextStyle,
    val placeholderColor: Int,
    val containerColor: Int,
    val borderWidth: UiDp,
    val borderColor: Int,
    val shape: UiShape,
    val minimumHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
) {
    init {
        require(borderWidth >= UiDp.Zero) { "BasicTextFieldStyle borderWidth must be non-negative." }
        require(minimumHeight >= UiDp.Zero) { "BasicTextFieldStyle minimumHeight must be non-negative." }
        require(horizontalPadding >= UiDp.Zero) {
            "BasicTextFieldStyle horizontalPadding must be non-negative."
        }
        require(verticalPadding >= UiDp.Zero) {
            "BasicTextFieldStyle verticalPadding must be non-negative."
        }
    }
}

/**
 * Emits the low-level text input node without outer label or supporting text.
 *
 * BasicTextField passes TextFieldState plus keyboard, transformation, and content-receiving options
 * directly to the renderer. [style] is complete and the primitive performs no Theme or component
 * Local lookup, making it suitable for design-system-owned composite widgets.
 *
 * @sample com.viewcompose.ui.foundation.samples.basicTextFieldStyleSample
 * @receiver active tree builder receiving the editable node
 * @param state caller-owned editable text, selection, and composition state
 * @param style complete resolved appearance for the current component state
 * @param hint legacy placeholder fallback used when [placeholder] is empty
 * @param placeholder visible text displayed while [state] is empty
 * @param enabled whether editing and focus input are accepted
 * @param singleLine whether input is constrained to one visual line
 * @param readOnly whether selection remains available while mutation is disabled
 * @param maxLines maximum visual line count when [singleLine] is `false`
 * @param minLines minimum visual line count when [singleLine] is `false`
 * @param keyboardOptions keyboard type, capitalization, correction, and IME action policy
 * @param inputTransformation optional synchronous edit filter
 * @param receiveContent accepted rich-content policy
 * @param onKeyboardAction callback that may consume an IME action on the renderer thread
 * @param onFocusChange callback invoked when native focus changes
 * @param autofillHints semantic Android autofill categories
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended to the emitted editable node
 */
fun UiTreeBuilder.BasicTextField(
    state: TextFieldState,
    style: BasicTextFieldStyle,
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
            hintColor = style.placeholderColor,
            readOnly = readOnly,
            textColor = style.textColor,
            textStyle = style.textStyle,
            backgroundColor = style.containerColor,
            borderWidth = style.borderWidth,
            borderColor = style.borderColor,
            shape = style.shape,
            minHeight = style.minimumHeight,
            paddingHorizontal = style.horizontalPadding,
            paddingVertical = style.verticalPadding,
            cursorColor = style.cursorColor,
        ),
        modifier = modifier,
    )
}

/**
 * Emits a standard text field composite with label, placeholder, and supporting text.
 *
 * [state] owns text, selection, composition, and undo history. The outer Column only lays out
 * helper text; [BasicTextField] emits the editable node. Appearance resolves once from instance,
 * scoped, and semantic defaults in that order.
 *
 * @sample com.viewcompose.ui.foundation.samples.textFieldVariantsSample
 * @receiver active tree builder receiving the composite
 * @param state caller-owned editable state retained independently from the emitted node
 * @param hint legacy placeholder fallback used when [placeholder] is empty
 * @param label optional text displayed above the editable area
 * @param placeholder text displayed while [state] is empty
 * @param supportingText optional guidance or error text displayed below the editable area
 * @param inputProfile coupled keyboard and autofill semantics for the input purpose
 * @param linePolicy single-line or validated multi-line layout policy
 * @param readOnly whether selection remains available while user edits are rejected
 * @param inputTransformation optional synchronous filter for proposed edits
 * @param receiveContent accepted rich-content policy
 * @param onKeyboardAction callback that may consume an IME action on the renderer thread
 * @param onFocusChange callback invoked on the renderer thread when native focus changes
 * @param variant visual treatment used to resolve container and border roles
 * @param size interaction-density tier used for typography, padding, and minimum height
 * @param enabled whether editing and focus input are accepted
 * @param isError whether error appearance roles are selected
 * @param overrides sparse instance appearance applied after scoped [ProvideTextFieldOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration applied to the outer composite
 */
fun UiTreeBuilder.TextField(
    state: TextFieldState,
    hint: String = "",
    label: String = "",
    placeholder: String = hint,
    supportingText: String = "",
    inputProfile: TextFieldInputProfile = TextFieldInputProfile.Text,
    linePolicy: TextFieldLinePolicy = TextFieldLinePolicy.SingleLine,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    variant: TextFieldVariant = TextFieldVariant.Filled,
    size: TextFieldSize = TextFieldSize.Medium,
    enabled: Boolean = true,
    isError: Boolean = false,
    overrides: TextFieldOverrides = TextFieldOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val singleLine: Boolean
    val minLines: Int
    val maxLines: Int
    when (linePolicy) {
        TextFieldLinePolicy.SingleLine -> {
            singleLine = true
            minLines = 1
            maxLines = 1
        }
        is TextFieldLinePolicy.MultiLine -> {
            singleLine = false
            minLines = linePolicy.minLines
            maxLines = linePolicy.maxLines
        }
    }
    val appearance = TextFieldDefaults.resolve(
        variant = variant,
        size = size,
        enabled = enabled,
        isError = isError,
        instance = overrides,
    )
    // Visual structure stays in the composite layer so the renderer only binds the editable field.
    Column(
        key = key,
        modifier = modifier,
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = appearance.labelTextStyle,
                color = appearance.labelColor,
                modifier = Modifier.margin(bottom = 4.dp),
            )
        }
        BasicTextField(
            state = state,
            style = BasicTextFieldStyle(
                cursorColor = appearance.cursorColor,
                textColor = appearance.textColor,
                textStyle = appearance.textStyle,
                placeholderColor = appearance.placeholderColor,
                containerColor = appearance.containerColor,
                borderWidth = appearance.borderWidth,
                borderColor = appearance.borderColor,
                shape = appearance.shape,
                minimumHeight = if (singleLine) appearance.minimumHeight else UiDp.Zero,
                horizontalPadding = appearance.horizontalPadding,
                verticalPadding = appearance.verticalPadding,
            ),
            hint = hint,
            placeholder = placeholder.ifEmpty { hint },
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = inputProfile.keyboardOptions,
            inputTransformation = inputTransformation,
            receiveContent = receiveContent,
            onKeyboardAction = onKeyboardAction,
            onFocusChange = onFocusChange,
            autofillHints = inputProfile.autofillHints,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
        )
        if (supportingText.isNotBlank()) {
            Text(
                text = supportingText,
                style = appearance.supportingTextStyle,
                color = appearance.supportingTextColor,
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
