package com.viewcompose.navigation

/**
 * Native View motion applied after a navigation transaction commits.
 *
 * The motion is intentionally a policy object rather than navigation state. Changing it affects
 * later transitions and never changes the back stack or destination ownership.
 */
data class NavTransitionSpec(
    val durationMillis: Long = 260L,
    val travelFraction: Float = 0.12f,
    val fadeEnabled: Boolean = true,
) {
    init {
        require(durationMillis >= 0L) {
            "Navigation transition duration must not be negative."
        }
        require(travelFraction.isFinite() && travelFraction in 0f..1f) {
            "Navigation transition travel fraction must be finite and between 0 and 1."
        }
    }

    companion object {
        val Default = NavTransitionSpec()
        val None = NavTransitionSpec(
            durationMillis = 0L,
            travelFraction = 0f,
            fadeEnabled = false,
        )
    }
}
