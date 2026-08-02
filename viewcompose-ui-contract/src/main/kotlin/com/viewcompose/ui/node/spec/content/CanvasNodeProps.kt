package com.viewcompose.ui.node.spec

import com.viewcompose.ui.graphics.DrawBlock

/**
 * Immutable renderer properties for a custom canvas node.
 *
 * @property onDraw drawing callback invoked by the renderer for each requested draw pass
 */
data class CanvasNodeProps(
    val onDraw: DrawBlock,
) : NodeSpec
