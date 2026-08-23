package com.viewcompose.material3.android.samples

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.viewcompose.host.android.resources.AndroidResourceRefreshController
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.Text

fun material3ActivityHostSample(activity: ComponentActivity) {
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event -> println(event) },
    )
    activity.setMaterial3UiContent(
        dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
        diagnostics = diagnostics,
    ) {
        Text("Hello from Material 3 ViewCompose")
    }
}

fun material3FragmentHostSample(fragment: Fragment): ViewGroup {
    return fragment.setMaterial3UiContent {
        Text("Material 3 Fragment content")
    }
}

fun material3HostResourceRefreshSample(
    activity: ComponentActivity,
    refreshController: AndroidResourceRefreshController,
) {
    activity.setMaterial3UiContent(
        resourceRefreshController = refreshController,
    ) {
        Text("Configuration-aware Material content")
    }

    // Invoke after a host-scoped resource mutation that did not dispatch Configuration change.
    refreshController.refresh()
}
