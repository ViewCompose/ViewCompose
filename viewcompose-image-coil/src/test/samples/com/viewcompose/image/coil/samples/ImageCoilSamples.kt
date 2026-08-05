package com.viewcompose.image.coil.samples

import android.widget.ImageView
import coil3.ImageLoader
import com.viewcompose.image.coil.CoilImageLoaderAdapter
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageRequest

/**
 * Demonstrates a general resource/URL request and explicit disposal of the returned handle.
 */
fun coilImageLoaderAdapterSample(
    imageLoader: ImageLoader,
    imageView: ImageView,
) {
    val loader = CoilImageLoaderAdapter(imageLoader)
    val handle = loader.load(
        target = object : PlatformUiImageTarget {
            override val target: Any = imageView
        },
        request = UiImageRequest(
            source = ImageSource.Url("https://example.com/avatar.png"),
            placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
        ),
    )
    handle.dispose()
}
