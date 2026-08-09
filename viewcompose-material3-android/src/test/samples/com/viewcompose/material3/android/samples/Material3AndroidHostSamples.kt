package com.viewcompose.material3.android.samples

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.foundation.Text

fun material3ActivityHostSample(activity: ComponentActivity) {
    activity.setMaterial3UiContent(
        dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
    ) {
        Text("Hello from Material 3 ViewCompose")
    }
}

fun material3FragmentHostSample(fragment: Fragment): ViewGroup {
    return fragment.setMaterial3UiContent {
        Text("Material 3 Fragment content")
    }
}
