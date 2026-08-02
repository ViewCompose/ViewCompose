package com.viewcompose.ui.modifier

import com.viewcompose.ui.graphics.DrawBlock
import com.viewcompose.ui.graphics.DrawCacheBuildBlock
import com.viewcompose.ui.graphics.DrawContentBlock

/**
 * Appends a draw callback that records commands behind wrapped content.
 *
 * The callback runs during the renderer's draw phase in modifier-chain order. On Android this is
 * the main thread. Changing [key] gives the element a new semantic identity for reconciliation.
 *
 * @receiver modifier chain to extend
 * @param key semantic identity associated with this draw element
 * @param onDraw command-recording callback for each draw pass
 * @return a new modifier chain
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
 * Appends a draw callback that decides when or whether wrapped content is drawn.
 *
 * The callback must invoke `drawContent()` to include downstream content. It may draw before and
 * after that invocation to form a layered pipeline.
 *
 * @receiver modifier chain to extend
 * @param key semantic identity associated with this draw element
 * @param onDraw content-aware callback for each draw pass
 * @return a new modifier chain
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
 * Appends a cache-aware draw-command builder.
 *
 * The callback runs in the draw phase and can use its [com.viewcompose.ui.graphics.DrawCacheScope]
 * to retain command lists by a caller-provided cache key. The modifier [key] identifies the element;
 * it is not automatically the cache-scope key.
 *
 * @receiver modifier chain to extend
 * @param key semantic identity associated with this draw element
 * @param onBuildDrawCache callback that returns commands for the current draw context
 * @return a new modifier chain
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

/**
 * Appends a draw-behind callback as a concise alias for [drawBehind].
 *
 * @receiver modifier chain to extend
 * @param key semantic identity associated with this draw element
 * @param onDraw command-recording callback for each draw pass
 * @return a new modifier chain
 */
fun Modifier.draw(
    key: Any = Unit,
    onDraw: DrawBlock,
): Modifier {
    return drawBehind(
        key = key,
        onDraw = onDraw,
    )
}

/**
 * Appends a cache-aware draw callback as a concise alias for [drawWithCache].
 *
 * @receiver modifier chain to extend
 * @param key semantic identity associated with this draw element
 * @param onBuildDrawCache callback that returns commands for the current draw context
 * @return a new modifier chain
 */
fun Modifier.drawCache(
    key: Any = Unit,
    onBuildDrawCache: DrawCacheBuildBlock,
): Modifier {
    return drawWithCache(
        key = key,
        onBuildDrawCache = onBuildDrawCache,
    )
}

/**
 * Appends native visibility behavior for the modified node.
 *
 * Later visibility elements override earlier ones during renderer resolution.
 *
 * @receiver modifier chain to extend
 * @param visibility desired drawing and layout visibility
 * @return a new modifier chain
 */
fun Modifier.visibility(visibility: Visibility): Modifier {
    return then(
        VisibilityModifierElement(visibility),
    )
}
