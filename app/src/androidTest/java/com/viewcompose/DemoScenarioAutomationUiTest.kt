package com.viewcompose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoScenarioAutomationUiTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @After
    fun stopTarget() {
        device.pressHome()
    }

    @Test
    fun ordinaryDedicatedOverlayNavigationAndBenchmarkHostsExposeRoleTargets() {
        listOf(
            "runtime.state",
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
    ) {
        val normalized = scenarioId.replace('.', '_').replace('-', '_')
        val resourceName = "demo_${normalized}_$role"
        val target = device.wait(
            Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
            TARGET_TIMEOUT_MS,
        )
        assertNotNull("Missing $scenarioId/$role", target)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.gzq.uiframework"
        const val TARGET_TIMEOUT_MS = 5_000L
        const val NEW_CLEAR_TASK_FLAGS = "0x10008000"
    }
}
