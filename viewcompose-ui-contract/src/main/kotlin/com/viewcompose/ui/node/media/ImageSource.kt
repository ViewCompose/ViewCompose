package com.viewcompose.ui.node

/**
 * 图片来源，renderer 根据类型选择资源或远程加载路径。
 * Image source used by the renderer to choose resource or remote loading paths.
 */
sealed interface ImageSource {
    data class Resource(
        val resId: Int,
    ) : ImageSource

    data class Remote(
        val url: String?,
    ) : ImageSource
}
