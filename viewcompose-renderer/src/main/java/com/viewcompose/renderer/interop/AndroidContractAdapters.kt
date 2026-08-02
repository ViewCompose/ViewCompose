package com.viewcompose.renderer.interop

import android.graphics.Typeface
import com.viewcompose.ui.node.PlatformRemoteImageTarget
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.spec.PlatformUiFontFamily
import com.viewcompose.ui.node.spec.UiFontFamily

/**
 * Android platform render-container handle.
 * Android platform render container handle.
 */
internal data class AndroidRenderContainerHandle(
    override val container: Any,
) : PlatformRenderContainerHandle

/**
 * Android target for remote image loading.
 * Android remote image loading target.
 */
internal data class AndroidRemoteImageTarget(
    override val target: Any,
) : PlatformRemoteImageTarget

/**
 * Wraps a renderer-owned object as a cross-module platform container handle.
 * Converts a renderer object into a cross-module platform container handle.
 */
internal fun Any.asRenderContainerHandle(): PlatformRenderContainerHandle {
    return AndroidRenderContainerHandle(this)
}

/**
 * Wraps an ImageView as a remote image-loading target.
 * Wraps an ImageView as a remote image loading target.
 */
internal fun Any.asRemoteImageTarget(): PlatformRemoteImageTarget {
    return AndroidRemoteImageTarget(this)
}

/**
 * Converts a declarative font family to an Android Typeface.
 * Converts declarative font family to Android Typeface.
 */
internal fun UiFontFamily?.toTypefaceOrNull(): Typeface? {
    return (this as? PlatformUiFontFamily)?.font as? Typeface
}
