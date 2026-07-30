package com.viewcompose.preview.tooling

/**
 * Built-in multi-preview for the framework Light and Dark token sets.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ViewComposePreview(name = "Light", theme = PreviewTheme.Light)
@ViewComposePreview(name = "Dark", theme = PreviewTheme.Dark)
annotation class PreviewLightDark

/**
 * Built-in multi-preview for common phone and tablet canvas sizes.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ViewComposePreview(name = "Phone", widthDp = 411, heightDp = 891)
@ViewComposePreview(name = "Tablet", widthDp = 800, heightDp = 1280)
annotation class PreviewPhoneTablet

/**
 * Built-in multi-preview for LTR English and RTL Arabic environments.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ViewComposePreview(
    name = "LTR · en-US",
    localeTag = "en-US",
    layoutDirection = PreviewLayoutDirection.Ltr,
)
@ViewComposePreview(
    name = "RTL · ar-EG",
    localeTag = "ar-EG",
    layoutDirection = PreviewLayoutDirection.Rtl,
)
annotation class PreviewLtrRtl

/**
 * Built-in multi-preview for default, large, and maximum accessibility font scales.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ViewComposePreview(name = "Font 100%", fontScale = 1f)
@ViewComposePreview(name = "Font 130%", fontScale = 1.3f)
@ViewComposePreview(name = "Font 200%", fontScale = 2f)
annotation class PreviewFontScales
