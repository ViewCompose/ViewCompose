package com.viewcompose.ui.graphics

import com.viewcompose.graphics.core.DrawCache
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawRecorder
import com.viewcompose.graphics.core.Size

/**
 * Captures the size and density supplied to a drawing modifier for one draw pass.
 *
 * Coordinates and [size] are expressed in physical pixels. [density] converts logical dp values
 * when a draw block needs device-independent dimensions.
 *
 * @property size current drawable bounds in physical pixels
 * @property density physical-pixels-per-dp scale for the current node
 */
data class DrawContext(
    val size: Size,
    val density: Float,
)

/**
 * Controls when a content-aware drawing modifier renders the wrapped node content.
 *
 * Calling [drawContent] delegates synchronously to the renderer-provided callback. A block may
 * call it before, between, or after its own commands; omitting the call suppresses wrapped content.
 *
 * @param drawContentCallback renderer callback that records or draws wrapped content
 */
class DrawContentScope(
    private val drawContentCallback: () -> Unit,
) {
    /** Draws the wrapped node content at the current position in the draw block. */
    fun drawContent() {
        drawContentCallback()
    }
}

/**
 * Exposes renderer-owned draw-command caching to one cache-building modifier.
 *
 * Cache lifetime and synchronization are owned by the supplied [DrawCache]. This scope does not
 * retain keys or commands independently.
 *
 * @param drawCache cache used to store recorded command lists
 */
class DrawCacheScope(
    private val drawCache: DrawCache<List<DrawCommand>>,
) {
    /**
     * Returns cached commands for [key], invoking [builder] only on a cache miss.
     *
     * Key equality follows the configured [DrawCache]. Exceptions from [builder] propagate and do
     * not produce a value through this scope.
     *
     * @param key semantic cache key, including `null` when supported by the backing cache
     * @param builder command producer invoked on a cache miss
     * @return the cached or newly built command list
     */
    fun cache(
        key: Any?,
        builder: () -> List<DrawCommand>,
    ): List<DrawCommand> {
        return drawCache.getOrBuild(key, builder)
    }
}

/**
 * Source-level alias for a draw command recorder invoked with the current [DrawContext].
 *
 * This alias does not introduce a distinct runtime type.
 */
typealias DrawBlock = DrawRecorder.(DrawContext) -> Unit

/**
 * Source-level alias for a content-aware draw block that controls [DrawContentScope.drawContent].
 *
 * This alias does not introduce a distinct runtime type.
 */
typealias DrawContentBlock = DrawContentScope.(DrawContext) -> Unit

/**
 * Source-level alias for a cache builder that returns renderer-replayable draw commands.
 *
 * This alias does not introduce a distinct runtime type.
 */
typealias DrawCacheBuildBlock = DrawCacheScope.(DrawContext) -> List<DrawCommand>
