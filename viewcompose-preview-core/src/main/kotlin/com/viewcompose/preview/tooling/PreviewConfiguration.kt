package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/**
 * Stable defaults shared by annotations, Gradle tooling, and render workers.
 */
object PreviewDefaults {
    const val WIDTH_DP: Int = 411
    const val HEIGHT_DP: Int = 891
    const val DENSITY: Float = 1f
    const val FONT_SCALE: Float = 1f
    const val LOCALE_TAG: String = "en-US"
    const val UNSPECIFIED_API_LEVEL: Int = -1
}

/**
 * Explicit theme used for a deterministic preview render.
 */
@Serializable
enum class PreviewTheme {
    Light,
    Dark,
}

/**
 * Explicit layout direction used for a deterministic preview render.
 */
@Serializable
enum class PreviewLayoutDirection {
    Ltr,
    Rtl,
}

/**
 * Fully resolved environment for one static preview render.
 *
 * This model intentionally contains no "system" values. A caller must resolve system-dependent
 * choices before sending a request so the same request produces the same preview in Gradle, CI,
 * and Android Studio.
 */
@Serializable
data class PreviewConfiguration(
    val widthDp: Int = PreviewDefaults.WIDTH_DP,
    val heightDp: Int = PreviewDefaults.HEIGHT_DP,
    val density: Float = PreviewDefaults.DENSITY,
    val fontScale: Float = PreviewDefaults.FONT_SCALE,
    val localeTags: List<String> = listOf(PreviewDefaults.LOCALE_TAG),
    val layoutDirection: PreviewLayoutDirection = PreviewLayoutDirection.Ltr,
    val theme: PreviewTheme = PreviewTheme.Light,
    val apiLevel: Int? = null,
) {
    init {
        require(widthDp > 0) { "Preview widthDp must be greater than zero." }
        require(heightDp > 0) { "Preview heightDp must be greater than zero." }
        require(density.isFinite() && density > 0f) {
            "Preview density must be finite and greater than zero."
        }
        require(fontScale.isFinite() && fontScale > 0f) {
            "Preview fontScale must be finite and greater than zero."
        }
        require(localeTags.isNotEmpty()) { "Preview localeTags must not be empty." }
        require(localeTags.none(String::isBlank)) {
            "Preview localeTags must not contain blank values."
        }
        require(apiLevel == null || apiLevel > 0) {
            "Preview apiLevel must be null or greater than zero."
        }
    }
}

/**
 * Named configuration presented as one selectable preview variant.
 */
@Serializable
data class PreviewVariant(
    val id: String,
    val displayName: String,
    val configuration: PreviewConfiguration,
) {
    init {
        require(id.isNotBlank()) { "Preview variant id must not be blank." }
        require(displayName.isNotBlank()) { "Preview variant displayName must not be blank." }
        require(id.none(Char::isISOControl)) {
            "Preview variant id must not contain control characters."
        }
    }
}
