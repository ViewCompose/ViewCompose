package com.viewcompose.ui.modifier

import com.viewcompose.ui.graphics.DrawBlock
import com.viewcompose.ui.graphics.DrawCacheBuildBlock
import com.viewcompose.ui.graphics.DrawContentBlock

/**
 * 在内容背后注册绘制回调。
 * Registers a draw callback behind content.
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

fun Modifier.visibility(visibility: Visibility): Modifier {
    return then(
        VisibilityModifierElement(visibility),
    )
}
