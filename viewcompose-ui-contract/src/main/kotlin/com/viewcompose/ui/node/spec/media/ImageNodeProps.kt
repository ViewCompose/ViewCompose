package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.RemoteImageLoader

/**
 * Immutable renderer properties for an image or icon node.
 *
 * @property contentDescription semantic description of the image, or `null` when decorative
 * @property contentScale scaling policy inside the available image bounds
 * @property tint optional color filter; `null` preserves source colors
 * @property source image source, or `null` for no image
 * @property placeholder resource shown while a remote request is pending
 * @property error resource shown after a remote request fails
 * @property fallback resource used when a remote URL is blank
 * @property remoteImageLoader loader used for remote sources
 */
data class ImageNodeProps(
    override val contentDescription: String?,
    override val contentScale: ImageContentScale,
    override val tint: Int?,
    override val source: ImageSource?,
    override val placeholder: ImageSource.Resource?,
    override val error: ImageSource.Resource?,
    override val fallback: ImageSource.Resource?,
    override val remoteImageLoader: RemoteImageLoader?,
) : ImageNodeSpec
