package com.viewcompose.ui.node

/**
 * Describes the soft-keyboard behavior requested by a text field.
 *
 * Platform renderers map unsupported combinations to the closest native behavior.
 *
 * @property keyboardType semantic kind of text expected by the field
 * @property imeAction action requested for the keyboard's primary button
 * @property capitalization automatic capitalization policy
 * @property autoCorrectEnabled explicit autocorrect preference, or `null` for the platform default
 */
data class TextFieldKeyboardOptions(
    val keyboardType: TextFieldType = TextFieldType.Text,
    val imeAction: TextFieldImeAction = TextFieldImeAction.Default,
    val capitalization: TextFieldCapitalization = TextFieldCapitalization.None,
    val autoCorrectEnabled: Boolean? = null,
)

/** Automatic capitalization requested from the platform input method. */
enum class TextFieldCapitalization {
    None,
    Characters,
    Words,
    Sentences,
}

/** Semantic autofill category exposed to the platform autofill service. */
enum class TextFieldAutofillHint {
    Username,
    Password,
    EmailAddress,
    PhoneNumber,
    PersonName,
    PostalAddress,
    PostalCode,
    CreditCardNumber,
    CreditCardSecurityCode,
}
