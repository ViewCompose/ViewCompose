package com.viewcompose.benchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

internal enum class DemoTargetRole(
    val wireValue: String,
) {
    Root("root"),
    Ready("ready"),
    PrimaryAction("primary_action"),
    SecondaryAction("secondary_action"),
    Reset("reset"),
    State("state"),
    Target("target"),
    SecondaryTarget("secondary_target"),
}

/** Starts one strict scenario and waits for its locale-independent ready resource. */
internal fun MacrobenchmarkScope.startDemoScenarioAndWait(
    scenarioId: String,
    configure: Intent.() -> Unit = {},
) {
    prepareBenchmarkUiAutomation()
    pressHome()
    startActivityAndWait { intent ->
        intent.removeExtra("demo_module_key")
        intent.removeExtra("demo_scenario_id")
        intent.removeExtra("performance_engine")
        intent.removeExtra("performance_scenario")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra("demo_scenario_id", scenarioId)
        intent.configure()
    }
    waitForScenarioTarget(scenarioId, DemoTargetRole.Ready)
}

/** Waits for a role target whose Android resource name is derived from the scenario contract. */
internal fun MacrobenchmarkScope.waitForScenarioTarget(
    scenarioId: String,
    role: DemoTargetRole,
): UiObject2 {
    val resourceName = scenarioTargetResourceName(scenarioId, role)
    val target = device.wait(
        Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
        UI_WAIT_TIMEOUT_MS,
    )
    assertNotNull("Expected scenario target: $scenarioId/${role.wireValue}", target)
    return target!!
}

/** Scrolls the current fixture until a locale-independent role target is mounted and visible. */
internal fun MacrobenchmarkScope.scrollUntilScenarioTarget(
    scenarioId: String,
    role: DemoTargetRole,
    maxSwipes: Int = 10,
): UiObject2 {
    val resourceName = scenarioTargetResourceName(scenarioId, role)
    repeat(maxSwipes + 1) { attempt ->
        device.findObject(By.res(TARGET_PACKAGE, resourceName))?.let { target ->
            if (hasVisibleBoundsInSafeViewport(target)) return target
        }
        if (attempt < maxSwipes) {
            swipePageUpForTextSearch()
        }
    }
    val target = device.findObject(By.res(TARGET_PACKAGE, resourceName))
    assertNotNull("Expected visible scenario target: $scenarioId/${role.wireValue}", target)
    return target!!
}

/** Clicks one visible role target without using localized copy. */
internal fun MacrobenchmarkScope.clickScenarioTarget(
    scenarioId: String,
    role: DemoTargetRole,
    waitForIdle: Boolean = true,
) {
    val target = waitForScenarioTarget(scenarioId, role)
    tapTarget(target, "scenario target: $scenarioId/${role.wireValue}")
    if (waitForIdle) {
        device.waitForIdle()
    }
}

/** Returns the current machine-target text for change detection without using it as a selector. */
internal fun MacrobenchmarkScope.scenarioTargetText(
    scenarioId: String,
    role: DemoTargetRole,
): String = waitForScenarioTarget(scenarioId, role).text.orEmpty()

/** Waits until an existing state target publishes a different value. */
internal fun MacrobenchmarkScope.waitForScenarioTargetTextChange(
    scenarioId: String,
    role: DemoTargetRole,
    previous: String,
): String {
    val resourceName = scenarioTargetResourceName(scenarioId, role)
    val changed = device.wait(
        Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
        UI_WAIT_TIMEOUT_MS,
    )
    assertNotNull("Expected scenario state target: $scenarioId/${role.wireValue}", changed)
    val deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS
    var current = changed!!.text.orEmpty()
    while (current == previous && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(16L)
        current = device.findObject(By.res(TARGET_PACKAGE, resourceName))?.text.orEmpty()
    }
    assertTrue("Expected scenario target text to change: $scenarioId/${role.wireValue}", current != previous)
    return current
}

/** Waits until a resource-addressed target publishes the expected value. */
internal fun MacrobenchmarkScope.waitForScenarioTargetText(
    scenarioId: String,
    role: DemoTargetRole,
    expected: String,
) {
    val resourceName = scenarioTargetResourceName(scenarioId, role)
    val deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS
    var current = device.findObject(By.res(TARGET_PACKAGE, resourceName))?.text.orEmpty()
    while (current != expected && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(16L)
        current = device.findObject(By.res(TARGET_PACKAGE, resourceName))?.text.orEmpty()
    }
    assertEquals(
        "Unexpected scenario target text: $scenarioId/${role.wireValue}",
        expected,
        current,
    )
}

/** Waits for a locale-independent Android resource target that is shared across scenario variants. */
internal fun MacrobenchmarkScope.waitForResourceTarget(resourceName: String): UiObject2 {
    val target = device.wait(
        Until.findObject(By.res(TARGET_PACKAGE, resourceName)),
        UI_WAIT_TIMEOUT_MS,
    )
    assertNotNull("Expected resource target: $resourceName", target)
    return target!!
}

/** Scrolls until a shared resource target is mounted inside the safe viewport. */
internal fun MacrobenchmarkScope.scrollUntilResourceTarget(
    resourceName: String,
    maxSwipes: Int = 10,
): UiObject2 {
    repeat(maxSwipes + 1) { attempt ->
        device.findObject(By.res(TARGET_PACKAGE, resourceName))?.let { target ->
            if (hasVisibleBoundsInSafeViewport(target)) return target
        }
        if (attempt < maxSwipes) {
            swipePageUpForTextSearch()
        }
    }
    val target = device.findObject(By.res(TARGET_PACKAGE, resourceName))
    assertNotNull("Expected visible resource target: $resourceName", target)
    return target!!
}

/** Clicks a currently visible shared resource target. */
internal fun MacrobenchmarkScope.clickResourceTarget(
    resourceName: String,
    waitForIdle: Boolean = true,
) {
    val target = waitForResourceTarget(resourceName)
    tapTarget(target, "resource target: $resourceName")
    if (waitForIdle) {
        device.waitForIdle()
    }
}

/** Returns the current text published by a shared resource target. */
internal fun MacrobenchmarkScope.resourceTargetText(
    resourceName: String,
): String = waitForResourceTarget(resourceName).text.orEmpty()

/** Waits until a shared resource target publishes a different value. */
internal fun MacrobenchmarkScope.waitForResourceTargetTextChange(
    resourceName: String,
    previous: String,
): String {
    val deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS
    var current = device.findObject(By.res(TARGET_PACKAGE, resourceName))?.text.orEmpty()
    while (current == previous && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(16L)
        current = device.findObject(By.res(TARGET_PACKAGE, resourceName))?.text.orEmpty()
    }
    assertTrue("Expected resource target text to change: $resourceName", current != previous)
    return current
}

/** Waits until a shared resource target leaves the active window. */
internal fun MacrobenchmarkScope.waitForResourceTargetGone(resourceName: String) {
    val gone = device.wait(
        Until.gone(By.res(TARGET_PACKAGE, resourceName)),
        UI_WAIT_TIMEOUT_MS,
    )
    assertTrue("Expected resource target to disappear: $resourceName", gone)
}

private fun scenarioTargetResourceName(
    scenarioId: String,
    role: DemoTargetRole,
): String {
    val normalizedId = scenarioId.replace('.', '_').replace('-', '_')
    return "demo_${normalizedId}_${role.wireValue}"
}

/**
 * 启动 demo 首页并等待目录锚点出现。
 * Starts the demo home page and waits for the catalog anchor.
 */
internal fun MacrobenchmarkScope.startDemoAndWait() {
    prepareBenchmarkUiAutomation()
    pressHome()
    startActivityAndWait()
    waitForScenarioTarget("catalog", DemoTargetRole.Ready)
}

/**
 * 确保当前页面回到 demo 目录顶部。
 * Ensures the current page is the top of the demo catalog.
 */
internal fun MacrobenchmarkScope.startCatalogAndWait() {
    startDemoAndWait()
    scrollToPageTop()
    waitForScenarioTarget("catalog", DemoTargetRole.Ready)
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
        intent.removeExtra("demo_scenario_id")
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
        intent.setClassName(TARGET_PACKAGE, legacyBenchmarkActivityClass(moduleKey))
        extras.forEach { (key, value) -> intent.putExtra(key, value) }
    }
    waitForText(expectedText)
}

/** Temporary benchmark-only bridge removed when Phase 4 assigns these fixtures strict IDs. */
private fun legacyBenchmarkActivityClass(moduleKey: String): String = when (moduleKey) {
    "environment" -> "com.viewcompose.DemoEnvironmentActivity"
    "foundations" -> "com.viewcompose.FoundationsActivity"
    "diagnostics" -> "com.viewcompose.DiagnosticsActivity"
    else -> error("Unknown legacy benchmark module: $moduleKey")
}

/** Starts the Diagnostics Theme page directly so long-fling measurements include its full fixture. */
internal fun MacrobenchmarkScope.startDiagnosticsThemeAndWait() {
    startDemoScenarioAndWait("diagnostics.theme")
    waitForScenarioTarget("diagnostics.theme", DemoTargetRole.Target)
}

/** Starts one revisioned multi-design-system scenario and returns its public scenario id. */
internal fun MacrobenchmarkScope.startDesignSystemAndWait(kind: String): String {
    val scenarioId = designSystemScenarioId(kind)
    startDemoScenarioAndWait(scenarioId) {
        putExtra("demo_design_system_kind", kind)
        putExtra("demo_design_system_dark", false)
        putExtra("demo_design_system_rtl", false)
        putExtra("demo_design_system_font_scale", 1f)
        putExtra("demo_design_system_reduced_motion", false)
    }
    return scenarioId
}

internal fun designSystemScenarioId(kind: String): String = when (kind) {
    "rounded-reference" -> "design.bundle-material3"
    "cut-contrast", "cupertino-pressure" -> "design.bundle-contrast"
    else -> error("Unknown design-system benchmark variant: $kind")
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
    startDemoScenarioAndWait("navigation.system")
}

/**
 * 从前台直接启动系统导航 Activity，用于测量系统 Activity 转场。
 * Directly starts the system-navigation Activity from foreground to measure system Activity transitions.
 */
internal fun MacrobenchmarkScope.startSystemNavigationActivityFromForeground() {
    device.executeShellCommand(
        "am start -W -n $TARGET_PACKAGE/com.viewcompose.SystemNavigationActivity " +
            "--es demo_scenario_id navigation.system",
    )
}

/**
 * 等待指定文本出现。
 * Waits until the given text appears.
 */
internal fun MacrobenchmarkScope.waitForText(text: String) {
    val found = device.wait(Until.hasObject(By.text(text)), UI_WAIT_TIMEOUT_MS)
    assertTrue("Expected to find text: $text", found)
}

/** Asserts that a text node is inside the visible safe viewport without scrolling to it. */
internal fun MacrobenchmarkScope.assertVisibleText(text: String) {
    assertNotNull("Expected visible text: $text", findVisibleTextNode(text))
}

/**
 * 等待指定文本从当前窗口消失。
 * Waits until the given text disappears from the current window.
 */
internal fun MacrobenchmarkScope.waitForTextGone(text: String) {
    val gone = device.wait(Until.gone(By.text(text)), UI_WAIT_TIMEOUT_MS)
    assertTrue("Expected text to disappear: $text", gone)
}

/**
 * 在页面内上下滑动，直到找到指定文本。
 * Swipes within the page until the given text is found.
 */
internal fun MacrobenchmarkScope.scrollUntilText(
    text: String,
    maxSwipes: Int = 10,
): UiObject2 {
    repeat(maxSwipes + 1) { attempt ->
        findVisibleTextNode(text)?.let { node -> return node }
        if (attempt < maxSwipes) {
            swipePageUpForTextSearch()
        }
    }
    // 向下查找失败后回滚向上查找，覆盖锚点已在上方的情况。
    // If scrolling down fails, scroll back up to cover anchors above the current viewport.
    repeat(maxSwipes * 2) {
        findVisibleTextNode(text)?.let { node -> return node }
        swipePageDownForTextSearch()
    }
    val node = findVisibleTextNode(text)
    assertNotNull("Expected to scroll text into the visible viewport: $text", node)
    return node!!
}

/**
 * 查找并点击文本节点，点击后等待 UiAutomator idle。
 * Finds and clicks a text node, then waits for UiAutomator idle.
 */
internal fun MacrobenchmarkScope.clickText(text: String) {
    val node = scrollUntilText(text)
    tapTextTarget(node)
    device.waitForIdle()
}

/**
 * Waits for a text node that must already be in the viewport, clicks it, and then waits for idle.
 *
 * This keeps overlay interactions stable while the accessibility tree is being replaced during
 * window entry or Activity recreation.
 */
internal fun MacrobenchmarkScope.clickVisibleText(text: String) {
    device.wait(Until.hasObject(By.text(text)), UI_WAIT_TIMEOUT_MS)
    val node = findVisibleTextNode(text)
    assertNotNull("Expected to find visible text: $text", node)
    tapTextTarget(node!!)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.findVisibleTextNode(text: String): UiObject2? {
    val node = device.findObject(By.text(text)) ?: return null
    val bounds = node.visibleBounds
    val centerY = bounds.centerY()
    val safeTop = (device.displayHeight * 0.08f).toInt()
    val safeBottom = (device.displayHeight * 0.90f).toInt()
    return node.takeIf {
        bounds.width() > 0 &&
            bounds.height() > 0 &&
            centerY in safeTop..safeBottom
    }
}

private fun MacrobenchmarkScope.swipePageUpForTextSearch() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.22f).toInt(),
        80,
    )
    SystemClock.sleep(TEXT_SEARCH_SCROLL_SETTLE_MILLIS)
}

private fun MacrobenchmarkScope.swipePageDownForTextSearch() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.22f).toInt(),
        width / 2,
        (height * 0.78f).toInt(),
        80,
    )
    SystemClock.sleep(TEXT_SEARCH_SCROLL_SETTLE_MILLIS)
}

/**
 * 点击当前可见文本节点但不等待 idle，适用于固定时长动效测量。
 * Clicks a visible text node without waiting for idle, for fixed-duration motion measurements.
 */
internal fun MacrobenchmarkScope.clickVisibleTextWithoutIdle(text: String) {
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find visible text: $text", node)
    tapTextTarget(node!!)
}

/** Clicks the visible checkable control without waiting for UiAutomator idle. */
internal fun MacrobenchmarkScope.clickVisibleCheckableControlWithoutIdle() {
    val node = device.findObjects(By.checkable(true))
        .firstOrNull { candidate ->
            candidate.isClickable &&
                hasVisibleBoundsInSafeViewport(candidate)
        }
    assertNotNull("Expected to find a visible checkable control", node)
    tapTarget(node!!, "checkable control")
}

private fun MacrobenchmarkScope.tapTextTarget(node: UiObject2) {
    val target = clickableTargetFor(node)
    tapTarget(target, "text: ${node.text}")
}

private fun MacrobenchmarkScope.tapTarget(
    target: UiObject2,
    description: String,
) {
    val bounds = target.visibleBounds
    assertTrue(
        "Expected a visible click target for $description",
        bounds.width() > 0 && bounds.height() > 0,
    )
    // A coordinate tap avoids OEM accessibility-action differences once the real surface is known.
    assertTrue(
        "Expected UiAutomator to inject the click for $description",
        device.click(bounds.centerX(), bounds.centerY()),
    )
}

private fun MacrobenchmarkScope.hasVisibleBoundsInSafeViewport(candidate: UiObject2): Boolean {
    val bounds = candidate.visibleBounds
    val centerY = bounds.centerY()
    val safeTop = (device.displayHeight * 0.08f).toInt()
    val safeBottom = (device.displayHeight * 0.90f).toInt()
    return bounds.width() > 0 && bounds.height() > 0 && centerY in safeTop..safeBottom
}

private fun MacrobenchmarkScope.clickableTargetFor(node: UiObject2): UiObject2 {
    node.clickableAncestorOrNull()?.let { target -> return target }

    // Some Samsung builds expose a widget's text and clickable surface as overlapping siblings.
    // Prefer the smallest clickable surface containing the text center so UiAutomator dispatches
    // the action to the real control instead of the non-clickable label.
    val textBounds = node.visibleBounds
    val centerX = textBounds.centerX()
    val centerY = textBounds.centerY()
    return device.findObjects(By.clickable(true))
        .asSequence()
        .map { candidate -> candidate to candidate.visibleBounds }
        .filter { (_, bounds) ->
            bounds.width() > 0 &&
                bounds.height() > 0 &&
                bounds.contains(centerX, centerY)
        }
        .minByOrNull { (_, bounds) -> bounds.width().toLong() * bounds.height() }
        ?.first
        ?: node
}

private fun UiObject2.clickableAncestorOrNull(): UiObject2? {
    var candidate: UiObject2? = this
    while (candidate != null) {
        if (candidate.isClickable) return candidate
        candidate = candidate.parent
    }
    return null
}

/**
 * 等待导航动效完成的固定窗口。
 * Fixed wait window for navigation motion completion.
 */
internal fun MacrobenchmarkScope.waitForNavigationMotion() {
    SystemClock.sleep(NAVIGATION_MOTION_WAIT_MILLIS)
}

private fun MacrobenchmarkScope.prepareBenchmarkUiAutomation() {
    // 构建或设备冷却期间屏幕可能自动休眠；锁屏会让 Perfetto 观测不到 RenderThread，
    // 并使 UiAutomator 查找失败。每个 benchmark 入口都显式恢复可交互状态。
    // The screen may sleep during builds or cooldown. A locked screen removes RenderThread slices
    // from Perfetto and makes UiAutomator lookups fail, so every benchmark entry restores it.
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
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
internal fun MacrobenchmarkScope.clickChapterTab(
    text: String,
    waitForIdle: Boolean = true,
) {
    scrollTabStripUntilText(text)
    val node = device.findObject(By.text(text))
    assertNotNull("Expected to find chapter tab: $text", node)
    node!!.click()
    if (waitForIdle) {
        device.waitForIdle()
    }
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
 * Performs a forceful short-duration swipe and lets Android continue the resulting fling.
 *
 * This intentionally models a user throwing a long document rather than dragging one viewport.
 */
internal fun MacrobenchmarkScope.flingPageUp() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.86f).toInt(),
        width / 2,
        (height * 0.12f).toInt(),
        4,
    )
    SystemClock.sleep(LONG_FLING_SETTLE_MILLIS)
}

/** Performs the reverse forceful fling used to traverse a long document back toward its top. */
internal fun MacrobenchmarkScope.flingPageDown() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.12f).toInt(),
        width / 2,
        (height * 0.86f).toInt(),
        4,
    )
    SystemClock.sleep(LONG_FLING_SETTLE_MILLIS)
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
private const val TEXT_SEARCH_SCROLL_SETTLE_MILLIS = 100L
private const val LONG_FLING_SETTLE_MILLIS = 1_200L
