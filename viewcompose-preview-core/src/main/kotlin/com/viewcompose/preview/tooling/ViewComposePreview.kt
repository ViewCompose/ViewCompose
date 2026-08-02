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
 *
 * The supported JVM entry point is a public static function with exactly one `UiTreeBuilder`
 * receiver/parameter, no additional parameters, and a `Unit` return. Kotlin default arguments do
 * not make extra parameters invokable by the worker.
 *
 * @property name optional display label; discovery derives one from the function when blank
 * @property group optional IDE gallery group
 * @property widthDp positive logical canvas width
 * @property heightDp positive fixed height or [PreviewDefaults.AUTO_HEIGHT_DP]
 * @property density positive finite physical-pixels-per-dp scale
 * @property fontScale positive finite typography scale
 * @property localeTag non-blank BCP-47 locale tag
 * @property layoutDirection explicit preview direction
 * @property theme light/dark application-resource mode
 * @property apiLevel positive Android API, or [PreviewDefaults.UNSPECIFIED_API_LEVEL]
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
 *
 * @property value preview annotations applied to the same function or annotation class
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ViewComposePreviews(
    val value: Array<ViewComposePreview>,
)

/**
 * Converts annotation values into a validated host-independent configuration.
 *
 * The single locale tag becomes a one-element preference list and the unspecified API sentinel
 * becomes `null`.
 *
 * @throws IllegalArgumentException when annotation values violate [PreviewConfiguration] invariants
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
