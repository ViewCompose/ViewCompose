package com.viewcompose.renderer.view.tree

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.shape.MaterialShapeDrawable
import com.viewcompose.renderer.R
import com.viewcompose.ui.modifier.CornerRadiusModifierElement
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.shape.toShapeAppearanceModel
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.ui.unit.UiDensity

/**
 * 应用背景、边框、shape、ripple 和 clip 等 surface style。
 * Applies surface style such as background, border, shape, ripple, and clipping.
 */
internal object ModifierSurfaceStyleApplier {
    /**
     * 缓存原始 background，便于 modifier 移除时恢复平台默认外观。
     * Caches original background so platform default appearance can be restored when modifiers are removed.
     */
    fun cacheOriginalBackground(view: View) {
        if (view.getTag(R.id.viewcompose_original_background) != null) {
            return
        }
        view.setTag(
            R.id.viewcompose_original_background,
            cloneDrawable(view.background),
        )
    }

    /**
     * 缓存原始 foreground，便于移除 ripple modifier 时恢复。
     * Caches original foreground so ripple modifier removal can restore it.
     */
    fun cacheOriginalForeground(view: View) {
        if (view.getTag(R.id.viewcompose_original_foreground) != null) {
            return
        }
        view.setTag(
            R.id.viewcompose_original_foreground,
            cloneDrawable(view.foreground),
        )
    }

    /**
     * 根据 ResolvedModifiers 和 NodeStyle 应用节点 surface 样式。
     * Applies node surface styling from ResolvedModifiers and NodeStyle.
     */
    fun applySurfaceStyle(
        view: View,
        resolved: ResolvedModifiers,
        nodeStyle: NodeStyle,
    ) {
        applyBackgroundAndInteraction(
            view = view,
            backgroundDrawableResId = nodeStyle.backgroundDrawableResId,
            backgroundColor = nodeStyle.backgroundColor,
            borderWidth = nodeStyle.borderWidth,
            borderColor = nodeStyle.borderColor,
            cornerRadius = nodeStyle.cornerRadius,
            rippleColor = nodeStyle.rippleColor,
            clickable = nodeStyle.clickable,
            forceClip = resolved.graphicsLayer?.clip ?: (resolved.clip?.clip ?: false),
            shape = nodeStyle.shape,
        )
    }

    /**
     * 创建或恢复背景/foreground，并按 shape 处理 outline clipping。
     * Creates or restores background/foreground and applies outline clipping from shape.
     */
    fun applyBackgroundAndInteraction(
        view: View,
        backgroundDrawableResId: Int?,
        backgroundColor: Int?,
        borderWidth: Int,
        borderColor: Int,
        cornerRadius: CornerRadiusModifierElement?,
        rippleColor: Int,
        clickable: Boolean,
        forceClip: Boolean = false,
        shape: UiShape? = null,
    ) {
        val legacyHasCorner = cornerRadius != null &&
            (cornerRadius.topStart > UiDp.Zero || cornerRadius.topEnd > UiDp.Zero ||
                cornerRadius.bottomEnd > UiDp.Zero || cornerRadius.bottomStart > UiDp.Zero)
        val resolvedShape = shape ?: cornerRadius?.toUiShape()
        val density = view.requireUiEnvironment().density
        val hasShape = resolvedShape != null
        val backgroundDrawable = backgroundDrawableResId
            ?.let { loadBackgroundDrawable(view, it) }
        val hasCustomSurface = backgroundDrawable != null ||
            backgroundColor != null ||
            hasShape ||
            legacyHasCorner ||
            borderWidth > 0
        if (hasCustomSurface) {
            // 自定义 surface 使用 background 承载 ripple/content/border，避免 foreground 与裁剪冲突。
            // Custom surfaces put ripple/content/border into background to avoid foreground and clipping conflicts.
            view.background = if (backgroundDrawable != null) {
                createDrawableResourceBackground(
                    backgroundDrawable = backgroundDrawable,
                    borderWidth = borderWidth,
                    borderColor = borderColor,
                    rippleColor = rippleColor,
                    clickable = clickable,
                    shape = resolvedShape,
                    layoutDirection = view.layoutDirection,
                    density = density,
                )
            } else {
                createBackgroundDrawable(
                    backgroundColor = backgroundColor ?: Color.TRANSPARENT,
                    borderWidth = borderWidth,
                    borderColor = borderColor,
                    rippleColor = rippleColor,
                    clickable = clickable,
                    shape = resolvedShape,
                    layoutDirection = view.layoutDirection,
                    density = density,
                )
            }
            view.foreground = null
        } else {
            // 没有自定义 surface 时恢复原生 background，只在 clickable 时用 foreground ripple。
            // Without a custom surface, restore native background and use foreground ripple only when clickable.
            restoreOriginalBackground(view)
            if (clickable) {
                view.foreground = RippleDrawable(
                    ColorStateList.valueOf(rippleColor),
                    null,
                    null,
                )
            } else {
                restoreOriginalForeground(view)
            }
        }
        val shouldAutoClipForDrawableShape = backgroundDrawable != null && hasShape
        applyShapeOutline(
            view = view,
            shape = resolvedShape,
            forceClip = forceClip || shouldAutoClipForDrawableShape,
            density = density,
        )
    }

    private fun restoreOriginalBackground(view: View) {
        view.background = cloneDrawable(
            view.getTag(R.id.viewcompose_original_background) as? Drawable,
        )
    }

    private fun restoreOriginalForeground(view: View) {
        view.foreground = cloneDrawable(
            view.getTag(R.id.viewcompose_original_foreground) as? Drawable,
        )
    }

    private fun cloneDrawable(drawable: Drawable?): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate() ?: drawable?.mutate()
    }

    private fun createBackgroundDrawable(
        backgroundColor: Int,
        borderWidth: Int,
        borderColor: Int,
        rippleColor: Int,
        clickable: Boolean,
        shape: UiShape?,
        layoutDirection: Int,
        density: UiDensity,
    ): Drawable {
        val content = MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection, density)).apply {
            fillColor = ColorStateList.valueOf(backgroundColor)
            if (borderWidth > 0) {
                setStroke(borderWidth.toFloat(), borderColor)
            }
        }
        if (!clickable) {
            return content
        }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            content,
            MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection, density)).apply {
                fillColor = ColorStateList.valueOf(Color.WHITE)
            },
        )
    }

    private fun createDrawableResourceBackground(
        backgroundDrawable: Drawable,
        borderWidth: Int,
        borderColor: Int,
        rippleColor: Int,
        clickable: Boolean,
        shape: UiShape?,
        layoutDirection: Int,
        density: UiDensity,
    ): Drawable {
        val layeredContent = if (borderWidth > 0) {
            LayerDrawable(
                arrayOf(
                    backgroundDrawable,
                    MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection, density)).apply {
                        fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                        setStroke(borderWidth.toFloat(), borderColor)
                    },
                ),
            )
        } else {
            backgroundDrawable
        }
        if (!clickable) {
            return layeredContent
        }
        return RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            layeredContent,
            MaterialShapeDrawable(shape.toShapeAppearanceModel(layoutDirection, density)).apply {
                fillColor = ColorStateList.valueOf(Color.WHITE)
            },
        )
    }

    private fun applyShapeOutline(
        view: View,
        shape: UiShape?,
        forceClip: Boolean = false,
        density: UiDensity,
    ) {
        if (shape == null && !forceClip) {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.invalidateOutline()
            return
        }
        if (shape != null) {
            val outlineDrawable = MaterialShapeDrawable(
                shape.toShapeAppearanceModel(view.layoutDirection, density),
            )
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outlineDrawable.setBounds(0, 0, view.width, view.height)
                    outlineDrawable.getOutline(outline)
                }
            }
        } else {
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        // Apply rounded outline for shadow, but only clip content when clip() is explicitly requested.
        view.clipToOutline = forceClip
        view.invalidateOutline()
    }

    private fun CornerRadiusModifierElement.toUiShape(): UiShape {
        return UiShape.rounded(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
        )
    }

    private fun loadBackgroundDrawable(
        view: View,
        backgroundDrawableResId: Int,
    ): Drawable? {
        return AppCompatResources.getDrawable(view.context, backgroundDrawableResId)
            ?.mutate()
    }
}
