package com.viewcompose.samples.migration.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// DOCS_REGION_START(compose-layout)
private val LocalContentPadding = compositionLocalOf { 8.dp }

@Composable
fun ComposeProfileRow(name: String) {
    CompositionLocalProvider(LocalContentPadding provides 16.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalContentPadding.current),
        ) {
            BasicText(name)
        }
    }
}
// DOCS_REGION_END(compose-layout)
