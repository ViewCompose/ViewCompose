package com.viewcompose.image.glide

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.GenericTransitionOptions
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target as GlideTarget
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageTransition

/**
 * Adapts general ViewCompose image requests to Glide 5 Android [ImageView] targets.
 *
 * Resource, URL, URI, file, and custom model sources are passed to Glide as request models. Common
 * cache, decode-size, transition, and content-scale options map to Glide request options; exact
 * tint and clipping remain renderer responsibilities. The application Glide configuration remains
 * caller-owned through Glide's normal singleton and `AppGlideModule` mechanisms.
 *
 * @sample com.viewcompose.image.glide.samples.glideImageLoaderAdapterSample
 */
class GlideImageLoaderAdapter : UiImageLoader {
    /**
     * Starts one Glide request for an Android [ImageView].
     *
     * @param target renderer-owned target whose native value must be an [ImageView]
     * @param request normalized general image request
     * @return idempotent handle that clears the exact target request
     * @throws IllegalArgumentException when [target] does not expose an [ImageView]
     */
    override fun load(
        target: com.viewcompose.ui.node.UiImageTarget,
        request: UiImageRequest,
    ): UiImageLoadHandle {
        val imageView = (target as? PlatformUiImageTarget)?.target as? ImageView
            ?: throw IllegalArgumentException(
                "GlideImageLoaderAdapter requires PlatformUiImageTarget<ImageView>.",
            )
        val requestManager = Glide.with(imageView)
        val requestTarget = buildRequest(requestManager, imageView, request).into(imageView)
        return GlideLoadHandle(requestManager, requestTarget)
    }

    internal fun buildRequest(
        requestManager: RequestManager,
        imageView: ImageView,
        request: UiImageRequest,
    ): RequestBuilder<Drawable> {
        val builder = requestManager
            .load(request.source.toGlideModel())
            .apply(
                RequestOptions()
                    .applyCommonOptions(request)
                    .applyFallbacks(request),
            )
        return when (val transition = request.options.transition) {
            UiImageTransition.Default -> builder
            UiImageTransition.None -> builder.transition(
                GenericTransitionOptions.withNoTransition<Drawable>(),
            )
            is UiImageTransition.Crossfade -> builder.transition(
                DrawableTransitionOptions.withCrossFade(transition.durationMillis),
            )
        }
    }

    internal fun mapSourceForTest(source: ImageSource): Any = source.toGlideModel()

    private fun RequestOptions.applyCommonOptions(request: UiImageRequest): RequestOptions {
        resourceCacheIdentity(request)?.let { identity -> signature(ObjectKey(identity)) }
        when (val decodeSize = request.options.decodeSize) {
            com.viewcompose.ui.node.UiImageDecodeSize.Target -> Unit
            com.viewcompose.ui.node.UiImageDecodeSize.Original -> {
                override(GlideTarget.SIZE_ORIGINAL)
            }
            is com.viewcompose.ui.node.UiImageDecodeSize.Fixed -> {
                override(
                    request.density.roundToPx(decodeSize.width).coerceAtLeast(1),
                    request.density.roundToPx(decodeSize.height).coerceAtLeast(1),
                )
            }
        }
        if (request.options.memoryCachePolicy == com.viewcompose.ui.node.UiImageCachePolicy.Disabled) {
            skipMemoryCache(true)
        }
        if (request.options.diskCachePolicy == com.viewcompose.ui.node.UiImageCachePolicy.Disabled) {
            diskCacheStrategy(DiskCacheStrategy.NONE)
        }
        when (request.contentScale) {
            ImageContentScale.Crop -> centerCrop()
            ImageContentScale.Fit -> fitCenter()
            ImageContentScale.Inside -> centerInside()
            ImageContentScale.FillBounds -> dontTransform()
        }
        return this
    }

    private fun RequestOptions.applyFallbacks(request: UiImageRequest): RequestOptions {
        request.placeholder?.let { placeholder(it.resId) }
        request.error?.let { error(it.resId) }
        return this
    }

    private fun ImageSource.toGlideModel(): Any {
        return when (this) {
            is ImageSource.Resource -> resId
            is ImageSource.Url -> url
            is ImageSource.Uri -> android.net.Uri.parse(uri)
            is ImageSource.File -> file
            is ImageSource.Model -> value
        }
    }

    internal fun resourceCacheIdentity(request: UiImageRequest): String? {
        val source = request.source as? ImageSource.Resource ?: return null
        return "viewcompose-resource:${source.resId}:${request.resourceRevision}"
    }

    private class GlideLoadHandle(
        private val requestManager: RequestManager,
        private val requestTarget: GlideTarget<Drawable>,
    ) : UiImageLoadHandle {
        private var disposed = false

        override fun dispose() {
            if (disposed) return
            disposed = true
            requestManager.clear(requestTarget)
        }
    }
}
