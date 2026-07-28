package com.viewcompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.viewcompose.preview.ViewComposePreview
import com.viewcompose.preview.ViewComposePreviewOptions
import com.viewcompose.preview.ViewComposePreviewTheme
import com.viewcompose.preview.ViewComposePreviewWithRoot

/**
 * demo 模块对 IDE Preview 暴露的调试入口。
 * Debug-only IDE Preview entrypoints for demo modules.
 */
@Preview(
    name = "Demo Preview Chapter Light",
    group = "Demo/Preview",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
)
@Composable
private fun DemoPreviewChapterLightPreview() {
    ViewComposePreview(
        options = ViewComposePreviewOptions(
            theme = ViewComposePreviewTheme.Light,
            debugTag = "DemoPreviewChapterLight",
        ),
    ) {
        PreviewPage(initialPageIndex = 0)
    }
}

@Preview(
    name = "Demo Preview Chapter Dark",
    group = "Demo/Preview",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
)
@Composable
private fun DemoPreviewChapterDarkPreview() {
    ViewComposePreview(
        options = ViewComposePreviewOptions(
            theme = ViewComposePreviewTheme.Dark,
            debugTag = "DemoPreviewChapterDark",
        ),
    ) {
        PreviewPage(initialPageIndex = 1)
    }
}


@Preview(
    name = "Demo Catalog Dark",
    group = "Demo/Preview",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
)
@Composable
private fun DemoPreviewCatalogPageDarkPreview() {
    ViewComposePreviewWithRoot(
        options = ViewComposePreviewOptions(
            theme = ViewComposePreviewTheme.Dark,
            debugTag = "DemoPreviewChapterDark",
        ),
    ) { root ->
        DemoCatalogPage(root)
    }
}
