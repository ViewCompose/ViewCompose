package com.viewcompose.widget.core

import com.viewcompose.ui.node.RemoteImageLoader

private val LocalRemoteImageLoader = uiLocalOf<RemoteImageLoader?> { null }

/** Exposes the remote image loader installed for the current composition scope. */
object ImageLoading {
    /** Current loader, or `null` when remote image loading is not configured. */
    val current: RemoteImageLoader?
        get() = UiLocals.current(LocalRemoteImageLoader)
}

/** Provides [loader] to image components built inside [content]. */
fun UiTreeBuilder.ProvideRemoteImageLoader(
    loader: RemoteImageLoader?,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalRemoteImageLoader, loader) {
        content()
    }
}
