package com.viewcompose.material3.samples

import android.content.Context
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3Button
import com.viewcompose.material3.Material3Card
import com.viewcompose.material3.Material3NavigationBar
import com.viewcompose.material3.Material3ResolvedTheme
import com.viewcompose.material3.Material3Surface
import com.viewcompose.material3.Material3Switch
import com.viewcompose.material3.Material3Theme
import com.viewcompose.material3.Material3ThemeBridge
import com.viewcompose.material3.Material3ThemeRefreshController
import com.viewcompose.material3.Material3TextField
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

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

fun material3ResolvedThemeRefreshSample(resolvedTheme: Material3ResolvedTheme) {
    resolvedTheme.refresh()
    Material3ThemeBridge.fromResolvedTheme(resolvedTheme)
}

fun UiTreeBuilder.material3ComponentsSample() {
    Material3Theme {
        val fieldState = rememberTextFieldState("Material")
        Material3Surface(modifier = Modifier.padding(16.dp)) {
            Material3Card {
                Text("Material card")
            }
            Material3Button(text = "Continue", onClick = {})
            Material3Switch(text = "Dynamic color", checked = true, onCheckedChange = {})
            Material3TextField(state = fieldState, label = "Name")
            Material3NavigationBar(selectedIndex = 0, onItemSelected = {}) {
                Item(label = "Home", icon = ImageSource.Resource(android.R.drawable.ic_menu_view))
                Item(label = "Settings", icon = ImageSource.Resource(android.R.drawable.ic_menu_preferences))
            }
        }
    }
}
