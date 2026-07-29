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
 * TextField 节点的文本状态、键盘、装饰和回调属性。
 * Text state, keyboard, decoration, and callback properties for a TextField node.
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
