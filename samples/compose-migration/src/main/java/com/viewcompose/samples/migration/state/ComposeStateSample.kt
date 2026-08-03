package com.viewcompose.samples.migration.state

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// DOCS_REGION_START(compose-state)
@Composable
fun ComposeStateCounter() {
    var count by remember { mutableIntStateOf(0) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        BasicText("Count: $count")
        BasicText(
            text = "Increment",
            modifier = Modifier.clickable { count += 1 },
        )
    }
}
// DOCS_REGION_END(compose-state)
