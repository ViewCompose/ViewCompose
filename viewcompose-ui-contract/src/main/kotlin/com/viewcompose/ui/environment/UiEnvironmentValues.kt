package com.viewcompose.ui.environment

import com.viewcompose.ui.unit.UiDensity

enum class UiLayoutDirection {
    Ltr,
    Rtl,
}

/**
 * Immutable, ordered locale preference list.
 */
class UiLocaleList private constructor(
    tags: List<String>,
) {
    val tags: List<String> = tags.toList()

    init {
        require(this.tags.isNotEmpty()) { "UiLocaleList must contain at least one locale tag." }
        require(this.tags.none(String::isBlank)) { "UiLocaleList tags must not be blank." }
    }

    operator fun get(index: Int): String = tags[index]

    fun firstOrNull(): String? = tags.firstOrNull()

    override fun equals(other: Any?): Boolean {
        return this === other || other is UiLocaleList && tags == other.tags
    }

    override fun hashCode(): Int = tags.hashCode()

    override fun toString(): String = tags.joinToString()

    companion object {
        val Undetermined = UiLocaleList(listOf("und"))

        fun of(vararg tags: String): UiLocaleList = UiLocaleList(tags.toList())

        fun from(tags: List<String>): UiLocaleList = UiLocaleList(tags)
    }
}

/**
 * Platform-neutral environment captured by every VNode.
 */
data class UiEnvironmentValues(
    val density: UiDensity = UiDensity.Default,
    val locales: UiLocaleList = UiLocaleList.Undetermined,
    val layoutDirection: UiLayoutDirection = UiLayoutDirection.Ltr,
) {
    companion object {
        val Default = UiEnvironmentValues()
    }
}
