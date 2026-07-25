package com.viewcompose.widget.core

import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange

/**
 * Remembers a stable text editor state. Host recreation support is supplied by its Saver layer.
 */
fun rememberTextFieldState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
): TextFieldState {
    return remember {
        TextFieldState(
            initialValue = TextFieldValue(
                text = initialText,
                selection = initialSelection,
            ),
        )
    }
}
