package com.viewcompose

import android.content.Context
import com.viewcompose.preview.PreviewThemeProvider
import com.viewcompose.preview.PreviewThemeResolution
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreviewThemeProvider

/**
 * Makes the debug preview worker consume the same stable token source as the on-device demo host.
 */
@ViewComposePreviewThemeProvider
object DemoPreviewThemeProvider : PreviewThemeProvider {
    override fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution {
        return PreviewThemeResolution(
            context = context,
            tokens = when (theme) {
                PreviewTheme.Light -> DemoThemeTokens.light
                PreviewTheme.Dark -> DemoThemeTokens.dark
            },
        )
    }
}
