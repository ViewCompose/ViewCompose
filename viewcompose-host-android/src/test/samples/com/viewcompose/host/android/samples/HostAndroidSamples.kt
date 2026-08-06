package com.viewcompose.host.android.samples

import android.view.ViewGroup
import android.widget.TextView
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.renderInto
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder

fun renderIntoSample(container: ViewGroup) {
    val session = renderInto(container) {
        Text("Custom host")
    }
    session.setRenderingActive(false)
    session.render()
    session.dispose()
}

fun androidViewInteropSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        factory = { context -> TextView(context) },
        update = { view -> (view as TextView).text = "Native TextView" },
        onRelease = { view -> (view as TextView).text = null },
    )
}
