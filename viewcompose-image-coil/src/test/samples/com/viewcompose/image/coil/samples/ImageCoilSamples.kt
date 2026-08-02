package com.viewcompose.image.coil.samples

import android.widget.ImageView
import coil3.ImageLoader
import com.viewcompose.image.coil.CoilRemoteImageLoader
import com.viewcompose.ui.node.PlatformRemoteImageTarget
import com.viewcompose.ui.node.RemoteImageRequest

fun coilRemoteImageLoaderSample(
    imageLoader: ImageLoader,
    imageView: ImageView,
) {
    val loader = CoilRemoteImageLoader(imageLoader)
    loader.load(
        target = object : PlatformRemoteImageTarget {
            override val target: Any = imageView
        },
        request = RemoteImageRequest(
            url = "https://example.com/avatar.png",
            placeholderResId = android.R.drawable.ic_menu_gallery,
            errorResId = android.R.drawable.stat_notify_error,
        ),
    )
}
