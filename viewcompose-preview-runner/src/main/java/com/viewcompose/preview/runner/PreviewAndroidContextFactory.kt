package com.viewcompose.preview.runner

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.viewportHeightDp
import kotlin.math.roundToInt

/**
 * Creates a resource Context matching the resolved preview configuration.
 *
 * UiEnvironment remains the DSL source of truth, while this Context keeps native Android Views,
 * resource qualifiers, AndroidView interop, and application-theme token resolution on the same
 * configuration.
 */
object PreviewAndroidContextFactory {
    fun create(
        base: Context,
        preview: PreviewConfiguration,
    ): Context {
        val viewportHeightDp = preview.viewportHeightDp
        val configuration = Configuration(base.resources.configuration).apply {
            densityDpi = (preview.density * DENSITY_DEFAULT).roundToInt().coerceAtLeast(1)
            fontScale = preview.fontScale
            screenWidthDp = preview.widthDp
            screenHeightDp = viewportHeightDp
            smallestScreenWidthDp = minOf(preview.widthDp, viewportHeightDp)
            orientation = if (preview.widthDp > viewportHeightDp) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
            setLocales(LocaleList.forLanguageTags(preview.localeTags.joinToString(",")))
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK.inv()) or
                when (preview.layoutDirection) {
                    PreviewLayoutDirection.Ltr -> Configuration.SCREENLAYOUT_LAYOUTDIR_LTR
                    PreviewLayoutDirection.Rtl -> Configuration.SCREENLAYOUT_LAYOUTDIR_RTL
                }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                when (preview.theme) {
                    PreviewTheme.Light -> Configuration.UI_MODE_NIGHT_NO
                    PreviewTheme.Dark -> Configuration.UI_MODE_NIGHT_YES
                }
        }
        val configuredContext: Context? = base.createConfigurationContext(configuration)
        // Some Layoutlib bridges return null because their device configuration is owned by the
        // render session. In that case the worker host must create/update Layoutlib with the same
        // resolved PreviewConfiguration before calling the runner.
        return configuredContext ?: base
    }

    private const val DENSITY_DEFAULT: Int = 160
}
