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
import com.bumptech.glide.signature.AndroidResourceSignature
import com.bumptech.glide.load.Key
import java.security.MessageDigest
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
 * Primary resources use the target theme and Android resource signature plus the host memory
 * scope/revision. Unknown scopes disable resource memory caching. Resource disk caching is disabled
 * because the host identity cannot safely be reused across processes or reconstructed themes.
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
        val source = request.source
        val modelRequest = if (source is ImageSource.Resource) requestManager.load(source.resId)
            else requestManager.load(source.toGlideModel())
        val builder = modelRequest
            .apply(
                RequestOptions()
                    .applyCommonOptions(imageView.context, request)
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

    private fun RequestOptions.applyCommonOptions(context: Context, request: UiImageRequest): RequestOptions {
        if (request.source is ImageSource.Resource) {
            theme(context.theme)
            val identity = resourceCacheIdentity(request)
            if (identity == null) skipMemoryCache(true)
            else signature(ResourceMemoryKey(AndroidResourceSignature.obtain(context), ObjectKey(identity)))
            // A host counter and theme scope have no stable cross-process disk meaning.
            diskCacheStrategy(DiskCacheStrategy.NONE)
        }
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
        val scope = request.resourceCacheScope ?: return null
        return "viewcompose-resource:${scope.length}:$scope:${source.resId}:${request.resourceRevision}"
    }

    private data class ResourceMemoryKey(val platform: Key, val scoped: Key) : Key {
        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            platform.updateDiskCacheKey(messageDigest)
            scoped.updateDiskCacheKey(messageDigest)
        }
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
