package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3Theme
import com.viewcompose.material3.Material3ThemeDefaults
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder

// DOCS_REGION_START(application-theme-mode)
enum class AppThemeMode { System, Light, Dark }

object AppThemePreference {
    val mode = mutableStateOf(AppThemeMode.System)
}

fun UiTreeBuilder.ApplicationTheme(content: UiTreeBuilder.() -> Unit) {
    val systemTokens = Theme.current
    val selectedTokens = when (AppThemePreference.mode.value) {
        AppThemeMode.System -> systemTokens
        AppThemeMode.Light -> Material3ThemeDefaults.light()
        AppThemeMode.Dark -> Material3ThemeDefaults.dark()
    }
    Material3Theme(tokens = selectedTokens, content = content)
}
// DOCS_REGION_END(application-theme-mode)

// DOCS_REGION_START(material3-dynamic-color)
class DynamicColorGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
        ) {
            Text("Dynamic Material color")
        }
    }
}
// DOCS_REGION_END(material3-dynamic-color)

// DOCS_REGION_START(theme-local-override)
fun UiTreeBuilder.AccentPanel() {
    UiThemeOverride(
        colors = { copy(primary = 0xFF6750A4.toInt()) },
        shapes = { copy(medium = large) },
    ) {
        Column {
            Text("Only this subtree uses the accent theme")
            Button("Continue", onClick = {})
        }
    }
}
// DOCS_REGION_END(theme-local-override)
