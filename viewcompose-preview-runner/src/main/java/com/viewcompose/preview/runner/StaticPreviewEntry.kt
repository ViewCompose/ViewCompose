package com.viewcompose.preview.runner

import com.viewcompose.preview.PreviewThemeProvider
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * A compiled preview function paired with the descriptor discovered by build or IDE tooling.
 */
data class StaticPreviewEntry(
    val descriptor: PreviewDescriptor,
    val themeProvider: PreviewThemeProvider? = null,
    val content: UiTreeBuilder.() -> Unit,
)
