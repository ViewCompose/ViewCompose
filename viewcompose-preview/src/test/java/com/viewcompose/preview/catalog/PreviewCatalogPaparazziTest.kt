package com.viewcompose.preview.catalog

/*
 * 测试职责：覆盖 preview catalog 中的 Preview Catalog Paparazzi 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Preview Catalog Paparazzi behavior in preview catalog and guards the contract against regressions.
 */

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.viewcompose.preview.catalog.ui.PreviewCatalogSpecScreen
import com.viewcompose.preview.tooling.PreviewTheme
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class PreviewCatalogPaparazziTest {
    private val runtimeRootFallbackRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val key = "paparazzi.layoutlib.runtime.root"
                val original = System.getProperty(key)
                val patched = original?.replace("android-36", "android-35")
                if (patched != null && patched != original) {
                    System.setProperty(key, patched)
                }
                try {
                    base.evaluate()
                } finally {
                    if (original == null) {
                        System.clearProperty(key)
                    } else {
                        System.setProperty(key, original)
                    }
                }
            }
        }
    }

    private val previewEnvironment = detectEnvironment().copy(
        compileSdkVersion = 35,
    )

    private val paparazziRule = Paparazzi(
        environment = previewEnvironment,
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        // Native font metrics, glyphs, and elevation shadows differ slightly between macOS and
        // Linux. Keep this narrow enough to catch layout, color, and structure changes.
        maxPercentDifference = 0.60,
    )

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(runtimeRootFallbackRule)
        .around(paparazziRule)

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
