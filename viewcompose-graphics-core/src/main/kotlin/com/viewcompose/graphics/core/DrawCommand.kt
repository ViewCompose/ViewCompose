package com.viewcompose.graphics.core

/**
 * 平台中立的绘制命令，作为 DSL/契约层与具体渲染器之间的稳定边界。
 * Platform-neutral draw command used as the stable boundary between DSL/contracts and concrete renderers.
 *
 * 命令对象应保持不可变，渲染器必须按列表顺序执行。
 * Command objects should remain immutable, and renderers must execute them in list order.
 */
sealed interface DrawCommand {
    /**
     * 保存当前画布状态。
     * Saves the current canvas state.
     */
    data object Save : DrawCommand

    /**
     * 恢复最近一次保存的画布状态。
     * Restores the most recently saved canvas state.
     */
    data object Restore : DrawCommand

    /**
     * 保存图层并可选限制边界，常用于透明度、滤镜或混合模式隔离。
     * Saves a layer with optional bounds, typically for alpha, filters, or blend-mode isolation.
     */
    data class SaveLayer(
        val bounds: Rect? = null,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 平移当前画布坐标系。
     * Translates the current canvas coordinate space.
     */
    data class Translate(
        val dx: Float,
        val dy: Float,
    ) : DrawCommand

    /**
     * 以 pivot 为中心缩放当前画布坐标系。
     * Scales the current canvas coordinate space around the pivot.
     */
    data class Scale(
        val sx: Float,
        val sy: Float,
        val pivot: Offset = Offset.Zero,
    ) : DrawCommand

    /**
     * 以 pivot 为中心旋转当前画布坐标系。
     * Rotates the current canvas coordinate space around the pivot.
     */
    data class Rotate(
        val degrees: Float,
        val pivot: Offset = Offset.Zero,
    ) : DrawCommand

    /**
     * 倾斜当前画布坐标系。
     * Skews the current canvas coordinate space.
     */
    data class Skew(
        val kx: Float,
        val ky: Float,
    ) : DrawCommand

    /**
     * 拼接自定义 3x3 变换矩阵。
     * Concatenates a custom 3x3 transform matrix.
     */
    data class Concat(
        val matrix: Matrix3,
    ) : DrawCommand

    /**
     * 以矩形裁剪后续绘制内容。
     * Clips following draw content to a rectangle.
     */
    data class ClipRect(
        val rect: Rect,
    ) : DrawCommand

    /**
     * 以路径裁剪后续绘制内容。
     * Clips following draw content to a path.
     */
    data class ClipPath(
        val path: PathModel,
    ) : DrawCommand

    /**
     * 绘制嵌套场景，允许以独立 transform/clip 组合复用命令列表。
     * Draws a nested scene so command lists can be reused with an independent transform/clip.
     */
    data class DrawScene(
        val scene: com.viewcompose.graphics.core.DrawScene,
        val transform: Matrix3 = Matrix3.identity(),
        val clip: Rect? = null,
    ) : DrawCommand

    /**
     * 绘制直线，默认使用描边画笔。
     * Draws a line, using a stroke paint by default.
     */
    data class DrawLine(
        val from: Offset,
        val to: Offset,
        val paint: DrawPaint = DrawPaint(style = DrawStyle.Stroke()),
    ) : DrawCommand

    /**
     * 绘制矩形。
     * Draws a rectangle.
     */
    data class DrawRect(
        val rect: Rect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 绘制圆角矩形。
     * Draws a rounded rectangle.
     */
    data class DrawRoundRect(
        val roundRect: RoundRect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 绘制圆形。
     * Draws a circle.
     */
    data class DrawCircle(
        val center: Offset,
        val radius: Float,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 在矩形边界内绘制椭圆。
     * Draws an oval inside the rectangle bounds.
     */
    data class DrawOval(
        val rect: Rect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 绘制弧线，可选择是否连接到圆心形成扇形。
     * Draws an arc and can connect it to the center to form a wedge.
     */
    data class DrawArc(
        val oval: Rect,
        val startAngleDegrees: Float,
        val sweepAngleDegrees: Float,
        val useCenter: Boolean,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 绘制路径模型。
     * Draws a path model.
     */
    data class DrawPath(
        val path: PathModel,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 绘制图像，可选指定源矩形和目标矩形。
     * Draws an image with optional source and destination rectangles.
     */
    data class DrawImage(
        val image: ImageRef,
        val src: Rect? = null,
        val dst: Rect? = null,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * 在指定基线原点绘制文本。
     * Draws text at the specified baseline origin.
     */
    data class DrawText(
        val text: String,
        val origin: Offset,
        val style: TextStyle = TextStyle(),
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand
}

/**
 * 不可变绘制场景，是一组已经校验 save/restore 平衡的命令快照。
 * Immutable draw scene containing a validated snapshot of commands with balanced save/restore pairs.
 */
class DrawScene internal constructor(
    commands: List<DrawCommand>,
) {
    val commands: List<DrawCommand> = commands.toList()

    init {
        validateSaveRestoreBalance(this.commands)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is DrawScene &&
                commands == other.commands
            )
    }

    override fun hashCode(): Int = commands.hashCode()

    override fun toString(): String = "DrawScene(commands=$commands)"

    private fun validateSaveRestoreBalance(commands: List<DrawCommand>) {
        var saveDepth = 0
        commands.forEachIndexed { index, command ->
            when (command) {
                DrawCommand.Save,
                is DrawCommand.SaveLayer,
                -> saveDepth += 1

                DrawCommand.Restore -> {
                    require(saveDepth > 0) {
                        "DrawScene contains an unmatched Restore at command index $index."
                    }
                    saveDepth -= 1
                }

                else -> Unit
            }
        }
        require(saveDepth == 0) {
            "DrawScene contains $saveDepth unmatched Save or SaveLayer command(s)."
        }
    }
}

/**
 * 图像引用的轻量描述，stableId 用于宿主侧缓存或资源定位。
 * Lightweight image reference whose stableId is used by hosts for caching or resource lookup.
 */
data class ImageRef(
    val stableId: Any,
    val width: Int,
    val height: Int,
)

/**
 * 文本绘制样式的最小跨平台模型。
 * Minimal cross-platform model for text drawing style.
 */
data class TextStyle(
    val textSizePx: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
)
