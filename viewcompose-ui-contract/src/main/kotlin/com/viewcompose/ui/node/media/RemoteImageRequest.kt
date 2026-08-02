package com.viewcompose.ui.node

/**
 * Describes one normalized remote-image request without exposing Android or loader-library types.
 *
 * The renderer supplies a non-blank [url]. Resource IDs are Android drawable resources resolved by
 * the loader from the target context.
 *
 * @property url normalized non-blank remote URL
 * @property placeholderResId resource displayed while loading, or `null`
 * @property errorResId resource displayed after a load failure, or `null`
 * @property fallbackResId resource used for absent/null data, or `null`
 */
data class RemoteImageRequest(
    val url: String,
    val placeholderResId: Int? = null,
    val errorResId: Int? = null,
    val fallbackResId: Int? = null,
)
