package com.viewcompose.renderer.view.tree

import android.graphics.Color
import com.viewcompose.graphics.core.Brush
import com.viewcompose.ui.modifier.CornerRadiusModifierElement
import com.viewcompose.ui.modifier.ImeInsetsPaddingModifierElement
import com.viewcompose.ui.modifier.PaddingModifierElement
import com.viewcompose.ui.modifier.SystemBarsInsetsPaddingModifierElement
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.resolve
import com.viewcompose.renderer.view.toPx

/**
 * Merges modifiers and NodeSpec visual fields into renderer style models.
 * Merges modifier values and visual fields from NodeSpec into renderer style models.
 */
internal object ModifierNodeStyleResolver {
    /**
     * Resolves style applied directly to the node View.
     * Resolves style applied directly to the node View.
     */
    fun resolveNodeStyle(
        node: VNode,
        resolved: ResolvedModifiers,
        defaultRippleColor: Int,
    ): NodeStyle {
        val surfaceInsets = readNodeSurfaceInsets(node, resolved)
        return NodeStyle(
            backgroundDrawableResId = resolved.backgroundDrawableRes?.resId,
            backgroundColor = resolved.backgroundColor?.color ?: readNodeBackgroundColor(node),
            surfaceFill = readNodeSurfaceFill(node),
            borderWidth = node.environment.roundToPx(
                resolved.border?.width ?: readNodeBorderWidth(node) ?: com.viewcompose.ui.unit.UiDp.Zero,
            ),
            borderColor = resolved.border?.color ?: readNodeBorderColor(node) ?: Color.TRANSPARENT,
            cornerRadius = resolved.cornerRadius,
            shape = resolved.shape?.shape
                ?: if (resolved.cornerRadius == null) readNodeShape(node) else null,
            padding = resolvePadding(node, resolved),
            minHeight = node.environment.roundToPx(
                resolved.minHeight?.minHeight ?: readNodeMinHeight(node) ?: com.viewcompose.ui.unit.UiDp.Zero,
            ),
            minWidth = node.environment.roundToPx(
                resolved.minWidth?.minWidth ?: readNodeMinWidth(node) ?: com.viewcompose.ui.unit.UiDp.Zero,
            ),
            defaultRippleColor = defaultRippleColor,
            interactionIndication = resolved.interactionIndication?.indication,
            textColor = readNodeTextColor(node),
            textSizePx = readNodeTextSize(node)?.let(node.environment::toPx),
            fontWeight = readNodeFontWeight(node),
            fontFamily = readNodeFontFamily(node),
            letterSpacingEm = readNodeLetterSpacing(node),
            lineHeightPx = readNodeLineHeight(node)?.let(node.environment.density::roundToPx),
            includeFontPadding = readNodeIncludeFontPadding(node),
            clickable = resolved.clickable != null || readNodeClickable(node),
            surfaceInsets = surfaceInsets,
            clipContent = readNodeClipContent(node),
        )
    }

    /**
     * Resolves padding and minimum size that the host layer must retain.
     * Resolves padding/min-size that must be retained by the host layer.
     */
    fun resolveHostStyle(
        node: VNode,
        resolved: ResolvedModifiers,
        nodeStyle: NodeStyle,
    ): HostStyle {
        val physicalOffset = resolved.offset
        val relativeOffset = resolved.relativeOffset
        val relativeOffsetDirection = when (node.environment.layoutDirection) {
            UiLayoutDirection.Ltr -> 1f
            UiLayoutDirection.Rtl -> -1f
        }
        return HostStyle(
            padding = nodeStyle.padding,
            minHeight = nodeStyle.minHeight,
            minWidth = nodeStyle.minWidth,
            offsetX = physicalOffset?.x?.let(node.environment::toPx)
                ?: relativeOffset?.horizontal?.let(node.environment::toPx)?.times(relativeOffsetDirection)
                ?: 0f,
            offsetY = physicalOffset?.y?.let(node.environment::toPx)
                ?: relativeOffset?.vertical?.let(node.environment::toPx)
                ?: 0f,
            systemBarsInsetsPadding = resolveSystemBarsInsets(node, resolved),
            imeInsetsPadding = resolveImeInsets(node, resolved),
        )
    }

    private fun resolvePadding(
        node: VNode,
        resolved: ResolvedModifiers,
    ): PaddingPx? {
        resolved.relativePadding?.let { return node.environment.resolve(it) }
        resolved.padding?.let { return node.environment.resolve(it) }
        return readNodePadding(node)?.toPx(node)
    }

    private fun resolveSystemBarsInsets(
        node: VNode,
        resolved: ResolvedModifiers,
    ): SystemBarsInsetsPaddingModifierElement? {
        resolved.systemBarsInsetsPadding?.let { return it }
        val relative = resolved.relativeSystemBarsInsetsPadding ?: return null
        val (left, right) = when (node.environment.layoutDirection) {
            UiLayoutDirection.Ltr -> relative.start to relative.end
            UiLayoutDirection.Rtl -> relative.end to relative.start
        }
        return SystemBarsInsetsPaddingModifierElement(
            left = left,
            top = relative.top,
            right = right,
            bottom = relative.bottom,
        )
    }

    private fun resolveImeInsets(
        node: VNode,
        resolved: ResolvedModifiers,
    ): ImeInsetsPaddingModifierElement? {
        resolved.imeInsetsPadding?.let { return it }
        val relative = resolved.relativeImeInsetsPadding ?: return null
        val (left, right) = when (node.environment.layoutDirection) {
            UiLayoutDirection.Ltr -> relative.start to relative.end
            UiLayoutDirection.Rtl -> relative.end to relative.start
        }
        return ImeInsetsPaddingModifierElement(
            left = left,
            top = relative.top,
            right = right,
            bottom = relative.bottom,
        )
    }

    private fun readNodeTextColor(node: VNode): Int? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.textColor
        is TextNodeProps -> spec.textColor
        is TextFieldNodeProps -> spec.textColor
        is ToggleNodeProps -> spec.textColor
        else -> null
    }

    private fun readNodeTextSize(node: VNode): com.viewcompose.ui.unit.UiSp? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.textSizeSp
        is TextNodeProps -> spec.textSizeSp
        is TextFieldNodeProps -> spec.textSizeSp
        is ToggleNodeProps -> spec.textSizeSp
        else -> null
    }

    private fun readNodeBackgroundColor(node: VNode): Int? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.backgroundColor
        is TextFieldNodeProps -> spec.backgroundColor
        is IconButtonNodeProps -> spec.backgroundColor
        else -> null
    }

    private fun readNodeSurfaceFill(node: VNode): Brush? =
        (node.spec as? SurfaceNodeProps)?.fill

    private fun readNodeBorderWidth(node: VNode): com.viewcompose.ui.unit.UiDp? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.borderWidth
        is TextFieldNodeProps -> spec.borderWidth
        is IconButtonNodeProps -> spec.borderWidth
        is SurfaceNodeProps -> spec.borderWidth
        else -> null
    }

    private fun readNodeBorderColor(node: VNode): Int? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.borderColor
        is TextFieldNodeProps -> spec.borderColor
        is IconButtonNodeProps -> spec.borderColor
        is SurfaceNodeProps -> spec.borderColor
        else -> null
    }

    private fun readNodeShape(node: VNode): UiShape? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.shape
        is TextFieldNodeProps -> spec.shape
        is IconButtonNodeProps -> spec.shape
        is SurfaceNodeProps -> spec.shape
        else -> null
    }

    private fun readNodeClickable(node: VNode): Boolean = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.onClick != null && spec.enabled
        is IconButtonNodeProps -> spec.enabled
        else -> false
    }

    private fun readNodeMinHeight(node: VNode): com.viewcompose.ui.unit.UiDp? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.minHeight
        is TextFieldNodeProps -> spec.minHeight
        is SurfaceNodeProps -> spec.minimumHeight
        else -> null
    }

    private fun readNodeMinWidth(node: VNode): com.viewcompose.ui.unit.UiDp? = when (val spec = node.spec) {
        is SurfaceNodeProps -> spec.minimumWidth
        else -> null
    }

    private fun readNodeSurfaceInsets(
        node: VNode,
        resolved: ResolvedModifiers,
    ): VerticalSurfaceInsetsPx {
        if (resolved.hasExplicitSurfaceOverride()) return VerticalSurfaceInsetsPx.Zero
        return when (val spec = node.spec) {
            is ButtonNodeProps -> centeredVerticalSurfaceInsets(
                effectiveHeightPx = node.environment.roundToPx(spec.minHeight),
                visualHeightPx = node.environment.roundToPx(spec.visualHeight),
            )
            is SurfaceNodeProps -> spec.visualHeight?.let { visualHeight ->
                centeredVerticalSurfaceInsets(
                    effectiveHeightPx = node.environment.roundToPx(spec.minimumHeight),
                    visualHeightPx = node.environment.roundToPx(visualHeight),
                )
            } ?: VerticalSurfaceInsetsPx.Zero
            else -> VerticalSurfaceInsetsPx.Zero
        }
    }

    private fun readNodeClipContent(node: VNode): Boolean =
        (node.spec as? SurfaceNodeProps)?.clipContent == true

    private fun readNodePadding(node: VNode): PaddingModifierElement? = when (val spec = node.spec) {
        is ButtonNodeProps -> PaddingModifierElement(
            left = spec.paddingHorizontal,
            top = spec.paddingVertical,
            right = spec.paddingHorizontal,
            bottom = spec.paddingVertical,
        )
        is TextFieldNodeProps -> PaddingModifierElement(
            left = spec.paddingHorizontal,
            top = spec.paddingVertical,
            right = spec.paddingHorizontal,
            bottom = spec.paddingVertical,
        )
        is IconButtonNodeProps -> PaddingModifierElement(
            left = spec.contentPadding,
            top = spec.contentPadding,
            right = spec.contentPadding,
            bottom = spec.contentPadding,
        )
        else -> null
    }

    private fun readNodeFontWeight(node: VNode): Int? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.fontWeight
        is TextNodeProps -> spec.fontWeight
        is TextFieldNodeProps -> spec.fontWeight
        is ToggleNodeProps -> spec.fontWeight
        else -> null
    }

    private fun readNodeFontFamily(node: VNode): UiFontFamily? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.fontFamily
        is TextNodeProps -> spec.fontFamily
        is TextFieldNodeProps -> spec.fontFamily
        is ToggleNodeProps -> spec.fontFamily
        else -> null
    }

    private fun readNodeLetterSpacing(node: VNode): Float? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.letterSpacingEm
        is TextNodeProps -> spec.letterSpacingEm
        is TextFieldNodeProps -> spec.letterSpacingEm
        is ToggleNodeProps -> spec.letterSpacingEm
        else -> null
    }

    private fun readNodeLineHeight(node: VNode): com.viewcompose.ui.unit.UiSp? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.lineHeightSp
        is TextNodeProps -> spec.lineHeightSp
        is TextFieldNodeProps -> spec.lineHeightSp
        is ToggleNodeProps -> spec.lineHeightSp
        else -> null
    }

    private fun readNodeIncludeFontPadding(node: VNode): Boolean? = when (val spec = node.spec) {
        is ButtonNodeProps -> spec.includeFontPadding
        is TextNodeProps -> spec.includeFontPadding
        is TextFieldNodeProps -> spec.includeFontPadding
        is ToggleNodeProps -> spec.includeFontPadding
        else -> null
    }

    private fun PaddingModifierElement.toPx(node: VNode): PaddingPx {
        return PaddingPx(
            left = node.environment.roundToPx(left),
            top = node.environment.roundToPx(top),
            right = node.environment.roundToPx(right),
            bottom = node.environment.roundToPx(bottom),
        )
    }

}

/**
 * Complete visual-style snapshot for a node View.
 * Complete visual style snapshot for a node View.
 */
internal data class NodeStyle(
    val backgroundDrawableResId: Int?,
    val backgroundColor: Int?,
    val surfaceFill: Brush?,
    val borderWidth: Int,
    val borderColor: Int,
    val cornerRadius: CornerRadiusModifierElement?,
    val shape: UiShape? = null,
    val padding: PaddingPx?,
    val minHeight: Int,
    val minWidth: Int,
    val defaultRippleColor: Int,
    val interactionIndication: UiInteractionIndication?,
    val textColor: Int?,
    val textSizePx: Float?,
    val fontWeight: Int?,
    val fontFamily: UiFontFamily?,
    val letterSpacingEm: Float?,
    val lineHeightPx: Int?,
    val includeFontPadding: Boolean?,
    val clickable: Boolean,
    val surfaceInsets: VerticalSurfaceInsetsPx = VerticalSurfaceInsetsPx.Zero,
    val clipContent: Boolean = false,
)

/** Pixel insets that center a node's visual surface inside its effective View bounds. */
internal data class VerticalSurfaceInsetsPx(
    val top: Int,
    val bottom: Int,
) {
    companion object {
        val Zero = VerticalSurfaceInsetsPx(top = 0, bottom = 0)
    }
}

/** Returns non-negative centered insets while preserving the requested visual height in pixels. */
internal fun centeredVerticalSurfaceInsets(
    effectiveHeightPx: Int,
    visualHeightPx: Int,
): VerticalSurfaceInsetsPx {
    val effective = effectiveHeightPx.coerceAtLeast(0)
    val visual = visualHeightPx.coerceIn(0, effective)
    val totalInset = effective - visual
    val top = totalInset / 2
    return VerticalSurfaceInsetsPx(
        top = top,
        bottom = totalInset - top,
    )
}

private fun ResolvedModifiers.hasExplicitSurfaceOverride(): Boolean {
    return backgroundDrawableRes != null ||
        backgroundColor != null ||
        border != null ||
        cornerRadius != null ||
        shape != null
}

/**
 * Style that an outer host must apply during layout and inset handling.
 * Style used by the outer host for layout and inset handling.
 */
internal data class HostStyle(
    val padding: PaddingPx?,
    val minHeight: Int,
    val minWidth: Int,
    val offsetX: Float,
    val offsetY: Float,
    val systemBarsInsetsPadding: SystemBarsInsetsPaddingModifierElement?,
    val imeInsetsPadding: ImeInsetsPaddingModifierElement?,
) {
    val hasWindowInsetsPadding: Boolean
        get() = systemBarsInsetsPadding != null || imeInsetsPadding != null
}
