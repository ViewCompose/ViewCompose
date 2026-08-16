package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.TextFieldType

/**
 * Couples keyboard and autofill semantics for one [TextField] input purpose.
 *
 * This Q2 value contains behavior only; appearance remains in [TextFieldOverrides]. The standard
 * profiles cover common input purposes, while callers may construct a custom profile without
 * introducing another component-shaped wrapper.
 *
 * @property keyboardOptions keyboard type, capitalization, correction, and IME action policy
 * @property autofillHints semantic Android autofill categories
 */
data class TextFieldInputProfile(
    val keyboardOptions: TextFieldKeyboardOptions = TextFieldKeyboardOptions(),
    val autofillHints: Set<TextFieldAutofillHint> = emptySet(),
) {
    /** Standard input-purpose profiles. */
    companion object {
        /** General text input with no autofill category. */
        val Text: TextFieldInputProfile = TextFieldInputProfile()

        /** Obscured password input with correction disabled and Password autofill semantics. */
        val Password: TextFieldInputProfile = TextFieldInputProfile(
            keyboardOptions = TextFieldKeyboardOptions(
                keyboardType = TextFieldType.Password,
                autoCorrectEnabled = false,
            ),
            autofillHints = setOf(TextFieldAutofillHint.Password),
        )

        /** Email-address input with EmailAddress autofill semantics. */
        val Email: TextFieldInputProfile = TextFieldInputProfile(
            keyboardOptions = TextFieldKeyboardOptions(keyboardType = TextFieldType.Email),
            autofillHints = setOf(TextFieldAutofillHint.EmailAddress),
        )

        /** Numeric input with correction disabled. */
        val Number: TextFieldInputProfile = TextFieldInputProfile(
            keyboardOptions = TextFieldKeyboardOptions(
                keyboardType = TextFieldType.Number,
                autoCorrectEnabled = false,
            ),
        )
    }
}

/** Defines the visual-line policy for a high-level [TextField]. */
sealed interface TextFieldLinePolicy {
    /** Restricts input and layout to one visual line. */
    data object SingleLine : TextFieldLinePolicy

    /**
     * Allows multi-line input within an inclusive visual-line range.
     *
     * @property minLines positive minimum visual-line count
     * @property maxLines maximum visual-line count, not smaller than [minLines]
     * @throws IllegalArgumentException when the line range is invalid
     */
    data class MultiLine(
        val minLines: Int = 3,
        val maxLines: Int = Int.MAX_VALUE,
    ) : TextFieldLinePolicy {
        init {
            require(minLines > 0) { "TextFieldLinePolicy.MultiLine minLines must be positive." }
            require(maxLines >= minLines) {
                "TextFieldLinePolicy.MultiLine maxLines must be at least minLines."
            }
        }
    }
}
