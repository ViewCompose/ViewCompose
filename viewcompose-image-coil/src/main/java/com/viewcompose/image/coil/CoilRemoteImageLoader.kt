package com.viewcompose.image.coil

import android.content.Context
import android.widget.ImageView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.target
import com.viewcompose.ui.node.RemoteImageLoader
import com.viewcompose.ui.node.RemoteImageRequest
import com.viewcompose.ui.node.RemoteImageTarget
import com.viewcompose.ui.node.PlatformRemoteImageTarget

/**
 * Adapts ViewCompose remote-image requests to Coil 3 Android [ImageView] targets.
 *
 * Each accepted [load] call enqueues a Coil [ImageRequest] whose data is the request URL and whose
 * placeholder, error, and fallback drawables are the optional Android resource IDs carried by the
 * ViewCompose contract. Coil owns request replacement, cancellation, networking, and memory/disk
 * caching for the target `ImageView`; this adapter does not retain a request handle.
 *
 * Targets that do not expose an Android `ImageView` are ignored. A caller-supplied [ImageLoader]
 * remains caller-owned and is never shut down by this adapter. Prefer sharing an application-level
 * loader when cache reuse and centralized lifecycle management are required.
 *
 * @sample com.viewcompose.image.coil.samples.coilRemoteImageLoaderSample
 * @param imageLoader Coil loader used for every accepted request
 */
class CoilRemoteImageLoader(
    private val imageLoader: ImageLoader,
) : RemoteImageLoader {
    /**
     * Creates an adapter with a dedicated Coil loader built from [context].
     *
     * Coil normalizes the supplied context while building the loader. Use the primary constructor
     * with an application-scoped loader when several screens should share configuration and caches.
     *
     * @param context Android context used to build the Coil loader
     */
    constructor(context: Context) : this(
        imageLoader = ImageLoader.Builder(context).build(),
    )

    /**
     * Enqueues or replaces the Coil request associated with [target].
     *
     * The call returns after enqueueing; success and failure are rendered by Coil directly into the
     * target `ImageView`. A non-platform target, or a platform target whose native object is not an
     * `ImageView`, is ignored without invoking Coil. Resource-ID validity and URL interpretation
     * follow Coil and Android resource rules.
     *
     * @param target renderer-owned target for the current image binding
     * @param request URL and optional placeholder, error, and fallback resources
     */
    override fun load(
        target: RemoteImageTarget,
        request: RemoteImageRequest,
    ) {
        val imageView = (target as? PlatformRemoteImageTarget)?.target as? ImageView
            ?: return
        imageLoader.enqueue(
            ImageRequest.Builder(imageView.context)
                .data(request.url)
                .apply {
                    request.placeholderResId?.let { placeholder(it) }
                    request.errorResId?.let { error(it) }
                    request.fallbackResId?.let { fallback(it) }
                }
                .target(imageView)
                .build(),
        )
    }
}
