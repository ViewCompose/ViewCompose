package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.viewcompose.graphics.core.DrawCache
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawRecorder
import com.viewcompose.graphics.core.Size
import com.viewcompose.renderer.view.graphics.AndroidDrawCommandExecutor
import com.viewcompose.ui.graphics.DrawBlock
import com.viewcompose.ui.graphics.DrawCacheScope
import com.viewcompose.ui.graphics.DrawContentScope
import com.viewcompose.ui.graphics.DrawContext
import com.viewcompose.ui.modifier.DrawBehindModifierElement
import com.viewcompose.ui.modifier.DrawWithCacheModifierElement
import com.viewcompose.ui.modifier.DrawWithContentModifierElement
import com.viewcompose.ui.modifier.ModifierElement

/**
 * Canvas 节点和 draw modifier 使用的绘制容器。
 * Drawing container used by Canvas nodes and draw modifiers.
 */
internal class DeclarativeCanvasLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var canvasDrawBlock: DrawBlock? = null
    private var drawModifierElements: List<ModifierElement> = emptyList()
    private val drawCaches = mutableMapOf<Int, DrawCache<List<DrawCommand>>>()
    private val recorder = DrawRecorder()

    fun setCanvasDrawBlock(block: DrawBlock) {
        if (canvasDrawBlock === block) return
        canvasDrawBlock = block
        invalidate()
    }

    fun setDrawModifierElements(elements: List<ModifierElement>) {
        if (drawModifierElements == elements) return
        drawModifierElements = elements
        drawCaches.clear()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(suggestedMinimumHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val context = DrawContext(
            size = Size(
                width = width.toFloat(),
                height = height.toFloat(),
            ),
            density = resources.displayMetrics.density,
        )
        val contentDraw: () -> Unit = {
            canvasDrawBlock?.let { block ->
                recorder.clear()
                block(recorder, context)
                AndroidDrawCommandExecutor.execute(
                    canvas = canvas,
                    commands = recorder.toCommands(),
                )
            }
        }
        val drawPipeline = drawModifierElements.withIndex().fold(contentDraw) { downstream, indexed ->
            when (val modifier = indexed.value) {
                is DrawBehindModifierElement -> {
                    {
                        recorder.clear()
                        modifier.onDraw(recorder, context)
                        AndroidDrawCommandExecutor.execute(
                            canvas = canvas,
                            commands = recorder.toCommands(),
                        )
                        downstream()
                    }
                }

                is DrawWithCacheModifierElement -> {
                    {
                        val cache = drawCaches.getOrPut(indexed.index) {
                            DrawCache()
                        }
                        val drawCommands = modifier.onBuildDrawCache(
                            DrawCacheScope(cache),
                            context,
                        )
                        AndroidDrawCommandExecutor.execute(
                            canvas = canvas,
                            commands = drawCommands,
                        )
                        downstream()
                    }
                }

                is DrawWithContentModifierElement -> {
                    {
                        modifier.onDraw(
                            DrawContentScope(downstream),
                            context,
                        )
                    }
                }

                else -> downstream
            }
        }
        drawPipeline()
    }
}
