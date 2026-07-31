package com.viewcompose

import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Static-preview entry point used to exercise the complete Gradle and Studio rendering path.
 */
@ViewComposePreview(
    name = "Preview chapter · Light",
    group = "Demo/Static preview",
    widthDp = 411,
    heightDp = STATIC_DEMO_PREVIEW_HEIGHT_DP,
    theme = PreviewTheme.Light,
)
@ViewComposePreview(
    name = "Preview chapter · Dark",
    group = "Demo/Static preview",
    widthDp = 411,
    heightDp = STATIC_DEMO_PREVIEW_HEIGHT_DP,
    theme = PreviewTheme.Dark,
)
fun UiTreeBuilder.StaticDemoPreview() {
    PreviewPage(initialPageIndex = 0)
}

private const val STATIC_DEMO_PREVIEW_HEIGHT_DP = 2400
