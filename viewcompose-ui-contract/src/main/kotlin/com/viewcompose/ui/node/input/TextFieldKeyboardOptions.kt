package com.viewcompose.ui.node

/**
 * TextField 与平台键盘/自动填充交互的选项。
 * Options for TextField interaction with platform keyboard and autofill.
 */
data class TextFieldKeyboardOptions(
    val keyboardType: TextFieldType = TextFieldType.Text,
    val imeAction: TextFieldImeAction = TextFieldImeAction.Default,
    val capitalization: TextFieldCapitalization = TextFieldCapitalization.None,
    val autoCorrectEnabled: Boolean? = null,
)

enum class TextFieldCapitalization {
    None,
    Characters,
    Words,
    Sentences,
}

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
