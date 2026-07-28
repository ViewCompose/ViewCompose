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
 * 使用 Coil3 将 ViewCompose 远程图片请求加载到 Android [ImageView]。
 * Uses Coil3 to load ViewCompose remote image requests into Android [ImageView].
 */
class CoilRemoteImageLoader(
    private val imageLoader: ImageLoader,
) : RemoteImageLoader {
    constructor(context: Context) : this(
        imageLoader = ImageLoader.Builder(context).build(),
    )

    override fun load(
        target: RemoteImageTarget,
        request: RemoteImageRequest,
    ) {
        val imageView = (target as? PlatformRemoteImageTarget)?.target as? ImageView
            ?: return
        // 非 Android ImageView target 直接忽略，保持 RemoteImageLoader 平台边界可安全复用。
        // Non-Android ImageView targets are ignored so the RemoteImageLoader boundary remains portable.
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
