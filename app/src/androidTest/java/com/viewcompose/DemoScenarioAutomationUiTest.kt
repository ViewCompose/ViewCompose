package com.viewcompose

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DemoScenarioAutomationUiTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val originalLanguageTags = currentApplicationLanguageTags()

    @After
    fun stopTarget() {
        setApplicationLanguageTags(originalLanguageTags)
        device.pressHome()
    }

    @Test
    fun catalogAutomationResourceIdsRemainStableAcrossEnglishAndSimplifiedChinese() {
        listOf(
            "en" to R.string.demo_catalog_ready,
            "zh-CN" to R.string.demo_catalog_ready,
        ).forEach { (languageTag, readyResource) ->
            setApplicationLanguageTags(languageTag)
            launchDemoActivity(MainActivity::class.java).use {
                waitForUiIdle()
                val ready = requireTarget("catalog", "ready")
                assertEquals(localizedString(languageTag, readyResource), ready.text)
            }
        }
    }

    @Test
    fun ordinaryDedicatedOverlayNavigationAndBenchmarkHostsExposeRoleTargets() {
        listOf(
            "runtime.state",
            "runtime.key-identity",
            "runtime.view-patch",
            "input.fields",
            "input.selection",
            "input.stress",
            "input.search",
            "input.derived-summary",
            "gesture.tap",
            "gesture.drag-swipe",
            "gesture.transform",
            "graphics.drawing",
            "graphics.outer-shadow",
            "graphics.inner-shadow",
            "graphics.shadow-list",
            "animation.core",
            "animation.content",
            "animation.list-motion",
            "animation.specs",
            "animation.transition",
            "animation.infinite",
            "modifier.visual",
            "modifier.sizing",
            "modifier.accessibility",
            "diagnostics.runtime",
            "diagnostics.theme",
            "diagnostics.renderer",
            "collection.controls",
            "collection.lazy-list",
            "collection.stress",
            "collection.android-view",
            "collection.lazy-row",
            "collection.grid",
            "collection.pull-refresh",
            "layout.linear",
            "layout.stack",
            "layout.edges",
            "layout.flow",
            "layout.scroll",
            "layout.constraint",
            "environment.resources",
            "environment.cross-activity-theme",
            "interop.android-view",
            "overlay.transient",
            "overlay.dialog",
            "overlay.menu",
            "navigation.system",
            "design.material3-xml",
            "design.material3-static",
            "design.material3-custom",
            "design.bundle-material3",
            "design.bundle-contrast",
            "design.oneui7",
            "component.card",
            "component.fab",
            "component.chip",
            "component.list-item",
            "component.app-bars",
            "component.navigation-bar",
            "component.scaffold",
            "component.button",
            "component.icon-button",
            "component.segmented-control",
            "component.divider",
            "component.progress",
            "foundations.locals",
            "foundations.theme",
            "foundations.media",
            "foundations.typography",
            "performance.list",
            "performance.complex-layout",
            "performance.shadow-list",
            "performance.shadow-complex-layout",
        ).forEach { scenarioId ->
            launchScenario(scenarioId)
            requireTarget(scenarioId, "root")
            requireTarget(scenarioId, "ready")
        }
    }

    @Test
    fun performanceFixturesExposeTheSameContractForBothEngines() {
        listOf(
            "performance.list",
            "performance.complex-layout",
            "performance.shadow-list",
            "performance.shadow-complex-layout",
        ).forEach { scenarioId ->
            listOf("viewcompose", "compose").forEach { engine ->
                launchPerformanceScenario(scenarioId, engine)
                requireTarget(scenarioId, "root")
                assertTrue(requireTarget(scenarioId, "ready").text.contains(engine, ignoreCase = true))
                requireTarget(scenarioId, "target")
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                val updated = waitForTargetTextChange(scenarioId, initial)
                assertNotEquals("$scenarioId/$engine action must publish state", initial, updated)

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId/$engine reset must restore initial state",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun crossActivityThemeFixturePublishesDeterministicStateAcrossIndependentSessions() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            val scenarioId = "environment.cross-activity-theme"
            launchScenario(scenarioId)
            val initial = requireTarget(scenarioId, "state").text.orEmpty()

            requireTarget(scenarioId, "primary_action").click()
            assertNotEquals(
                "$scenarioId action must publish state",
                initial,
                waitForTargetTextChange(scenarioId, initial),
            )
            requireTarget(scenarioId, "reset").click()
            assertEquals(initial, waitForTargetText(scenarioId, initial))

            requireTarget(scenarioId, "secondary_action").click()
            val secondaryInitial = requireResource(
                "demo_cross_activity_theme_secondary_state",
            ).text.orEmpty()
            requireResource("demo_cross_activity_theme_secondary_action").click()
            assertNotEquals(
                "$scenarioId secondary action must publish state",
                secondaryInitial,
                waitForResourceTextChange(
                    resourceName = "demo_cross_activity_theme_secondary_state",
                    previous = secondaryInitial,
                ),
            )
            requireResource("demo_cross_activity_theme_secondary_return").click()
            assertNotEquals(
                "$scenarioId primary Session must observe the secondary change",
                initial,
                waitForTargetTextChange(scenarioId, initial),
            )
            requireTarget(scenarioId, "reset").click()
            assertEquals(initial, waitForTargetText(scenarioId, initial))
        }
    }

    @Test
    fun inputFixturesPublishDeterministicActionAndResetState() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "input.fields",
                "input.selection",
                "input.stress",
                "input.search",
                "input.derived-summary",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                val changed = waitForTargetTextChange(scenarioId, initial)
                assertNotEquals("$scenarioId action must publish state", initial, changed)

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must restore initial state",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun gestureFixturesPublishDeterministicActionAndResetState() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "gesture.tap",
                "gesture.drag-swipe",
                "gesture.transform",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                val changed = waitForTargetTextChange(scenarioId, initial)
                assertNotEquals("$scenarioId action must publish state", initial, changed)

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must restore initial state",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun graphicsFixturesPublishDeterministicActionAndResetState() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "graphics.drawing",
                "graphics.inner-shadow",
                "graphics.shadow-list",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                val changed = waitForTargetTextChange(scenarioId, initial)
                assertNotEquals("$scenarioId action must publish state", initial, changed)

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must restore initial state",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun animationFixturesPublishDeterministicActionAndResetState() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "animation.core",
                "animation.content",
                "animation.list-motion",
                "animation.specs",
                "animation.transition",
                "animation.infinite",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                val changed = waitForTargetTextChange(scenarioId, initial)
                assertNotEquals("$scenarioId action must publish state", initial, changed)

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must restore initial state",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun interopFixturePublishesDeterministicActionAndResetState() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            val scenarioId = "interop.android-view"
            launchScenario(scenarioId)
            val initial = requireTarget(scenarioId, "state").text.orEmpty()

            requireTarget(scenarioId, "primary_action").click()
            val changed = waitForTargetTextChange(scenarioId, initial)
            assertNotEquals("$scenarioId action must publish state", initial, changed)

            requireTarget(scenarioId, "reset").click()
            assertEquals(
                "$scenarioId reset must restore initial state",
                initial,
                waitForTargetText(scenarioId, initial),
            )
        }
    }

    @Test
    fun overlayFixturesPublishDeterministicActionAndResetState() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "overlay.transient",
                "overlay.dialog",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                requireTarget(scenarioId, "target")

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must restore initial state",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }

            val menuScenarioId = "overlay.menu"
            launchScenario(menuScenarioId)
            val initial = requireTarget(menuScenarioId, "state").text.orEmpty()
            requireTarget(menuScenarioId, "primary_action").click()
            assertNotEquals(
                "$menuScenarioId action must publish state",
                initial,
                waitForTargetTextChange(menuScenarioId, initial),
            )
            requireTarget(menuScenarioId, "reset").click()
            assertEquals(
                "$menuScenarioId reset must restore initial state",
                initial,
                waitForTargetText(menuScenarioId, initial),
            )
        }
    }

    @Test
    fun systemNavigationFixturePublishesDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            val scenarioId = "navigation.system"
            launchScenario(scenarioId)
            val initial = requireTarget(scenarioId, "state").text.orEmpty()

            requireTarget(scenarioId, "primary_action").click()
            assertNotEquals(
                "$scenarioId action must publish state",
                initial,
                waitForTargetTextChange(scenarioId, initial),
            )

            requireTarget(scenarioId, "reset").click()
            assertEquals(
                "$scenarioId reset must recreate the initial navigation Session",
                initial,
                waitForTargetText(scenarioId, initial),
            )
        }
    }

    @Test
    fun material3SourceFixturesPublishDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "design.material3-xml",
                "design.material3-static",
                "design.material3-custom",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                assertNotEquals(
                    "$scenarioId action must publish state",
                    initial,
                    waitForTargetTextChange(scenarioId, initial),
                )

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must recreate the initial Material 3 fixture Session",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun designSystemBundleFixturesPublishDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "design.bundle-material3",
                "design.bundle-contrast",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                assertNotEquals(
                    "$scenarioId action must publish state",
                    initial,
                    waitForTargetTextChange(scenarioId, initial),
                )

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must recreate the initial design-system Session",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun oneUi7FixturePublishesDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            val scenarioId = "design.oneui7"
            launchScenario(scenarioId)
            val initial = requireTarget(scenarioId, "state").text.orEmpty()

            requireTarget(scenarioId, "primary_action").click()
            assertNotEquals(
                "$scenarioId action must publish state",
                initial,
                waitForTargetTextChange(scenarioId, initial),
            )

            requireTarget(scenarioId, "reset").click()
            assertEquals(
                "$scenarioId reset must recreate the initial One UI Session",
                initial,
                waitForTargetText(scenarioId, initial),
            )
        }
    }

    @Test
    fun actionComponentFixturesPublishDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "component.card",
                "component.fab",
                "component.chip",
                "component.list-item",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                assertNotEquals(
                    "$scenarioId action must publish state",
                    initial,
                    waitForTargetTextChange(scenarioId, initial),
                )

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must recreate the initial component Session",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun navigationComponentFixturesPublishDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "component.app-bars",
                "component.navigation-bar",
                "component.scaffold",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                assertNotEquals(
                    "$scenarioId action must publish state",
                    initial,
                    waitForTargetTextChange(scenarioId, initial),
                )

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must recreate the initial component Session",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun componentShowcaseFixturesPublishDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            listOf(
                "component.button",
                "component.icon-button",
                "component.segmented-control",
                "component.progress",
            ).forEach { scenarioId ->
                launchScenario(scenarioId)
                val initial = requireTarget(scenarioId, "state").text.orEmpty()

                requireTarget(scenarioId, "primary_action").click()
                assertNotEquals(
                    "$scenarioId action must publish state",
                    initial,
                    waitForTargetTextChange(scenarioId, initial),
                )

                requireTarget(scenarioId, "reset").click()
                assertEquals(
                    "$scenarioId reset must recreate the initial component Session",
                    initial,
                    waitForTargetText(scenarioId, initial),
                )
            }
        }
    }

    @Test
    fun foundationsMediaPublishesDeterministicActionAndFullSessionReset() {
        listOf("en", "zh-CN").forEach { languageTag ->
            setApplicationLanguageTags(languageTag)
            val scenarioId = "foundations.media"
            launchScenario(scenarioId)
            val initial = requireTarget(scenarioId, "state").text.orEmpty()

            requireTarget(scenarioId, "primary_action").click()
            assertNotEquals(
                "$scenarioId action must publish state",
                initial,
                waitForTargetTextChange(scenarioId, initial),
            )

            requireTarget(scenarioId, "reset").click()
            assertEquals(
                "$scenarioId reset must recreate the initial media Session",
                initial,
                waitForTargetText(scenarioId, initial),
            )
        }
    }

    @Test
    fun strictLauncherRedirectLeavesOnlyTheScenarioHostInTheForegroundTask() {
        launchScenario("runtime.state")
        requireTarget("runtime.state", "ready")

        val dump = device.executeShellCommand("dumpsys activity activities")
        val lines = dump.lineSequence().toList()
        val taskStart = lines.indexOfFirst { line -> line.trimStart().startsWith("* Task{") }
        assertTrue("Expected a foreground task in activity dump", taskStart >= 0)
        val nextTaskOffset = lines.drop(taskStart + 1).indexOfFirst { line ->
            line.trimStart().startsWith("* Task{")
        }
        val taskEnd = if (nextTaskOffset >= 0) taskStart + 1 + nextTaskOffset else lines.size
        val foregroundTask = lines.subList(taskStart, taskEnd).joinToString("\n")
        val history = foregroundTask.lineSequence()
            .filter { line -> line.contains("* Hist") }
            .toList()

        assertTrue(foregroundTask.contains(TARGET_PACKAGE))
        assertTrue(history.any { line -> line.contains("com.viewcompose.StateActivity") })
        assertFalse(history.any { line -> line.contains("com.viewcompose.MainActivity") })
    }

    private fun launchScenario(scenarioId: String) {
        device.pressHome()
        device.executeShellCommand(
            "am start -W -n $TARGET_PACKAGE/com.viewcompose.MainActivity " +
                "-f $NEW_CLEAR_TASK_FLAGS " +
                "--es demo_scenario_id $scenarioId",
        )
    }

    private fun launchPerformanceScenario(
        scenarioId: String,
        engine: String,
    ) {
        device.pressHome()
        device.executeShellCommand(
            "am start -W -n $TARGET_PACKAGE/com.viewcompose.MainActivity " +
                "-f $NEW_CLEAR_TASK_FLAGS " +
                "--es demo_scenario_id $scenarioId " +
                "--es performance_engine $engine",
        )
    }

    private fun requireTarget(
        scenarioId: String,
        role: String,
    ): androidx.test.uiautomator.UiObject2 {
        val normalized = scenarioId.replace('.', '_').replace('-', '_')
        val resourceName = "demo_${normalized}_$role"
        val target = device.wait(
            Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
            TARGET_TIMEOUT_MS,
        )
        assertNotNull("Missing $scenarioId/$role", target)
        return requireNotNull(target)
    }

    private fun requireResource(resourceName: String): androidx.test.uiautomator.UiObject2 {
        val target = device.wait(
            Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
            TARGET_TIMEOUT_MS,
        )
        assertNotNull("Missing resource target: $resourceName", target)
        return requireNotNull(target)
    }

    private fun waitForResourceTextChange(
        resourceName: String,
        previous: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + TARGET_TIMEOUT_MS
        var current = requireResource(resourceName).text.orEmpty()
        while (current == previous && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(16L)
            current = requireResource(resourceName).text.orEmpty()
        }
        return current
    }

    private fun waitForTargetTextChange(
        scenarioId: String,
        previous: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + TARGET_TIMEOUT_MS
        var current = readTargetText(scenarioId)
        while ((current == null || current == previous) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(16L)
            current = readTargetText(scenarioId)
        }
        return current.orEmpty()
    }

    private fun waitForTargetText(
        scenarioId: String,
        expected: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + TARGET_TIMEOUT_MS
        var current = readTargetText(scenarioId)
        while (current != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(16L)
            current = readTargetText(scenarioId)
        }
        return current.orEmpty()
    }

    private fun readTargetText(scenarioId: String): String? =
        try {
            requireTarget(scenarioId, "state").text.orEmpty()
        } catch (_: StaleObjectException) {
            // A full fixture reset intentionally replaces the exposed View node.
            null
        }

    private fun localizedString(
        languageTag: String,
        resourceId: Int,
    ): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        return context.createConfigurationContext(configuration).getString(resourceId)
    }

    private fun currentApplicationLanguageTags(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
    }

    private fun setApplicationLanguageTags(languageTags: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(languageTags)
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(languageTags),
                )
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.gzq.uiframework"
        const val TARGET_TIMEOUT_MS = 5_000L
        const val NEW_CLEAR_TASK_FLAGS = "0x10008000"
    }
}
