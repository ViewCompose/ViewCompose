package com.viewcompose.graphics.core

/**
 * 路径填充规则，决定自交路径或嵌套轮廓的内部区域计算方式。
 * Path fill rule that defines how interiors are computed for self-intersecting or nested contours.
 */
enum class PathFillType {
    NonZero,
    EvenOdd,
}

/**
 * 平台中立的路径命令序列，渲染器按顺序转换为原生 Path。
 * Platform-neutral path command sequence that renderers convert to native Path objects in order.
 */
sealed interface PathCommand {
    /**
     * 移动当前点，不绘制线段。
     * Moves the current point without drawing a segment.
     */
    data class MoveTo(
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * 从当前点绘制直线到目标点。
     * Draws a straight line from the current point to the target point.
     */
    data class LineTo(
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * 二次贝塞尔曲线命令，x1/y1 为控制点，x2/y2 为终点。
     * Quadratic Bezier command where x1/y1 is the control point and x2/y2 is the end point.
     */
    data class QuadTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : PathCommand

    /**
     * 三次贝塞尔曲线命令，包含两个控制点和一个终点。
     * Cubic Bezier command containing two control points and one end point.
     */
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x3: Float,
        val y3: Float,
    ) : PathCommand

    /**
     * 沿椭圆边界绘制弧线，可选择是否强制移动到弧线起点。
     * Draws an arc on the oval bounds and can force a move to the arc start.
     */
    data class ArcTo(
        val oval: Rect,
        val startAngleDegrees: Float,
        val sweepAngleDegrees: Float,
        val forceMoveTo: Boolean,
    ) : PathCommand

    /**
     * 闭合当前轮廓。
     * Closes the current contour.
     */
    data object Close : PathCommand
}

/**
 * 不可变路径模型，保存填充规则和路径命令快照。
 * Immutable path model that stores the fill rule and a snapshot of path commands.
 */
data class PathModel(
    val fillType: PathFillType = PathFillType.NonZero,
    val commands: List<PathCommand> = emptyList(),
)

/**
 * 路径构建器，用链式 API 记录命令并在 build 时复制为不可变模型。
 * Path builder that records commands through a fluent API and copies them into an immutable model on build.
 */
class PathBuilder {
    private val commands = mutableListOf<PathCommand>()
    private var fillType: PathFillType = PathFillType.NonZero

    /**
     * 设置后续构建出的路径填充规则。
     * Sets the fill rule for the path produced by this builder.
     */
    fun fillType(fillType: PathFillType): PathBuilder {
        this.fillType = fillType
        return this
    }

    fun moveTo(
        x: Float,
        y: Float,
    ): PathBuilder {
        commands += PathCommand.MoveTo(x, y)
        return this
    }

    fun lineTo(
        x: Float,
        y: Float,
    ): PathBuilder {
        commands += PathCommand.LineTo(x, y)
        return this
    }

    fun quadTo(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): PathBuilder {
        commands += PathCommand.QuadTo(x1, y1, x2, y2)
        return this
    }

    fun cubicTo(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
    ): PathBuilder {
        commands += PathCommand.CubicTo(x1, y1, x2, y2, x3, y3)
        return this
    }

    fun arcTo(
        oval: Rect,
        startAngleDegrees: Float,
        sweepAngleDegrees: Float,
        forceMoveTo: Boolean = false,
    ): PathBuilder {
        commands += PathCommand.ArcTo(
            oval = oval,
            startAngleDegrees = startAngleDegrees,
            sweepAngleDegrees = sweepAngleDegrees,
            forceMoveTo = forceMoveTo,
        )
        return this
    }

    fun close(): PathBuilder {
        commands += PathCommand.Close
        return this
    }

    fun build(): PathModel {
        return PathModel(
            fillType = fillType,
            commands = commands.toList(),
        )
    }
}

/**
 * 使用 DSL 构建不可变路径模型。
 * Builds an immutable path model with a DSL block.
 */
fun path(builder: PathBuilder.() -> Unit): PathModel {
    return PathBuilder().apply(builder).build()
}
