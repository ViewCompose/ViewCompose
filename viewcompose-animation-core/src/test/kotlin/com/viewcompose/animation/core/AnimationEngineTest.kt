package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnimationEngineTest {
    @Test
    fun `tween returns structured finished state at exact target`() = runBlocking {
        var latest = AnimationState(0f, AnimationVelocity(0f), 0L)
        val result = runAnimation(
            frameClock = StepClock(),
            startValue = 0f,
            endValue = 1f,
            animationSpec = tween(durationMillis = 64),
            converter = AnimationConverters.Float,
        ) { state ->
            latest = state
        }

        assertEquals(AnimationEndReason.Finished, result.endReason)
        assertEquals(1f, latest.value, 0f)
        assertEquals(0f, latest.velocity.valuePerSecond, 0f)
        assertEquals(latest, result.endState)
    }

    @Test
    fun `repeatable reverse samples a shared explicit timeline`() {
        val animation = TargetAnimation(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = repeatable(
                iterations = 2,
                animation = tween(durationMillis = 100, easing = EasingDefaults.Linear),
                repeatMode = RepeatMode.Reverse,
            ),
            converter = AnimationConverters.Float,
        )

        assertEquals(200_000_000L, animation.durationNanos)
        assertEquals(0.5f, animation.stateAt(50_000_000L).value, 0.001f)
        assertEquals(0.5f, animation.stateAt(150_000_000L).value, 0.001f)
        assertEquals(0f, animation.stateAt(animation.durationNanos).value, 0f)
    }

    @Test
    fun `under damped spring overshoots and finishes exactly at target`() {
        val animation = TargetAnimation(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.2f, stiffness = 100f),
            converter = AnimationConverters.Float,
        )

        assertTrue(animation.stateAt(300_000_000L).value > 1f)
        val terminal = animation.stateAt(animation.durationNanos)
        assertEquals(AnimationEndReason.Finished, animation.terminalEndReason)
        assertEquals(1f, terminal.value, 0f)
        assertEquals(0f, terminal.velocity.valuePerSecond, 0f)
    }

    @Test
    fun `critical and over damped springs approach without overshoot`() {
        val critical = TargetAnimation(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 1f, stiffness = 100f),
            converter = AnimationConverters.Float,
        )
        val over = TargetAnimation(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 2f, stiffness = 100f),
            converter = AnimationConverters.Float,
        )

        listOf(100_000_000L, 300_000_000L, 600_000_000L).forEach { playTime ->
            assertTrue(critical.stateAt(playTime).value in 0f..1f)
            assertTrue(over.stateAt(playTime).value in 0f..1f)
        }
    }

    @Test
    fun `undamped spring reaches safety guard without snapping`() = runBlocking {
        val result = runAnimation(
            frameClock = StepClock(frameStepNanos = 20_000_000L),
            startValue = 0f,
            endValue = 1f,
            animationSpec = spring(
                dampingRatio = 0f,
                stiffness = 100f,
                maxDurationMillis = 100,
            ),
            converter = AnimationConverters.Float,
        ) {}

        assertEquals(AnimationEndReason.DurationLimitReached, result.endReason)
        assertNotEquals(1f, result.endState.value, 0.0001f)
        assertNotEquals(0f, result.endState.velocity.valuePerSecond, 0.0001f)
    }

    @Test
    fun `initial velocity changes physical sample and remains typed`() {
        val animation = TargetAnimation(
            initialValue = 0,
            targetValue = 100,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
            converter = AnimationConverters.Int,
            initialVelocity = AnimationVelocity(400f),
        )

        val state = animation.stateAt(16_000_000L)
        assertTrue(state.value > 0)
        assertTrue(state.velocity.valuePerSecond > 0f)
    }

    @Test
    fun `zero distance spring settles immediately unless velocity still carries motion`() {
        val idle = TargetAnimation(
            initialValue = 5f,
            targetValue = 5f,
            animationSpec = spring(),
            converter = AnimationConverters.Float,
        )
        val moving = TargetAnimation(
            initialValue = 5f,
            targetValue = 5f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
            converter = AnimationConverters.Float,
            initialVelocity = AnimationVelocity(120f),
        )

        assertEquals(0L, idle.durationNanos)
        assertEquals(5f, idle.stateAt(0L).value, 0f)
        assertEquals(0f, idle.stateAt(0L).velocity.valuePerSecond, 0f)
        assertTrue(moving.durationNanos > 0L)
        assertTrue(moving.stateAt(16_000_000L).value > 5f)
    }

    @Test
    fun `multi component physical solve stays finite and terminates at exact vector target`() {
        val target = VectorValue(x = 1_001_000f, y = -999_000f)
        val animation = TargetAnimation(
            initialValue = VectorValue(x = 1_000_000f, y = -1_000_000f),
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.35f, stiffness = 260f),
            converter = VectorConverter,
            initialVelocity = AnimationVelocity(VectorDelta(x = 50_000f, y = -25_000f)),
        )

        listOf(1L, 16_000_000L, 250_000_000L, animation.durationNanos).forEach { playTime ->
            val state = animation.stateAt(playTime)
            assertTrue(state.value.x.isFinite())
            assertTrue(state.value.y.isFinite())
            assertTrue(state.velocity.valuePerSecond.x.isFinite())
            assertTrue(state.velocity.valuePerSecond.y.isFinite())
        }
        assertEquals(target, animation.stateAt(animation.durationNanos).value)
    }

    @Test
    fun `physical evaluators reuse position and velocity vectors across samples`() {
        val springConverter = TrackingFloatConverter()
        val springAnimation = TargetAnimation(
            initialValue = 0f,
            targetValue = 100f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
            converter = springConverter,
        )
        repeat(100) { index ->
            springAnimation.stateAt(index * 8_000_000L)
        }

        val decayConverter = TrackingFloatConverter()
        val decayAnimation = DecayAnimation(
            initialValue = 0f,
            initialVelocity = AnimationVelocity(800f),
            animationSpec = exponentialDecay(),
            converter = decayConverter,
        )
        repeat(100) { index ->
            decayAnimation.stateAt(index * 8_000_000L)
        }

        assertTrue(springConverter.reusedSampleVectors)
        assertTrue(decayConverter.reusedSampleVectors)
    }

    @Test
    fun `decay preserves direction and finishes with zero retained velocity`() {
        val decay = DecayAnimation(
            initialValue = 10f,
            initialVelocity = AnimationVelocity(-420f),
            animationSpec = exponentialDecay(frictionMultiplier = 1f),
            converter = AnimationConverters.Float,
        )

        val middle = decay.stateAt(decay.durationNanos / 2L)
        val terminal = decay.stateAt(decay.durationNanos)
        assertTrue(middle.value < 10f)
        assertTrue(middle.velocity.valuePerSecond < 0f)
        assertEquals(AnimationEndReason.Finished, decay.terminalEndReason)
        assertEquals(0f, terminal.velocity.valuePerSecond, 0f)
    }

    @Test
    fun `bound crossing clamps before publication and terminates whole run`() = runBlocking {
        val published = mutableListOf<Float>()
        val result = runAnimation(
            frameClock = StepClock(),
            startValue = 0f,
            endValue = 100f,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 250f),
            converter = AnimationConverters.Float,
            initialVelocity = AnimationVelocity(800f),
            lowerBound = -10f,
            upperBound = 20f,
        ) { state ->
            published += state.value
        }

        assertEquals(AnimationEndReason.BoundReached, result.endReason)
        assertEquals(20f, result.endState.value, 0f)
        assertEquals(0f, result.endState.velocity.valuePerSecond, 0f)
        assertTrue(published.all { it in -10f..20f })
    }

    @Test
    fun `non monotonic clock fails before candidate publication`() = runBlocking {
        val published = mutableListOf<Float>()
        try {
            runAnimation(
                frameClock = SequenceClock(0L, 16_000_000L, 16_000_000L),
                startValue = 0f,
                endValue = 1f,
                animationSpec = tween(durationMillis = 100),
                converter = AnimationConverters.Float,
            ) { state ->
                published += state.value
            }
            fail("Expected a non-monotonic clock failure.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("monotonically"))
        }
        assertEquals(1, published.size)
    }

    @Test
    fun `cancelled animation retains latest sample and returns no result`() = runBlocking {
        val clock = SuspendingStepClock()
        val samples = mutableListOf<Float>()
        lateinit var animationJob: Job
        animationJob = launch {
            runAnimation(
                frameClock = clock,
                startValue = 0f,
                endValue = 1f,
                animationSpec = tween(durationMillis = 320),
                converter = AnimationConverters.Float,
            ) { state ->
                samples += state.value
                if (samples.size == 1) {
                    animationJob.cancel()
                }
            }
        }

        animationJob.join()

        assertTrue(animationJob.isCancelled)
        assertTrue(samples.isNotEmpty())
        assertTrue(abs(samples.last() - 1f) > 0.0001f)
    }

    @Test
    fun `cancelled zero duration animation publishes no sample`() = runBlocking {
        val cancelledContext = Job()
        cancelledContext.cancel()
        var published = false

        try {
            kotlinx.coroutines.withContext(cancelledContext) {
                runAnimation(
                    frameClock = StepClock(),
                    startValue = 0f,
                    endValue = 1f,
                    animationSpec = repeatable(iterations = 0, animation = snap()),
                    converter = AnimationConverters.Float,
                ) {
                    published = true
                }
            }
            fail("Expected cancellation before a zero-duration publication.")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }

        assertTrue(!published)
    }

    @Test
    fun `invalid physical configuration fails at construction`() {
        expectIllegalArgument { spring(dampingRatio = -0.1f) }
        expectIllegalArgument { spring(stiffness = 0f) }
        expectIllegalArgument { spring(maxDurationMillis = 0) }
        expectIllegalArgument { exponentialDecay(frictionMultiplier = Float.NaN) }
        expectIllegalArgument { exponentialDecay(maxDurationMillis = 60_001) }
    }

    @Test
    fun `non finite physical outputs fail before publication`() {
        expectIllegalArgument {
            TargetAnimation(
                initialValue = Float.MAX_VALUE,
                targetValue = -Float.MAX_VALUE,
                animationSpec = spring(dampingRatio = 0.2f, stiffness = 100f),
                converter = AnimationConverters.Float,
            )
        }
        expectIllegalArgument {
            DecayAnimation(
                initialValue = Float.MAX_VALUE,
                initialVelocity = AnimationVelocity(Float.MAX_VALUE),
                animationSpec = exponentialDecay(frictionMultiplier = Float.MIN_VALUE),
                converter = AnimationConverters.Float,
            )
        }
    }

    @Test
    fun `converter rejects incomplete threshold and invalid zero velocity`() {
        val incompleteThreshold = object : AnimationConverter<Float, Float> {
            override val vectorSize: Int = 2
            override val zeroVelocity: Float = 0f
            override val visibilityThreshold: Float = 1f

            override fun convertToVector(value: Float, destination: FloatArray) {
                destination[0] = value
                destination[1] = value
            }

            override fun convertFromVector(vector: FloatArray): Float = vector[0]

            override fun convertVelocityToVector(velocity: Float, destination: FloatArray) {
                destination[0] = velocity
            }

            override fun convertVelocityFromVector(vector: FloatArray): Float = vector[0]
        }
        val invalidZero = object : AnimationConverter<Float, Float> {
            override val vectorSize: Int = 1
            override val zeroVelocity: Float = 0f
            override val visibilityThreshold: Float = 0.01f

            override fun convertToVector(value: Float, destination: FloatArray) {
                destination[0] = value
            }

            override fun convertFromVector(vector: FloatArray): Float = vector[0]

            override fun convertVelocityToVector(velocity: Float, destination: FloatArray) {
                destination[0] = velocity + 1f
            }

            override fun convertVelocityFromVector(vector: FloatArray): Float = vector[0]
        }

        expectIllegalArgument {
            TargetAnimation(0f, 1f, spring(), incompleteThreshold)
        }
        expectIllegalArgument {
            TargetAnimation(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(),
                converter = invalidZero,
                initialVelocity = AnimationVelocity(2f),
            )
        }
    }

    private data class VectorValue(
        val x: Float,
        val y: Float,
    )

    private data class VectorDelta(
        val x: Float,
        val y: Float,
    )

    private object VectorConverter : AnimationConverter<VectorValue, VectorDelta> {
        override val vectorSize: Int = 2
        override val zeroVelocity: VectorDelta = VectorDelta(0f, 0f)
        override val visibilityThreshold: VectorDelta = VectorDelta(0.01f, 0.01f)

        override fun convertToVector(value: VectorValue, destination: FloatArray) {
            destination[0] = value.x
            destination[1] = value.y
        }

        override fun convertFromVector(vector: FloatArray): VectorValue {
            return VectorValue(vector[0], vector[1])
        }

        override fun convertVelocityToVector(velocity: VectorDelta, destination: FloatArray) {
            destination[0] = velocity.x
            destination[1] = velocity.y
        }

        override fun convertVelocityFromVector(vector: FloatArray): VectorDelta {
            return VectorDelta(vector[0], vector[1])
        }
    }

    private class TrackingFloatConverter : AnimationConverter<Float, Float> {
        override val vectorSize: Int = 1
        override val zeroVelocity: Float = 0f
        override val visibilityThreshold: Float = 0.01f

        private var sampledValueVector: FloatArray? = null
        private var sampledVelocityVector: FloatArray? = null
        var reusedSampleVectors: Boolean = true
            private set

        override fun convertToVector(value: Float, destination: FloatArray) {
            destination[0] = value
        }

        override fun convertFromVector(vector: FloatArray): Float {
            reusedSampleVectors = reusedSampleVectors && recordsSameVector(
                recorded = sampledValueVector,
                candidate = vector,
            )
            if (sampledValueVector == null) sampledValueVector = vector
            return vector[0]
        }

        override fun convertVelocityToVector(velocity: Float, destination: FloatArray) {
            destination[0] = velocity
        }

        override fun convertVelocityFromVector(vector: FloatArray): Float {
            reusedSampleVectors = reusedSampleVectors && recordsSameVector(
                recorded = sampledVelocityVector,
                candidate = vector,
            )
            if (sampledVelocityVector == null) sampledVelocityVector = vector
            return vector[0]
        }

        private fun recordsSameVector(recorded: FloatArray?, candidate: FloatArray): Boolean {
            return recorded == null || recorded === candidate
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
        }
    }

    private class StepClock(
        private val frameStepNanos: Long = 16_000_000L,
    ) : MonotonicFrameClock {
        private var nowNanos: Long = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R {
            nowNanos += frameStepNanos
            return onFrame(nowNanos)
        }
    }

    private class SuspendingStepClock : MonotonicFrameClock {
        private var nowNanos: Long = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R {
            delay(1)
            nowNanos += 16_000_000L
            return onFrame(nowNanos)
        }
    }

    private class SequenceClock(
        vararg frames: Long,
    ) : MonotonicFrameClock {
        private val values = frames.iterator()

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R = onFrame(values.next())
    }
}
