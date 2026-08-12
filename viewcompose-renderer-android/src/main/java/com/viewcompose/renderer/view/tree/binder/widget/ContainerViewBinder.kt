package com.viewcompose.renderer.view.tree

import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.lazy.adapter.LazyListSpacingDecoration
import com.viewcompose.renderer.view.PaddingPx

/**
 * Binds base container nodes and maps direction, alignment, spacing, constraints, and animation hosts to Views.
 * Binds primitive container nodes and maps orientation, alignment, spacing, constraints, and
 * animation-host state onto Android Views.
 */
internal object ContainerViewBinder {
    data class LinearSpec(
        val spacing: Int,
        val arrangement: MainAxisArrangement,
        val gravity: Int,
    )

    data class BoxSpec(
        val gravity: Int,
    )

    data class ConstraintLayoutSpec(
        val decoupledConstraintSet: ConstraintSetSpec?,
        val inlineHelpers: ConstraintHelpersSpec,
    )

    data class DividerSpec(
        val color: Int,
        val thickness: Int,
    )

    data class AnimatedVisibilityHostSpec(
        val alpha: Float,
        val widthScale: Float,
        val heightScale: Float,
        val clipToBounds: Boolean,
    )

    data class AnimatedSizeHostSpec(
        val animationSpec: ContentSizeAnimationSpecModel,
    )

    data class AndroidViewSpec(
        val update: ((Any) -> Unit)?,
    )

    data class FlowRowSpec(
        val horizontalSpacing: Int,
        val verticalSpacing: Int,
        val maxItemsInEachRow: Int,
    )

    data class FlowColumnSpec(
        val horizontalSpacing: Int,
        val verticalSpacing: Int,
        val maxItemsInEachColumn: Int,
    )

    fun bindRow(
        view: DeclarativeLinearLayout,
        spec: LinearSpec,
    ) {
        view.orientation = LinearLayout.HORIZONTAL
        view.itemSpacing = spec.spacing
        view.mainAxisArrangement = spec.arrangement
        view.gravity = spec.gravity
    }

    fun bindColumn(
        view: DeclarativeLinearLayout,
        spec: LinearSpec,
    ) {
        view.orientation = LinearLayout.VERTICAL
        view.itemSpacing = spec.spacing
        view.mainAxisArrangement = spec.arrangement
        view.gravity = spec.gravity
    }

    fun bindBox(
        view: DeclarativeBoxLayout,
        spec: BoxSpec,
    ) {
        view.contentGravity = spec.gravity
    }

    fun bindConstraintLayout(
        view: DeclarativeConstraintLayout,
        spec: ConstraintLayoutSpec,
    ) {
        view.decoupledConstraintSetSpec = spec.decoupledConstraintSet
        view.inlineHelpersSpec = spec.inlineHelpers
        // Environment changes do not change the logical constraint spec, but they do change
        // every dp-to-pixel result. A full node rebind must therefore rebuild ConstraintSet.
        view.requestConstraintRebuild()
    }

    fun bindAnimatedVisibilityHost(
        view: DeclarativeAnimatedVisibilityHostLayout,
        spec: AnimatedVisibilityHostSpec,
    ) {
        view.alpha = spec.alpha
        view.widthScale = spec.widthScale
        view.heightScale = spec.heightScale
        view.clipToBounds = spec.clipToBounds
    }

    fun bindAnimatedSizeHost(
        view: DeclarativeAnimatedSizeHostLayout,
        spec: AnimatedSizeHostSpec,
    ) {
        view.animationSpec = spec.animationSpec
    }

    fun bindFlowRow(
        view: DeclarativeFlowRowLayout,
        spec: FlowRowSpec,
    ) {
        view.horizontalSpacing = spec.horizontalSpacing
        view.verticalSpacing = spec.verticalSpacing
        view.maxItemsInEachRow = spec.maxItemsInEachRow
    }

    fun bindFlowColumn(
        view: DeclarativeFlowColumnLayout,
        spec: FlowColumnSpec,
    ) {
        view.horizontalSpacing = spec.horizontalSpacing
        view.verticalSpacing = spec.verticalSpacing
        view.maxItemsInEachColumn = spec.maxItemsInEachColumn
    }

    fun readRowSpec(node: VNode): LinearSpec {
        return ContainerViewSpecReader.readRowSpec(node)
    }

    fun readColumnSpec(node: VNode): LinearSpec {
        return ContainerViewSpecReader.readColumnSpec(node)
    }

    fun readFlowRowSpec(node: VNode): FlowRowSpec {
        return ContainerViewSpecReader.readFlowRowSpec(node)
    }

    fun readFlowColumnSpec(node: VNode): FlowColumnSpec {
        return ContainerViewSpecReader.readFlowColumnSpec(node)
    }

    fun readBoxSpec(node: VNode): BoxSpec {
        return ContainerViewSpecReader.readBoxSpec(node)
    }

    fun readSurfaceSpec(node: VNode): BoxSpec {
        return ContainerViewSpecReader.readSurfaceSpec(node)
    }

    fun readConstraintLayoutSpec(node: VNode): ConstraintLayoutSpec {
        return ContainerViewSpecReader.readConstraintLayoutSpec(node)
    }

    fun readDividerSpec(node: VNode): DividerSpec {
        return ContainerViewSpecReader.readDividerSpec(node)
    }

    fun readAnimatedVisibilityHostSpec(node: VNode): AnimatedVisibilityHostSpec {
        return ContainerViewSpecReader.readAnimatedVisibilityHostSpec(node)
    }

    fun readAnimatedSizeHostSpec(node: VNode): AnimatedSizeHostSpec {
        return ContainerViewSpecReader.readAnimatedSizeHostSpec(node)
    }

    fun readAndroidViewSpec(node: VNode): AndroidViewSpec {
        return ContainerViewSpecReader.readAndroidViewSpec(node)
    }

    internal fun applyLazyListPadding(
        recyclerView: RecyclerView,
        padding: PaddingPx,
    ) {
        ModifierInsetsApplier.applyLazyContentPadding(recyclerView, padding)
        val shouldClipToPadding =
            padding.left == 0 && padding.top == 0 && padding.right == 0 && padding.bottom == 0
        if (recyclerView.clipToPadding != shouldClipToPadding) {
            recyclerView.clipToPadding = shouldClipToPadding
        }
    }

    internal fun applyLazyListSpacing(
        recyclerView: RecyclerView,
        spacing: Int,
        orientation: Int = LinearLayoutManager.VERTICAL,
    ) {
        val existing = recyclerView.getTag(R.id.viewcompose_lazy_spacing_decoration) as? LazyListSpacingDecoration
        if (existing != null) {
            if (existing.update(spacing, orientation)) {
                recyclerView.invalidateItemDecorations()
            }
            return
        }
        val decoration = LazyListSpacingDecoration(spacing, orientation)
        recyclerView.setTag(R.id.viewcompose_lazy_spacing_decoration, decoration)
        recyclerView.addItemDecoration(decoration)
    }
}
