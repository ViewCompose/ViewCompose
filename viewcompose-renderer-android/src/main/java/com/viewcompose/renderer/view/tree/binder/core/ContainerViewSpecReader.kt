package com.viewcompose.renderer.view.tree

import android.view.Gravity
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedBoundsHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentItemNodeProps
import com.viewcompose.ui.node.spec.LayoutConstraintHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.FlowColumnNodeProps
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.renderer.view.roundToPx

/**
 * Converts container NodeSpecs into platform-neutral intermediate specs used by binders.
 * Converts container NodeSpec values into platform-neutral intermediate specs consumed by binders.
 */
internal object ContainerViewSpecReader {
    /**
     * Reads linear-layout properties for a Row.
     * Reads Row linear layout parameters.
     */
    fun readRowSpec(node: VNode): ContainerViewBinder.LinearSpec {
        val spec = node.requireSpec<RowNodeProps>()
        return ContainerViewBinder.LinearSpec(
            spacing = node.environment.roundToPx(spec.spacing),
            arrangement = spec.arrangement,
            gravity = spec.verticalAlignment.toGravity(),
        )
    }

    fun readColumnSpec(node: VNode): ContainerViewBinder.LinearSpec {
        val spec = node.requireSpec<ColumnNodeProps>()
        return ContainerViewBinder.LinearSpec(
            spacing = node.environment.roundToPx(spec.spacing),
            arrangement = spec.arrangement,
            gravity = spec.horizontalAlignment.toGravity(),
        )
    }

    fun readFlowRowSpec(node: VNode): ContainerViewBinder.FlowRowSpec {
        val spec = node.requireSpec<FlowRowNodeProps>()
        return ContainerViewBinder.FlowRowSpec(
            horizontalSpacing = node.environment.roundToPx(spec.horizontalSpacing),
            verticalSpacing = node.environment.roundToPx(spec.verticalSpacing),
            maxItemsInEachRow = spec.maxItemsInEachRow,
        )
    }

    fun readFlowColumnSpec(node: VNode): ContainerViewBinder.FlowColumnSpec {
        val spec = node.requireSpec<FlowColumnNodeProps>()
        return ContainerViewBinder.FlowColumnSpec(
            horizontalSpacing = node.environment.roundToPx(spec.horizontalSpacing),
            verticalSpacing = node.environment.roundToPx(spec.verticalSpacing),
            maxItemsInEachColumn = spec.maxItemsInEachColumn,
        )
    }

    fun readBoxSpec(node: VNode): ContainerViewBinder.BoxSpec {
        val spec = node.requireSpec<BoxNodeProps>()
        return ContainerViewBinder.BoxSpec(
            gravity = spec.contentAlignment.toGravity(),
        )
    }

    fun readSurfaceSpec(node: VNode): ContainerViewBinder.BoxSpec {
        val spec = node.requireSpec<SurfaceNodeProps>()
        return ContainerViewBinder.BoxSpec(
            gravity = spec.contentAlignment.toGravity(),
        )
    }

    fun readConstraintLayoutSpec(node: VNode): ContainerViewBinder.ConstraintLayoutSpec {
        val spec = node.requireSpec<ConstraintLayoutNodeProps>()
        return ContainerViewBinder.ConstraintLayoutSpec(
            decoupledConstraintSet = spec.constraintSet,
            inlineHelpers = spec.helpers,
        )
    }

    fun readDividerSpec(node: VNode): ContainerViewBinder.DividerSpec {
        val spec = node.requireSpec<DividerNodeProps>()
        return ContainerViewBinder.DividerSpec(
            color = spec.color,
            thickness = node.environment.roundToPx(spec.thickness),
        )
    }

    fun readAnimatedVisibilityHostSpec(node: VNode): ContainerViewBinder.AnimatedVisibilityHostSpec {
        val spec = node.requireSpec<AnimatedVisibilityHostNodeProps>()
        return ContainerViewBinder.AnimatedVisibilityHostSpec(
            alpha = spec.alpha,
            widthScale = spec.widthScale,
            heightScale = spec.heightScale,
            scaleX = spec.scaleX,
            scaleY = spec.scaleY,
            translationXFraction = spec.translationXFraction,
            translationYFraction = spec.translationYFraction,
            pivotFractionX = spec.transformOrigin.pivotFractionX,
            pivotFractionY = spec.transformOrigin.pivotFractionY,
            contentGravity = spec.contentAlignment.toGravity(),
            clipToBounds = spec.clipToBounds,
            active = spec.active,
        )
    }

    fun readAnimatedContentHostSpec(node: VNode): ContainerViewBinder.AnimatedContentHostSpec {
        val spec = node.requireSpec<AnimatedContentHostNodeProps>()
        return ContainerViewBinder.AnimatedContentHostSpec(
            segmentId = spec.segmentId,
            sizeProgress = spec.sizeProgress,
            sizeTransformEnabled = spec.sizeTransformEnabled,
            clipToBounds = spec.clipToBounds,
            contentGravity = spec.contentAlignment.toGravity(),
        )
    }

    fun readAnimatedContentItemSpec(node: VNode): ContainerViewBinder.AnimatedContentItemSpec {
        val spec = node.requireSpec<AnimatedContentItemNodeProps>()
        return ContainerViewBinder.AnimatedContentItemSpec(
            alpha = spec.alpha,
            scaleX = spec.scaleX,
            scaleY = spec.scaleY,
            translationXFraction = spec.translationXFraction,
            translationYFraction = spec.translationYFraction,
            revealWidthFraction = spec.revealWidthFraction,
            revealHeightFraction = spec.revealHeightFraction,
            pivotFractionX = spec.transformOrigin.pivotFractionX,
            pivotFractionY = spec.transformOrigin.pivotFractionY,
            active = spec.active,
        )
    }

    fun readAnimatedSizeHostSpec(node: VNode): ContainerViewBinder.AnimatedSizeHostSpec {
        val spec = node.requireSpec<AnimatedSizeHostNodeProps>()
        return ContainerViewBinder.AnimatedSizeHostSpec(
            animationSpec = spec.animationSpec,
        )
    }

    fun readAnimatedBoundsHostSpec(node: VNode): ContainerViewBinder.AnimatedBoundsHostSpec {
        val spec = node.requireSpec<AnimatedBoundsHostNodeProps>()
        return ContainerViewBinder.AnimatedBoundsHostSpec(
            animationSpec = spec.animationSpec,
        )
    }

    fun readLayoutConstraintHostSpec(node: VNode): ContainerViewBinder.LayoutConstraintHostSpec {
        val spec = node.requireSpec<LayoutConstraintHostNodeProps>()
        return ContainerViewBinder.LayoutConstraintHostSpec(
            maxWidthPx = spec.maxWidth?.let(node.environment::roundToPx),
            maxHeightPx = spec.maxHeight?.let(node.environment::roundToPx),
            aspectRatio = spec.aspectRatio,
            matchHeightConstraintsFirst = spec.matchHeightConstraintsFirst,
            fillWidth = spec.fillWidth,
            fillHeight = spec.fillHeight,
        )
    }

    fun readAndroidViewSpec(node: VNode): ContainerViewBinder.AndroidViewSpec {
        val spec = node.requireSpec<AndroidViewNodeProps>()
        return ContainerViewBinder.AndroidViewSpec(
            update = spec.update,
        )
    }

    /**
     * Converts vertical alignment to Android gravity.
     * Converts vertical alignment to Android gravity.
     */
    internal fun VerticalAlignment.toGravity(): Int {
        return when (this) {
            VerticalAlignment.Top -> Gravity.TOP
            VerticalAlignment.Center -> Gravity.CENTER_VERTICAL
            VerticalAlignment.Bottom -> Gravity.BOTTOM
        }
    }

    /**
     * Converts horizontal alignment to Android gravity.
     * Converts horizontal alignment to Android gravity.
     */
    internal fun HorizontalAlignment.toGravity(): Int {
        return when (this) {
            HorizontalAlignment.Start -> Gravity.START
            HorizontalAlignment.Center -> Gravity.CENTER_HORIZONTAL
            HorizontalAlignment.End -> Gravity.END
        }
    }

    /**
     * Converts Box content alignment to Android gravity.
     * Converts Box content alignment to Android gravity.
     */
    internal fun BoxAlignment.toGravity(): Int {
        return when (this) {
            BoxAlignment.TopStart -> Gravity.TOP or Gravity.START
            BoxAlignment.TopCenter -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            BoxAlignment.TopEnd -> Gravity.TOP or Gravity.END
            BoxAlignment.CenterStart -> Gravity.CENTER_VERTICAL or Gravity.START
            BoxAlignment.Center -> Gravity.CENTER
            BoxAlignment.CenterEnd -> Gravity.CENTER_VERTICAL or Gravity.END
            BoxAlignment.BottomStart -> Gravity.BOTTOM or Gravity.START
            BoxAlignment.BottomCenter -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            BoxAlignment.BottomEnd -> Gravity.BOTTOM or Gravity.END
        }
    }
}
