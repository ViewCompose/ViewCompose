package com.viewcompose.samples.tutorials

import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.UiTreeBuilder

private data class WindowTheme(
    val darkIcons: Boolean,
)

private object Theme {
    val current: WindowTheme
        get() = WindowTheme(darkIcons = true)
}

private class SampleWindow {
    var darkIcons: Boolean = false
        private set

    fun applyWindowAppearance(theme: WindowTheme) {
        darkIcons = theme.darkIcons
    }
}

private class SampleActivity(
    val window: SampleWindow,
)

private fun UiTreeBuilder.effectsLocalCaptureSample(activity: SampleActivity) {
    // DOCS_REGION_START(architecture-effects-local-capture)
    val theme = Theme.current
    val window = activity.window

    SideEffect(theme, window) {
        window.applyWindowAppearance(theme)
    }
    // DOCS_REGION_END(architecture-effects-local-capture)
}
