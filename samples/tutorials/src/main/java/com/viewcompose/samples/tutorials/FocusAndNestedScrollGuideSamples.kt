package com.viewcompose.samples.tutorials

import com.viewcompose.gesture.nestedScroll
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LocalFocusManager
import com.viewcompose.ui.foundation.ScrollableColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.foundation.rememberUpdatedState
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.input.Key
import com.viewcompose.ui.input.KeyEventType
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.focusGroup
import com.viewcompose.ui.modifier.focusProperties
import com.viewcompose.ui.modifier.focusRequester
import com.viewcompose.ui.modifier.onPreviewKeyEvent

// DOCS_REGION_START(focus-form)
fun UiTreeBuilder.CredentialFocusForm() {
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    focusManager.clearFocus(force = true)
                    true
                } else {
                    false
                }
            },
    ) {
        TextField(
            state = email,
            label = "Email",
            modifier = Modifier.focusProperties {
                next = passwordFocus
                down = passwordFocus
            },
        )
        TextField(
            state = password,
            label = "Password",
            modifier = Modifier.focusRequester(passwordFocus),
        )
        Button(
            text = "Focus password",
            onClick = { passwordFocus.requestFocus() },
        )
    }
}
// DOCS_REGION_END(focus-form)

// DOCS_REGION_START(nested-scroll-toolbar)
fun UiTreeBuilder.CollapsingToolbar(
    collapseBy: (deltaY: Float) -> Float,
) {
    val latestCollapseBy = rememberUpdatedState(collapseBy)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: ScrollDelta,
                source: NestedScrollSource,
            ): ScrollDelta {
                return ScrollDelta(
                    x = 0f,
                    y = latestCollapseBy.value(available.y),
                )
            }
        }
    }

    Column(modifier = Modifier.nestedScroll(connection)) {
        Text("Collapsing toolbar")
        ScrollableColumn {
            repeat(40) { index -> Text("Row $index") }
        }
    }
}
// DOCS_REGION_END(nested-scroll-toolbar)
