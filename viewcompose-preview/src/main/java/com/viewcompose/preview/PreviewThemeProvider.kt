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
    fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution
}
