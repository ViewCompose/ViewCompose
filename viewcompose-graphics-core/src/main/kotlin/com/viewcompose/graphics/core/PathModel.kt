package com.viewcompose.graphics.core

/** Selects the winding rule used to determine the interior of self-intersecting or nested paths. */
enum class PathFillType {
    NonZero,
    EvenOdd,
}

/** Describes one immutable operation in a renderer-replayable path command sequence. */
sealed interface PathCommand {
    /**
     * Starts or repositions a contour without drawing a segment.
     *
     * @property x target horizontal coordinate
     * @property y target vertical coordinate
     */
    data class MoveTo(
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * Draws a straight segment from the current point to the target.
     *
     * @property x target horizontal coordinate
     * @property y target vertical coordinate
     */
    data class LineTo(
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * Draws a quadratic Bézier segment from the current point.
     *
     * @property x1 control-point horizontal coordinate
     * @property y1 control-point vertical coordinate
     * @property x2 endpoint horizontal coordinate
     * @property y2 endpoint vertical coordinate
     */
    data class QuadTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : PathCommand

    /**
     * Draws a cubic Bézier segment from the current point.
     *
     * @property x1 first control-point horizontal coordinate
     * @property y1 first control-point vertical coordinate
     * @property x2 second control-point horizontal coordinate
     * @property y2 second control-point vertical coordinate
     * @property x3 endpoint horizontal coordinate
     * @property y3 endpoint vertical coordinate
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
     * Adds an arc along [oval].
     *
     * Angles follow Android Canvas convention: degrees are measured clockwise from the positive x
     * axis in the default y-down coordinate space.
     *
     * @property oval axis-aligned bounds of the source ellipse
     * @property startAngleDegrees starting angle in degrees
     * @property sweepAngleDegrees signed angular sweep in degrees
     * @property forceMoveTo whether to begin a new contour at the arc start instead of connecting it
     */
    data class ArcTo(
        val oval: Rect,
        val startAngleDegrees: Float,
        val sweepAngleDegrees: Float,
        val forceMoveTo: Boolean,
    ) : PathCommand

    /** Closes the current contour with a segment to its starting point. */
    data object Close : PathCommand
}

/**
 * Stores a platform-neutral path fill rule and ordered command sequence.
 *
 * The supplied command list is retained by the data class rather than defensively copied. Builder
 * APIs provide a snapshot; direct callers should not mutate a mutable list after construction.
 * Numeric coordinates are stored without finite-value validation.
 *
 * @property fillType winding rule used for filling and clipping
 * @property commands commands replayed in list order by a renderer
 * @sample com.viewcompose.graphics.core.samples.pathSample
 */
data class PathModel(
    val fillType: PathFillType = PathFillType.NonZero,
    val commands: List<PathCommand> = emptyList(),
)

/**
 * Mutable fluent builder that snapshots an ordered command list into [PathModel].
 *
 * The builder can be reused after [build]; previously built models are unaffected by later commands.
 */
class PathBuilder {
    private val commands = mutableListOf<PathCommand>()
    private var fillType: PathFillType = PathFillType.NonZero

    /** Sets the fill rule captured by subsequent [build] calls and returns this builder. */
    fun fillType(fillType: PathFillType): PathBuilder {
        this.fillType = fillType
        return this
    }

    /** Adds a contour move to ([x], [y]) and returns this builder. */
    fun moveTo(
        x: Float,
        y: Float,
    ): PathBuilder {
        commands += PathCommand.MoveTo(x, y)
        return this
    }

    /** Adds a straight segment to ([x], [y]) and returns this builder. */
    fun lineTo(
        x: Float,
        y: Float,
    ): PathBuilder {
        commands += PathCommand.LineTo(x, y)
        return this
    }

    /** Adds a quadratic Bézier through control point ([x1], [y1]) to ([x2], [y2]). */
    fun quadTo(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): PathBuilder {
        commands += PathCommand.QuadTo(x1, y1, x2, y2)
        return this
    }

    /** Adds a cubic Bézier through two control points to endpoint ([x3], [y3]). */
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

    /**
     * Adds an elliptical arc and returns this builder.
     *
     * @param oval ellipse bounds
     * @param startAngleDegrees clockwise start angle in degrees in the default coordinate space
     * @param sweepAngleDegrees signed sweep in degrees
     * @param forceMoveTo whether the arc begins a new contour
     */
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

    /** Closes the current contour and returns this builder. */
    fun close(): PathBuilder {
        commands += PathCommand.Close
        return this
    }

    /** Returns an independent immutable command-list snapshot with the current fill type. */
    fun build(): PathModel {
        return PathModel(
            fillType = fillType,
            commands = commands.toList(),
        )
    }
}

/**
 * Builds a [PathModel] with a fresh [PathBuilder].
 *
 * @sample com.viewcompose.graphics.core.samples.pathSample
 */
fun path(builder: PathBuilder.() -> Unit): PathModel {
    return PathBuilder().apply(builder).build()
}
