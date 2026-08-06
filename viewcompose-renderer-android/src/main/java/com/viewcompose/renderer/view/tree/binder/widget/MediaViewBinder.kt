package com.viewcompose.renderer.view.tree

import android.content.res.ColorStateList
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.unit.UiDensity

/** Binds image and icon-button nodes to Android targets and manages loader request replacement. */
internal object MediaViewBinder {
    data class ImageSpec(
        val contentDescription: String?,
        val contentScale: ImageContentScale,
        val scaleType: ImageView.ScaleType,
        val tint: Int?,
        val source: ImageSource?,
        val placeholder: ImageSource.Resource?,
        val error: ImageSource.Resource?,
        val fallback: ImageSource.Resource?,
        val imageLoader: UiImageLoader?,
        val requestOptions: com.viewcompose.ui.node.UiImageRequestOptions,
        val density: UiDensity,
    )

    fun bindImage(
        view: ImageView,
        spec: ImageSpec,
    ) {
        view.contentDescription = spec.contentDescription
        view.scaleType = spec.scaleType
        // Layout hosts allow shadow overflow, so ImageView must constrain scaled drawables itself.
        view.cropToPadding = true
        view.imageTintList = spec.tint?.let(ColorStateList::valueOf)

        when (val source = spec.source) {
            null -> {
                ImageRequestBindingController.clear(view)
                bindPlaceholder(view, spec.fallback)
            }
            else -> {
                val loader = spec.imageLoader
                if (loader != null) {
                    ImageRequestBindingController.replace(
                        view = view,
                        loader = loader,
                        request = UiImageRequest(
                            source = source,
                            placeholder = spec.placeholder,
                            error = spec.error,
                            options = spec.requestOptions,
                            contentScale = spec.contentScale,
                            density = spec.density,
                        ),
                        beforeStart = {
                            bindPlaceholder(view, spec.placeholder)
                        },
                    )
                } else {
                    ImageRequestBindingController.clear(view)
                    if (source is ImageSource.Resource) {
                        view.setImageResource(source.resId)
                    } else {
                        bindPlaceholder(view, spec.error ?: spec.placeholder ?: spec.fallback)
                    }
                }
            }
        }
    }

    fun readImageSpec(node: VNode): ImageSpec {
        val spec = node.requireSpec<ImageNodeSpec>()
        return ImageSpec(
            contentDescription = spec.contentDescription,
            contentScale = spec.contentScale,
            scaleType = spec.contentScale.toScaleType(),
            tint = spec.tint,
            source = spec.source,
            placeholder = spec.placeholder,
            error = spec.error,
            fallback = spec.fallback,
            imageLoader = spec.imageLoader,
            requestOptions = spec.requestOptions,
            density = node.environment.density,
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
