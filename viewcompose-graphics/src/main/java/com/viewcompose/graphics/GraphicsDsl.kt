package com.viewcompose.graphics

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.DrawBehindModifierElement
import com.viewcompose.ui.modifier.DrawWithCacheModifierElement
import com.viewcompose.ui.modifier.DrawWithContentModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 绘制节点或 modifier 使用的基础绘制回调。
 * Basic draw callback used by drawing nodes or modifiers.
 */
typealias DrawBlock = com.viewcompose.ui.graphics.DrawBlock
typealias DrawContentBlock = com.viewcompose.ui.graphics.DrawContentBlock
typealias DrawCacheBuildBlock = com.viewcompose.ui.graphics.DrawCacheBuildBlock
typealias DrawContext = com.viewcompose.ui.graphics.DrawContext
typealias DrawContentScope = com.viewcompose.ui.graphics.DrawContentScope
typealias DrawCacheScope = com.viewcompose.ui.graphics.DrawCacheScope

/**
 * 发射一个只负责自定义绘制的 Canvas 节点。
 * Emits a Canvas node dedicated to custom drawing.
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
 * 在内容之后、但视觉上位于内容背后执行绘制。
 * Draws behind the node content.
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
 * 允许绘制逻辑显式决定何时绘制原始内容。
 * Lets draw logic explicitly decide when to draw the original content.
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
 * 构建可复用绘制缓存，适合昂贵 path/brush 计算。
 * Builds reusable drawing cache for expensive path or brush calculations.
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

fun Modifier.draw(
    key: Any = Unit,
    onDraw: DrawBlock,
): Modifier {
    return drawBehind(
        key = key,
        onDraw = onDraw,
    )
}

fun Modifier.drawCache(
    key: Any = Unit,
    onBuildDrawCache: DrawCacheBuildBlock,
): Modifier {
    return drawWithCache(
        key = key,
        onBuildDrawCache = onBuildDrawCache,
    )
}
