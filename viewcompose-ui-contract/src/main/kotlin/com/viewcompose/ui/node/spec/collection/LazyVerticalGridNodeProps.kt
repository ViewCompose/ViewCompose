package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyItemTable
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for a vertically scrolling grid.
 *
 * Focused descendants use the renderer's native child-rectangle protocol without an opt-in field.
 *
 * @property cells fixed or adaptive horizontal cell policy
 * @property contentPadding logical padding inside the scrollable content
 * @property horizontalSpacing spacing between adjacent columns
 * @property verticalSpacing spacing between adjacent rows
 * @property items ordered keyed item models
 * @property state optional command and observation state attached to the native grid
 * @property reverseLayout whether row placement and scrolling start from the opposite edge
 * @property userScrollEnabled whether direct user scrolling is accepted
 * @property prefetchPolicy eager preparation and native view-cache hints
 * @property reusePolicy native item-view pool policy
 * @property motionPolicy native item mutation animation policy
 * @throws IllegalArgumentException when either spacing is negative or non-finite
 */
data class LazyVerticalGridNodeProps(
    val cells: GridCells,
    val contentPadding: LazyContentPadding,
    val horizontalSpacing: UiDp,
    val verticalSpacing: UiDp,
    val items: LazyItemTable,
    val state: LazyListState?,
    val reverseLayout: Boolean = false,
    val userScrollEnabled: Boolean = true,
    val prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
) : NodeSpec {
    init {
        require(horizontalSpacing.value.isFinite() && horizontalSpacing >= UiDp.Zero) {
            "Lazy grid horizontalSpacing must be non-negative and finite."
        }
        require(verticalSpacing.value.isFinite() && verticalSpacing >= UiDp.Zero) {
            "Lazy grid verticalSpacing must be non-negative and finite."
        }
    }
}
