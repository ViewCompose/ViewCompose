package com.viewcompose.ui.environment

import com.viewcompose.ui.unit.UiDensity

/**
 * Defines the logical horizontal direction used to resolve start/end layout semantics.
 *
 * `Ltr` resolves start to the left edge and `Rtl` resolves start to the right edge. Renderers
 * consume this value from [UiEnvironmentValues] instead of reading a platform configuration
 * directly.
 */
enum class UiLayoutDirection {
    Ltr,
    Rtl,
}

/**
 * Stores an immutable, ordered list of locale tags for a UI subtree.
 *
 * Tags are retained exactly as supplied; this type does not canonicalize or validate BCP 47
 * syntax. Construction rejects empty lists and blank entries.
 *
 * @property tags immutable locale tags in preference order
 */
class UiLocaleList private constructor(
    tags: List<String>,
) {
    val tags: List<String> = tags.toList()

    init {
        require(this.tags.isNotEmpty()) { "UiLocaleList must contain at least one locale tag." }
        require(this.tags.none(String::isBlank)) { "UiLocaleList tags must not be blank." }
    }

    /**
     * Returns the locale tag at [index].
     *
     * @param index zero-based preference index
     * @return the tag stored at [index]
     * @throws IndexOutOfBoundsException if [index] is outside [tags]
     */
    operator fun get(index: Int): String = tags[index]

    /**
     * Returns the highest-priority locale tag, or `null` if no tag exists.
     *
     * A normally constructed list is never empty, so `null` is reserved for forward-compatible
     * collection use.
     *
     * @return the first tag, or `null` when the list is empty
     */
    fun firstOrNull(): String? = tags.firstOrNull()

    /** Returns whether [other] contains the same tags in the same order. */
    override fun equals(other: Any?): Boolean {
        return this === other || other is UiLocaleList && tags == other.tags
    }

    /** Returns an order-sensitive hash derived from [tags]. */
    override fun hashCode(): Int = tags.hashCode()

    /** Returns the ordered tags joined with the standard collection separator. */
    override fun toString(): String = tags.joinToString()

    /** Creates locale lists and exposes the platform-neutral fallback value. */
    companion object {
        /** Locale list containing the BCP 47 undetermined-language tag `und`. */
        val Undetermined = UiLocaleList(listOf("und"))

        /**
         * Creates a locale list from [tags] in preference order.
         *
         * @param tags locale tags retained in the supplied order
         * @return an immutable locale list
         * @throws IllegalArgumentException if no tags are supplied or any tag is blank
         */
        fun of(vararg tags: String): UiLocaleList = UiLocaleList(tags.toList())

        /**
         * Creates a locale list by copying [tags].
         *
         * Later mutations of [tags] do not affect the returned value.
         *
         * @param tags locale tags in preference order
         * @return an immutable locale list
         * @throws IllegalArgumentException if [tags] is empty or contains a blank tag
         */
        fun from(tags: List<String>): UiLocaleList = UiLocaleList(tags)
    }
}

/**
 * Captures the platform-neutral environment resolved for one VNode subtree.
 *
 * Values are snapshots taken while the declarative tree is built. A renderer must use this
 * captured environment for unit conversion, locale-sensitive behavior, and logical direction;
 * later platform configuration changes require a new tree to update existing nodes.
 *
 * @property density density and font scale used at the renderer boundary
 * @property locales ordered locale preferences for the subtree
 * @property layoutDirection logical direction used to resolve start/end semantics
 */
data class UiEnvironmentValues(
    val density: UiDensity = UiDensity.Default,
    val locales: UiLocaleList = UiLocaleList.Undetermined,
    val layoutDirection: UiLayoutDirection = UiLayoutDirection.Ltr,
) {
    /** Provides the environment used when no host-specific values are installed. */
    companion object {
        /** Unit density, undetermined locale, and left-to-right layout defaults. */
        val Default = UiEnvironmentValues()
    }
}
