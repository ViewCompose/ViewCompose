package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavCommand

/**
 * Visual transform applied to one destination at an edge of a navigation transition.
 *
 * [travelFraction] is relative to one visible pane and [travelDp] is added to that distance. The
 * transition direction is resolved from the navigation command and layout direction by the Android
 * host.
 */
data class NavDestinationTransform(
    val travelFraction: Float = 0f,
    val travelDp: Float = 0f,
    val alpha: Float = 1f,
    val scale: Float = 1f,
) {
    init {
        require(travelFraction.isFinite() && travelFraction in 0f..1f) {
            "Navigation travel fraction must be finite and between 0 and 1."
        }
        require(travelDp.isFinite()) {
            "Navigation travel distance must be finite."
        }
        require(alpha.isFinite() && alpha in 0f..1f) {
            "Navigation alpha must be finite and between 0 and 1."
        }
        require(scale.isFinite() && scale > 0f) {
            "Navigation scale must be finite and greater than zero."
        }
    }

    internal val isIdentity: Boolean
        get() = travelFraction == 0f &&
            travelDp == 0f &&
            alpha == 1f &&
            scale == 1f
}

/**
 * Timing path used by native navigation motion.
 *
 * The public constructor creates one cubic Bézier from `(0, 0)` to `(1, 1)`. System motion can
 * use multiple cubic segments so Android's `fast_out_extra_slow_in` path is represented without
 * approximating it as a single curve.
 */
class NavMotionEasing private constructor(
    internal val segments: List<NavMotionPathSegment>,
) {
    constructor(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) : this(
        listOf(
            NavMotionPathSegment(
                control1X = x1,
                control1Y = y1,
                control2X = x2,
                control2Y = y2,
                endX = 1f,
                endY = 1f,
            ),
        ),
    )

    init {
        require(segments.isNotEmpty()) {
            "Navigation easing must contain at least one path segment."
        }
        var startX = 0f
        segments.forEach { segment ->
            require(segment.values.all(Float::isFinite)) {
                "Navigation easing path values must be finite."
            }
            require(segment.endX > startX && segment.endX <= 1f) {
                "Navigation easing path must advance monotonically to x=1."
            }
            require(
                segment.control1X in startX..segment.endX &&
                    segment.control2X in startX..segment.endX,
            ) {
                "Navigation easing x control points must stay inside their segment."
            }
            startX = segment.endX
        }
        require(segments.last().endX == 1f && segments.last().endY == 1f) {
            "Navigation easing path must end at (1, 1)."
        }
    }

    fun transform(fraction: Float): Float {
        val targetX = fraction.coerceIn(0f, 1f)
        if (targetX == 0f || targetX == 1f) {
            return targetX
        }
        var startX = 0f
        var startY = 0f
        val segment = segments.first { candidate ->
            if (targetX <= candidate.endX) {
                true
            } else {
                startX = candidate.endX
                startY = candidate.endY
                false
            }
        }
        var low = 0f
        var high = 1f
        repeat(16) {
            val mid = (low + high) * 0.5f
            if (
                cubic(
                    start = startX,
                    control1 = segment.control1X,
                    control2 = segment.control2X,
                    end = segment.endX,
                    fraction = mid,
                ) < targetX
            ) {
                low = mid
            } else {
                high = mid
            }
        }
        return cubic(
            start = startY,
            control1 = segment.control1Y,
            control2 = segment.control2Y,
            end = segment.endY,
            fraction = (low + high) * 0.5f,
        )
    }

    private fun cubic(
        start: Float,
        control1: Float,
        control2: Float,
        end: Float,
        fraction: Float,
    ): Float {
        val inverse = 1f - fraction
        return inverse * inverse * inverse * start +
            3f * inverse * inverse * fraction * control1 +
            3f * inverse * fraction * fraction * control2 +
            fraction * fraction * fraction * end
    }

    companion object {
        val Standard = NavMotionEasing(
            x1 = 0.2f,
            y1 = 0f,
            x2 = 0f,
            y2 = 1f,
        )
        val Accelerate = NavMotionEasing(
            x1 = 0.3f,
            y1 = 0f,
            x2 = 1f,
            y2 = 1f,
        )
        val Linear = NavMotionEasing(
            x1 = 0f,
            y1 = 0f,
            x2 = 1f,
            y2 = 1f,
        )
        val BackGesture = NavMotionEasing(
            x1 = 0.1f,
            y1 = 0.1f,
            x2 = 0f,
            y2 = 1f,
        )
        val Emphasized = NavMotionEasing(
            segments = listOf(
                NavMotionPathSegment(
                    control1X = 0.05f,
                    control1Y = 0f,
                    control2X = 0.133333f,
                    control2Y = 0.06f,
                    endX = 0.166666f,
                    endY = 0.4f,
                ),
                NavMotionPathSegment(
                    control1X = 0.208333f,
                    control1Y = 0.82f,
                    control2X = 0.25f,
                    control2Y = 1f,
                    endX = 1f,
                    endY = 1f,
                ),
            ),
        )
    }
}

internal data class NavMotionPathSegment(
    val control1X: Float,
    val control1Y: Float,
    val control2X: Float,
    val control2Y: Float,
    val endX: Float,
    val endY: Float,
) {
    val values: List<Float>
        get() = listOf(control1X, control1Y, control2X, control2Y, endX, endY)
}

/**
 * Independent timing for one navigation property.
 */
data class NavMotionTiming(
    val durationMillis: Long,
    val startDelayMillis: Long = 0L,
    val easing: NavMotionEasing = NavMotionEasing.Linear,
) {
    init {
        require(durationMillis >= 0L) {
            "Navigation property duration must not be negative."
        }
        require(startDelayMillis >= 0L) {
            "Navigation property start delay must not be negative."
        }
    }

    internal val endTimeMillis: Long
        get() = startDelayMillis + durationMillis

    internal fun progressAt(playTimeMillis: Long): Float {
        if (durationMillis == 0L) {
            return if (playTimeMillis >= startDelayMillis) 1f else 0f
        }
        val linearProgress = (
            (playTimeMillis - startDelayMillis).toFloat() /
                durationMillis.toFloat()
            ).coerceIn(0f, 1f)
        return easing.transform(linearProgress)
    }
}

/**
 * Physics used to settle gesture-driven navigation motion.
 */
data class NavSpringSpec(
    val stiffness: Float,
    val dampingRatio: Float,
    val maxDurationMillis: Long = 500L,
) {
    init {
        require(stiffness.isFinite() && stiffness > 0f) {
            "Navigation spring stiffness must be finite and greater than zero."
        }
        require(dampingRatio.isFinite() && dampingRatio > 0f) {
            "Navigation spring damping ratio must be finite and greater than zero."
        }
        require(maxDurationMillis > 0L) {
            "Navigation spring maximum duration must be greater than zero."
        }
    }
}

/**
 * Gesture-driven motion used before and immediately after a predictive Back transaction settles.
 *
 * Progress velocity is estimated from [velocitySampleWindowMillis] and capped by
 * [maxProgressVelocity] before it becomes the initial velocity of [commitSpring] or
 * [cancelSpring].
 */
data class NavPredictiveBackSpec(
    val incomingStart: NavDestinationTransform,
    val incomingEnd: NavDestinationTransform = NavDestinationTransform(),
    val outgoingEnd: NavDestinationTransform,
    val progressEasing: NavMotionEasing = NavMotionEasing.Linear,
    val commitMotion: NavDestinationMotionSpec = DefaultCommitMotion,
    val cancelSpring: NavSpringSpec = DefaultCancelSpring,
    val velocitySampleWindowMillis: Long = 100L,
    val maxProgressVelocity: Float = 4f,
) {
    init {
        require(velocitySampleWindowMillis > 0L) {
            "Predictive Back velocity sample window must be greater than zero."
        }
        require(maxProgressVelocity.isFinite() && maxProgressVelocity > 0f) {
            "Predictive Back maximum progress velocity must be finite and greater than zero."
        }
    }

    internal val isDisabled: Boolean
        get() = incomingStart.isIdentity &&
            incomingEnd.isIdentity &&
            outgoingEnd.isIdentity &&
            commitMotion.isDisabled

    companion object {
        private val DefaultCommitMotion = NavDestinationMotionSpec(
            durationMillis = 450L,
            outgoingEnd = NavDestinationTransform(
                travelDp = 96f,
                alpha = 0f,
            ),
            easing = NavMotionEasing.Emphasized,
            outgoingAlphaTiming = NavMotionTiming(
                durationMillis = 90L,
                easing = NavMotionEasing.Linear,
            ),
        )
        private val DefaultCancelSpring = NavSpringSpec(
            stiffness = 900f,
            dampingRatio = 1f,
            maxDurationMillis = 450L,
        )
        val None = NavPredictiveBackSpec(
            incomingStart = NavDestinationTransform(),
            incomingEnd = NavDestinationTransform(),
            outgoingEnd = NavDestinationTransform(),
            commitMotion = NavDestinationMotionSpec.None,
        )
    }
}

/**
 * Motion for one committed navigation command.
 *
 * [incomingStart] describes the entering destination before the animation. [outgoingEnd]
 * describes the leaving destination at the end of the animation.
 */
data class NavDestinationMotionSpec(
    val durationMillis: Long,
    val incomingStart: NavDestinationTransform = NavDestinationTransform(),
    val outgoingEnd: NavDestinationTransform = NavDestinationTransform(),
    val easing: NavMotionEasing = NavMotionEasing.Standard,
    val incomingAlphaTiming: NavMotionTiming = NavMotionTiming(
        durationMillis = durationMillis,
        easing = easing,
    ),
    val outgoingAlphaTiming: NavMotionTiming = NavMotionTiming(
        durationMillis = durationMillis,
        easing = easing,
    ),
) {
    init {
        require(durationMillis >= 0L) {
            "Navigation transition duration must not be negative."
        }
    }

    internal val totalDurationMillis: Long
        get() = maxOf(
            durationMillis,
            incomingAlphaTiming.endTimeMillis,
            outgoingAlphaTiming.endTimeMillis,
        )

    internal val isDisabled: Boolean
        get() = totalDurationMillis == 0L ||
            (incomingStart.isIdentity && outgoingEnd.isIdentity)

    companion object {
        val None = NavDestinationMotionSpec(durationMillis = 0L)
    }
}

/**
 * Command-aware native View motion policy applied after navigation transactions commit.
 *
 * Motion remains visual policy: changing it never mutates the back stack, destination ownership,
 * or lifecycle plan.
 */
data class NavTransitionSpec(
    val push: NavDestinationMotionSpec = DefaultPush,
    val pop: NavDestinationMotionSpec = DefaultPop,
    val replace: NavDestinationMotionSpec = DefaultReplace,
    val reset: NavDestinationMotionSpec = DefaultReset,
    val stackSelection: NavDestinationMotionSpec = DefaultStackSelection,
    val deepLink: NavDestinationMotionSpec = DefaultDeepLink,
    val predictiveBack: NavPredictiveBackSpec = DefaultPredictiveBack,
) {
    internal fun motionFor(command: NavCommand): NavDestinationMotionSpec {
        return when (command) {
            is NavCommand.Push -> push
            NavCommand.Pop -> pop
            is NavCommand.ReplaceTop -> replace
            is NavCommand.Reset -> reset
            is NavCommand.SelectStack,
            NavCommand.PopStackHistory,
            -> stackSelection

            is NavCommand.OpenDeepLink -> deepLink
        }
    }

    companion object {
        // Mirrors current AOSP activity_open_*.xml/activity_close_*.xml motion: 96dp travel,
        // 450ms fast_out_extra_slow_in geometry, and independently timed 83ms alpha windows.
        private val DefaultPush = NavDestinationMotionSpec(
            durationMillis = 450L,
            incomingStart = NavDestinationTransform(
                travelDp = 96f,
                alpha = 0f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelDp = 96f,
            ),
            easing = NavMotionEasing.Emphasized,
            incomingAlphaTiming = NavMotionTiming(
                durationMillis = 83L,
                startDelayMillis = 50L,
                easing = NavMotionEasing.Linear,
            ),
        )
        private val DefaultPop = NavDestinationMotionSpec(
            durationMillis = 450L,
            incomingStart = NavDestinationTransform(
                travelDp = 96f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelDp = 96f,
                alpha = 0f,
            ),
            easing = NavMotionEasing.Emphasized,
            outgoingAlphaTiming = NavMotionTiming(
                durationMillis = 83L,
                startDelayMillis = 35L,
                easing = NavMotionEasing.Linear,
            ),
        )
        private val DefaultReplace = NavDestinationMotionSpec(
            durationMillis = 220L,
            incomingStart = NavDestinationTransform(
                travelFraction = 0.03f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.015f,
            ),
        )
        private val DefaultReset = NavDestinationMotionSpec(
            durationMillis = 260L,
            incomingStart = NavDestinationTransform(
                travelFraction = 0.04f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.02f,
            ),
        )
        private val DefaultStackSelection = NavDestinationMotionSpec(
            durationMillis = 180L,
            incomingStart = NavDestinationTransform(travelFraction = 0.015f),
            outgoingEnd = NavDestinationTransform(travelFraction = 0.01f),
        )
        private val DefaultDeepLink = NavDestinationMotionSpec(
            durationMillis = 280L,
            incomingStart = NavDestinationTransform(
                travelFraction = 0.04f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.015f,
            ),
        )
        // Mirrors WM Shell DefaultCrossActivityBackAnimation geometry and back-gesture easing.
        private val DefaultPredictiveBack = NavPredictiveBackSpec(
            incomingStart = NavDestinationTransform(
                travelDp = 96f,
            ),
            incomingEnd = NavDestinationTransform(
                travelDp = 96f,
                scale = 0.9f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.05f,
                travelDp = -8f,
                scale = 0.9f,
            ),
            progressEasing = NavMotionEasing.BackGesture,
        )

        val Default = NavTransitionSpec()
        val None = NavTransitionSpec(
            push = NavDestinationMotionSpec.None,
            pop = NavDestinationMotionSpec.None,
            replace = NavDestinationMotionSpec.None,
            reset = NavDestinationMotionSpec.None,
            stackSelection = NavDestinationMotionSpec.None,
            deepLink = NavDestinationMotionSpec.None,
            predictiveBack = NavPredictiveBackSpec.None,
        )
    }
}
