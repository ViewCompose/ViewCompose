package com.viewcompose.renderer.view.tree.patch

import android.content.res.ColorStateList
import android.widget.ImageButton
import android.widget.ImageView
import com.viewcompose.renderer.view.tree.IconButtonNodePatch
import com.viewcompose.renderer.view.tree.ImageNodePatch
import com.viewcompose.renderer.view.tree.MediaViewBinder
import com.viewcompose.renderer.view.tree.ViewModifierApplier
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.roundToPx

/**
 * 媒体类节点的细粒度 patch 应用器。
 * Fine-grained patch applier for media nodes.
 */
internal object MediaNodePatchApplier {
    /**
     * 更新 ImageView 的可访问描述、缩放、tint 和图片来源。
     * Updates ImageView accessibility description, scale, tint, and image source.
     */
    fun applyImagePatch(
        view: ImageView,
        patch: ImageNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.contentDescription != next.contentDescription) {
            view.contentDescription = next.contentDescription
        }
        if (previous.contentScale != next.contentScale) {
            with(MediaViewBinder) {
                view.scaleType = next.contentScale.toScaleType()
            }
        }
        if (previous.tint != next.tint) {
            view.imageTintList = next.tint?.let(ColorStateList::valueOf)
        }
        val sourceChanged = previous.source != next.source ||
            previous.placeholder != next.placeholder ||
            previous.error != next.error ||
            previous.fallback != next.fallback ||
            previous.remoteImageLoader != next.remoteImageLoader
        if (sourceChanged) {
            // 图片来源变化交回 MediaViewBinder，确保占位图、错误图和远程加载策略一致。
            // Source changes go back through MediaViewBinder to keep placeholder, error, and remote loading policy consistent.
            MediaViewBinder.bindImage(
                view = view,
                spec = MediaViewBinder.ImageSpec(
                    contentDescription = next.contentDescription,
                    scaleType = with(MediaViewBinder) { next.contentScale.toScaleType() },
                    tint = next.tint,
                    source = next.source,
                    placeholder = next.placeholder,
                    error = next.error,
                    fallback = next.fallback,
                    remoteImageLoader = next.remoteImageLoader,
                ),
            )
        }
    }

    /**
     * 更新 IconButton 的图片内容、启用态、样式和 padding。
     * Updates IconButton image content, enabled state, styling, and padding.
     */
    fun applyIconButtonPatch(
        view: ImageButton,
        patch: IconButtonNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.contentDescription != next.contentDescription) {
            view.contentDescription = next.contentDescription
        }
        if (previous.contentScale != next.contentScale) {
            with(MediaViewBinder) {
                view.scaleType = next.contentScale.toScaleType()
            }
        }
        if (previous.tint != next.tint) {
            view.imageTintList = next.tint?.let(ColorStateList::valueOf)
        }
        val sourceChanged = previous.source != next.source ||
            previous.placeholder != next.placeholder ||
            previous.error != next.error ||
            previous.fallback != next.fallback ||
            previous.remoteImageLoader != next.remoteImageLoader
        if (sourceChanged) {
            MediaViewBinder.bindImage(
                view = view,
                spec = MediaViewBinder.ImageSpec(
                    contentDescription = next.contentDescription,
                    scaleType = with(MediaViewBinder) { next.contentScale.toScaleType() },
                    tint = next.tint,
                    source = next.source,
                    placeholder = next.placeholder,
                    error = next.error,
                    fallback = next.fallback,
                    remoteImageLoader = next.remoteImageLoader,
                ),
            )
        }
        if (previous.enabled != next.enabled) {
            view.isEnabled = next.enabled
        }
        if (
            previous.backgroundColor != next.backgroundColor ||
            previous.borderWidth != next.borderWidth ||
            previous.borderColor != next.borderColor ||
            previous.shape != next.shape ||
            previous.rippleColor != next.rippleColor
        ) {
            ViewModifierApplier.applyStylePatch(
                view = view,
                backgroundColor = next.backgroundColor,
                borderWidth = next.borderWidth,
                borderColor = next.borderColor,
                shape = next.shape,
                rippleColor = next.rippleColor,
                clickable = true,
            )
        }
        if (previous.contentPadding != next.contentPadding) {
            val contentPaddingPx = view.requireUiEnvironment().roundToPx(next.contentPadding)
            view.setPadding(
                contentPaddingPx,
                contentPaddingPx,
                contentPaddingPx,
                contentPaddingPx,
            )
        }
    }
}
