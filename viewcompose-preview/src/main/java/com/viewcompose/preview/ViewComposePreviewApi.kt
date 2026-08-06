package com.viewcompose.preview

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.viewcompose.preview.host.ViewComposePreviewHost
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Configures the Compose-to-ViewCompose preview bridge.
 *
 * This convenience bridge selects `UiThemeDefaults` for [theme]. It does not invoke an application
 * `PreviewThemeProvider`; use the ViewCompose static-preview runner when production theme fidelity
 * is required.
 *
 * @property theme light or dark default ViewCompose token set installed around the DSL tree
 * @property debug whether the Android render session emits its debug diagnostics
 * @property debugTag log tag associated with debug output; ignored when [debug] is `false`
 */
data class ViewComposePreviewOptions(
    val theme: PreviewTheme = PreviewTheme.Light,
    val debug: Boolean = false,
    val debugTag: String = "ViewComposePreview",
)

/**
 * Renders [content] inside a Compose `AndroidView` preview host.
 *
 * The host retains one ViewCompose render session across Compose recompositions. Updating [content]
 * schedules a render on that session, while changes to [options] recreate the session so debug and
 * theme boundaries remain coherent. Leaving the Compose composition disposes the session and its
 * native View tree.
 *
 * Use [ViewComposePreviewWithRoot] when DSL code must inspect the hosting `ViewGroup`. This bridge
 * is intended for Android Studio Compose Preview and does not replace the static-preview runner's
 * application theme, configuration, diagnostics, or artifact pipeline.
 *
 * @param modifier Compose modifier applied to the hosting `AndroidView`
 * @param options bridge theme and debug configuration
 * @param content ViewCompose DSL body rendered without exposing the host root
 * @sample com.viewcompose.preview.samples.composePreviewBridgeSample
 */
@Composable
fun ViewComposePreview(
    modifier: Modifier = Modifier,
    options: ViewComposePreviewOptions = ViewComposePreviewOptions(),
    content: UiTreeBuilder.() -> Unit,
) {
    ViewComposePreviewHost(
        modifier = modifier,
        themeMode = options.theme,
        debug = options.debug,
        debugTag = options.debugTag,
        content = { _ ->
            content.invoke(this)
        },
    )
}

/**
 * Renders [content] inside a Compose preview and supplies its Android root `ViewGroup`.
 *
 * Session retention, recreation, disposal, and theme limitations are the same as
 * [ViewComposePreview]. The root is owned by the bridge: content may use it as an interop anchor but
 * must not remove it, retain it beyond the composition, or independently dispose its render
 * session.
 *
 * @param modifier Compose modifier applied to the hosting `AndroidView`
 * @param options bridge theme and debug configuration
 * @param content ViewCompose DSL body receiving the bridge-owned Android root
 * @sample com.viewcompose.preview.samples.composePreviewWithRootSample
 */
@Composable
fun ViewComposePreviewWithRoot(
    modifier: Modifier = Modifier,
    options: ViewComposePreviewOptions = ViewComposePreviewOptions(),
    content: UiTreeBuilder.(ViewGroup) -> Unit,
) {
    ViewComposePreviewHost(
        modifier = modifier,
        themeMode = options.theme,
        debug = options.debug,
        debugTag = options.debugTag,
        content = content,
    )
}
