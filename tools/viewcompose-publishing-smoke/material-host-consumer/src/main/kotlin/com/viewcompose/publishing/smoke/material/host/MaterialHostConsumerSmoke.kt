package com.viewcompose.publishing.smoke.material.host

import androidx.activity.ComponentActivity
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Text

/** Compiles the advertised Material application surface from the named aggregate alone. */
fun ComponentActivity.installMaterialViewComposeContent() {
    setMaterial3UiContent(
        dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
    ) {
        Text("Material host")
        Button(text = "Continue", onClick = {})
    }
}
