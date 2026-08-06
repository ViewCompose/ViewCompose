package com.viewcompose.samples.migration.layout

import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf

// DOCS_REGION_START(viewcompose-layout)
private val LocalContentPadding = uiLocalOf { 8.dp }

fun UiTreeBuilder.ViewComposeProfileRow(name: String) {
    ProvideLocal(LocalContentPadding, 16.dp) {
        Row(
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiLocals.current(LocalContentPadding)),
        ) {
            Text(name)
        }
    }
}
// DOCS_REGION_END(viewcompose-layout)
