package com.viewcompose.ui.node

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
