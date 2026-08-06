package com.viewcompose.preview.runner

import com.viewcompose.preview.PreviewThemeProvider
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * A compiled preview function paired with the descriptor discovered by build or IDE tooling.
 *
 * @property descriptor stable metadata and configuration variants for the compiled function
 * @property themeProvider optional application-owned resolver used before mounting the DSL tree;
 * `null` selects the deterministic Android theme bridge
 * @property content executable DSL body; callers must invoke it only inside a renderer-owned
 * `UiTreeBuilder` composition
 */
data class StaticPreviewEntry(
    val descriptor: PreviewDescriptor,
    val themeProvider: PreviewThemeProvider? = null,
    val content: UiTreeBuilder.() -> Unit,
)
