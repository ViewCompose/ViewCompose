package com.viewcompose.samples.migration.host

import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

// DOCS_REGION_START(compose-host)
fun ComponentActivity.installComposeInteropSample() {
    setContent {
        ComposeInteropSample()
    }
}

@Composable
private fun ComposeInteropSample() {
    AndroidView(
        factory = { context -> TextView(context) },
        update = { view -> view.text = "Native TextView" },
    )
}
// DOCS_REGION_END(compose-host)
