package com.viewcompose.renderer.view.tree

import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentItemLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.ConstraintRebuildReason
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeLayoutConstraintHost
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.lazy.adapter.LazyListSpacingDecoration
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec

/**
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
        val scaleX: Float,
        val scaleY: Float,
        val translationXFraction: Float,
        val translationYFraction: Float,
        val pivotFractionX: Float,
        val pivotFractionY: Float,
        val contentGravity: Int,
        val clipToBounds: Boolean,
        val active: Boolean,
    )

    data class AnimatedContentHostSpec(
        val segmentId: Long,
        val sizeProgress: Float,
        val sizeTransformEnabled: Boolean,
        val clipToBounds: Boolean,
        val contentGravity: Int,
    )

    data class AnimatedContentItemSpec(
        val alpha: Float,
        val scaleX: Float,
        val scaleY: Float,
        val translationXFraction: Float,
        val translationYFraction: Float,
        val revealWidthFraction: Float,
        val revealHeightFraction: Float,
        val pivotFractionX: Float,
        val pivotFractionY: Float,
        val active: Boolean,
    )

    data class AnimatedSizeHostSpec(
        val animationSpec: ContentSizeAnimationSpecModel,
    )

    data class LayoutConstraintHostSpec(
        val maxWidthPx: Int?,
        val maxHeightPx: Int?,
        val aspectRatio: Float?,
        val matchHeightConstraintsFirst: Boolean,
        val fillWidth: Boolean,
        val fillHeight: Boolean,
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
        view.requestConstraintRebuild(ConstraintRebuildReason.ContentOnly)
    }

    fun bindAnimatedVisibilityHost(
        view: DeclarativeAnimatedVisibilityHostLayout,
        spec: AnimatedVisibilityHostSpec,
    ) {
        view.alpha = spec.alpha
        view.widthScale = spec.widthScale
        view.heightScale = spec.heightScale
        view.visualScaleX = spec.scaleX
        view.visualScaleY = spec.scaleY
        view.translationXFraction = spec.translationXFraction
        view.translationYFraction = spec.translationYFraction
        view.pivotFractionX = spec.pivotFractionX
        view.pivotFractionY = spec.pivotFractionY
        view.contentGravity = spec.contentGravity
        view.clipToBounds = spec.clipToBounds
        view.contentActive = spec.active
    }

    fun bindAnimatedContentHost(
        view: DeclarativeAnimatedContentHostLayout,
        spec: AnimatedContentHostSpec,
    ) {
        view.segmentId = spec.segmentId
        view.sizeProgress = spec.sizeProgress
        view.sizeTransformEnabled = spec.sizeTransformEnabled
        view.clipToBounds = spec.clipToBounds
        view.contentGravity = spec.contentGravity
    }

    fun bindAnimatedContentItem(
        view: DeclarativeAnimatedContentItemLayout,
        spec: AnimatedContentItemSpec,
    ) {
        view.alpha = spec.alpha.coerceIn(0f, 1f)
        view.scaleX = spec.scaleX
        view.scaleY = spec.scaleY
        view.translationXFraction = spec.translationXFraction
        view.translationYFraction = spec.translationYFraction
        view.revealWidthFraction = spec.revealWidthFraction
        view.revealHeightFraction = spec.revealHeightFraction
        view.pivotFractionX = spec.pivotFractionX
        view.pivotFractionY = spec.pivotFractionY
        view.contentActive = spec.active
    }

    fun bindAnimatedSizeHost(
        view: DeclarativeAnimatedSizeHostLayout,
        spec: AnimatedSizeHostSpec,
    ) {
        view.animationSpec = spec.animationSpec
    }

    fun bindLayoutConstraintHost(
        view: DeclarativeLayoutConstraintHost,
        spec: LayoutConstraintHostSpec,
    ) {
        view.bind(
            maxWidthPx = spec.maxWidthPx,
            maxHeightPx = spec.maxHeightPx,
            aspectRatio = spec.aspectRatio,
            matchHeightConstraintsFirst = spec.matchHeightConstraintsFirst,
            fillWidth = spec.fillWidth,
            fillHeight = spec.fillHeight,
        )
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

    fun readAnimatedContentHostSpec(node: VNode): AnimatedContentHostSpec {
        return ContainerViewSpecReader.readAnimatedContentHostSpec(node)
    }

    fun readAnimatedContentItemSpec(node: VNode): AnimatedContentItemSpec {
        return ContainerViewSpecReader.readAnimatedContentItemSpec(node)
    }

    fun readAnimatedSizeHostSpec(node: VNode): AnimatedSizeHostSpec {
        return ContainerViewSpecReader.readAnimatedSizeHostSpec(node)
    }

    fun readLayoutConstraintHostSpec(node: VNode): LayoutConstraintHostSpec {
        return ContainerViewSpecReader.readLayoutConstraintHostSpec(node)
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
