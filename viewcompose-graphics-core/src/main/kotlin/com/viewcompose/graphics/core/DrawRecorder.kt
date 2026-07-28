package com.viewcompose.graphics.core

/**
 * 绘制命令记录器，为 Canvas DSL 收集平台中立的命令列表。
 * Draw command recorder that collects platform-neutral command lists for the Canvas DSL.
 *
 * 记录器本身是可变构建器；导出的 DrawScene/commands 会复制当前快照。
 * The recorder itself is a mutable builder; exported DrawScene/commands copy the current snapshot.
 */
class DrawRecorder {
    private val commands = mutableListOf<DrawCommand>()

    /**
     * 清空已记录命令，以便复用记录器实例。
     * Clears recorded commands so the recorder instance can be reused.
     */
    fun clear() {
        commands.clear()
    }

    /**
     * 直接追加一条绘制命令，供高级调用方或测试使用。
     * Appends one draw command directly for advanced callers or tests.
     */
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

    /**
     * 导出不可变绘制场景，并校验 save/restore 配对。
     * Exports an immutable draw scene and validates save/restore pairing.
     */
    fun toScene(): DrawScene {
        return DrawScene(commands)
    }
}

/**
 * 使用 DSL 构建不可变绘制场景。
 * Builds an immutable draw scene with a DSL block.
 */
fun drawScene(block: DrawRecorder.() -> Unit): DrawScene {
    return DrawRecorder()
        .apply(block)
        .toScene()
}
