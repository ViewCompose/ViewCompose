package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/** Resolves IconButton appearance from semantic Button roles and independent scoped overrides. */
object IconButtonDefaults {
    /** Resolves the container color for [variant] and [enabled] state. */
    fun containerColor(
        variant: ButtonVariant = ButtonVariant.Text,
        enabled: Boolean = true,
    ): Int {
        val overrides = scopedOverrides()
        return resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.containerColor,
            disabledOverride = overrides.disabledContainerColor,
            enabledDefault = ButtonDefaults.semanticContainerColor(variant, enabled = true),
            disabledDefault = ButtonDefaults.semanticContainerColor(variant, enabled = false),
        )
    }

    /** Resolves icon color for [variant] and [enabled] state. */
    fun contentColor(
        variant: ButtonVariant = ButtonVariant.Text,
        enabled: Boolean = true,
    ): Int {
        val overrides = scopedOverrides()
        return resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.contentColor,
            disabledOverride = overrides.disabledContentColor,
            enabledDefault = semanticContentColor(variant, enabled = true),
            disabledDefault = semanticContentColor(variant, enabled = false),
        )
    }

    /** Resolves border color for [variant] and [enabled] state. */
    fun borderColor(
        variant: ButtonVariant = ButtonVariant.Text,
        enabled: Boolean = true,
    ): Int {
        val overrides = scopedOverrides()
        return resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.borderColor,
            disabledOverride = overrides.disabledBorderColor,
            enabledDefault = ButtonDefaults.semanticBorderColor(variant, enabled = true),
            disabledDefault = ButtonDefaults.semanticBorderColor(variant, enabled = false),
        )
    }

    /** Resolves border width for [variant]. */
    fun borderWidth(variant: ButtonVariant = ButtonVariant.Text): UiDp =
        scopedOverrides().borderWidth ?: ButtonDefaults.semanticBorderWidth(variant)

    /** Resolves the standard full shape or a scoped override. */
    fun shape(): UiShape = scopedOverrides().shape ?: Theme.shapes.full

    /** Resolves square IconButton bounds for [size]. */
    fun size(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().size ?: ButtonDefaults.semanticHeight(size)

    /** Resolves uniform icon padding for [size]. */
    fun contentPadding(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().contentPadding ?: semanticContentPadding(size)

    /** Resolves transient interaction colors from the effective enabled icon role. */
    fun stateLayerColors(variant: ButtonVariant = ButtonVariant.Text): UiStateLayerColors {
        val overrides = scopedOverrides()
        return overrides.stateLayerColors ?: stateLayerColorsFor(
            overrides.contentColor ?: semanticContentColor(variant, enabled = true),
        )
    }

    internal fun resolve(
        variant: ButtonVariant,
        size: ButtonSize,
        enabled: Boolean,
        instance: IconButtonOverrides,
    ): ResolvedIconButtonAppearance {
        val overrides = scopedOverrides().merge(instance)
        val contentColor = resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.contentColor,
            disabledOverride = overrides.disabledContentColor,
            enabledDefault = semanticContentColor(variant, enabled = true),
            disabledDefault = semanticContentColor(variant, enabled = false),
        )
        return ResolvedIconButtonAppearance(
            containerColor = resolveStateValue(
                enabled = enabled,
                enabledOverride = overrides.containerColor,
                disabledOverride = overrides.disabledContainerColor,
                enabledDefault = ButtonDefaults.semanticContainerColor(variant, enabled = true),
                disabledDefault = ButtonDefaults.semanticContainerColor(variant, enabled = false),
            ),
            contentColor = contentColor,
            borderColor = resolveStateValue(
                enabled = enabled,
                enabledOverride = overrides.borderColor,
                disabledOverride = overrides.disabledBorderColor,
                enabledDefault = ButtonDefaults.semanticBorderColor(variant, enabled = true),
                disabledDefault = ButtonDefaults.semanticBorderColor(variant, enabled = false),
            ),
            borderWidth = overrides.borderWidth ?: ButtonDefaults.semanticBorderWidth(variant),
            shape = overrides.shape ?: Theme.shapes.full,
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(
                overrides.contentColor ?: semanticContentColor(variant, enabled = true),
            ),
            size = overrides.size ?: ButtonDefaults.semanticHeight(size),
            contentPadding = overrides.contentPadding ?: semanticContentPadding(size),
        )
    }

    private fun semanticContentColor(variant: ButtonVariant, enabled: Boolean): Int {
        if (!enabled) return colorWithAlpha(Theme.colors.onSurface, 0.38f)
        return if (variant == ButtonVariant.Text) {
            Theme.colors.onSurfaceVariant
        } else {
            ButtonDefaults.semanticContentColor(variant, enabled = true)
        }
    }

    private fun semanticContentPadding(size: ButtonSize): UiDp = when (size) {
        ButtonSize.Compact -> 12.dp
        ButtonSize.Medium -> 12.dp
        ButtonSize.Large -> 16.dp
    }

    private fun scopedOverrides(): IconButtonOverrides = UiLocals.current(LocalIconButtonOverrides)
}

internal data class ResolvedIconButtonAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val borderColor: Int,
    val borderWidth: UiDp,
    val shape: UiShape,
    val stateLayerColors: UiStateLayerColors,
    val size: UiDp,
    val contentPadding: UiDp,
)
