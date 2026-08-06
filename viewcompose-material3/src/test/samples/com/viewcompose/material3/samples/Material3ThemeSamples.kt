package com.viewcompose.material3.samples

import android.content.Context
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3ResolvedTheme
import com.viewcompose.material3.Material3Theme
import com.viewcompose.material3.Material3ThemeBridge
import com.viewcompose.material3.Material3ThemeRefreshController
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder

fun material3ThemeBridgeSample(context: Context): Material3ResolvedTheme {
    return Material3ThemeBridge.resolveContext(
        context = context,
        dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
    )
}

fun UiTreeBuilder.material3ThemeSample(context: Context) {
    val resolvedTheme = Material3ThemeBridge.resolveContext(context)
    Material3Theme(resolvedTheme = resolvedTheme) {
        Text("Content using Material 3 theme tokens")
    }
}

fun material3ThemeRefreshSample(controller: Material3ThemeRefreshController) {
    // Invoke on the main thread after applying a runtime theme or resource overlay.
    controller.refresh()
}
