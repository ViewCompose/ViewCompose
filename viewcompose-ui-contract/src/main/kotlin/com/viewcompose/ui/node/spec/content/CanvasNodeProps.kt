package com.viewcompose.ui.node.spec

import com.viewcompose.ui.graphics.DrawBlock

/**
 * Canvas 节点的绘制回调属性。
 * Drawing-callback properties for a Canvas node.
 */
data class CanvasNodeProps(
    val onDraw: DrawBlock,
) : NodeSpec
