package com.viewcompose.renderer.view.tree

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.content.res.AppCompatResources
import com.viewcompose.renderer.R
import com.viewcompose.ui.modifier.CornerRadiusModifierElement
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.shape.UiShapeDrawable
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.ui.unit.UiDensity

/**
 * Applies surface styling including background, border, shape, ripple, and clipping.
 * Applies surface style such as background, border, shape, ripple, and clipping.
 */
internal object ModifierSurfaceStyleApplier {
    /**
     * Caches the original background so removing modifiers restores platform appearance.
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
     * Caches the original foreground so removing a ripple modifier restores it.
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
     * Applies node surface styling from ResolvedModifiers and NodeStyle.
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
            stateLayerColors = nodeStyle.stateLayerColors,
            clickable = nodeStyle.clickable,
            forceClip = resolved.graphicsLayer?.clip ?: (resolved.clip?.clip ?: false),
            shape = nodeStyle.shape,
            surfaceInsets = nodeStyle.surfaceInsets,
        )
    }

    /**
     * Creates or restores background and foreground layers and configures shape outline clipping.
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
        stateLayerColors: UiStateLayerColors? = null,
        clickable: Boolean,
        forceClip: Boolean = false,
        shape: UiShape? = null,
        surfaceInsets: VerticalSurfaceInsetsPx = VerticalSurfaceInsetsPx.Zero,
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
            // Custom surfaces put ripple, content, and border in the background to avoid foreground clipping conflicts.
            // Custom surfaces put ripple/content/border into background to avoid foreground and clipping conflicts.
            val resolvedBackground = if (backgroundDrawable != null) {
                createDrawableResourceBackground(
                    backgroundDrawable = backgroundDrawable,
                    borderWidth = borderWidth,
                    borderColor = borderColor,
                    rippleColor = rippleColor,
                    stateLayerColors = stateLayerColors,
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
                    stateLayerColors = stateLayerColors,
                    clickable = clickable,
                    shape = resolvedShape,
                    layoutDirection = view.layoutDirection,
                    density = density,
                )
            }
            view.background = resolvedBackground.withVerticalInsets(surfaceInsets)
            view.foreground = null
        } else {
            // Without a custom surface, restore native background and use a foreground ripple only when clickable.
            // Without a custom surface, restore native background and use foreground ripple only when clickable.
            restoreOriginalBackground(view)
            if (clickable) {
                view.foreground = RippleDrawable(
                    interactionColorStateList(rippleColor, stateLayerColors),
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
            surfaceInsets = surfaceInsets,
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
        stateLayerColors: UiStateLayerColors?,
        clickable: Boolean,
        shape: UiShape?,
        layoutDirection: Int,
        density: UiDensity,
    ): Drawable {
        val content = UiShapeDrawable(shape, layoutDirection, density).apply {
            setFillColor(backgroundColor)
            if (borderWidth > 0) {
                setStroke(borderWidth.toFloat(), borderColor)
            }
        }
        if (!clickable) {
            return content
        }
        return RippleDrawable(
            interactionColorStateList(rippleColor, stateLayerColors),
            content,
            UiShapeDrawable(shape, layoutDirection, density).apply {
                setFillColor(Color.WHITE)
            },
        )
    }

    private fun createDrawableResourceBackground(
        backgroundDrawable: Drawable,
        borderWidth: Int,
        borderColor: Int,
        rippleColor: Int,
        stateLayerColors: UiStateLayerColors?,
        clickable: Boolean,
        shape: UiShape?,
        layoutDirection: Int,
        density: UiDensity,
    ): Drawable {
        val layeredContent = if (borderWidth > 0) {
            LayerDrawable(
                arrayOf(
                    backgroundDrawable,
                    UiShapeDrawable(shape, layoutDirection, density).apply {
                        setFillColor(Color.TRANSPARENT)
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
            interactionColorStateList(rippleColor, stateLayerColors),
            layeredContent,
            UiShapeDrawable(shape, layoutDirection, density).apply {
                setFillColor(Color.WHITE)
            },
        )
    }

    private fun applyShapeOutline(
        view: View,
        shape: UiShape?,
        forceClip: Boolean = false,
        density: UiDensity,
        surfaceInsets: VerticalSurfaceInsetsPx = VerticalSurfaceInsetsPx.Zero,
    ) {
        if (shape == null && !forceClip) {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.invalidateOutline()
            return
        }
        if (shape != null) {
            val outlineDrawable = UiShapeDrawable(shape, view.layoutDirection, density)
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val top = surfaceInsets.top.coerceIn(0, view.height)
                    val bottom = (view.height - surfaceInsets.bottom).coerceIn(top, view.height)
                    outlineDrawable.setBounds(
                        0,
                        top,
                        view.width,
                        bottom,
                    )
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

    private fun Drawable.withVerticalInsets(insets: VerticalSurfaceInsetsPx): Drawable {
        if (insets == VerticalSurfaceInsetsPx.Zero) return this
        return InsetDrawable(
            this,
            0,
            insets.top,
            0,
            insets.bottom,
        )
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

/** Builds the Android selector for an already resolved renderer-neutral state-layer contract. */
internal fun UiStateLayerColors.toColorStateList(): ColorStateList {
    return ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_hovered),
            intArrayOf(),
        ),
        intArrayOf(
            pressedColor,
            focusedColor,
            hoveredColor,
            Color.TRANSPARENT,
        ),
    )
}

/** Preserves the previous value-only selector when no multi-state contract is supplied. */
internal fun interactionColorStateList(
    rippleColor: Int,
    stateLayerColors: UiStateLayerColors?,
): ColorStateList = stateLayerColors?.toColorStateList() ?: ColorStateList.valueOf(rippleColor)
