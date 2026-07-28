package com.viewcompose.graphics.core

/**
 * ARGB 颜色整数，格式与 Android 的 0xAARRGGBB 约定保持一致。
 * ARGB color integer aligned with Android's 0xAARRGGBB convention.
 */
typealias UiColor = Int

/**
 * 渐变色标，offset 通常位于 0f..1f 区间。
 * Gradient color stop whose offset is normally in the 0f..1f range.
 */
data class ColorStop(
    val offset: Float,
    val color: UiColor,
)

/**
 * 绘制填充源，保持平台中立，由渲染器转换为原生 shader/paint。
 * Paint fill source kept platform-neutral and converted to native shaders/paints by renderers.
 */
sealed interface Brush {
    /**
     * 单色画刷，适用于纯色填充和描边。
     * Solid-color brush for plain fills and strokes.
     */
    data class SolidColor(
        val color: UiColor,
    ) : Brush

    /**
     * 线性渐变，from/to 采用当前绘制坐标系。
     * Linear gradient whose from/to points use the current drawing coordinate space.
     */
    data class LinearGradient(
        val from: Offset,
        val to: Offset,
        val colorStops: List<ColorStop>,
    ) : Brush

    /**
     * 径向渐变，center/radius 采用当前绘制坐标系。
     * Radial gradient whose center/radius use the current drawing coordinate space.
     */
    data class RadialGradient(
        val center: Offset,
        val radius: Float,
        val colorStops: List<ColorStop>,
    ) : Brush

    /**
     * 扫描渐变，围绕 center 按角度插值颜色。
     * Sweep gradient that interpolates colors around the center by angle.
     */
    data class SweepGradient(
        val center: Offset,
        val colorStops: List<ColorStop>,
    ) : Brush
}

/**
 * 几何图形绘制方式，决定形状填充还是描边。
 * Geometry draw style deciding whether shapes are filled or stroked.
 */
sealed interface DrawStyle {
    /**
     * 填充形状内部区域。
     * Fills the interior of a shape.
     */
    data object Fill : DrawStyle

    /**
     * 沿形状轮廓描边，width 使用绘制坐标系单位。
     * Strokes the shape outline; width uses drawing-coordinate units.
     */
    data class Stroke(
        val width: Float = 1f,
        val cap: StrokeCap = StrokeCap.Butt,
        val join: StrokeJoin = StrokeJoin.Miter,
        val miterLimit: Float = 4f,
    ) : DrawStyle
}

/**
 * 线段端点样式，对齐常见 Canvas/Paint stroke cap 语义。
 * Stroke-end style aligned with common Canvas/Paint stroke cap semantics.
 */
enum class StrokeCap {
    Butt,
    Round,
    Square,
}

/**
 * 折线连接点样式，对齐常见 Canvas/Paint stroke join 语义。
 * Stroke-corner style aligned with common Canvas/Paint stroke join semantics.
 */
enum class StrokeJoin {
    Miter,
    Round,
    Bevel,
}

/**
 * 图层混合模式的跨平台枚举，渲染器负责映射到平台能力。
 * Cross-platform blend-mode enum; renderers map it to platform capabilities.
 */
enum class BlendMode {
    SrcOver,
    SrcIn,
    SrcOut,
    SrcAtop,
    DstOver,
    DstIn,
    DstOut,
    DstAtop,
    Multiply,
    Screen,
    Overlay,
    Darken,
    Lighten,
    Plus,
}

/**
 * 颜色过滤模型，用于在不改变原始图像或画刷的情况下调整输出颜色。
 * Color-filter model used to adjust output colors without mutating source images or brushes.
 */
sealed interface ColorFilterModel {
    /**
     * 使用指定颜色和混合模式对输出着色。
     * Tints output with the given color and blend mode.
     */
    data class Tint(
        val color: UiColor,
        val blendMode: BlendMode = BlendMode.SrcIn,
    ) : ColorFilterModel

    /**
     * 4x5 颜色矩阵过滤器，数组必须包含 20 个值。
     * 4x5 color-matrix filter; the backing array must contain exactly 20 values.
     */
    data class ColorMatrix(
        val values: FloatArray,
    ) : ColorFilterModel {
        init {
            require(values.size == 20) { "ColorMatrix filter requires 20 values." }
        }
    }
}

/**
 * 图像过滤模型，用于描述模糊、链式过滤等后处理效果。
 * Image-filter model describing post-processing effects such as blur and filter chains.
 */
sealed interface ImageFilterModel {
    /**
     * 高斯模糊半径，x/y 方向可以独立设置。
     * Gaussian blur radius with independently configurable x/y directions.
     */
    data class Blur(
        val radiusX: Float,
        val radiusY: Float,
    ) : ImageFilterModel

    /**
     * 过滤器链，inner 先执行，outer 后执行。
     * Filter chain where inner runs first and outer runs afterward.
     */
    data class Chain(
        val outer: ImageFilterModel,
        val inner: ImageFilterModel,
    ) : ImageFilterModel
}

/**
 * 单次绘制操作的完整画笔状态。
 * Complete paint state for one draw operation.
 *
 * 该对象保持不可变，便于绘制命令缓存、比较和跨渲染器传递。
 * This object stays immutable to support command caching, equality checks, and renderer handoff.
 */
data class DrawPaint(
    val brush: Brush = Brush.SolidColor(0xFF000000.toInt()),
    val style: DrawStyle = DrawStyle.Fill,
    val alpha: Float = 1f,
    val blendMode: BlendMode = BlendMode.SrcOver,
    val colorFilter: ColorFilterModel? = null,
    val imageFilter: ImageFilterModel? = null,
    val antiAlias: Boolean = true,
)
