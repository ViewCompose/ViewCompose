package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.node.VNode

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
        cornerRadius: Int,
        rippleColor: Int,
        clickable: Boolean,
    ) {
        ModifierSurfaceStyleApplier.applyStylePatch(
            view = view,
            backgroundColor = backgroundColor,
            borderWidth = borderWidth,
            borderColor = borderColor,
            cornerRadius = cornerRadius,
            rippleColor = rippleColor,
            clickable = clickable,
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
