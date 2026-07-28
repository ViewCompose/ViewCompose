package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

/**
 * Card 的视觉层级。
 * Visual hierarchy variants for Card.
 */
enum class CardVariant {
    Filled,
    Elevated,
    Outlined,
}

/**
 * Card DSL 的默认容器、边框、阴影和点击反馈 token。
 * Default container, border, elevation, and click feedback tokens for the Card DSL.
 */
object CardDefaults {
    fun containerColor(
        variant: CardVariant = CardVariant.Filled,
    ): Int {
        return when (variant) {
            CardVariant.Filled -> Theme.colors.surfaceVariant
            CardVariant.Elevated,
            CardVariant.Outlined,
            -> Theme.colors.surface
        }
    }

    fun contentColor(): Int = Theme.colors.onSurface

    fun shape(): UiShape = Theme.shapes.medium

    fun elevation(
        variant: CardVariant = CardVariant.Filled,
    ): Int {
        return when (variant) {
            CardVariant.Elevated -> 2.dp
            else -> 0
        }
    }

    fun borderWidth(
        variant: CardVariant = CardVariant.Filled,
    ): Int {
        return when (variant) {
            CardVariant.Outlined -> 1.dp
            else -> 0
        }
    }

    fun borderColor(
        variant: CardVariant = CardVariant.Filled,
    ): Int {
        return when (variant) {
            CardVariant.Outlined -> Theme.colors.outline
            else -> 0x00000000
        }
    }

    fun pressedColor(): Int = Theme.colors.ripple
}
