package com.viewcompose.navigation

/**
 * Controls how many hidden destination presentations a [NavHost] keeps alive.
 *
 * A presentation is the destination's child render session and native View hierarchy. Disposing
 * it never removes the logical navigation entry or clears that entry's lifecycle owner,
 * ViewModelStore, saved state, or route arguments. The presentation is rebuilt transactionally
 * before the entry becomes visible again.
 *
 * @sample com.viewcompose.navigation.samples.BoundedPresentationNavigation
 */
sealed interface NavPresentationRetentionPolicy {
    /**
     * Disposes every destination presentation after it becomes fully hidden.
     *
     * This is the default because it places a strict bound on native Views, render effects, focus,
     * accessibility state, and other presentation-only resources.
     */
    data object DisposeWhenHidden : NavPresentationRetentionPolicy

    /**
     * Retains every hidden destination presentation until its logical entry is removed.
     *
     * Choose this only for measured, expensive-to-rebuild destinations. The retained render
     * sessions remain inactive while hidden, but their native Views and presentation resources
     * continue to consume memory.
     */
    data object RetainAll : NavPresentationRetentionPolicy

    /**
     * Retains at most [maxHiddenPresentations] hidden presentations using least-recently-hidden
     * eviction. Visible and transitioning presentations never count against this bound.
     *
     * @property maxHiddenPresentations positive maximum number of hidden presentations
     */
    data class Bounded(
        val maxHiddenPresentations: Int,
    ) : NavPresentationRetentionPolicy {
        init {
            require(maxHiddenPresentations > 0) {
                "Navigation hidden-presentation limit must be positive."
            }
        }
    }

    /** Common presentation-retention policies. */
    companion object {
        /** Strict resource-bound default for navigation hosts. */
        val Default: NavPresentationRetentionPolicy = DisposeWhenHidden
    }
}
