package com.viewcompose

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
            "environment.resources",
            "overlay.dialog",
            "navigation.system",
            "performance.list",
        ).forEach { scenarioId ->
            launchScenario(scenarioId)
            requireTarget(scenarioId, "root")
            requireTarget(scenarioId, "ready")
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
