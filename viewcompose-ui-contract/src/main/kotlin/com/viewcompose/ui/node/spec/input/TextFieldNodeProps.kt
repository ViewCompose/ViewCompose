package com.viewcompose.ui.node.spec

import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Immutable renderer properties for a state-backed text field.
 *
 * [state] owns editing, selection, composition, and undo history. [value] is the immutable snapshot
 * for the current render and must originate from the same logical state.
 *
 * @property state mutable editing model retained across recomposition
 * @property value immutable text, selection, and composition snapshot for this render
 * @property placeholder text shown while the editable content is empty
 * @property enabled whether editing and enabled semantics are active
 * @property singleLine whether input and layout are constrained to one visual line
 * @property minLines minimum number of laid-out text lines
 * @property maxLines maximum number of laid-out text lines
 * @property keyboardOptions semantic keyboard and IME configuration
 * @property inputTransformation optional transformation applied to proposed edits
 * @property onKeyboardAction optional handler for IME actions; `true` consumes the action
 * @property onFocusChange callback receiving native focus changes
 * @property autofillHints semantic categories exposed to the platform autofill service
 * @property hintColor placeholder text color
 * @property readOnly whether selection is allowed while user edits are rejected
 * @property textColor editable text color
 * @property textSizeSp editable text size in scale-independent pixels
 * @property fontWeight optional platform font weight
 * @property fontFamily optional renderer-compatible font family
 * @property letterSpacingEm optional text letter spacing in em units
 * @property lineHeightSp optional text line height
 * @property includeFontPadding whether platform font top and bottom padding is included
 * @property backgroundColor field surface color
 * @property borderWidth field border width
 * @property borderColor field border color
 * @property shape outline used for background, border, and clipping
 * @property minHeight minimum field height
 * @property paddingHorizontal horizontal padding around editable content
 * @property paddingVertical vertical padding around editable content
 * @property cursorColor cursor color; renderer defaults may apply when unspecified by the DSL
 * @property receiveContent accepted rich receive-content configuration
 */
data class TextFieldNodeProps(
    val state: TextFieldState,
    val value: TextFieldValue,
    val placeholder: String,
    val enabled: Boolean,
    val singleLine: Boolean,
    val minLines: Int,
    val maxLines: Int,
    val keyboardOptions: TextFieldKeyboardOptions,
    val inputTransformation: InputTransformation?,
    val onKeyboardAction: ((TextFieldImeAction) -> Boolean)?,
    val onFocusChange: ((Boolean) -> Unit)?,
    val autofillHints: Set<TextFieldAutofillHint>,
    val hintColor: Int,
    val readOnly: Boolean,
    val textColor: Int,
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val backgroundColor: Int,
    val borderWidth: UiDp,
    val borderColor: Int,
    val shape: UiShape,
    val minHeight: UiDp,
    val paddingHorizontal: UiDp,
    val paddingVertical: UiDp,
    val cursorColor: Int = 0,
    val receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
) : NodeSpec
