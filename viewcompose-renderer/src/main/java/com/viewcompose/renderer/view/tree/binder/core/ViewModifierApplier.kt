package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.shape.UiShape

internal object ViewModifierApplier {
    fun bindView(
        view: View,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers = node.modifier.resolve(),
    ) {
        applyModifier(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
        )
        NodeViewBinderRegistry.bind(view, node)
        ModifierInteractionApplier.applyNativeViewConfigs(view, node)
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
        view.setTag(R.id.viewcompose_resolved_modifiers, resolved)
        val nodeStyle = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = resolved,
            defaultRippleColor = defaultRippleColor,
        )
        val hostStyle = ModifierNodeStyleResolver.resolveHostStyle(
            resolved = resolved,
            nodeStyle = nodeStyle,
        )
        ModifierGraphicsApplier.applyGraphicsModifiers(
            view = view,
            resolved = resolved,
        )
        ModifierSurfaceStyleApplier.applySurfaceStyle(
            view = view,
            resolved = resolved,
            nodeStyle = nodeStyle,
        )
        ModifierInteractionApplier.applyCommonHostProperties(
            view = view,
            resolved = resolved,
            minHeight = hostStyle.minHeight,
            minWidth = hostStyle.minWidth,
        )
        ModifierSemanticsApplier.apply(
            view = view,
            semantics = resolved.semantics,
        )
        ModifierInteractionApplier.applyClickAndFocusState(
            view = view,
            node = node,
            resolved = resolved,
        )
        ModifierNestedScrollApplier.apply(
            view = view,
            resolved = resolved,
        )
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
}
