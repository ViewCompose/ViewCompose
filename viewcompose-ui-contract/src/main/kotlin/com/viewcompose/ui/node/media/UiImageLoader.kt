package com.viewcompose.ui.node

/**
 * Starts image work for a renderer-owned [UiImageTarget].
 *
 * Implementations are called synchronously on the renderer's UI thread. The returned handle must
 * own every request started by the call before it returns. If loading cannot be started, the
 * implementation must leave no unowned work running and may throw an exception.
 *
 * The loader, its configuration, and any source model remain owned by the caller. A renderer only
 * owns the returned handle and disposes it when the target is replaced or released.
 *
 * @sample com.viewcompose.ui.samples.uiImageLoaderSample
 */
fun interface UiImageLoader {
    /**
     * Starts or replaces work for [target].
     *
     * @param target opaque target valid for the current binding lifecycle
     * @param request normalized source, resource states, scale, and common request options
     * @return handle that cancels or clears work started by this call
     */
    fun load(
        target: UiImageTarget,
        request: UiImageRequest,
    ): UiImageLoadHandle
}

/** Opaque target passed from a renderer to a [UiImageLoader]. */
interface UiImageTarget

/**
 * Exposes a native target to a platform-specific image-loader integration.
 *
 * The native object is owned by the renderer. An adapter may retain it only for the lifetime of
 * the request and must not dispose, recycle, or otherwise take ownership of it.
 */
interface PlatformUiImageTarget : UiImageTarget {
    /** Native target, such as an Android `ImageView`. */
    val target: Any
}

/**
 * Owns one image load started through [UiImageLoader].
 *
 * Disposal is idempotent, safe after asynchronous completion, and confined to the renderer's UI
 * thread. Implementations must cancel or clear the target-associated work started by the matching
 * [UiImageLoader.load] call and must not shut down the caller's loader or source model.
 */
fun interface UiImageLoadHandle {
    /** Cancels or clears this request. Repeated calls must have no additional effect. */
    fun dispose()
}
