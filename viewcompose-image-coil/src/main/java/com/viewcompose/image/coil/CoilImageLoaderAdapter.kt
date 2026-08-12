package com.viewcompose.image.coil

import android.net.Uri
import android.widget.ImageView
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.target
import coil3.size.Scale
import coil3.size.SizeResolver
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageTransition
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapts general ViewCompose image requests to Coil 3 Android [ImageView] targets.
 *
 * Resource, URL, URI, file, and custom model sources are passed to Coil as request data. Common
 * cache, decode-size, transition, and content-scale options are normalized into one Coil request;
 * exact tint and clipping remain renderer responsibilities. The supplied [ImageLoader] remains
 * caller-owned and is never shut down by request disposal.
 *
 * @sample com.viewcompose.image.coil.samples.coilImageLoaderAdapterSample
 * @param imageLoader Coil loader used for every accepted request
 */
class CoilImageLoaderAdapter(
    private val imageLoader: ImageLoader,
) : UiImageLoader {
    /**
     * Enqueues a Coil request and returns a handle that disposes that exact request.
     *
     * Only [PlatformUiImageTarget] values whose native target is an [ImageView] are accepted. A
     * caller-supplied Coil loader remains active after the handle is disposed.
     *
     * @param target renderer-owned target for the current image binding
     * @param request normalized general image request
     * @return idempotent handle for Coil's returned disposable
     * @throws IllegalArgumentException when [target] does not expose an [ImageView]
     */
    override fun load(
        target: com.viewcompose.ui.node.UiImageTarget,
        request: UiImageRequest,
    ): UiImageLoadHandle {
        val imageView = (target as? PlatformUiImageTarget)?.target as? ImageView
            ?: throw IllegalArgumentException(
                "CoilImageLoaderAdapter requires PlatformUiImageTarget<ImageView>.",
            )
        val disposable = imageLoader.enqueue(buildRequest(imageView, request))
        return DisposableHandle(disposable)
    }

    /** Builds a deterministic Coil request without starting work. */
    internal fun buildRequest(
        imageView: ImageView,
        request: UiImageRequest,
    ): ImageRequest {
        return ImageRequest.Builder(imageView.context)
            .data(request.source.toCoilData())
            .target(imageView)
            .apply {
                resourceCacheIdentity(request)?.let(::memoryCacheKey)
                request.placeholder?.let { placeholder(it.resId) }
                request.error?.let { error(it.resId) }
                val decodeSize = request.options.decodeSize
                when (decodeSize) {
                    com.viewcompose.ui.node.UiImageDecodeSize.Target -> Unit
                    com.viewcompose.ui.node.UiImageDecodeSize.Original -> size(SizeResolver.ORIGINAL)
                    is com.viewcompose.ui.node.UiImageDecodeSize.Fixed -> {
                        size(
                            width = request.density.roundToPx(decodeSize.width).coerceAtLeast(1),
                            height = request.density.roundToPx(decodeSize.height).coerceAtLeast(1),
                        )
                    }
                }
                if (request.options.memoryCachePolicy == com.viewcompose.ui.node.UiImageCachePolicy.Disabled) {
                    memoryCachePolicy(CachePolicy.DISABLED)
                }
                if (request.options.diskCachePolicy == com.viewcompose.ui.node.UiImageCachePolicy.Disabled) {
                    diskCachePolicy(CachePolicy.DISABLED)
                }
                when (val transition = request.options.transition) {
                    UiImageTransition.Default -> Unit
                    UiImageTransition.None -> crossfade(false)
                    is UiImageTransition.Crossfade -> crossfade(transition.durationMillis)
                }
                scale(request.contentScale.toCoilScale())
            }
            .build()
    }

    private fun ImageSource.toCoilData(): Any {
        return when (this) {
            is ImageSource.Resource -> resId
            is ImageSource.Url -> url
            is ImageSource.Uri -> Uri.parse(uri)
            is ImageSource.File -> file
            is ImageSource.Model -> value
        }
    }

    internal fun resourceCacheIdentity(request: UiImageRequest): String? {
        val source = request.source as? ImageSource.Resource ?: return null
        return "viewcompose-resource:${source.resId}:${request.resourceRevision}"
    }

    private fun ImageContentScale.toCoilScale(): Scale {
        return when (this) {
            ImageContentScale.Fit,
            ImageContentScale.Inside,
            -> Scale.FIT
            ImageContentScale.Crop,
            ImageContentScale.FillBounds,
            -> Scale.FILL
        }
    }

    private class DisposableHandle(
        private val disposable: Disposable,
    ) : UiImageLoadHandle {
        private val disposed = AtomicBoolean(false)

        override fun dispose() {
            if (disposed.compareAndSet(false, true)) {
                disposable.dispose()
            }
        }
    }
}
