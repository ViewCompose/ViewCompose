package com.viewcompose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viewcompose.shadow.android.ShadowDecorationLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedShadowDemoDeviceTest {
    @Test
    fun outerShadowPage_installsSingleAndOrderedMultiLayerSpecs() {
        launchGraphicsScenario("graphics.outer-shadow").use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val single = activity.requireScenarioViewByIdVisible<android.view.View>(
                    R.id.demo_graphics_outer_shadow_target,
                )
                val multi = activity.requireViewByTestTagVisible(
                    DemoGraphicsTestTags.GRAPHICS_SHADOW_OUTER_MULTI,
                )

                val singleSpec = ShadowDecorationLayer.specOrNull(single)
                val multiSpec = ShadowDecorationLayer.specOrNull(multi)
                assertNotNull("Expected single shadow spec on rendered sample", singleSpec)
                assertNotNull("Expected multi shadow spec on rendered sample", multiSpec)
                assertEquals(1, singleSpec!!.layerCount)
                assertEquals(2, multiSpec!!.layerCount)
                assertTrue(
                    "Expected declared multi-shadow order to retain different offsets",
                    multiSpec.groups.single().shadows[0].offsetXPx <
                        multiSpec.groups.single().shadows[1].offsetXPx,
                )
            }
        }
    }

    @Test
    fun innerShadowPage_keepsForegroundDecorationInputTransparent() {
        launchGraphicsScenario("graphics.inner-shadow").use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val single = activity.requireScenarioViewByIdVisible<android.view.View>(
                    R.id.demo_graphics_inner_shadow_target,
                )
                val multi = activity.requireViewByTestTagVisible(
                    DemoGraphicsTestTags.GRAPHICS_SHADOW_INNER_MULTI,
                )
                assertEquals(1, ShadowDecorationLayer.innerSpecOrNull(single)?.layerCount)
                assertEquals(2, ShadowDecorationLayer.innerSpecOrNull(multi)?.layerCount)

                activity.requireViewByTestTagVisible(DemoGraphicsTestTags.GRAPHICS_SHADOW_INNER_FIELD)
                    .requestFocus()
                activity.clickScenarioViewById(R.id.demo_graphics_inner_shadow_primary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val count = activity.requireScenarioViewByIdVisible<android.widget.TextView>(
                    R.id.demo_graphics_inner_shadow_state,
                )
                assertEquals(
                    activity.getString(R.string.demo_graphics_inner_click_count, 1),
                    count.text.toString(),
                )
            }
        }
    }

    @Test
    fun diagnosticsPage_drawsLazyShadowAndReportsExactAutoBackend() {
        ShadowDecorationLayer.clearCache()
        ShadowDecorationLayer.resetBackendDiagnostics()
        launchGraphicsScenario("graphics.shadow-list").use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_graphics_shadow_list_primary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val policy = activity.requireTextViewByTestTagVisible(
                    DemoGraphicsTestTags.GRAPHICS_SHADOW_BACKEND_POLICY,
                )
                val backend = activity.requireTextViewByTestTagVisible(
                    DemoGraphicsTestTags.GRAPHICS_SHADOW_BACKEND_ACTUAL,
                )
                val misses = activity.requireTextViewByTestTagVisible(
                    DemoGraphicsTestTags.GRAPHICS_SHADOW_CACHE_MISSES,
                )

                assertEquals("auto", policy.text.toString())
                assertEquals("Bitmap", backend.text.toString())
                assertTrue(misses.text.toString().toLong() > 0L)
            }
            var hitsBeforeRepeatDraw = 0L
            scenario.onActivity { activity ->
                val lazyFirst = activity.requireScenarioViewByIdVisible<android.view.View>(
                    R.id.demo_graphics_shadow_list_target,
                )
                assertEquals(2, ShadowDecorationLayer.specOrNull(lazyFirst)?.layerCount)
                hitsBeforeRepeatDraw = ShadowDecorationLayer.cacheStats().hits
                lazyFirst.invalidate()
                (lazyFirst.parent as? android.view.View)?.invalidate()
            }
            waitForUiIdle()
            assertTrue(
                "Expected a repeated lazy-item draw to reuse the process shadow raster",
                ShadowDecorationLayer.cacheStats().hits > hitsBeforeRepeatDraw,
            )
        }
    }

    private fun launchGraphicsScenario(scenarioId: String) = launchDemoScenarioActivity(
        activityClass = GraphicsActivity::class.java,
        scenarioId = scenarioId,
        themeMode = DemoThemeMode.Light,
    )
}
