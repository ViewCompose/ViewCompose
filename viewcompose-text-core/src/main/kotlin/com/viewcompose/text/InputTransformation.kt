package com.viewcompose.text

/**
 * Validates or rewrites a platform-proposed user edit before it reaches [TextFieldState].
 *
 * Programmatic [TextFieldState.edit] calls are intentionally not transformed.
 */
fun interface InputTransformation {
    fun transformInput(buffer: TextFieldBuffer)

    companion object {
        fun maxCodePoints(maxCodePoints: Int): InputTransformation {
            require(maxCodePoints >= 0) { "maxCodePoints must be non-negative." }
            return MaxCodePointsTransformation(maxCodePoints)
        }

        fun digitsOnly(): InputTransformation {
            return DigitsOnlyTransformation
        }
    }
}

fun InputTransformation.then(next: InputTransformation): InputTransformation {
    return ChainedInputTransformation(this, next)
}

private data class MaxCodePointsTransformation(
    val maxCodePoints: Int,
) : InputTransformation {
    override fun transformInput(buffer: TextFieldBuffer) {
        val count = buffer.text.codePointCount(0, buffer.length)
        if (count > maxCodePoints) {
            buffer.revertAllChanges()
        }
    }
}

private data object DigitsOnlyTransformation : InputTransformation {
    override fun transformInput(buffer: TextFieldBuffer) {
        if (buffer.text.any { !it.isDigit() }) {
            buffer.revertAllChanges()
        }
    }
}

private data class ChainedInputTransformation(
    val first: InputTransformation,
    val second: InputTransformation,
) : InputTransformation {
    override fun transformInput(buffer: TextFieldBuffer) {
        first.transformInput(buffer)
        second.transformInput(buffer)
    }
}
