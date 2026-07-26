package com.viewcompose.graphics.core

sealed interface DrawCommand {
    data object Save : DrawCommand

    data object Restore : DrawCommand

    data class SaveLayer(
        val bounds: Rect? = null,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class Translate(
        val dx: Float,
        val dy: Float,
    ) : DrawCommand

    data class Scale(
        val sx: Float,
        val sy: Float,
        val pivot: Offset = Offset.Zero,
    ) : DrawCommand

    data class Rotate(
        val degrees: Float,
        val pivot: Offset = Offset.Zero,
    ) : DrawCommand

    data class Skew(
        val kx: Float,
        val ky: Float,
    ) : DrawCommand

    data class Concat(
        val matrix: Matrix3,
    ) : DrawCommand

    data class ClipRect(
        val rect: Rect,
    ) : DrawCommand

    data class ClipPath(
        val path: PathModel,
    ) : DrawCommand

    data class DrawScene(
        val scene: com.viewcompose.graphics.core.DrawScene,
        val transform: Matrix3 = Matrix3.identity(),
        val clip: Rect? = null,
    ) : DrawCommand

    data class DrawLine(
        val from: Offset,
        val to: Offset,
        val paint: DrawPaint = DrawPaint(style = DrawStyle.Stroke()),
    ) : DrawCommand

    data class DrawRect(
        val rect: Rect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawRoundRect(
        val roundRect: RoundRect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawCircle(
        val center: Offset,
        val radius: Float,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawOval(
        val rect: Rect,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawArc(
        val oval: Rect,
        val startAngleDegrees: Float,
        val sweepAngleDegrees: Float,
        val useCenter: Boolean,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawPath(
        val path: PathModel,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawImage(
        val image: ImageRef,
        val src: Rect? = null,
        val dst: Rect? = null,
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand

    data class DrawText(
        val text: String,
        val origin: Offset,
        val style: TextStyle = TextStyle(),
        val paint: DrawPaint = DrawPaint(),
    ) : DrawCommand
}

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

data class ImageRef(
    val stableId: Any,
    val width: Int,
    val height: Int,
)

data class TextStyle(
    val textSizePx: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
)
