package com.viewcompose.graphics.core

/** Source-level alias for a packed, non-premultiplied `0xAARRGGBB` color integer. */
typealias UiColor = Int

/**
 * Associates a packed color with one normalized gradient position.
 *
 * The model does not validate or sort stops. Renderers may reject, clamp, or platform-normalize
 * offsets outside `0f..1f`, non-finite values, duplicate positions, or unsorted lists.
 *
 * @property offset normalized gradient position, conventionally in `0f..1f`
 * @property color packed `0xAARRGGBB` color
 */
data class ColorStop(
    val offset: Float,
    val color: UiColor,
)

/** Defines a platform-neutral color or shader source for fills and strokes. */
sealed interface Brush {
    /**
     * Uses one packed color for every covered pixel.
     *
     * @property color packed `0xAARRGGBB` color
     */
    data class SolidColor(
        val color: UiColor,
    ) : Brush

    /**
     * Interpolates color along the line from [from] to [to].
     *
     * @property from gradient start in the current drawing coordinate space
     * @property to gradient end in the current drawing coordinate space
     * @property colorStops caller-ordered color stops; no validation or defensive copy is applied
     */
    data class LinearGradient(
        val from: Offset,
        val to: Offset,
        val colorStops: List<ColorStop>,
    ) : Brush

    /**
     * Interpolates color outward from [center] to [radius].
     *
     * @property center gradient center in the current drawing coordinate space
     * @property radius radial extent in drawing-coordinate units
     * @property colorStops caller-ordered color stops; no validation or defensive copy is applied
     */
    data class RadialGradient(
        val center: Offset,
        val radius: Float,
        val colorStops: List<ColorStop>,
    ) : Brush

    /**
     * Interpolates color by angle around [center].
     *
     * @property center sweep origin in the current drawing coordinate space
     * @property colorStops caller-ordered color stops; no validation or defensive copy is applied
     */
    data class SweepGradient(
        val center: Offset,
        val colorStops: List<ColorStop>,
    ) : Brush
}

/** Selects interior filling or outline stroking for geometry commands. */
sealed interface DrawStyle {
    /** Fills the interior selected by the geometry's fill rule. */
    data object Fill : DrawStyle

    /**
     * Strokes the geometry outline.
     *
     * Values are not validated; platform renderers decide how to handle non-positive or non-finite
     * widths and miter limits.
     *
     * @property width stroke width in the current drawing coordinate space
     * @property cap treatment applied to open segment endpoints
     * @property join treatment applied where segments meet
     * @property miterLimit maximum miter ratio before a renderer falls back to bevel behavior
     */
    data class Stroke(
        val width: Float = 1f,
        val cap: StrokeCap = StrokeCap.Butt,
        val join: StrokeJoin = StrokeJoin.Miter,
        val miterLimit: Float = 4f,
    ) : DrawStyle
}

/** Selects the platform-neutral treatment of open stroke endpoints. */
enum class StrokeCap {
    Butt,
    Round,
    Square,
}

/** Selects the platform-neutral treatment of corners where stroked segments meet. */
enum class StrokeJoin {
    Miter,
    Round,
    Bevel,
}

/**
 * Selects a platform-neutral source/destination blend operation.
 *
 * Renderer support can vary by Android API level and backing surface. A renderer may approximate or
 * fall back when the platform cannot represent a mode exactly.
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

/** Describes color post-processing without mutating the source image or brush. */
sealed interface ColorFilterModel {
    /**
     * Blends [color] over the source output with [blendMode].
     *
     * @property color packed tint color
     * @property blendMode source/destination operation used for tinting
     */
    data class Tint(
        val color: UiColor,
        val blendMode: BlendMode = BlendMode.SrcIn,
    ) : ColorFilterModel

    /**
     * Applies a row-major 4-by-5 color matrix.
     *
     * The array is retained rather than copied, and Kotlin data-class equality compares it by array
     * identity. Do not mutate it after recording a command or use independently allocated equal
     * arrays when stable structural cache equality is required.
     *
     * @property values exactly 20 row-major coefficients
     * @throws IllegalArgumentException if [values] does not contain 20 elements
     */
    data class ColorMatrix(
        val values: FloatArray,
    ) : ColorFilterModel {
        init {
            require(values.size == 20) { "ColorMatrix filter requires 20 values." }
        }
    }
}

/** Describes platform-neutral image post-processing applied by a renderer. */
sealed interface ImageFilterModel {
    /**
     * Requests independent horizontal and vertical blur radii.
     *
     * @property radiusX horizontal blur radius in drawing-coordinate units
     * @property radiusY vertical blur radius in drawing-coordinate units
     */
    data class Blur(
        val radiusX: Float,
        val radiusY: Float,
    ) : ImageFilterModel

    /**
     * Applies [inner] first and then [outer].
     *
     * @property outer filter applied to the intermediate result
     * @property inner filter applied to the source first
     */
    data class Chain(
        val outer: ImageFilterModel,
        val inner: ImageFilterModel,
    ) : ImageFilterModel
}

/**
 * Captures the complete platform-neutral paint request for one draw operation.
 *
 * The object itself is immutable, but nested lists and arrays are not defensively copied. [alpha]
 * and numeric filter/style inputs are stored without validation; renderers may clamp or reject
 * unsupported values. Platform capability determines the exact blend and image-filter result.
 *
 * @property brush color or shader source
 * @property style interior fill or outline stroke policy
 * @property alpha additional opacity multiplier, conventionally in `0f..1f`
 * @property blendMode source/destination blend operation
 * @property colorFilter optional color post-processing
 * @property imageFilter optional image post-processing
 * @property antiAlias whether the renderer should request edge anti-aliasing
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
