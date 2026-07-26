package com.viewcompose.ui.node.spec

import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.shape.UiShape

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
    val textSizeSp: Int,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: Int? = null,
    val includeFontPadding: Boolean = false,
    val backgroundColor: Int,
    val borderWidth: Int,
    val borderColor: Int,
    val shape: UiShape,
    val minHeight: Int,
    val paddingHorizontal: Int,
    val paddingVertical: Int,
    val cursorColor: Int = 0,
    val receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
) : NodeSpec
