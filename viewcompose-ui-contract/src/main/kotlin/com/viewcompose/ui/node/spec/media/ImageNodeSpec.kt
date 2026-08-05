package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions

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

    /** Android resource shown while a configured loader request is pending. */
    val placeholder: ImageSource.Resource?

    /** Android resource shown after a configured loader request fails. */
    val error: ImageSource.Resource?

    /** Android resource shown when no primary source is present. */
    val fallback: ImageSource.Resource?

    /** Loader used for every non-null source, including [ImageSource.Resource]. */
    val imageLoader: UiImageLoader?

    /** Common decode, cache, transition, and adapter-extension options. */
    val requestOptions: UiImageRequestOptions
}
