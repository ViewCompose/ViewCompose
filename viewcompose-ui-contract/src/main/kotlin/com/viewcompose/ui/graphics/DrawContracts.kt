package com.viewcompose.ui.graphics

import com.viewcompose.graphics.core.DrawCache
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawRecorder
import com.viewcompose.graphics.core.Size

/**
 * 绘制 modifier 执行时传入的画布尺寸上下文。
 * Canvas-size context passed to drawing modifiers.
 */
data class DrawContext(
    val size: Size,
    val density: Float,
)

class DrawContentScope(
    private val drawContentCallback: () -> Unit,
) {
    fun drawContent() {
        drawContentCallback()
    }
}

class DrawCacheScope(
    private val drawCache: DrawCache<List<DrawCommand>>,
) {
    fun cache(
        key: Any?,
        builder: () -> List<DrawCommand>,
    ): List<DrawCommand> {
        return drawCache.getOrBuild(key, builder)
    }
}

typealias DrawBlock = DrawRecorder.(DrawContext) -> Unit
typealias DrawContentBlock = DrawContentScope.(DrawContext) -> Unit
typealias DrawCacheBuildBlock = DrawCacheScope.(DrawContext) -> List<DrawCommand>
