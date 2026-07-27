package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavCommand

/**
 * Visual transform applied to one destination at an edge of a navigation transition.
 *
 * [travelFraction] is relative to one visible pane. The transition direction is resolved from the
 * navigation command and layout direction by the Android host.
 */
data class NavDestinationTransform(
    val travelFraction: Float = 0f,
    val alpha: Float = 1f,
    val scale: Float = 1f,
) {
    init {
        require(travelFraction.isFinite() && travelFraction in 0f..1f) {
            "Navigation travel fraction must be finite and between 0 and 1."
        }
        require(alpha.isFinite() && alpha in 0f..1f) {
            "Navigation alpha must be finite and between 0 and 1."
        }
        require(scale.isFinite() && scale > 0f) {
            "Navigation scale must be finite and greater than zero."
        }
    }

    internal val isIdentity: Boolean
        get() = travelFraction == 0f && alpha == 1f && scale == 1f
}

/**
 * Cubic Bézier timing curve used by native navigation motion.
 */
data class NavMotionEasing(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) {
    init {
        require(listOf(x1, y1, x2, y2).all(Float::isFinite)) {
            "Navigation easing control points must be finite."
        }
        require(x1 in 0f..1f && x2 in 0f..1f) {
            "Navigation easing x control points must be between 0 and 1."
        }
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
) {
    init {
        require(durationMillis >= 0L) {
            "Navigation transition duration must not be negative."
        }
    }

    internal val isDisabled: Boolean
        get() = durationMillis == 0L ||
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
        private val DefaultPush = NavDestinationMotionSpec(
            durationMillis = 300L,
            incomingStart = NavDestinationTransform(
                travelFraction = 0.08f,
                alpha = 0.9f,
                scale = 0.985f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.025f,
                alpha = 0.94f,
            ),
        )
        private val DefaultPop = NavDestinationMotionSpec(
            durationMillis = 260L,
            incomingStart = NavDestinationTransform(
                travelFraction = 0.025f,
                alpha = 0.94f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.08f,
                alpha = 0.88f,
                scale = 0.985f,
            ),
        )
        private val DefaultReplace = NavDestinationMotionSpec(
            durationMillis = 220L,
            incomingStart = NavDestinationTransform(
                alpha = 0.88f,
                scale = 0.98f,
            ),
            outgoingEnd = NavDestinationTransform(
                alpha = 0f,
                scale = 1.015f,
            ),
        )
        private val DefaultReset = NavDestinationMotionSpec(
            durationMillis = 260L,
            incomingStart = NavDestinationTransform(
                alpha = 0.86f,
                scale = 0.97f,
            ),
            outgoingEnd = NavDestinationTransform(
                alpha = 0f,
                scale = 1.02f,
            ),
        )
        private val DefaultStackSelection = NavDestinationMotionSpec(
            durationMillis = 180L,
            incomingStart = NavDestinationTransform(alpha = 0f),
            outgoingEnd = NavDestinationTransform(alpha = 0f),
        )
        private val DefaultDeepLink = NavDestinationMotionSpec(
            durationMillis = 280L,
            incomingStart = NavDestinationTransform(
                travelFraction = 0.04f,
                alpha = 0.84f,
                scale = 0.975f,
            ),
            outgoingEnd = NavDestinationTransform(
                travelFraction = 0.015f,
                alpha = 0f,
                scale = 1.015f,
            ),
        )

        val Default = NavTransitionSpec()
        val None = NavTransitionSpec(
            push = NavDestinationMotionSpec.None,
            pop = NavDestinationMotionSpec.None,
            replace = NavDestinationMotionSpec.None,
            reset = NavDestinationMotionSpec.None,
            stackSelection = NavDestinationMotionSpec.None,
            deepLink = NavDestinationMotionSpec.None,
        )
    }
}
