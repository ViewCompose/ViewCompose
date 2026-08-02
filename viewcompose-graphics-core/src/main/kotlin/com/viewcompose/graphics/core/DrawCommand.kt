package com.viewcompose.graphics.core

/**
 * Defines one platform-neutral operation in an ordered renderer command stream.
 *
 * Renderers execute commands in list order and carry canvas state until a matching restore. Most
 * numeric inputs are stored without validation and use the current drawing coordinate space,
 * normally physical pixels on Android.
 */
sealed interface DrawCommand {
    /** Pushes the current transform, clip, and paint-related canvas state. */
    data object Save : DrawCommand

    /** Pops the state from the most recent [Save] or [SaveLayer]. */
    data object Restore : DrawCommand

    /**
     * Pushes canvas state and redirects drawing to an isolated layer.
     *
     * @property bounds optional layer bounds; `null` delegates bounds choice to the renderer
     * @property paint compositing paint applied when the layer is restored
     */
    data class SaveLayer(
        val bounds: Rect? = null,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Translates the current coordinate space.
     *
     * @property dx horizontal translation
     * @property dy vertical translation
     */
    data class Translate(
        val dx: Float,
        val dy: Float,
    ) : DrawCommand

    /**
     * Scales the current coordinate space around [pivot].
     *
     * @property sx horizontal scale multiplier
     * @property sy vertical scale multiplier
     * @property pivot fixed point in the pre-scale coordinate space
     */
    data class Scale(
        val sx: Float,
        val sy: Float,
        val pivot: Offset = Offset.Zero,
    ) : DrawCommand

    /**
     * Rotates the current coordinate space around [pivot].
     *
     * @property degrees clockwise angle in the default Android y-down coordinate space
     * @property pivot fixed point in the pre-rotation coordinate space
     */
    data class Rotate(
        val degrees: Float,
        val pivot: Offset = Offset.Zero,
    ) : DrawCommand

    /**
     * Skews the current coordinate space.
     *
     * @property kx horizontal skew factor
     * @property ky vertical skew factor
     */
    data class Skew(
        val kx: Float,
        val ky: Float,
    ) : DrawCommand

    /**
     * Concatenates [matrix] with the current canvas transform.
     *
     * @property matrix row-major 3-by-3 transform
     */
    data class Concat(
        val matrix: Matrix3,
    ) : DrawCommand

    /**
     * Intersects the current clip with [rect] for subsequent commands.
     *
     * @property rect clip rectangle in the current coordinate space
     */
    data class ClipRect(
        val rect: Rect,
    ) : DrawCommand

    /**
     * Intersects the current clip with [path] for subsequent commands.
     *
     * @property path path and fill rule defining the clip region
     */
    data class ClipPath(
        val path: PathModel,
    ) : DrawCommand

    /**
     * Replays a validated nested scene under an independent transform and optional clip.
     *
     * @property scene immutable scene to replay
     * @property transform transform applied around nested command execution
     * @property clip optional clip applied around nested command execution
     */
    data class DrawScene(
        val scene: com.viewcompose.graphics.core.DrawScene,
        val transform: Matrix3 = Matrix3.identity(),
        val clip: Rect? = null,
    ) : DrawCommand

    /**
     * Draws a line segment.
     *
     * @property from segment start
     * @property to segment end
     * @property paint stroke paint by default; renderers interpret other styles if supplied
     */
    data class DrawLine(
        val from: Offset,
        val to: Offset,
        val paint: DrawPaint = DrawPaint(style = DrawStyle.Stroke()),
    ) : DrawCommand

    /**
     * Draws an axis-aligned rectangle.
     *
     * @property rect rectangle bounds
     * @property paint paint used for fill or stroke
     */
    data class DrawRect(
        val rect: Rect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws a rounded rectangle.
     *
     * @property roundRect bounds and independent corner radii
     * @property paint paint used for fill or stroke
     */
    data class DrawRoundRect(
        val roundRect: RoundRect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws a circle.
     *
     * @property center circle center
     * @property radius radius in current coordinate units
     * @property paint paint used for fill or stroke
     */
    data class DrawCircle(
        val center: Offset,
        val radius: Float,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws an ellipse fitted to [rect].
     *
     * @property rect ellipse bounds
     * @property paint paint used for fill or stroke
     */
    data class DrawOval(
        val rect: Rect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws an elliptical arc or center-connected wedge.
     *
     * @property oval source ellipse bounds
     * @property startAngleDegrees clockwise start angle from the positive x axis
     * @property sweepAngleDegrees signed angular sweep
     * @property useCenter whether to connect arc endpoints to the ellipse center
     * @property paint paint used for fill or stroke
     */
    data class DrawArc(
        val oval: Rect,
        val startAngleDegrees: Float,
        val sweepAngleDegrees: Float,
        val useCenter: Boolean,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws a path model.
     *
     * @property path path commands and fill rule
     * @property paint paint used for fill or stroke
     */
    data class DrawPath(
        val path: PathModel,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws a host-resolved image region into an optional destination.
     *
     * @property image stable image reference resolved by the host
     * @property src optional source-pixel crop; `null` selects the full image
     * @property dst optional destination bounds; `null` uses renderer-native intrinsic placement
     * @property paint opacity, filtering, and blend request
     */
    data class DrawImage(
        val image: ImageRef,
        val src: Rect? = null,
        val dst: Rect? = null,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    /**
     * Draws one unwrapped text string from a baseline origin.
     *
     * @property text text passed unchanged to the renderer
     * @property origin baseline origin in the current coordinate space
     * @property style minimal type size and emphasis request
     * @property paint color, alpha, filter, and blend request
     */
    data class DrawText(
        val text: String,
        val origin: Offset,
        val style: TextStyle = TextStyle(),
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand
}

/**
 * Stores an immutable snapshot of commands with balanced canvas save and restore operations.
 *
 * Construction copies the command list and rejects underflow or leftover save depth. Nested
 * [DrawCommand.DrawScene] values are already validated independently and do not affect the outer
 * scene's balance.
 *
 * @sample com.viewcompose.graphics.core.samples.drawSceneSample
 */
class DrawScene internal constructor(
    commands: List<DrawCommand>,
) {
    /** Ordered immutable command-list snapshot replayed by renderers. */
    val commands: List<DrawCommand> = commands.toList()

    init {
        validateSaveRestoreBalance(this.commands)
    }

    /** Compares scenes by their ordered command lists. */
    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is DrawScene &&
                commands == other.commands
            )
    }

    /** Computes a hash from the ordered command list. */
    override fun hashCode(): Int = commands.hashCode()

    /** Returns a diagnostic representation containing the command list. */
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
 * Identifies an image without embedding platform bitmap ownership in a draw scene.
 *
 * @property stableId host-defined equality key used for lookup or caching
 * @property width declared intrinsic pixel width
 * @property height declared intrinsic pixel height
 */
data class ImageRef(
    val stableId: Any,
    val width: Int,
    val height: Int,
)

/**
 * Describes the minimal cross-platform text style supported by draw commands.
 *
 * It does not model font family, locale, shaping, wrapping, alignment, or rich spans.
 *
 * @property textSizePx requested physical-pixel text size
 * @property isBold whether to request a bold typeface style
 * @property isItalic whether to request an italic typeface style
 */
data class TextStyle(
    val textSizePx: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
)
