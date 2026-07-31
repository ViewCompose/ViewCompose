package com.viewcompose.preview.tooling

/**
 * Selects the single application-owned theme provider used by static previews in one module.
 *
 * The annotated class must implement the Android runtime `PreviewThemeProvider` contract from the
 * `viewcompose-preview` artifact. Keeping discovery as a marker in preview-core lets production
 * source sets retain lightweight preview annotations while the provider itself stays debug-only.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class ViewComposePreviewThemeProvider
