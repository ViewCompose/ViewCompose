package com.viewcompose.renderer.view.tree

import android.os.LocaleList
import android.view.View
import android.widget.TextView
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.shape.UiShape

/**
 * 将已解析 modifier 增量应用到 Android View，集中维护可复用 View 的原始状态缓存与回滚规则。
 * Applies resolved modifiers incrementally to Android Views and centralizes original-state caching
 * plus rollback rules for reused views.
 */
internal object ViewModifierApplier {
    fun bindView(
        view: View,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers = node.modifier.resolve(),
    ) {
        applyEnvironment(view, node)
        applyModifier(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
        )
        NodeViewBinderRegistry.bind(view, node)
        ModifierInteractionApplier.applyNativeViewConfigs(view, node)
    }

    private fun applyEnvironment(
        view: View,
        node: VNode,
    ) {
        view.layoutDirection = when (node.environment.layoutDirection) {
            UiLayoutDirection.Ltr -> View.LAYOUT_DIRECTION_LTR
            UiLayoutDirection.Rtl -> View.LAYOUT_DIRECTION_RTL
        }
        if (view is TextView) {
            view.textLocales = LocaleList.forLanguageTags(
                node.environment.locales.tags.joinToString(separator = ","),
            )
        }
    }

    fun cacheOriginalBackground(view: View) {
        ModifierSurfaceStyleApplier.cacheOriginalBackground(view)
    }

    fun cacheOriginalForeground(view: View) {
        ModifierSurfaceStyleApplier.cacheOriginalForeground(view)
    }

    fun applyStylePatch(
        view: View,
        backgroundColor: Int,
        borderWidth: Int,
        borderColor: Int,
        shape: UiShape,
        rippleColor: Int,
        clickable: Boolean,
    ) {
        val resolved = view.getTag(R.id.viewcompose_resolved_modifiers) as? ResolvedModifiers
        ModifierSurfaceStyleApplier.applyBackgroundAndInteraction(
            view = view,
            backgroundDrawableResId = resolved?.backgroundDrawableRes?.resId,
            backgroundColor = resolved?.backgroundColor?.color ?: backgroundColor,
            borderWidth = resolved?.border?.width ?: borderWidth,
            borderColor = resolved?.border?.color ?: borderColor,
            cornerRadius = resolved?.cornerRadius,
            rippleColor = rippleColor,
            clickable = resolved?.clickable != null || clickable,
            forceClip = resolved?.graphicsLayer?.clip ?: (resolved?.clip?.clip ?: false),
            shape = resolved?.shape?.shape
                ?: if (resolved?.cornerRadius == null) shape else null,
        )
    }

    fun applyModifier(
        view: View,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers = node.modifier.resolve(),
    ) {
        val nodeStyle = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = resolved,
            defaultRippleColor = defaultRippleColor,
        )
        val hostStyle = ModifierNodeStyleResolver.resolveHostStyle(
            resolved = resolved,
            nodeStyle = nodeStyle,
        )
        val previous = view.getTag(
            R.id.viewcompose_applied_modifier_state,
        ) as? AppliedModifierState
        val next = AppliedModifierState(
            nodeType = node.type,
            nodeKey = node.key,
            resolved = resolved,
            nodeStyle = nodeStyle,
            hostStyle = hostStyle,
        )
        view.setTag(R.id.viewcompose_resolved_modifiers, resolved)

        if (previous == null || graphicsChanged(previous, next)) {
            ModifierGraphicsApplier.applyGraphicsModifiers(
                view = view,
                resolved = resolved,
            )
        }
        if (previous == null || surfaceChanged(previous, next)) {
            ModifierSurfaceStyleApplier.applySurfaceStyle(
                view = view,
                resolved = resolved,
                nodeStyle = nodeStyle,
            )
        }
        if (previous == null || commonHostPropertiesChanged(previous, next)) {
            ModifierInteractionApplier.applyCommonHostProperties(
                view = view,
                resolved = resolved,
                minHeight = hostStyle.minHeight,
                minWidth = hostStyle.minWidth,
            )
        }
        if (previous == null || previous.resolved.semantics != resolved.semantics) {
            ModifierSemanticsApplier.apply(
                view = view,
                semantics = resolved.semantics,
            )
        }
        if (previous == null || interactionChanged(previous, next)) {
            ModifierInteractionApplier.applyClickAndFocusState(
                view = view,
                node = node,
                resolved = resolved,
            )
        }
        if (previous == null || previous.resolved.nestedScroll != resolved.nestedScroll) {
            ModifierNestedScrollApplier.apply(
                view = view,
                resolved = resolved,
            )
        }
        if (previous == null || insetsChanged(previous, next)) {
            ModifierInsetsApplier.applyHostPaddingWhenNoInsets(
                view = view,
                hasWindowInsetsPadding = hostStyle.hasWindowInsetsPadding,
                hostPadding = hostStyle.padding,
            )
            ModifierInsetsApplier.applyWindowInsetsPadding(
                view = view,
                systemBarsModifier = resolved.systemBarsInsetsPadding,
                imeModifier = resolved.imeInsetsPadding,
                basePadding = if (hostStyle.hasWindowInsetsPadding) {
                    nodeStyle.padding
                } else {
                    null
                },
            )
        }
        if (previous == null || textAppearanceChanged(previous, next)) {
            ModifierInteractionApplier.applyTextAppearanceIfTextView(
                view = view,
                textColor = nodeStyle.textColor,
                textSizeSp = nodeStyle.textSizeSp,
                fontWeight = nodeStyle.fontWeight,
                fontFamily = nodeStyle.fontFamily,
                letterSpacingEm = nodeStyle.letterSpacingEm,
                lineHeightSp = nodeStyle.lineHeightSp,
                includeFontPadding = nodeStyle.includeFontPadding,
            )
        }
        view.setTag(R.id.viewcompose_applied_modifier_state, next)
    }

    private fun graphicsChanged(
        previous: AppliedModifierState,
        next: AppliedModifierState,
    ): Boolean = previous.resolved.drawElements != next.resolved.drawElements

    private fun surfaceChanged(
        previous: AppliedModifierState,
        next: AppliedModifierState,
    ): Boolean {
        val previousStyle = previous.nodeStyle
        val nextStyle = next.nodeStyle
        return previousStyle.backgroundDrawableResId != nextStyle.backgroundDrawableResId ||
            previousStyle.backgroundColor != nextStyle.backgroundColor ||
            previousStyle.borderWidth != nextStyle.borderWidth ||
            previousStyle.borderColor != nextStyle.borderColor ||
            previousStyle.cornerRadius != nextStyle.cornerRadius ||
            previousStyle.shape != nextStyle.shape ||
            previousStyle.rippleColor != nextStyle.rippleColor ||
            previousStyle.clickable != nextStyle.clickable ||
            previous.resolved.clip != next.resolved.clip ||
            previous.resolved.graphicsLayer?.clip != next.resolved.graphicsLayer?.clip
    }

    private fun commonHostPropertiesChanged(
        previous: AppliedModifierState,
        next: AppliedModifierState,
    ): Boolean {
        val previousResolved = previous.resolved
        val nextResolved = next.resolved
        return previousResolved.overlayAnchor != nextResolved.overlayAnchor ||
            previousResolved.testTag != nextResolved.testTag ||
            previousResolved.alpha != nextResolved.alpha ||
            previousResolved.visibility != nextResolved.visibility ||
            previousResolved.offset != nextResolved.offset ||
            previousResolved.zIndex != nextResolved.zIndex ||
            previousResolved.elevation != nextResolved.elevation ||
            previousResolved.graphicsLayer != nextResolved.graphicsLayer ||
            previous.hostStyle.minHeight != next.hostStyle.minHeight ||
            previous.hostStyle.minWidth != next.hostStyle.minWidth ||
            previousResolved.layoutId != nextResolved.layoutId ||
            previousResolved.constraint != nextResolved.constraint
    }

    private fun interactionChanged(
        previous: AppliedModifierState,
        next: AppliedModifierState,
    ): Boolean {
        val previousResolved = previous.resolved
        val nextResolved = next.resolved
        return previous.nodeType != next.nodeType ||
            previous.nodeKey != next.nodeKey ||
            previousResolved.clickable != nextResolved.clickable ||
            previousResolved.pointerInput != nextResolved.pointerInput ||
            previousResolved.combinedClickable != nextResolved.combinedClickable ||
            previousResolved.draggable != nextResolved.draggable ||
            previousResolved.anchoredDraggable != nextResolved.anchoredDraggable ||
            previousResolved.transformable != nextResolved.transformable ||
            previousResolved.gesturePriority != nextResolved.gesturePriority ||
            previousResolved.focusable != nextResolved.focusable ||
            previousResolved.focusRequester != nextResolved.focusRequester ||
            previousResolved.focusProperties != nextResolved.focusProperties ||
            previousResolved.focusGroup != nextResolved.focusGroup ||
            previousResolved.onFocusChanged != nextResolved.onFocusChanged ||
            previousResolved.previewKeyEvent != nextResolved.previewKeyEvent ||
            previousResolved.keyEvent != nextResolved.keyEvent
    }

    private fun insetsChanged(
        previous: AppliedModifierState,
        next: AppliedModifierState,
    ): Boolean {
        return previous.hostStyle != next.hostStyle ||
            previous.resolved.systemBarsInsetsPadding != next.resolved.systemBarsInsetsPadding ||
            previous.resolved.imeInsetsPadding != next.resolved.imeInsetsPadding
    }

    private fun textAppearanceChanged(
        previous: AppliedModifierState,
        next: AppliedModifierState,
    ): Boolean {
        val previousStyle = previous.nodeStyle
        val nextStyle = next.nodeStyle
        return previousStyle.textColor != nextStyle.textColor ||
            previousStyle.textSizeSp != nextStyle.textSizeSp ||
            previousStyle.fontWeight != nextStyle.fontWeight ||
            previousStyle.fontFamily != nextStyle.fontFamily ||
            previousStyle.letterSpacingEm != nextStyle.letterSpacingEm ||
            previousStyle.lineHeightSp != nextStyle.lineHeightSp ||
            previousStyle.includeFontPadding != nextStyle.includeFontPadding
    }
}

/**
 * 记录上一轮已应用的 modifier 快照，用于避免重复设置和恢复被移除的视觉状态。
 * Records the previously applied modifier snapshot so unchanged values are skipped and removed
 * visual state can be restored.
 */
private data class AppliedModifierState(
    val nodeType: com.viewcompose.ui.node.NodeType,
    val nodeKey: Any?,
    val resolved: ResolvedModifiers,
    val nodeStyle: NodeStyle,
    val hostStyle: HostStyle,
)
