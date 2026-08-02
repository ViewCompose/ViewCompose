package com.viewcompose.ui.node

/**
 * Loads remote image content into a renderer-owned [RemoteImageTarget].
 *
 * The renderer calls [load] synchronously on its UI thread when binding a non-blank remote URL.
 * Implementations may complete asynchronously, but must associate work with the supplied target so a recycled
 * or rebound target cannot display stale content. Cancellation, caching, networking, and lifecycle
 * handling belong to the implementation; this contract returns no request handle.
 *
 * @sample com.viewcompose.ui.samples.remoteImageLoaderSample
 */
fun interface RemoteImageLoader {
    /**
     * Starts or replaces loading for [target].
     *
     * @param target opaque renderer target valid for the current binding lifecycle
     * @param request normalized URL and optional Android resource fallbacks
     */
    fun load(
        target: RemoteImageTarget,
        request: RemoteImageRequest,
    )
}

/** Opaque platform-neutral target passed to [RemoteImageLoader]. */
interface RemoteImageTarget

/** Exposes a platform target object to a platform-specific image-loader integration. */
interface PlatformRemoteImageTarget : RemoteImageTarget {
    /** Native target owned by the renderer, such as an Android `ImageView`. */
    val target: Any
}
