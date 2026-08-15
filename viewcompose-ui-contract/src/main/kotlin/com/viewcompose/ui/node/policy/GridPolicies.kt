package com.viewcompose.ui.node.policy

import com.viewcompose.ui.unit.UiDp

/**
 * Defines how a vertical lazy grid derives its horizontal cell count.
 *
 * This Q3 contract is renderer-neutral. A renderer recomputes [Adaptive] cells from the available
 * inner width whenever layout constraints, content padding, spacing, density, or configuration
 * change; changing the physical column count must not replace logical item sessions.
 *
 * @sample com.viewcompose.ui.samples.gridPolicySample
 */
sealed interface GridCells {
    /**
     * Uses exactly [count] columns.
     *
     * @property count positive column count
     * @throws IllegalArgumentException when [count] is not positive
     */
    data class Fixed(val count: Int) : GridCells {
        init {
            require(count > 0) { "Grid fixed cell count must be greater than zero." }
        }
    }

    /**
     * Fits as many columns as possible while keeping each cell at least [minSize] wide.
     *
     * Remaining horizontal space is distributed by the renderer's grid layout manager. At least
     * one column is produced even when the available width is smaller than [minSize].
     *
     * @property minSize positive minimum cell width
     * @throws IllegalArgumentException when [minSize] is not positive
     */
    data class Adaptive(val minSize: UiDp) : GridCells {
        init {
            require(minSize.value.isFinite() && minSize.value > 0f) {
                "Grid adaptive minimum cell size must be positive and finite."
            }
        }
    }
}

/**
 * Defines how many cells one lazy-grid item occupies.
 *
 * [FullLine] remains correct when an adaptive grid changes column count because the renderer
 * resolves it against the current physical layout rather than composition-time information.
 *
 * @sample com.viewcompose.ui.samples.gridPolicySample
 */
sealed interface GridItemSpan {
    /** Occupies one grid cell. */
    data object Single : GridItemSpan

    /**
     * Occupies [count] cells, capped to the current physical column count by the renderer.
     *
     * @property count positive requested cell count
     * @throws IllegalArgumentException when [count] is not positive
     */
    data class Fixed(val count: Int) : GridItemSpan {
        init {
            require(count > 0) { "Grid item span count must be greater than zero." }
        }
    }

    /** Occupies the complete current grid line. */
    data object FullLine : GridItemSpan
}
