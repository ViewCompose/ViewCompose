package com.viewcompose.host.android.samples

import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.renderInto
import com.viewcompose.host.android.setUiContent
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder

fun activityHostSample(activity: ComponentActivity) {
    activity.setUiContent {
        Text("Hello from ViewCompose")
    }
}

fun fragmentHostSample(fragment: Fragment): ViewGroup {
    return fragment.setUiContent {
        Text("Fragment content")
    }
}

fun renderIntoSample(container: ViewGroup) {
    val session = renderInto(container) {
        Text("Custom host")
    }

    session.setRenderingActive(false)
    session.render()
    session.dispose()
}

fun androidViewInteropSample(
    builder: UiTreeBuilder,
) {
    builder.AndroidView(
        factory = { context -> TextView(context) },
        update = { view ->
            (view as TextView).text = "Native TextView"
        },
        onRelease = { view ->
            (view as TextView).text = null
        },
    )
}
