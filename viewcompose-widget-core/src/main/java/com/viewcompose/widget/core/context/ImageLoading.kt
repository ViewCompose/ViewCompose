package com.viewcompose.widget.core

import com.viewcompose.ui.node.RemoteImageLoader

private val LocalRemoteImageLoader = uiLocalOf<RemoteImageLoader?> { null }

/**
 * 当前 composition 的远程图片加载器。
 * Remote image loader for the current composition.
 */
object ImageLoading {
    val current: RemoteImageLoader?
        get() = UiLocals.current(LocalRemoteImageLoader)
}

/**
 * 在 content 范围内提供远程图片加载器。
 * Provides a remote image loader within the content scope.
 */
fun UiTreeBuilder.ProvideRemoteImageLoader(
    loader: RemoteImageLoader?,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalRemoteImageLoader, loader) {
        content()
    }
}
