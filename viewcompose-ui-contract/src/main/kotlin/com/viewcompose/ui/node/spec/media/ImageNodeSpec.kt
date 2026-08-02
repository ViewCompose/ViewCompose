package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.RemoteImageLoader

/** Shared renderer contract for image and image-backed action nodes. */
interface ImageNodeSpec : NodeSpec {
    /** Semantic description of the image, or `null` when it is decorative. */
    val contentDescription: String?

    /** Scaling policy applied inside the available image bounds. */
    val contentScale: ImageContentScale

    /** Optional color filter; `null` preserves the source colors. */
    val tint: Int?

    /** Image source, or `null` for no image. */
    val source: ImageSource?

    /** Android resource shown while a remote request is pending. */
    val placeholder: ImageSource.Resource?

    /** Android resource shown after a remote request fails. */
    val error: ImageSource.Resource?

    /** Android resource used when a remote URL is blank. */
    val fallback: ImageSource.Resource?

    /** Loader used for [ImageSource.Remote] values. */
    val remoteImageLoader: RemoteImageLoader?
}
