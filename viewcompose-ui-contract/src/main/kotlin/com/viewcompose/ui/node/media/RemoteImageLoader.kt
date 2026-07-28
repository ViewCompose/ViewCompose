package com.viewcompose.ui.node

/**
 * 业务侧注入的远程图片加载器接口。
 * Remote image loader interface injected by app code.
 */
fun interface RemoteImageLoader {
    fun load(
        target: RemoteImageTarget,
        request: RemoteImageRequest,
    )
}

interface RemoteImageTarget

interface PlatformRemoteImageTarget : RemoteImageTarget {
    val target: Any
}
