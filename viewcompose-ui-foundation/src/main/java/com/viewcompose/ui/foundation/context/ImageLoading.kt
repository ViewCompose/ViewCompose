package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiImageLoader

private val LocalImageLoader = uiLocalOf<UiImageLoader?> { null }

/** Resolves the image loader captured by image nodes in the current tree-building scope. */
object ImageLoading {
    /**
     * Returns the innermost scoped loader, or `null` when image loading is not configured.
     *
     * The value is read while a node is emitted and copied into its immutable specification;
     * changing a provider therefore affects the next emitted/rendered tree rather than mutating an
     * already-built node.
     */
    val current: UiImageLoader?
        get() = UiLocals.current(LocalImageLoader)
}

/**
 * Provides an image loader to image components emitted by [content].
 *
 * Providers nest lexically. Passing `null` intentionally shadows an outer loader so a subtree uses
 * direct resource rendering only. The caller owns [loader]; leaving this scope neither disposes nor
 * shuts it down, while the renderer continues to own each per-target load handle.
 *
 * @sample com.viewcompose.ui.foundation.samples.imageLoadingSample
 * @receiver active tree builder used to execute [content]
 * @param loader loader captured by descendant image nodes, or `null` to disable inherited loading
 * @param content tree-building block executed synchronously with [loader] installed
 */
fun UiTreeBuilder.ProvideImageLoader(
    loader: UiImageLoader?,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalImageLoader, loader) {
        content()
    }
}
