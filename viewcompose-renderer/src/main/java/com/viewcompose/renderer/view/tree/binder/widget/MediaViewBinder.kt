package com.viewcompose.renderer.view.tree

import android.content.res.ColorStateList
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.RemoteImageLoader
import com.viewcompose.ui.node.RemoteImageRequest
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.renderer.interop.asRemoteImageTarget

/**
 * 绑定图片与图标按钮节点，负责图片来源、缩放模式、色彩滤镜和点击语义的 Android 映射。
 * Binds image and icon-button nodes, mapping image sources, scale types, color filters, and click
 * semantics to Android widgets.
 */
internal object MediaViewBinder {
    data class ImageSpec(
        val contentDescription: String?,
        val scaleType: ImageView.ScaleType,
        val tint: Int?,
        val source: ImageSource?,
        val placeholder: ImageSource.Resource?,
        val error: ImageSource.Resource?,
        val fallback: ImageSource.Resource?,
        val remoteImageLoader: RemoteImageLoader?,
    )

    fun bindImage(
        view: ImageView,
        spec: ImageSpec,
    ) {
        view.contentDescription = spec.contentDescription
        view.scaleType = spec.scaleType
        view.imageTintList = spec.tint?.let(ColorStateList::valueOf)

        when (val source = spec.source) {
            is ImageSource.Resource -> {
                view.setImageResource(source.resId)
            }
            is ImageSource.Remote -> {
                val normalizedUrl = source.url?.takeIf { it.isNotBlank() }
                if (normalizedUrl == null) {
                    bindPlaceholder(view, spec.fallback)
                    return
                }
                if (spec.remoteImageLoader == null) {
                    bindPlaceholder(view, spec.error ?: spec.placeholder ?: spec.fallback)
                    return
                }
                bindPlaceholder(view, spec.placeholder)
                spec.remoteImageLoader.load(
                    target = view.asRemoteImageTarget(),
                    request = RemoteImageRequest(
                        url = normalizedUrl,
                        placeholderResId = spec.placeholder?.resId,
                        errorResId = spec.error?.resId,
                        fallbackResId = spec.fallback?.resId,
                    ),
                )
            }
            null -> {
                view.setImageDrawable(null)
            }
        }
    }

    fun readImageSpec(node: VNode): ImageSpec {
        val spec = node.requireSpec<ImageNodeSpec>()
        return ImageSpec(
            contentDescription = spec.contentDescription,
            scaleType = spec.contentScale.toScaleType(),
            tint = spec.tint,
            source = spec.source,
            placeholder = spec.placeholder,
            error = spec.error,
            fallback = spec.fallback,
            remoteImageLoader = spec.remoteImageLoader,
        )
    }

    fun bindIconButton(
        view: ImageButton,
        enabled: Boolean,
    ) {
        view.isEnabled = enabled
        view.scaleType = ImageView.ScaleType.CENTER_INSIDE
        view.adjustViewBounds = false
    }

    fun readIconButtonEnabled(node: VNode): Boolean {
        return node.requireSpec<IconButtonNodeProps>().enabled
    }

    private fun bindPlaceholder(
        view: ImageView,
        source: ImageSource.Resource?,
    ) {
        if (source == null) {
            view.setImageDrawable(null)
            return
        }
        view.setImageDrawable(
            ContextCompat.getDrawable(view.context, source.resId),
        )
    }

    internal fun ImageContentScale.toScaleType(): ImageView.ScaleType {
        return when (this) {
            ImageContentScale.Fit -> ImageView.ScaleType.FIT_CENTER
            ImageContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
            ImageContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
            ImageContentScale.Inside -> ImageView.ScaleType.CENTER_INSIDE
        }
    }
}
