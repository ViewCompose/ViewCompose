package com.viewcompose.image.glide.samples

import android.widget.ImageView
import com.viewcompose.image.glide.GlideImageLoaderAdapter
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageRequest

/** Demonstrates a Glide request and explicit renderer-owned handle disposal. */
fun glideImageLoaderAdapterSample(imageView: ImageView) {
    val handle = GlideImageLoaderAdapter().load(
        target = object : PlatformUiImageTarget {
            override val target: Any = imageView
        },
        request = UiImageRequest(
            source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
        ),
    )
    handle.dispose()
}
