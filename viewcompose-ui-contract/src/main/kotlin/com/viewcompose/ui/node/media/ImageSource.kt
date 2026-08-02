package com.viewcompose.ui.node

/**
 * Selects a local Android resource or a remote URL as image content.
 *
 * Consumers should handle this sealed hierarchy exhaustively. Renderers resolve [Resource]
 * directly and delegate a usable [Remote] URL to the configured [RemoteImageLoader].
 */
sealed interface ImageSource {
    /**
     * Loads an Android drawable resource from the rendered View context.
     *
     * @property resId Android drawable resource identifier
     */
    data class Resource(
        val resId: Int,
    ) : ImageSource

    /**
     * Requests content from a remote URL.
     *
     * A `null` or blank URL selects the fallback resource without invoking the remote loader.
     *
     * @property url remote URL, or `null` when no remote data is available
     */
    data class Remote(
        val url: String?,
    ) : ImageSource
}
