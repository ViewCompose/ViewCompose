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
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.UiEnvironment
import com.viewcompose.widget.core.UiTheme
import com.viewcompose.widget.core.UiThemeDefaults
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 将 ViewCompose 渲染会话嵌入 Compose Preview 的 AndroidView 容器。
 * Embeds a ViewCompose render session inside the AndroidView container used by Compose Preview.
 *
 * 该宿主负责把 Compose 的重组更新转成 ViewCompose 的重新渲染，同时在参数变化时重建会话。
 * This host converts Compose recomposition updates into ViewCompose re-renders and recreates
 * the session when host parameters change.
 */
@Composable
fun ViewComposePreviewHost(
    modifier: Modifier = Modifier,
    themeMode: PreviewTheme = PreviewTheme.Light,
    debug: Boolean = false,
    debugTag: String = "ViewComposePreview",
    overlayHost: OverlayHost = OverlayHostDefaults.noOp,
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
                    themeMode = themeMode,
                ),
                content = {
                    UiEnvironment(androidContext = container.context) {
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
    val themeMode: PreviewTheme,
)

/**
 * 保留 Android render session，避免 Preview 每次重组都重建整棵 View 树。
 * Retains the Android render session so Preview recompositions do not rebuild the whole View tree.
 */
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
            // 容器或宿主配置变化时必须重建，确保 overlay/debug/theme 边界一致。
            // Recreate when container or host config changes so overlay/debug/theme boundaries stay consistent.
            session?.dispose()
            attachedContainer = container
            session = renderInto(
                container = container,
                debug = config.debug,
                debugTag = config.debugTag,
                overlayHost = config.overlayHost,
            ) {
                requireNotNull(latestContent).invoke(this)
            }
            return
        }
        // 内容 lambda 更新但宿主边界未变时，只触发一次渲染即可。
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
