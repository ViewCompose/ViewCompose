package com.viewcompose.renderer.view.tree.patch

import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.renderer.view.tree.ButtonNodePatch
import com.viewcompose.renderer.view.tree.CanvasNodePatch
import com.viewcompose.renderer.view.tree.ContentViewBinder
import com.viewcompose.renderer.view.tree.DividerNodePatch
import com.viewcompose.renderer.view.tree.TextNodePatch
import com.viewcompose.renderer.view.tree.ViewModifierApplier
import com.viewcompose.renderer.view.container.DeclarativeCanvasLayout

internal object ContentNodePatchApplier {
    fun applyTextPatch(
        view: TextView,
        patch: TextNodePatch,
    ) {
        if (patch.previous.document != patch.next.document) {
            view.text = com.viewcompose.renderer.view.tree.AndroidTextDocumentAdapter.toCharSequence(
                view,
                patch.next.document,
            )
        }
        if (patch.previous.maxLines != patch.next.maxLines) {
            view.maxLines = patch.next.maxLines
        }
        if (patch.previous.overflow != patch.next.overflow) {
            view.ellipsize = when (patch.next.overflow) {
                TextOverflow.Clip -> null
                TextOverflow.Ellipsis -> TextUtils.TruncateAt.END
            }
        }
        if (patch.previous.textAlign != patch.next.textAlign) {
            view.gravity = ContentViewBinder.toTextGravity(patch.next.textAlign)
        }
        if (hasTextAppearanceChange(patch)) {
            ContentViewBinder.applyTextAppearance(
                view = view,
                textColor = patch.next.textColor,
                textSizeSp = patch.next.textSizeSp,
                fontWeight = patch.next.fontWeight,
                fontFamily = patch.next.fontFamily,
                letterSpacingEm = patch.next.letterSpacingEm,
                lineHeightSp = patch.next.lineHeightSp,
                includeFontPadding = patch.next.includeFontPadding,
            )
        }
        if (patch.previous.textDecoration != patch.next.textDecoration) {
            ContentViewBinder.applyTextDecoration(view, patch.next.textDecoration)
        }
    }

    fun applyButtonPatch(
        view: Button,
        patch: ButtonNodePatch,
    ) {
        if (patch.previous.text != patch.next.text) {
            view.text = patch.next.text
        }
        if (patch.previous.enabled != patch.next.enabled) {
            view.isEnabled = patch.next.enabled
        }
        if (patch.previous.iconSpacing != patch.next.iconSpacing) {
            view.compoundDrawablePadding = patch.next.iconSpacing
        }
        if (
            patch.previous.leadingIcon != patch.next.leadingIcon ||
            patch.previous.trailingIcon != patch.next.trailingIcon ||
            patch.previous.iconTint != patch.next.iconTint ||
            patch.previous.iconSize != patch.next.iconSize
        ) {
            view.setCompoundDrawablesRelative(
                ContentViewBinder.resolveButtonIconDrawable(
                    view = view,
                    source = patch.next.leadingIcon,
                    tint = patch.next.iconTint,
                    size = patch.next.iconSize,
                ),
                null,
                ContentViewBinder.resolveButtonIconDrawable(
                    view = view,
                    source = patch.next.trailingIcon,
                    tint = patch.next.iconTint,
                    size = patch.next.iconSize,
                ),
                null,
            )
        }
        if (
            patch.previous.onClick != patch.next.onClick ||
            patch.previous.enabled != patch.next.enabled
        ) {
            view.setOnClickListener {
                if (patch.next.enabled) {
                    patch.next.onClick?.invoke()
                }
            }
        }
        if (hasTextAppearanceChange(patch)) {
            ContentViewBinder.applyTextAppearance(
                view = view,
                textColor = patch.next.textColor,
                textSizeSp = patch.next.textSizeSp,
                fontWeight = patch.next.fontWeight,
                fontFamily = patch.next.fontFamily,
                letterSpacingEm = patch.next.letterSpacingEm,
                lineHeightSp = patch.next.lineHeightSp,
                includeFontPadding = patch.next.includeFontPadding,
            )
        }
        if (hasStyleChange(patch)) {
            ViewModifierApplier.applyStylePatch(
                view = view,
                backgroundColor = patch.next.backgroundColor,
                borderWidth = patch.next.borderWidth,
                borderColor = patch.next.borderColor,
                shape = patch.next.shape,
                rippleColor = patch.next.rippleColor,
                clickable = true,
            )
        }
        if (patch.previous.minHeight != patch.next.minHeight) {
            view.minimumHeight = patch.next.minHeight
        }
        if (
            patch.previous.paddingHorizontal != patch.next.paddingHorizontal ||
            patch.previous.paddingVertical != patch.next.paddingVertical
        ) {
            view.setPadding(
                patch.next.paddingHorizontal,
                patch.next.paddingVertical,
                patch.next.paddingHorizontal,
                patch.next.paddingVertical,
            )
        }
    }

    fun applyDividerPatch(
        view: View,
        patch: DividerNodePatch,
    ) {
        if (patch.previous.color != patch.next.color) {
            view.setBackgroundColor(patch.next.color)
        }
    }

    fun applyCanvasPatch(
        view: DeclarativeCanvasLayout,
        patch: CanvasNodePatch,
    ) {
        if (patch.previous.onDraw != patch.next.onDraw) {
            view.setCanvasDrawBlock(patch.next.onDraw)
        }
    }

    private fun hasStyleChange(patch: ButtonNodePatch): Boolean {
        return patch.previous.backgroundColor != patch.next.backgroundColor ||
            patch.previous.borderWidth != patch.next.borderWidth ||
            patch.previous.borderColor != patch.next.borderColor ||
            patch.previous.shape != patch.next.shape ||
            patch.previous.rippleColor != patch.next.rippleColor
    }

    private fun hasTextAppearanceChange(patch: TextNodePatch): Boolean {
        return patch.previous.textColor != patch.next.textColor ||
            patch.previous.textSizeSp != patch.next.textSizeSp ||
            patch.previous.fontWeight != patch.next.fontWeight ||
            patch.previous.fontFamily != patch.next.fontFamily ||
            patch.previous.letterSpacingEm != patch.next.letterSpacingEm ||
            patch.previous.lineHeightSp != patch.next.lineHeightSp ||
            patch.previous.includeFontPadding != patch.next.includeFontPadding
    }

    private fun hasTextAppearanceChange(patch: ButtonNodePatch): Boolean {
        return patch.previous.textColor != patch.next.textColor ||
            patch.previous.textSizeSp != patch.next.textSizeSp ||
            patch.previous.fontWeight != patch.next.fontWeight ||
            patch.previous.fontFamily != patch.next.fontFamily ||
            patch.previous.letterSpacingEm != patch.next.letterSpacingEm ||
            patch.previous.lineHeightSp != patch.next.lineHeightSp ||
            patch.previous.includeFontPadding != patch.next.includeFontPadding
    }
}
