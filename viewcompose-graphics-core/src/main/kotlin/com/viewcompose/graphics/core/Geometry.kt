package com.viewcompose.graphics.core

/**
 * 二维坐标点，单位由宿主渲染器约定，Android 侧通常解释为像素。
 * Two-dimensional point; the unit is defined by the host renderer and is usually pixels on Android.
 */
data class Offset(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Zero = Offset(0f, 0f)
    }
}

/**
 * 二维尺寸值，表示绘制或布局边界的宽高。
 * Two-dimensional size used for drawing or layout bounds.
 */
data class Size(
    val width: Float,
    val height: Float,
) {
    companion object {
        val Zero = Size(0f, 0f)
    }
}

/**
 * 轴对齐矩形，使用 left/top/right/bottom 边界描述。
 * Axis-aligned rectangle described by left, top, right, and bottom edges.
 */
data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    companion object {
        val Zero = Rect(0f, 0f, 0f, 0f)
    }
}

/**
 * 圆角半径，x/y 分别表示水平和垂直方向半径。
 * Corner radius whose x/y values represent horizontal and vertical radii.
 */
data class Radius(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Zero = Radius(0f, 0f)
    }
}

/**
 * 圆角矩形模型，每个角可以独立配置半径。
 * Rounded rectangle model with independently configurable corner radii.
 */
data class RoundRect(
    val rect: Rect,
    val topLeft: Radius = Radius.Zero,
    val topRight: Radius = Radius.Zero,
    val bottomRight: Radius = Radius.Zero,
    val bottomLeft: Radius = Radius.Zero,
)

/**
 * 3x3 仿射变换矩阵，用于跨平台描述画布变换。
 * 3x3 affine transformation matrix used to describe canvas transforms across renderers.
 *
 * 传入数组会被复制，避免调用方后续修改破坏命令不可变性。
 * Incoming arrays are copied so later caller-side mutation cannot alter recorded commands.
 */
class Matrix3(
    values: FloatArray = identityValues(),
) {
    val values: FloatArray = values.copyOf()

    init {
        require(this.values.size == 9) { "Matrix3 requires 9 values." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix3) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    companion object {
        fun identity(): Matrix3 = Matrix3(identityValues())

        private fun identityValues(): FloatArray {
            return floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            )
        }
    }
}
