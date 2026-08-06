package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavPaneStrategies
import com.viewcompose.navigation.core.NavPaneStrategy
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Resolves the number of simultaneously visible navigation panes from native View width.
 *
 * A pane is admitted only when every pane can retain at least [minPaneWidthDp]. The same committed
 * back stack and entry owners are reused when the width changes.
 *
 * @property strategy maps the active back stack to logical pane roles
 * @property minPaneWidthDp finite positive width required for each visible pane
 * @property maxPaneCount maximum simultaneous panes in `1..3`
 * @property paneSpacingDp finite non-negative spacing between adjacent panes
 */
data class NavPanePolicy(
    val strategy: NavPaneStrategy = NavPaneStrategies.BackStack,
    val minPaneWidthDp: Float = 320f,
    val maxPaneCount: Int = 3,
    val paneSpacingDp: Float = 0f,
) {
    init {
        require(minPaneWidthDp.isFinite() && minPaneWidthDp > 0f) {
            "Navigation minimum pane width must be finite and positive."
        }
        require(maxPaneCount in 1..3) {
            "Navigation max pane count must be between 1 and 3."
        }
        require(paneSpacingDp.isFinite() && paneSpacingDp >= 0f) {
            "Navigation pane spacing must be finite and non-negative."
        }
    }

    internal fun resolvePaneCount(
        widthPixels: Int,
        density: Float,
    ): Int {
        require(density.isFinite() && density > 0f) {
            "Android display density must be finite and positive."
        }
        if (widthPixels <= 0) {
            return 1
        }
        val widthDp = widthPixels / density
        val count = floor(
            (widthDp + paneSpacingDp) / (minPaneWidthDp + paneSpacingDp),
        ).toInt()
        return count.coerceIn(1, maxPaneCount)
    }

    internal fun resolveSpacingPixels(density: Float): Int {
        require(density.isFinite() && density > 0f) {
            "Android display density must be finite and positive."
        }
        return (paneSpacingDp * density).roundToInt()
    }

    /** Common pane policies. */
    companion object {
        /** Preserves classic full-host destination behavior at every width. */
        val Single = NavPanePolicy(
            strategy = NavPaneStrategies.Single,
            minPaneWidthDp = 1f,
            maxPaneCount = 1,
        )

        /** Shows up to three newest back-stack entries when native width permits. */
        val Adaptive = NavPanePolicy()
    }
}
