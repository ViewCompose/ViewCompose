package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.RemoteImageLoader
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for an icon button.
 *
 * Image properties follow [ImageNodeSpec]. When [source] is remote, [remoteImageLoader] owns
 * asynchronous loading and the resource fallbacks are forwarded with the request.
 *
 * @property contentDescription semantic description of the icon, or `null` when decorative
 * @property contentScale scaling policy inside the available content bounds
 * @property tint optional color filter; `null` preserves source colors
 * @property source image source, or `null` for no image
 * @property placeholder resource shown while a remote request is pending
 * @property error resource shown after a remote request fails
 * @property fallback resource used when a remote URL is blank
 * @property remoteImageLoader loader used for remote sources
 * @property enabled whether the button accepts input
 * @property backgroundColor button surface color
 * @property borderWidth button border width
 * @property borderColor button border color
 * @property shape outline used for background, border, clipping, and ripple
 * @property rippleColor pressed-state ripple color
 * @property contentPadding padding applied around the icon on every edge
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
    val borderWidth: UiDp,
    val borderColor: Int,
    val shape: UiShape,
    val rippleColor: Int,
    val contentPadding: UiDp,
) : ImageNodeSpec
