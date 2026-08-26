package com.viewcompose.preview.samples

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.viewcompose.preview.PreviewThemeProvider
import com.viewcompose.preview.PreviewThemeResolution
import com.viewcompose.preview.ViewComposePreview
import com.viewcompose.preview.ViewComposePreviewOptions
import com.viewcompose.preview.ViewComposePreviewWithRoot
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreviewThemeProvider
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.UiThemeDefaults

/** Supplies the same application theme to native Views and the ViewCompose DSL tree. */
// DOCS_REGION_START(preview-theme-provider)
@ViewComposePreviewThemeProvider
object ApplicationPreviewThemeProvider : PreviewThemeProvider {
    override fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution {
        val tokens = when (theme) {
            PreviewTheme.Light -> UiThemeDefaults.light()
            PreviewTheme.Dark -> UiThemeDefaults.dark()
        }
        return PreviewThemeResolution(context = context, tokens = tokens)
    }
}
// DOCS_REGION_END(preview-theme-provider)

/** Resolves a preview theme without retaining the configuration context. */
fun applicationPreviewThemeProviderSample(
    context: Context,
    theme: PreviewTheme,
): PreviewThemeResolution {
    return ApplicationPreviewThemeProvider.resolve(context, theme)
}

/** Uses the convenient Compose Preview bridge for a root-independent DSL tree. */
// DOCS_REGION_START(preview-compose-bridge)
@Preview
@Composable
fun composePreviewBridgeSample() {
    val diagnostics = remember {
        RenderDiagnostics(
            collection = RenderDiagnosticCollection(),
            sink = { event -> println(event) },
        )
    }
    ViewComposePreview(
        options = ViewComposePreviewOptions(diagnostics = diagnostics),
    ) {
        Text("ViewCompose")
    }
}
// DOCS_REGION_END(preview-compose-bridge)

/** Uses the bridge-owned Android root as an interop input without retaining it. */
@Preview
@Composable
fun composePreviewWithRootSample() {
    ViewComposePreviewWithRoot { root ->
        Text("Host children: ${root.childCount}")
    }
}
