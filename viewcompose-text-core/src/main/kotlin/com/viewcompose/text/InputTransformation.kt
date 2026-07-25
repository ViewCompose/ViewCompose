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
            return InputTransformation { buffer ->
                val count = buffer.text.codePointCount(0, buffer.length)
                if (count > maxCodePoints) {
                    buffer.revertAllChanges()
                }
            }
        }

        fun digitsOnly(): InputTransformation {
            return InputTransformation { buffer ->
                if (buffer.text.any { !it.isDigit() }) {
                    buffer.revertAllChanges()
                }
            }
        }
    }
}

fun InputTransformation.then(next: InputTransformation): InputTransformation {
    return InputTransformation { buffer ->
        transformInput(buffer)
        next.transformInput(buffer)
    }
}
