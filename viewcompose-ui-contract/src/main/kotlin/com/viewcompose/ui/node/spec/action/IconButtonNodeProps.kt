package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.RemoteImageLoader
import com.viewcompose.ui.shape.UiShape

/**
 * IconButton 节点传给 renderer 的完整属性快照。
 * Complete property snapshot passed to the renderer for an IconButton node.
 */
data class IconButtonNodeProps(
    override val contentDescription: String?,
    override val contentScale: ImageContentScale,
    override val tint: Int?,
    override val source: ImageSource?,
    override val placeholder: ImageSource.Resource?,
    override val error: ImageSource.Resource?,
    override val fallback: ImageSource.Resource?,
    override val remoteImageLoader: RemoteImageLoader?,
    val enabled: Boolean,
    val backgroundColor: Int,
    val borderWidth: Int,
    val borderColor: Int,
    val shape: UiShape,
    val rippleColor: Int,
    val contentPadding: Int,
) : ImageNodeSpec
