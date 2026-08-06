package com.viewcompose.samples.migration.state

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

// DOCS_REGION_START(viewcompose-state)
fun UiTreeBuilder.ViewComposeStateCounter() {
    val count = remember { mutableStateOf(0) }

    Column(
        spacing = 16.dp,
        modifier = Modifier.padding(24.dp),
    ) {
        Text("Count: ${count.value}")
        Button(
            text = "Increment",
            onClick = { count.value += 1 },
        )
    }
}
// DOCS_REGION_END(viewcompose-state)
