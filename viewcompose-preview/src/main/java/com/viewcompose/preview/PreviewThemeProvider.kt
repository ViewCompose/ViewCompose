package com.viewcompose.preview

import android.content.Context
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.widget.core.UiThemeTokens

/**
 * One application-owned theme resolution shared by the preview root, native Views, and DSL tree.
 *
 * [context] must carry the Android theme/resources that native Views should observe. [tokens] are
 * installed as the outermost ViewCompose UiTheme. Returning both from one provider prevents the
 * two rendering layers from silently resolving different themes.
 *
 * @property context themed context used to create the preview root and every native View
 * @property tokens immutable ViewCompose theme installed around the preview DSL tree
 */
data class PreviewThemeResolution(
    val context: Context,
    val tokens: UiThemeTokens,
)

/**
 * Resolves the application theme for a static preview variant.
 *
 * Implementations should be stateless Kotlin objects or public classes with a no-argument
 * constructor. Mark exactly one implementation in a previewed module with
 * `@ViewComposePreviewThemeProvider` from preview-core.
 */
fun interface PreviewThemeProvider {
    /**
     * Resolves the themed Android context and ViewCompose tokens for [theme].
     *
     * The supplied [context] already carries the requested density, font scale, locales, layout
     * direction, viewport qualifiers, and light/dark resource mode. Implementations may wrap it
     * with an application theme but must preserve those qualifiers. This method is called once per
     * static-preview mount and should not retain the context or depend on mutable process state.
     *
     * Throwing aborts the mount and becomes a source-aware theme-provider diagnostic in the static
     * runner; thread death and out-of-memory errors remain fatal.
     *
     * @return one coherent native-View and ViewCompose theme resolution
     * @sample com.viewcompose.preview.samples.applicationPreviewThemeProviderSample
     */
    fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution
}
