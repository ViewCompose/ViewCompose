package com.viewcompose.ui.node

/**
 * 远程图片加载请求的 renderer 中立描述。
 * Renderer-neutral description of a remote image request.
 */
data class RemoteImageRequest(
    val url: String,
    val placeholderResId: Int? = null,
    val errorResId: Int? = null,
    val fallbackResId: Int? = null,
)
