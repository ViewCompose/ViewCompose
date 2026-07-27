package com.viewcompose.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull

internal fun MacrobenchmarkScope.startDemoAndWait() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.text("Demo Theme")), UI_WAIT_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.startCatalogAndWait() {
    startDemoAndWait()
    if (!device.wait(Until.hasObject(By.text("Capability Modules")), 1_000)) {
        val backNode = device.findObject(By.text("Back to catalog"))
        if (backNode != null) {
            backNode.click()
            device.waitForIdle()
        } else {
            device.pressBack()
            device.waitForIdle()
        }
    }
    waitForText("Capability Modules")
    scrollToPageTop()
    waitForText("Capability Modules")
}

internal fun MacrobenchmarkScope.startDemoActivityAndWait(
    moduleKey: String,
    expectedText: String,
    extras: Map<String, Int> = emptyMap(),
) {
    pressHome()
    startActivityAndWait { intent ->
        // Clear app-specific extras from previous test methods while
        // preserving any system extras the benchmark framework needs.
        intent.removeExtra("demo_module_key")
        intent.removeExtra("state_page_index")
        intent.removeExtra("performance_engine")
        intent.removeExtra("performance_scenario")
        // When extras are present, clear the task so the activity is recreated
        // fresh with the new extras. Otherwise, remove the flag so subsequent
        // tests without extras don't inherit the aggressive clear behavior.
        if (extras.isNotEmpty()) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } else {
            intent.flags = intent.flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK.inv()
        }
        intent.putExtra("demo_module_key", moduleKey)
        extras.forEach { (key, value) -> intent.putExtra(key, value) }
    }
    waitForText(expectedText)
}

internal fun MacrobenchmarkScope.startPerformanceComparisonAndWait(
    engine: String,
    scenario: String,
    expectedText: String,
) {
    pressHome()
    startActivityAndWait { intent ->
        intent.removeExtra("demo_module_key")
        intent.removeExtra("state_page_index")
        intent.putExtra("performance_engine", engine)
        intent.putExtra("performance_scenario", scenario)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    waitForText(expectedText)
}

internal fun MacrobenchmarkScope.startSystemNavigationAndWait() {
    pressHome()
    startActivityAndWait { intent ->
        intent.setClassName(
            TARGET_PACKAGE,
            "com.viewcompose.SystemNavigationActivity",
        )
        intent.action = android.content.Intent.ACTION_MAIN
        intent.data = null
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    waitForText("首页总览")
}

internal fun MacrobenchmarkScope.waitForText(text: String) {
    device.wait(Until.hasObject(By.text(text)), UI_WAIT_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.scrollUntilText(
    text: String,
    maxSwipes: Int = 10,
) {
    repeat(maxSwipes + 1) { attempt ->
        if (device.hasObject(By.text(text))) {
            return
        }
        if (attempt < maxSwipes) {
            swipePageUp()
        }
    }
    // Text not found scrolling down — try scrolling back up.
    repeat(maxSwipes * 2) { attempt ->
        if (device.hasObject(By.text(text))) {
            return
        }
        swipePageDown()
    }
    waitForText(text)
}

internal fun MacrobenchmarkScope.clickText(text: String) {
    scrollUntilText(text)
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find text: $text", node)
    node!!.click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.clickVisibleTextWithoutIdle(text: String) {
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find visible text: $text", node)
    node!!.click()
}

internal fun MacrobenchmarkScope.waitForNavigationMotion() {
    SystemClock.sleep(NAVIGATION_MOTION_WAIT_MILLIS)
}

internal fun MacrobenchmarkScope.openDemoModule(title: String) {
    clickText("Open $title")
}

internal fun MacrobenchmarkScope.returnToCatalog() {
    clickText("Back to catalog")
    waitForText("Capability Modules")
}

internal fun MacrobenchmarkScope.clickChapterTab(text: String) {
    scrollTabStripUntilText(text)
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find chapter tab: $text", node)
    node!!.click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollTabStripUntilText(
    text: String,
    maxSwipes: Int = 6,
) {
    repeat(maxSwipes + 1) { attempt ->
        if (device.hasObject(By.text(text))) {
            return
        }
        if (attempt < maxSwipes) {
            swipeTabStripLeft()
        }
    }
    waitForText(text)
}

internal fun MacrobenchmarkScope.swipePageUp() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.22f).toInt(),
        20,
    )
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.swipePageDown() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.22f).toInt(),
        width / 2,
        (height * 0.78f).toInt(),
        20,
    )
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollToPageTop(
    attempts: Int = 4,
) {
    repeat(attempts) {
        swipePageDown()
    }
}

internal fun MacrobenchmarkScope.swipeTabStripLeft() {
    val width = device.displayWidth
    val height = device.displayHeight
    val y = (height * 0.32f).toInt()
    device.swipe(
        (width * 0.82f).toInt(),
        y,
        (width * 0.18f).toInt(),
        y,
        16,
    )
    device.waitForIdle()
}

private const val NAVIGATION_MOTION_WAIT_MILLIS = 650L
