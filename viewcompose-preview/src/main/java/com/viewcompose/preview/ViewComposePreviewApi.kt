package com.viewcompose.preview

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.viewcompose.preview.host.PreviewThemeMode
import com.viewcompose.preview.host.ViewComposePreviewHost
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 对外暴露的 Preview 主题选择。
 * Public theme selection exposed by the Preview bridge.
 */
enum class ViewComposePreviewTheme {
    Light,
    Dark,
}

/**
 * 配置单个 ViewCompose Preview 的宿主行为。
 * Configures host behavior for one ViewCompose Preview.
 *
 * [debug] 会传递给 Android 渲染宿主，用于在静态预览中开启结构日志。
 * [debug] is forwarded to the Android render host so static previews can emit tree diagnostics.
 */
data class ViewComposePreviewOptions(
    val theme: ViewComposePreviewTheme = ViewComposePreviewTheme.Light,
    val debug: Boolean = false,
    val debugTag: String = "ViewComposePreview",
)

/**
 * 在 Compose Preview 中渲染一段不依赖 Android root 的 ViewCompose DSL 内容。
 * Renders ViewCompose DSL content that does not need the Android root inside Compose Preview.
 */
@Composable
fun ViewComposePreview(
    modifier: Modifier = Modifier,
    options: ViewComposePreviewOptions = ViewComposePreviewOptions(),
    content: UiTreeBuilder.() -> Unit,
) {
    ViewComposePreviewHost(
        modifier = modifier,
        themeMode = options.theme.toHostThemeMode(),
        debug = options.debug,
        debugTag = options.debugTag,
        content = { _ ->
            content.invoke(this)
        },
    )
}

/**
 * 在 Compose Preview 中渲染一段需要访问 Android root ViewGroup 的 ViewCompose DSL 内容。
 * Renders ViewCompose DSL content that needs access to the Android root ViewGroup in Compose Preview.
 */
@Composable
fun ViewComposePreviewWithRoot(
    modifier: Modifier = Modifier,
    options: ViewComposePreviewOptions = ViewComposePreviewOptions(),
    content: UiTreeBuilder.(ViewGroup) -> Unit,
) {
    ViewComposePreviewHost(
        modifier = modifier,
        themeMode = options.theme.toHostThemeMode(),
        debug = options.debug,
        debugTag = options.debugTag,
        content = content,
    )
}

private fun ViewComposePreviewTheme.toHostThemeMode(): PreviewThemeMode {
    return when (this) {
        ViewComposePreviewTheme.Light -> PreviewThemeMode.Light
        ViewComposePreviewTheme.Dark -> PreviewThemeMode.Dark
    }
}
