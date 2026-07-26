package com.viewcompose.graphics.core

class DrawRecorder {
    private val commands = mutableListOf<DrawCommand>()

    fun clear() {
        commands.clear()
    }

    fun record(command: DrawCommand) {
        commands += command
    }

    fun save() {
        record(DrawCommand.Save)
    }

    fun restore() {
        record(DrawCommand.Restore)
    }

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

    fun clipRect(rect: Rect) {
        record(DrawCommand.ClipRect(rect))
    }

    fun clipPath(path: PathModel) {
        record(DrawCommand.ClipPath(path))
    }

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

    fun translate(
        dx: Float,
        dy: Float,
    ) {
        record(DrawCommand.Translate(dx = dx, dy = dy))
    }

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

    fun skew(
        kx: Float,
        ky: Float,
    ) {
        record(DrawCommand.Skew(kx = kx, ky = ky))
    }

    fun concat(matrix: Matrix3) {
        record(DrawCommand.Concat(matrix))
    }

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

    fun toCommands(): List<DrawCommand> {
        return commands.toList()
    }

    fun toScene(): DrawScene {
        return DrawScene(commands)
    }
}

fun drawScene(block: DrawRecorder.() -> Unit): DrawScene {
    return DrawRecorder()
        .apply(block)
        .toScene()
}
