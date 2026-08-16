package com.viewcompose.ui.foundation

import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxHeight
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Creates a single-line search input backed by caller-owned [state].
 *
 * A non-null [onSearch] changes the IME action to Search and consumes only that action. The callback
 * receives the latest text synchronously on the renderer thread; this composite does not submit,
 * debounce, or clear text by itself.
 *
 * @sample com.viewcompose.ui.foundation.samples.searchBarSample
 * @receiver active tree builder receiving the search composite
 * @param state caller-owned editable text, selection, composition, and undo state
 * @param onSearch optional callback invoked when the Search IME action is accepted
 * @param placeholder text displayed while [state] is empty
 * @param leadingIcon optional image displayed before the editable region
 * @param trailingIcon optional custom content displayed after the editable region
 * @param enabled whether editing and focus input are accepted
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered caller configuration applied after search-bar geometry and appearance
 */
fun UiTreeBuilder.SearchBar(
    state: TextFieldState,
    onSearch: ((String) -> Unit)? = null,
    placeholder: String = "",
    leadingIcon: ImageSource? = null,
    trailingIcon: (UiTreeBuilder.() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val containerColor = SearchBarDefaults.containerColor()
    val shape = SearchBarDefaults.shape()
    val semanticModifier = Modifier
        .height(SearchBarDefaults.height())
        .backgroundColor(containerColor)
        .shape(shape)
        .clip()
        .elevation(SearchBarDefaults.elevation())
        .padding(horizontal = SearchBarDefaults.horizontalPadding())
        .then(modifier)
    Row(
        key = key,
        spacing = SearchBarDefaults.iconSpacing(),
        verticalAlignment = VerticalAlignment.Center,
        modifier = semanticModifier,
    ) {
        if (leadingIcon != null) {
            Icon(
                source = leadingIcon,
                tint = SearchBarDefaults.iconColor(),
                size = SearchBarDefaults.iconSize(),
            )
        }
        BasicTextField(
            state = state,
            style = BasicTextFieldStyle(
                cursorColor = SearchBarDefaults.contentColor(),
                textColor = SearchBarDefaults.contentColor(),
                textStyle = SearchBarDefaults.textStyle(),
                placeholderColor = SearchBarDefaults.placeholderColor(),
                containerColor = 0x00000000,
                borderWidth = UiDp.Zero,
                borderColor = 0x00000000,
                shape = UiShape.rounded(UiDp.Zero),
                minimumHeight = UiDp.Zero,
                horizontalPadding = UiDp.Zero,
                verticalPadding = UiDp.Zero,
            ),
            placeholder = placeholder,
            enabled = enabled,
            singleLine = true,
            minLines = 1,
            maxLines = 1,
            keyboardOptions = TextFieldKeyboardOptions(
                imeAction = if (onSearch != null) {
                    TextFieldImeAction.Search
                } else {
                    TextFieldImeAction.Default
                },
            ),
            // When onSearch is null the IME behavior remains default; otherwise only Search is consumed.
            onKeyboardAction = if (onSearch == null) {
                null
            } else {
                { action ->
                    if (action == TextFieldImeAction.Search) {
                        onSearch(state.text)
                        true
                    } else {
                        false
                    }
                }
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}
