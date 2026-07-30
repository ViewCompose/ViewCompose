package com.viewcompose

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
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
        launchGraphicsPage(GRAPHICS_PAGE_OUTER_SHADOWS).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val single = activity.requireViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_OUTER_SINGLE,
                )
                val multi = activity.requireViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_OUTER_MULTI,
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
        launchGraphicsPage(GRAPHICS_PAGE_INNER_SHADOWS).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val single = activity.requireViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_INNER_SINGLE,
                )
                val multi = activity.requireViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_INNER_MULTI,
                )
                assertEquals(1, ShadowDecorationLayer.innerSpecOrNull(single)?.layerCount)
                assertEquals(2, ShadowDecorationLayer.innerSpecOrNull(multi)?.layerCount)

                activity.requireViewByTestTagVisible(DemoTestTags.GRAPHICS_SHADOW_INNER_FIELD)
                    .requestFocus()
                activity.clickByTestTag(DemoTestTags.GRAPHICS_SHADOW_INNER_CLICK_BUTTON)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val count = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_INNER_CLICK_COUNT,
                )
                assertEquals("点击次数 1", count.text.toString())
            }
        }
    }

    @Test
    fun diagnosticsPage_drawsLazyShadowAndReportsExactAutoBackend() {
        ShadowDecorationLayer.clearCache()
        ShadowDecorationLayer.resetBackendDiagnostics()
        launchGraphicsPage(GRAPHICS_PAGE_SHADOW_DIAGNOSTICS).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.GRAPHICS_SHADOW_DIAGNOSTICS_REFRESH)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val policy = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_BACKEND_POLICY,
                )
                val backend = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_BACKEND_ACTUAL,
                )
                val misses = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_CACHE_MISSES,
                )

                assertEquals("auto", policy.text.toString())
                assertEquals("Bitmap", backend.text.toString())
                assertTrue(misses.text.toString().toLong() > 0L)
            }
            var hitsBeforeRepeatDraw = 0L
            scenario.onActivity { activity ->
                val lazyFirst = activity.requireViewByTestTagVisible(
                    DemoTestTags.GRAPHICS_SHADOW_LAZY_FIRST,
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

    private fun launchGraphicsPage(page: Int) = launchDemoActivity<GraphicsActivity>(
        Intent(
            ApplicationProvider.getApplicationContext(),
            GraphicsActivity::class.java,
        ).putExtra(GraphicsActivity.EXTRA_GRAPHICS_PAGE_INDEX, page),
        themeMode = DemoThemeMode.Light,
    )
}
