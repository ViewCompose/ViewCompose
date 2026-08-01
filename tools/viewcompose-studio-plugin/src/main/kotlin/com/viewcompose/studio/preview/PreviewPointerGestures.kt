package com.viewcompose.studio.preview

import java.awt.Toolkit
import kotlin.math.abs

internal enum class PreviewScrollAxis {
    Horizontal,
    Vertical,
}

/**
 * Locks a trackpad gesture to its dominant axis while allowing a new orthogonal intent to take
 * over without waiting for inertial scrolling to stop completely.
 */
internal class PreviewTrackpadAxisLock(
    private val activationThreshold: Double = DEFAULT_AXIS_ACTIVATION_THRESHOLD,
    private val dominanceRatio: Double = DEFAULT_AXIS_DOMINANCE_RATIO,
    private val resetDelayMillis: Long = DEFAULT_AXIS_RESET_DELAY_MILLIS,
    private val switchSampleCount: Int = DEFAULT_AXIS_SWITCH_SAMPLE_COUNT,
) {
    private var lockedAxis: PreviewScrollAxis? = null
    private var accumulatedHorizontal = 0.0
    private var accumulatedVertical = 0.0
    private var accumulatedOpposite = 0.0
    private var oppositeSamples = 0
    private var lastEventMillis: Long? = null

    init {
        require(activationThreshold > 0.0)
        require(dominanceRatio > 1.0)
        require(resetDelayMillis >= 0L)
        require(switchSampleCount > 0)
    }

    fun resolve(
        horizontalRotation: Double,
        verticalRotation: Double,
        eventMillis: Long,
    ): PreviewScrollAxis? {
        require(horizontalRotation.isFinite() && verticalRotation.isFinite())
        val previousEventMillis = lastEventMillis
        if (
            previousEventMillis == null ||
            eventMillis < previousEventMillis ||
            eventMillis - previousEventMillis > resetDelayMillis
        ) {
            reset()
        }
        lastEventMillis = eventMillis
        val horizontalMagnitude = abs(horizontalRotation)
        val verticalMagnitude = abs(verticalRotation)
        lockedAxis?.let { axis ->
            return resolveLockedAxis(
                axis = axis,
                horizontalMagnitude = horizontalMagnitude,
                verticalMagnitude = verticalMagnitude,
            )
        }
        accumulatedHorizontal += horizontalMagnitude
        accumulatedVertical += verticalMagnitude
        val dominant = maxOf(accumulatedHorizontal, accumulatedVertical)
        val secondary = minOf(accumulatedHorizontal, accumulatedVertical)
        if (dominant < activationThreshold) return null
        if (secondary > 0.0 && dominant / secondary < dominanceRatio) return null
        lockedAxis = if (accumulatedHorizontal > accumulatedVertical) {
            PreviewScrollAxis.Horizontal
        } else {
            PreviewScrollAxis.Vertical
        }
        return lockedAxis
    }

    private fun resolveLockedAxis(
        axis: PreviewScrollAxis,
        horizontalMagnitude: Double,
        verticalMagnitude: Double,
    ): PreviewScrollAxis {
        val lockedMagnitude = when (axis) {
            PreviewScrollAxis.Horizontal -> horizontalMagnitude
            PreviewScrollAxis.Vertical -> verticalMagnitude
        }
        val oppositeMagnitude = when (axis) {
            PreviewScrollAxis.Horizontal -> verticalMagnitude
            PreviewScrollAxis.Vertical -> horizontalMagnitude
        }
        val isOppositeIntent = oppositeMagnitude > 0.0 &&
            (lockedMagnitude == 0.0 || oppositeMagnitude >= lockedMagnitude * dominanceRatio)
        if (!isOppositeIntent) {
            resetSwitchCandidate()
            return axis
        }
        accumulatedOpposite += oppositeMagnitude
        oppositeSamples += 1
        val discreteWheelIntent = oppositeMagnitude >= DISCRETE_WHEEL_ROTATION
        if (
            accumulatedOpposite < activationThreshold ||
            (oppositeSamples < switchSampleCount && !discreteWheelIntent)
        ) {
            return axis
        }
        val nextAxis = axis.opposite()
        lockedAxis = nextAxis
        resetSwitchCandidate()
        return nextAxis
    }

    private fun PreviewScrollAxis.opposite(): PreviewScrollAxis = when (this) {
        PreviewScrollAxis.Horizontal -> PreviewScrollAxis.Vertical
        PreviewScrollAxis.Vertical -> PreviewScrollAxis.Horizontal
    }

    private fun resetSwitchCandidate() {
        accumulatedOpposite = 0.0
        oppositeSamples = 0
    }

    fun reset() {
        lockedAxis = null
        accumulatedHorizontal = 0.0
        accumulatedVertical = 0.0
        resetSwitchCandidate()
        lastEventMillis = null
    }
}

/** Detects a double press even when focus changes reset AWT's click counter. */
internal class PreviewDoublePressTracker(
    private val maximumIntervalMillis: Long = previewDoubleClickIntervalMillis(),
    private val maximumDistancePixels: Int = DEFAULT_DOUBLE_PRESS_DISTANCE_PIXELS,
) {
    private var previousPress: PreviewPointerPress? = null

    fun register(
        awtClickCount: Int,
        eventMillis: Long,
        x: Int,
        y: Int,
    ): Boolean {
        if (awtClickCount >= 2) {
            reset()
            return true
        }
        val previous = previousPress
        val isDoublePress = previous != null &&
            eventMillis >= previous.eventMillis &&
            eventMillis - previous.eventMillis <= maximumIntervalMillis &&
            abs(x - previous.x) <= maximumDistancePixels &&
            abs(y - previous.y) <= maximumDistancePixels
        if (isDoublePress) {
            reset()
        } else {
            previousPress = PreviewPointerPress(eventMillis, x, y)
        }
        return isDoublePress
    }

    fun reset() {
        previousPress = null
    }
}

internal fun previewDoubleClickIntervalMillis(
    desktopProperty: Any? = runCatching {
        Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval")
    }.getOrNull(),
): Long = (desktopProperty as? Number)
    ?.toLong()
    ?.coerceIn(MINIMUM_DOUBLE_PRESS_INTERVAL_MILLIS, MAXIMUM_DOUBLE_PRESS_INTERVAL_MILLIS)
    ?: FALLBACK_DOUBLE_PRESS_INTERVAL_MILLIS

/** Prevents an IDE caret move caused by preview navigation from replacing the exact node choice. */
internal class PreviewSourceSelectionGuard(
    private val clockNanos: () -> Long = System::nanoTime,
    private val durationNanos: Long = DEFAULT_SOURCE_SELECTION_GUARD_NANOS,
) {
    private var suppressedUntilNanos: Long = 0L

    init {
        require(durationNanos > 0L)
    }

    fun beginNavigation() {
        suppressedUntilNanos = clockNanos() + durationNanos
    }

    fun acceptsCaretSelection(): Boolean = clockNanos() >= suppressedUntilNanos
}

private data class PreviewPointerPress(
    val eventMillis: Long,
    val x: Int,
    val y: Int,
)

private const val DEFAULT_AXIS_ACTIVATION_THRESHOLD = 0.25
private const val DEFAULT_AXIS_DOMINANCE_RATIO = 1.35
private const val DEFAULT_AXIS_RESET_DELAY_MILLIS = 180L
private const val DEFAULT_AXIS_SWITCH_SAMPLE_COUNT = 2
private const val DISCRETE_WHEEL_ROTATION = 1.0
private const val FALLBACK_DOUBLE_PRESS_INTERVAL_MILLIS = 500L
private const val MINIMUM_DOUBLE_PRESS_INTERVAL_MILLIS = 250L
private const val MAXIMUM_DOUBLE_PRESS_INTERVAL_MILLIS = 1_000L
private const val DEFAULT_DOUBLE_PRESS_DISTANCE_PIXELS = 8
private const val DEFAULT_SOURCE_SELECTION_GUARD_NANOS = 1_000_000_000L
