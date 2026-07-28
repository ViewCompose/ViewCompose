package com.viewcompose.renderer.interop

import android.graphics.Typeface
import com.viewcompose.ui.node.PlatformRemoteImageTarget
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.spec.PlatformUiFontFamily
import com.viewcompose.ui.node.spec.UiFontFamily

/**
 * Android 平台渲染容器句柄。
 * Android platform render container handle.
 */
internal data class AndroidRenderContainerHandle(
    override val container: Any,
) : PlatformRenderContainerHandle

/**
 * Android 远程图片加载目标。
 * Android remote image loading target.
 */
internal data class AndroidRemoteImageTarget(
    override val target: Any,
) : PlatformRemoteImageTarget

/**
 * 将 renderer 内部对象转换为跨模块平台容器句柄。
 * Converts a renderer object into a cross-module platform container handle.
 */
internal fun Any.asRenderContainerHandle(): PlatformRenderContainerHandle {
    return AndroidRenderContainerHandle(this)
}

/**
 * 将 ImageView 包装为远程图片加载目标。
 * Wraps an ImageView as a remote image loading target.
 */
internal fun Any.asRemoteImageTarget(): PlatformRemoteImageTarget {
    return AndroidRemoteImageTarget(this)
}

/**
 * 将声明式字体族转换为 Android Typeface。
 * Converts declarative font family to Android Typeface.
 */
internal fun UiFontFamily?.toTypefaceOrNull(): Typeface? {
    return (this as? PlatformUiFontFamily)?.font as? Typeface
}
