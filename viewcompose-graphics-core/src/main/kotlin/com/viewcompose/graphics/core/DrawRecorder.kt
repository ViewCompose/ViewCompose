package com.viewcompose.graphics.core

/**
 * Mutably records a platform-neutral command sequence for later renderer replay.
 *
 * The recorder performs no drawing and validates little numeric input. [toCommands] and [toScene]
 * return snapshots, so the recorder may be cleared and reused afterward. The class is not
 * thread-safe; confine each instance to one build or render thread.
 *
 * @sample com.viewcompose.graphics.core.samples.drawSceneSample
 */
class DrawRecorder {
    private val commands = mutableListOf<DrawCommand>()

    /** Removes all recorded commands without affecting previously exported snapshots. */
    fun clear() {
        commands.clear()
    }

    /** Appends [command] unchanged for advanced DSL extensions or tests. */
    fun record(command: DrawCommand) {
        commands += command
    }

    /** Records a canvas-state push that must be balanced by a later [restore]. */
    fun save() {
        record(DrawCommand.Save)
    }

    /** Records a canvas-state pop; [toScene] rejects it when no save is active. */
    fun restore() {
        record(DrawCommand.Restore)
    }

    /**
     * Records an isolated layer and canvas-state push.
     *
     * @param bounds optional layer bounds
     * @param paint compositing request applied when a renderer restores the layer
     */
    fun saveLayer(
        bounds: Rect? = null,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.SaveLayer(
                bounds = bounds,
                paint = paint,
            ),
        )
    }

    /** Intersects the current clip with [rect] for subsequent commands. */
    fun clipRect(rect: Rect) {
        record(DrawCommand.ClipRect(rect))
    }

    /** Intersects the current clip with [path] for subsequent commands. */
    fun clipPath(path: PathModel) {
        record(DrawCommand.ClipPath(path))
    }

    /**
     * Records replay of [scene] under [transform] and optional [clip].
     *
     * Nested scene save/restore balance is independent of the outer recorder.
     */
    fun drawScene(
        scene: DrawScene,
        transform: Matrix3 = Matrix3.identity(),
        clip: Rect? = null,
    ) {
        record(
            DrawCommand.DrawScene(
                scene = scene,
                transform = transform,
                clip = clip,
            ),
        )
    }

    /**
     * Builds a nested scene from [block] and records it under [transform] and [clip].
     *
     * @throws IllegalArgumentException if the nested block leaves save/restore unbalanced
     */
    fun group(
        transform: Matrix3 = Matrix3.identity(),
        clip: Rect? = null,
        block: DrawRecorder.() -> Unit,
    ) {
        drawScene(
            scene = drawScene(block),
            transform = transform,
            clip = clip,
        )
    }

    /** Records translation by [dx] and [dy] in current coordinate units. */
    fun translate(
        dx: Float,
        dy: Float,
    ) {
        record(DrawCommand.Translate(dx = dx, dy = dy))
    }

    /** Records scale multipliers [sx] and [sy] around [pivot]. */
    fun scale(
        sx: Float,
        sy: Float,
        pivot: Offset = Offset.Zero,
    ) {
        record(
            DrawCommand.Scale(
                sx = sx,
                sy = sy,
                pivot = pivot,
            ),
        )
    }

    /** Records clockwise [degrees] around [pivot] in the default Android coordinate space. */
    fun rotate(
        degrees: Float,
        pivot: Offset = Offset.Zero,
    ) {
        record(
            DrawCommand.Rotate(
                degrees = degrees,
                pivot = pivot,
            ),
        )
    }

    /** Records horizontal [kx] and vertical [ky] skew factors. */
    fun skew(
        kx: Float,
        ky: Float,
    ) {
        record(DrawCommand.Skew(kx = kx, ky = ky))
    }

    /** Records concatenation of a row-major 3-by-3 [matrix]. */
    fun concat(matrix: Matrix3) {
        record(DrawCommand.Concat(matrix))
    }

    /** Records a line from [from] to [to] using [paint]. */
    fun drawLine(
        from: Offset,
        to: Offset,
        paint: DrawPaint = DrawPaint(style = DrawStyle.Stroke()),
    ) {
        record(
            DrawCommand.DrawLine(
                from = from,
                to = to,
                paint = paint,
            ),
        )
    }

    /** Records an axis-aligned [rect] using [paint]. */
    fun drawRect(
        rect: Rect,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawRect(
                rect = rect,
                paint = paint,
            ),
        )
    }

    /** Records [roundRect] using [paint]. */
    fun drawRoundRect(
        roundRect: RoundRect,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawRoundRect(
                roundRect = roundRect,
                paint = paint,
            ),
        )
    }

    /** Records a circle at [center] with [radius] using [paint]. */
    fun drawCircle(
        center: Offset,
        radius: Float,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawCircle(
                center = center,
                radius = radius,
                paint = paint,
            ),
        )
    }

    /** Records an ellipse fitted to [rect] using [paint]. */
    fun drawOval(
        rect: Rect,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawOval(
                rect = rect,
                paint = paint,
            ),
        )
    }

    /**
     * Records an elliptical arc.
     *
     * @param oval source ellipse bounds
     * @param startAngleDegrees clockwise start angle from the positive x axis
     * @param sweepAngleDegrees signed angular sweep
     * @param useCenter whether to connect endpoints to the center
     * @param paint fill or stroke request
     */
    fun drawArc(
        oval: Rect,
        startAngleDegrees: Float,
        sweepAngleDegrees: Float,
        useCenter: Boolean,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawArc(
                oval = oval,
                startAngleDegrees = startAngleDegrees,
                sweepAngleDegrees = sweepAngleDegrees,
                useCenter = useCenter,
                paint = paint,
            ),
        )
    }

    /** Records [path] using [paint]. */
    fun drawPath(
        path: PathModel,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawPath(
                path = path,
                paint = paint,
            ),
        )
    }

    /**
     * Records a host-resolved [image].
     *
     * @param src optional source-pixel crop, or `null` for the full image
     * @param dst optional destination bounds, or `null` for renderer-native intrinsic placement
     * @param paint opacity, filter, and blend request
     */
    fun drawImage(
        image: ImageRef,
        src: Rect? = null,
        dst: Rect? = null,
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawImage(
                image = image,
                src = src,
                dst = dst,
                paint = paint,
            ),
        )
    }

    /**
     * Records one unwrapped [text] string from baseline [origin].
     *
     * @param style minimal text size and emphasis request
     * @param paint color, opacity, filter, and blend request
     */
    fun drawText(
        text: String,
        origin: Offset,
        style: TextStyle = TextStyle(),
        paint: DrawPaint = DrawPaint(),
    ) {
        record(
            DrawCommand.DrawText(
                text = text,
                origin = origin,
                style = style,
                paint = paint,
            ),
        )
    }

    /** Returns an independent snapshot of the current command sequence without validating balance. */
    fun toCommands(): List<DrawCommand> {
        return commands.toList()
    }

    /**
     * Returns a validated immutable snapshot of the current command sequence.
     *
     * @throws IllegalArgumentException if save, save-layer, and restore operations are unbalanced
     */
    fun toScene(): DrawScene {
        return DrawScene(commands)
    }
}

/**
 * Records and validates an immutable [DrawScene] with a fresh recorder.
 *
 * @throws IllegalArgumentException if [block] leaves save/restore operations unbalanced
 * @sample com.viewcompose.graphics.core.samples.drawSceneSample
 */
fun drawScene(block: DrawRecorder.() -> Unit): DrawScene {
    return DrawRecorder()
        .apply(block)
        .toScene()
}
