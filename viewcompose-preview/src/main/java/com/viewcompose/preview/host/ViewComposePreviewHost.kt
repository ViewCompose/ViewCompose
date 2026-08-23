package com.viewcompose.preview.host

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.host.android.resources.AndroidResourceEnvironment
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Embeds a ViewCompose render session in a Compose `AndroidView` host.
 *
 * One controller and native root are remembered for the lifetime of this call site. Content-only
 * recompositions request another ViewCompose render; changing [themeMode], [debug], [debugTag],
 * [overlayHost], [diagnostics], or the Android container disposes and recreates the session. Disposal of the
 * Compose effect always disposes the active session.
 *
 * [themeMode] installs `UiThemeDefaults` and [AndroidResourceEnvironment] derives Android values
 * and resource lookups from the host context. This low-level bridge does not call an application
 * `PreviewThemeProvider` and therefore should not be used as the source of production-theme
 * screenshot truth.
 *
 * @param modifier Compose modifier applied to the `AndroidView`
 * @param themeMode default light or dark ViewCompose tokens
 * @param debug whether renderer debug diagnostics are enabled
 * @param debugTag tag associated with renderer debug output
 * @param overlayHost overlay backend used by DSL content; the default intentionally presents no
 * platform surfaces
 * @param diagnostics optional correlated lifecycle, failure, and frame event sink
 * @param content ViewCompose DSL body receiving the bridge-owned native root
 */
@Composable
fun ViewComposePreviewHost(
    modifier: Modifier = Modifier,
    themeMode: PreviewTheme = PreviewTheme.Light,
    debug: Boolean = false,
    debugTag: String = "ViewComposePreview",
    overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    diagnostics: RenderDiagnostics? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
) {
    val renderController = remember { PreviewRenderController() }
    val latestContent = rememberUpdatedState(content)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { container ->
            renderController.attach(
                container = container,
                config = PreviewRenderConfig(
                    debug = debug,
                    debugTag = debugTag,
                    overlayHost = overlayHost,
                    diagnostics = diagnostics,
                    themeMode = themeMode,
                ),
                content = {
                    AndroidResourceEnvironment(context = container.context) {
                        UiTheme(
                            tokens = when (themeMode) {
                                PreviewTheme.Light -> UiThemeDefaults.light()
                                PreviewTheme.Dark -> UiThemeDefaults.dark()
                            },
                        ) {
                            latestContent.value.invoke(this, container)
                        }
                    }
                },
            )
        },
    )
    DisposableEffect(renderController) {
        onDispose {
            renderController.dispose()
        }
    }
}

private data class PreviewRenderConfig(
    val debug: Boolean,
    val debugTag: String,
    val overlayHost: OverlayHost,
    val diagnostics: RenderDiagnostics?,
    val themeMode: PreviewTheme,
)

/** Retains the Android render session across content-only Compose recompositions. */
private class PreviewRenderController {
    private var attachedContainer: ViewGroup? = null
    private var config: PreviewRenderConfig? = null
    private var session: RenderSession? = null
    private var latestContent: (UiTreeBuilder.() -> Unit)? = null

    fun attach(
        container: ViewGroup,
        config: PreviewRenderConfig,
        content: UiTreeBuilder.() -> Unit,
    ) {
        latestContent = content
        val shouldRecreate = attachedContainer !== container || this.config != config || session == null
        this.config = config
        if (shouldRecreate) {
            // Recreate when container or host config changes so overlay/debug/theme boundaries stay
            // coherent.
            session?.dispose()
            attachedContainer = container
            session = renderInto(
                container = container,
                debug = config.debug,
                debugTag = config.debugTag,
                overlayHost = config.overlayHost,
                role = RenderSessionRole.Preview,
                diagnostics = config.diagnostics,
            ) {
                requireNotNull(latestContent).invoke(this)
            }
            return
        }
        // When only the content lambda changed, a render pass is enough.
        session?.render()
    }

    fun dispose() {
        session?.dispose()
        session = null
        attachedContainer = null
        config = null
        latestContent = null
    }
}
