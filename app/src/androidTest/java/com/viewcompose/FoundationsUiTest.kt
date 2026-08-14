package com.viewcompose

import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationsUiTest {
    @Test
    fun strictFoundationsFixturesExposeTheirRealTargets() {
        listOf(
            "foundations.locals" to R.id.demo_foundations_locals_target,
            "foundations.theme" to R.id.demo_foundations_theme_target,
            "foundations.media" to R.id.demo_foundations_media_target,
            "foundations.typography" to R.id.demo_foundations_typography_target,
        ).forEach { (scenarioId, targetId) ->
            launchDemoScenarioActivity(
                activityClass = FoundationsActivity::class.java,
                scenarioId = scenarioId,
            ).use { scenario ->
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertViewFullyVisible(
                        activity.requireScenarioViewByIdVisible<View>(targetId),
                    )
                }
            }
        }
    }

    @Test
    fun scopedThemeOverridesRetainTheirDeclaredColors() {
        launchDemoScenarioActivity(
            activityClass = FoundationsActivity::class.java,
            scenarioId = "foundations.theme",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val paletteOverride = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_foundations_theme_target,
                )
                assertTextNotEllipsized(paletteOverride)
                assertViewBackgroundColor(
                    view = paletteOverride,
                    expectedColor = DemoThemeTokens.light.colors.secondary,
                )

                val componentOverride = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_foundations_theme_secondary_target,
                )
                assertTextNotEllipsized(componentOverride)
                assertViewBackgroundColor(
                    view = componentOverride,
                    expectedColor = DemoThemeTokens.light.colors.onSurface,
                )
            }
        }
    }

    @Test
    fun mediaPipelineAndFallbackUseImageViews() {
        launchDemoScenarioActivity(
            activityClass = FoundationsActivity::class.java,
            scenarioId = "foundations.media",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireScenarioViewByIdVisible<ImageView>(
                    R.id.demo_foundations_media_target,
                )
                activity.requireScenarioViewByIdVisible<ImageView>(
                    R.id.demo_foundations_media_secondary_target,
                )
            }
        }
    }

    @Test
    fun typographyTargetUsesSingleLineEndEllipsis() {
        launchDemoScenarioActivity(
            activityClass = FoundationsActivity::class.java,
            scenarioId = "foundations.typography",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_foundations_typography_target,
                )
                assertEquals(1, target.maxLines)
                assertEquals(TextUtils.TruncateAt.END, target.ellipsize)
            }
        }
    }
}
