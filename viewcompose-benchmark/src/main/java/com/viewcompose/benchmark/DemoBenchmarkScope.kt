package com.viewcompose.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull

/**
 * 启动 demo 首页并等待目录锚点出现。
 * Starts the demo home page and waits for the catalog anchor.
 */
internal fun MacrobenchmarkScope.startDemoAndWait() {
    prepareBenchmarkUiAutomation()
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.text("已实现模块")), UI_WAIT_TIMEOUT_MS)
}

/**
 * 确保当前页面回到 demo 目录顶部。
 * Ensures the current page is the top of the demo catalog.
 */
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

/**
 * 启动指定 demo 模块并等待模块内的稳定文本锚点。
 * Starts a specific demo module and waits for a stable in-module text anchor.
 */
internal fun MacrobenchmarkScope.startDemoActivityAndWait(
    moduleKey: String,
    expectedText: String,
    extras: Map<String, Int> = emptyMap(),
) {
    prepareBenchmarkUiAutomation()
    pressHome()
    startActivityAndWait { intent ->
        // 清理上一次测试写入的业务 extra，同时保留 benchmark 框架需要的系统 extra。
        // Clear app-specific extras from previous test methods while preserving framework extras.
        intent.removeExtra("demo_module_key")
        intent.removeExtra("state_page_index")
        intent.removeExtra("performance_engine")
        intent.removeExtra("performance_scenario")
        // 带 extra 时清空任务栈以强制重建；无 extra 时移除标记，避免后续测试继承强清理行为。
        // With extras, clear the task to force recreation; without extras, remove the flag
        // so later tests do not inherit aggressive clearing.
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

/**
 * 启动性能对比 Activity 并选择指定引擎与场景。
 * Starts the performance comparison Activity with the requested engine and scenario.
 */
internal fun MacrobenchmarkScope.startPerformanceComparisonAndWait(
    engine: String,
    scenario: String,
    expectedText: String,
    shadowRenderPolicy: String? = null,
) {
    prepareBenchmarkUiAutomation()
    pressHome()
    startActivityAndWait { intent ->
        intent.removeExtra("demo_module_key")
        intent.removeExtra("state_page_index")
        intent.removeExtra("shadow_render_policy")
        intent.putExtra("performance_engine", engine)
        intent.putExtra("performance_scenario", scenario)
        shadowRenderPolicy?.let { policy ->
            intent.putExtra("shadow_render_policy", policy)
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    waitForText(expectedText)
}

/**
 * 从冷入口启动系统导航验收页并等待首页锚点。
 * Starts the system-navigation acceptance page from the cold entry and waits for its home anchor.
 */
internal fun MacrobenchmarkScope.startSystemNavigationAndWait() {
    prepareBenchmarkUiAutomation()
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

/**
 * 从前台直接启动系统导航 Activity，用于测量系统 Activity 转场。
 * Directly starts the system-navigation Activity from foreground to measure system Activity transitions.
 */
internal fun MacrobenchmarkScope.startSystemNavigationActivityFromForeground() {
    device.executeShellCommand(
        "am start -W -n $TARGET_PACKAGE/com.viewcompose.SystemNavigationActivity",
    )
}

/**
 * 等待指定文本出现。
 * Waits until the given text appears.
 */
internal fun MacrobenchmarkScope.waitForText(text: String) {
    device.wait(Until.hasObject(By.text(text)), UI_WAIT_TIMEOUT_MS)
}

/**
 * 在页面内上下滑动，直到找到指定文本。
 * Swipes within the page until the given text is found.
 */
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
    // 向下查找失败后回滚向上查找，覆盖锚点已在上方的情况。
    // If scrolling down fails, scroll back up to cover anchors above the current viewport.
    repeat(maxSwipes * 2) { attempt ->
        if (device.hasObject(By.text(text))) {
            return
        }
        swipePageDown()
    }
    waitForText(text)
}

/**
 * 查找并点击文本节点，点击后等待 UiAutomator idle。
 * Finds and clicks a text node, then waits for UiAutomator idle.
 */
internal fun MacrobenchmarkScope.clickText(text: String) {
    scrollUntilText(text)
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find text: $text", node)
    node!!.click()
    device.waitForIdle()
}

/**
 * 点击当前可见文本节点但不等待 idle，适用于固定时长动效测量。
 * Clicks a visible text node without waiting for idle, for fixed-duration motion measurements.
 */
internal fun MacrobenchmarkScope.clickVisibleTextWithoutIdle(text: String) {
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find visible text: $text", node)
    node!!.click()
}

/**
 * 等待导航动效完成的固定窗口。
 * Fixed wait window for navigation motion completion.
 */
internal fun MacrobenchmarkScope.waitForNavigationMotion() {
    SystemClock.sleep(NAVIGATION_MOTION_WAIT_MILLIS)
}

private fun prepareBenchmarkUiAutomation() {
    // 部分 OEM 构建会持续发送 accessibility/window 事件，UiAutomator 的隐式 idle 等待会拖满超时。
    // Some OEM builds keep accessibility/window events flowing, so UiAutomator idle waits hit timeout.
    // benchmark 已使用显式文本条件和固定动效窗口同步，因此可以关闭隐式 idle timeout。
    // Benchmarks already use explicit text conditions and fixed motion windows, so idle timeout is disabled.
    Configurator.getInstance().setWaitForIdleTimeout(0L)
}

/**
 * 在目录中打开指定标题的模块。
 * Opens a module with the given title from the catalog.
 */
internal fun MacrobenchmarkScope.openDemoModule(title: String) {
    clickText("Open $title")
}

/**
 * 从模块页面返回目录。
 * Returns from a module page to the catalog.
 */
internal fun MacrobenchmarkScope.returnToCatalog() {
    clickText("Back to catalog")
    waitForText("Capability Modules")
}

/**
 * 点击章节 tab，必要时先横向滚动 tab strip。
 * Clicks a chapter tab, scrolling the tab strip first if needed.
 */
internal fun MacrobenchmarkScope.clickChapterTab(text: String) {
    scrollTabStripUntilText(text)
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find chapter tab: $text", node)
    node!!.click()
    device.waitForIdle()
}

/**
 * 横向滚动 tab strip 直到指定文本可见。
 * Scrolls the tab strip horizontally until the given text is visible.
 */
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

/**
 * 执行一页向上滚动手势。
 * Performs one page-up swipe gesture.
 */
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

/**
 * 执行一页向下滚动手势。
 * Performs one page-down swipe gesture.
 */
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

/**
 * 多次向下滚动，把页面尽量回到顶部。
 * Repeatedly swipes down to move the page close to the top.
 */
internal fun MacrobenchmarkScope.scrollToPageTop(
    attempts: Int = 4,
) {
    repeat(attempts) {
        swipePageDown()
    }
}

/**
 * 向左滑动章节 tab strip。
 * Swipes the chapter tab strip left.
 */
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

/**
 * 导航动效 benchmark 使用的固定等待时长。
 * Fixed wait duration used by navigation motion benchmarks.
 */
private const val NAVIGATION_MOTION_WAIT_MILLIS = 650L
