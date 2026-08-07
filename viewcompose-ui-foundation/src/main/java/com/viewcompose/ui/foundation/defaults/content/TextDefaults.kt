package com.viewcompose.ui.foundation

/** Semantic size tiers used to select typography tokens. */
enum class TypographyTier {
    /** The largest style in a typography family. */
    Large,
    /** The default style in a typography family. */
    Medium,
    /** The smallest style in a typography family. */
    Small,
}

/** Resolves default typography and color values for text components. */
object TextDefaults {
    /** Returns the text style inherited from the nearest text-style provider. */
    fun currentStyle(): UiTextStyle = TextStyle.current

    /** Returns a title style at [tier] from the current theme. */
    fun titleStyle(tier: TypographyTier = TypographyTier.Medium): UiTextStyle {
        return when (tier) {
            TypographyTier.Large -> Theme.typography.titleLarge
            TypographyTier.Medium -> Theme.typography.titleMedium
            TypographyTier.Small -> Theme.typography.titleSmall
        }
    }

    /**
     * Returns a display style at [tier] from the current theme.
     *
     * @param tier semantic size within the display family
     * @return the matching display text style
     */
    fun displayStyle(tier: TypographyTier = TypographyTier.Medium): UiTextStyle {
        return when (tier) {
            TypographyTier.Large -> Theme.typography.displayLarge
            TypographyTier.Medium -> Theme.typography.displayMedium
            TypographyTier.Small -> Theme.typography.displaySmall
        }
    }

    /**
     * Returns a headline style at [tier] from the current theme.
     *
     * @param tier semantic size within the headline family
     * @return the matching headline text style
     */
    fun headlineStyle(tier: TypographyTier = TypographyTier.Medium): UiTextStyle {
        return when (tier) {
            TypographyTier.Large -> Theme.typography.headlineLarge
            TypographyTier.Medium -> Theme.typography.headlineMedium
            TypographyTier.Small -> Theme.typography.headlineSmall
        }
    }

    /** Returns a body style at [tier] from the current theme. */
    fun bodyStyle(tier: TypographyTier = TypographyTier.Medium): UiTextStyle {
        return when (tier) {
            TypographyTier.Large -> Theme.typography.bodyLarge
            TypographyTier.Medium -> Theme.typography.bodyMedium
            TypographyTier.Small -> Theme.typography.bodySmall
        }
    }

    /** Returns a label style at [tier] from the current theme. */
    fun labelStyle(tier: TypographyTier = TypographyTier.Medium): UiTextStyle {
        return when (tier) {
            TypographyTier.Large -> Theme.typography.labelLarge
            TypographyTier.Medium -> Theme.typography.labelMedium
            TypographyTier.Small -> Theme.typography.labelSmall
        }
    }

    /** Returns the current theme's large title style. */
    fun titleLargeStyle(): UiTextStyle = titleStyle(TypographyTier.Large)

    /** Returns the current theme's medium title style. */
    fun titleMediumStyle(): UiTextStyle = titleStyle(TypographyTier.Medium)

    /** Returns the current theme's small title style. */
    fun titleSmallStyle(): UiTextStyle = titleStyle(TypographyTier.Small)

    /** Returns the current theme's large display style. */
    fun displayLargeStyle(): UiTextStyle = displayStyle(TypographyTier.Large)

    /** Returns the current theme's medium display style. */
    fun displayMediumStyle(): UiTextStyle = displayStyle(TypographyTier.Medium)

    /** Returns the current theme's small display style. */
    fun displaySmallStyle(): UiTextStyle = displayStyle(TypographyTier.Small)

    /** Returns the current theme's large headline style. */
    fun headlineLargeStyle(): UiTextStyle = headlineStyle(TypographyTier.Large)

    /** Returns the current theme's medium headline style. */
    fun headlineMediumStyle(): UiTextStyle = headlineStyle(TypographyTier.Medium)

    /** Returns the current theme's small headline style. */
    fun headlineSmallStyle(): UiTextStyle = headlineStyle(TypographyTier.Small)

    /** Returns the current theme's large body style. */
    fun bodyLargeStyle(): UiTextStyle = bodyStyle(TypographyTier.Large)

    /** Returns the current theme's medium body style. */
    fun bodyMediumStyle(): UiTextStyle = bodyStyle(TypographyTier.Medium)

    /** Returns the current theme's small body style. */
    fun bodySmallStyle(): UiTextStyle = bodyStyle(TypographyTier.Small)

    /** Returns the current theme's large label style. */
    fun labelLargeStyle(): UiTextStyle = labelStyle(TypographyTier.Large)

    /** Returns the current theme's medium label style. */
    fun labelMediumStyle(): UiTextStyle = labelStyle(TypographyTier.Medium)

    /** Returns the current theme's small label style. */
    fun labelSmallStyle(): UiTextStyle = labelStyle(TypographyTier.Small)

    /** Returns the primary text color inherited from the content-color provider. */
    fun primaryColor(): Int = ContentColor.current

    /** Returns the current theme's secondary text color. */
    fun secondaryColor(): Int = Theme.colors.onSurfaceVariant
}
