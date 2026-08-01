package com.viewcompose.preview.tooling

import kotlin.jvm.JvmRepeatable

/**
 * Marks a top-level or static ViewCompose DSL function as a tooling preview entry point.
 *
 * The function signature is validated by the preview discovery layer. Keeping this annotation
 * independent from Android and Compose lets Gradle, an IDE plugin, or another host discover the
 * same entry point without loading a UI runtime. The Gradle plugin preserves this metadata for
 * debuggable variants and removes it from non-debuggable Android output before packaging.
 *
 * [heightDp] defaults to [PreviewDefaults.AUTO_HEIGHT_DP]. Auto-height previews start from a
 * standard device viewport and grow to capture vertically scrollable root content. Pass a positive
 * height to keep a fixed viewport when the scroll boundary itself is what the preview validates.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@JvmRepeatable(ViewComposePreviews::class)
annotation class ViewComposePreview(
    val name: String = "",
    val group: String = "",
    val widthDp: Int = PreviewDefaults.WIDTH_DP,
    val heightDp: Int = PreviewDefaults.HEIGHT_DP,
    val density: Float = PreviewDefaults.DENSITY,
    val fontScale: Float = PreviewDefaults.FONT_SCALE,
    val localeTag: String = PreviewDefaults.LOCALE_TAG,
    val layoutDirection: PreviewLayoutDirection = PreviewLayoutDirection.Ltr,
    val theme: PreviewTheme = PreviewTheme.Light,
    val apiLevel: Int = PreviewDefaults.UNSPECIFIED_API_LEVEL,
)

/**
 * JVM container used for repeatable [ViewComposePreview] declarations.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ViewComposePreviews(
    val value: Array<ViewComposePreview>,
)

/**
 * Converts annotation values into the resolved, host-independent preview configuration.
 */
fun ViewComposePreview.toPreviewConfiguration(): PreviewConfiguration {
    return PreviewConfiguration(
        widthDp = widthDp,
        heightDp = heightDp,
        density = density,
        fontScale = fontScale,
        localeTags = listOf(localeTag),
        layoutDirection = layoutDirection,
        theme = theme,
        apiLevel = apiLevel.takeIf { it != PreviewDefaults.UNSPECIFIED_API_LEVEL },
    )
}
