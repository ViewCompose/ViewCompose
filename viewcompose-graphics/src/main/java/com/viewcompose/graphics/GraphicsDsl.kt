package com.viewcompose.graphics

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.DrawBehindModifierElement
import com.viewcompose.ui.modifier.DrawWithCacheModifierElement
import com.viewcompose.ui.modifier.DrawWithContentModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.widget.core.UiTreeBuilder

/** Source-level alias for a command recorder invoked with the current draw context. */
typealias DrawBlock = com.viewcompose.ui.graphics.DrawBlock

/** Source-level alias for a draw callback that decides when wrapped content is rendered. */
typealias DrawContentBlock = com.viewcompose.ui.graphics.DrawContentBlock

/** Source-level alias for a cache builder that returns renderer-replayable commands. */
typealias DrawCacheBuildBlock = com.viewcompose.ui.graphics.DrawCacheBuildBlock

/** Source-level alias for physical-pixel draw bounds and density. */
typealias DrawContext = com.viewcompose.ui.graphics.DrawContext

/** Source-level alias for the scope exposing `drawContent()`. */
typealias DrawContentScope = com.viewcompose.ui.graphics.DrawContentScope

/** Source-level alias for the scope exposing renderer-owned command caching. */
typealias DrawCacheScope = com.viewcompose.ui.graphics.DrawCacheScope

/**
 * Emits a node whose visual content is entirely defined by [onDraw].
 *
 * The callback records commands during each renderer draw pass on the UI thread. [DrawContext] `size`
 * uses the node's measured physical-pixel bounds; no intrinsic size is derived from commands, so a
 * layout modifier or parent constraint must provide usable bounds.
 *
 * @param key optional sibling identity used during reconciliation
 * @param modifier layout, semantics, input, and additional draw behavior
 * @param onDraw command-recording callback for each draw pass
 * @sample com.viewcompose.graphics.samples.canvasSample
 */
fun UiTreeBuilder.Canvas(
    key: Any? = null,
    modifier: Modifier = Modifier,
    onDraw: DrawBlock,
) {
    emit(
        type = NodeType.Canvas,
        key = key,
        spec = CanvasNodeProps(
            onDraw = onDraw,
        ),
        modifier = modifier,
    )
}

/**
 * Appends a callback that records commands before wrapped node content is drawn.
 *
 * [key] identifies the modifier element for reconciliation; it is not a draw-cache key. The callback
 * runs on every draw pass and should avoid expensive allocation.
 *
 * @sample com.viewcompose.graphics.samples.drawBehindSample
 */
fun Modifier.drawBehind(
    key: Any = Unit,
    onDraw: DrawBlock,
): Modifier {
    return then(
        DrawBehindModifierElement(
            key = key,
            onDraw = onDraw,
        ),
    )
}

/**
 * Appends a callback that controls when or whether wrapped content is drawn.
 *
 * Invoke `drawContent()` exactly where downstream content should appear. Omitting it suppresses
 * content; commands recorded before and after it create background and foreground layers.
 *
 * @sample com.viewcompose.graphics.samples.drawWithContentSample
 */
fun Modifier.drawWithContent(
    key: Any = Unit,
    onDraw: DrawContentBlock,
): Modifier {
    return then(
        DrawWithContentModifierElement(
            key = key,
            onDraw = onDraw,
        ),
    )
}

/**
 * Appends a cache-aware command builder for expensive path, brush, or scene preparation.
 *
 * [key] identifies the modifier element only. Inside [onBuildDrawCache], use the cache scope's
 * `cache` function with a semantic key containing every size, density, theme, and resource input.
 * The renderer owns a single-entry cache for the mounted element and clears it on disposal.
 *
 * @sample com.viewcompose.graphics.samples.drawWithCacheSample
 */
fun Modifier.drawWithCache(
    key: Any = Unit,
    onBuildDrawCache: DrawCacheBuildBlock,
): Modifier {
    return then(
        DrawWithCacheModifierElement(
            key = key,
            onBuildDrawCache = onBuildDrawCache,
        ),
    )
}

/** Concise source-compatible alias for [drawBehind]. */
fun Modifier.draw(
    key: Any = Unit,
    onDraw: DrawBlock,
): Modifier {
    return drawBehind(
        key = key,
        onDraw = onDraw,
    )
}

/** Concise source-compatible alias for [drawWithCache]. */
fun Modifier.drawCache(
    key: Any = Unit,
    onBuildDrawCache: DrawCacheBuildBlock,
): Modifier {
    return drawWithCache(
        key = key,
        onBuildDrawCache = onBuildDrawCache,
    )
}
