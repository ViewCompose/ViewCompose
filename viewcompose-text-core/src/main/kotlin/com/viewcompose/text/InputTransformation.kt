package com.viewcompose.text

/**
 * Validates or rewrites a platform-proposed edit before [TextFieldState] commits it.
 *
 * The mutable [TextFieldBuffer] initially contains the platform proposal and exposes the last
 * committed value through `originalValue`. A transformation may edit the proposal or call
 * [TextFieldBuffer.revertAllChanges] to reject it. Programmatic [TextFieldState.edit] calls bypass
 * this policy deliberately.
 *
 * @sample com.viewcompose.text.samples.inputTransformationSample
 */
fun interface InputTransformation {
    /** Applies this policy synchronously to one proposed user edit. */
    fun transformInput(buffer: TextFieldBuffer)

    /** Built-in input policies. */
    companion object {
        /**
         * Rejects proposals whose Unicode code-point count exceeds [maxCodePoints].
         *
         * Surrogate pairs count as one code point, so the limit cannot split a valid pair.
         *
         * @throws IllegalArgumentException when [maxCodePoints] is negative
         */
        fun maxCodePoints(maxCodePoints: Int): InputTransformation {
            require(maxCodePoints >= 0) { "maxCodePoints must be non-negative." }
            return MaxCodePointsTransformation(maxCodePoints)
        }

        /** Rejects a proposal when any UTF-16 character is not classified as a digit. */
        fun digitsOnly(): InputTransformation {
            return DigitsOnlyTransformation
        }
    }
}

/**
 * Returns a policy that applies this transformation and then [next] to the same buffer.
 *
 * The second policy observes every mutation or reversion made by the first policy.
 */
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
