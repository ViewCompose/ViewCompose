package com.viewcompose.samples.migration.host

import android.widget.TextView
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.AndroidView
import com.viewcompose.android.setUiContent
import com.viewcompose.ui.foundation.UiTreeBuilder

// DOCS_REGION_START(viewcompose-host)
fun ComponentActivity.installViewComposeInteropSample() {
    setUiContent {
        ViewComposeInteropSample()
    }
}

private fun UiTreeBuilder.ViewComposeInteropSample() {
    AndroidView(
        factory = { context -> TextView(context) },
        update = { view ->
            (view as TextView).text = "Native TextView"
        },
    )
}
// DOCS_REGION_END(viewcompose-host)
