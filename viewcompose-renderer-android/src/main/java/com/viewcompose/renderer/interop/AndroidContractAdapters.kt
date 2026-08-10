package com.viewcompose.renderer.interop

import android.graphics.Typeface
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.spec.PlatformUiFontFamily
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.ui.tooling.UiSourceSessionContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole

/**
 * Android platform render-container handle.
 * Android platform render container handle.
 */
internal data class AndroidRenderContainerHandle(
    override val container: Any,
    override val sourceSessionRole: UiSourceSessionRole,
) : PlatformRenderContainerHandle,
    UiSourceSessionContainerHandle

/** Android target for the general image-loading contract. */
internal data class AndroidUiImageTarget(
    override val target: Any,
) : PlatformUiImageTarget

/**
 * Wraps a renderer-owned object as a cross-module platform container handle.
 * Converts a renderer object into a cross-module platform container handle.
 */
internal fun Any.asRenderContainerHandle(
    sourceSessionRole: UiSourceSessionRole = UiSourceSessionRole.Content,
): PlatformRenderContainerHandle {
    return AndroidRenderContainerHandle(this, sourceSessionRole)
}

/** Wraps an ImageView as a general image-loading target. */
internal fun Any.asUiImageTarget(): PlatformUiImageTarget {
    return AndroidUiImageTarget(this)
}

/**
 * Converts a declarative font family to an Android Typeface.
 * Converts declarative font family to Android Typeface.
 */
internal fun UiFontFamily?.toTypefaceOrNull(): Typeface? {
    return (this as? PlatformUiFontFamily)?.font as? Typeface
}
