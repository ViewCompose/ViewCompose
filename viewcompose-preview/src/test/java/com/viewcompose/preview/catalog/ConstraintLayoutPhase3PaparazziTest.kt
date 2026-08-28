package com.viewcompose.preview.catalog

import android.content.pm.ApplicationInfo
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection as ComposeLayoutDirection
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.viewcompose.preview.ViewComposePreviewOptions
import com.viewcompose.preview.ViewComposePreviewWithRoot
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Rule
import org.junit.Test

class ConstraintLayoutPhase3PaparazziTest {
    @get:Rule
    val paparazziRule = Paparazzi(
        deviceConfig = MATRIX.first().deviceConfig(),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.60,
    )

    @Test
    fun constraintLayoutConfigurationMatrix() {
        MATRIX.forEach { fixture ->
            paparazziRule.unsafeUpdateConfig(deviceConfig = fixture.deviceConfig())
            paparazziRule.snapshot(name = fixture.id) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (fixture.rtl) {
                        ComposeLayoutDirection.Rtl
                    } else {
                        ComposeLayoutDirection.Ltr
                    },
                ) {
                    ViewComposePreviewWithRoot(
                        options = ViewComposePreviewOptions(theme = fixture.theme),
                    ) { root ->
                        root.context.applicationInfo.flags = root.context.applicationInfo.flags or
                            ApplicationInfo.FLAG_SUPPORTS_RTL
                        root.layoutDirection = if (fixture.rtl) {
                            View.LAYOUT_DIRECTION_RTL
                        } else {
                            View.LAYOUT_DIRECTION_LTR
                        }
                        UiEnvironment(
                            Environment.values.copy(
                                layoutDirection = if (fixture.rtl) {
                                    UiLayoutDirection.Rtl
                                } else {
                                    UiLayoutDirection.Ltr
                                },
                                locales = UiLocaleList.of(if (fixture.rtl) "ar" else "en"),
                            ),
                        ) {
                            val spec = PreviewCatalog.require("container-constraint-layout")
                            Column(
                                spacing = 10.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .backgroundColor(Theme.colors.background)
                                    .padding(12.dp),
                            ) {
                                Text(text = spec.title, style = UiTextStyle(fontSizeSp = 18.sp))
                                spec.content(this)
                            }
                        }
                    }
                }
            }
        }
    }

    private data class MatrixFixture(
        val id: String,
        val tablet: Boolean,
        val landscape: Boolean,
        val theme: PreviewTheme,
        val rtl: Boolean,
        val fontScale: Float,
    ) {
        fun deviceConfig(): DeviceConfig {
            val base = if (tablet) DeviceConfig.NEXUS_7 else DeviceConfig.PIXEL_5
            return base.copy(
                orientation = if (landscape) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
                nightMode = if (theme == PreviewTheme.Dark) NightMode.NIGHT else NightMode.NOTNIGHT,
                fontScale = fontScale,
                layoutDirection = if (rtl) LayoutDirection.RTL else LayoutDirection.LTR,
                locale = if (rtl) "ar" else "en",
                softButtons = false,
            )
        }
    }

    private companion object {
        val MATRIX = listOf(
            MatrixFixture("phone-portrait-light-ltr-font100", false, false, PreviewTheme.Light, false, 1f),
            MatrixFixture("phone-portrait-dark-rtl-font130", false, false, PreviewTheme.Dark, true, 1.3f),
            MatrixFixture("phone-portrait-light-rtl-font200", false, false, PreviewTheme.Light, true, 2f),
            MatrixFixture("phone-landscape-dark-ltr-font100", false, true, PreviewTheme.Dark, false, 1f),
            MatrixFixture("phone-landscape-light-rtl-font130", false, true, PreviewTheme.Light, true, 1.3f),
            MatrixFixture("phone-landscape-dark-ltr-font200", false, true, PreviewTheme.Dark, false, 2f),
            MatrixFixture("tablet-portrait-dark-rtl-font100", true, false, PreviewTheme.Dark, true, 1f),
            MatrixFixture("tablet-portrait-light-ltr-font130", true, false, PreviewTheme.Light, false, 1.3f),
            MatrixFixture("tablet-portrait-dark-ltr-font200", true, false, PreviewTheme.Dark, false, 2f),
            MatrixFixture("tablet-landscape-light-rtl-font100", true, true, PreviewTheme.Light, true, 1f),
            MatrixFixture("tablet-landscape-dark-ltr-font130", true, true, PreviewTheme.Dark, false, 1.3f),
            MatrixFixture("tablet-landscape-light-rtl-font200", true, true, PreviewTheme.Light, true, 2f),
        )
    }
}
