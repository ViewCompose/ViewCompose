package com.viewcompose.preview.catalog

/*
 * 测试职责：覆盖 preview catalog 中的 Preview Catalog Paparazzi 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Preview Catalog Paparazzi behavior in preview catalog and guards the contract against regressions.
 */

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.viewcompose.preview.catalog.ui.PreviewCatalogSpecScreen
import com.viewcompose.preview.tooling.PreviewTheme
import org.junit.Rule
import org.junit.Test

class PreviewCatalogPaparazziTest {
    @get:Rule
    val paparazziRule = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        // Native font metrics, glyphs, and elevation shadows differ slightly between macOS and
        // Linux. Keep this narrow enough to catch layout, color, and structure changes.
        maxPercentDifference = 0.60,
    )

    @Test
    fun snapshotCatalogLightTheme() {
        PreviewCatalog.specs.forEach { spec ->
            runCatching {
                paparazziRule.snapshot(name = spec.id) {
                    PreviewCatalogSpecScreen(
                        specId = spec.id,
                        themeMode = PreviewTheme.Light,
                    )
                }
            }.getOrElse { error ->
                throw AssertionError("Paparazzi snapshot failed for preview spec `${spec.id}`", error)
            }
        }
    }
}
