package com.viewcompose.benchmark

import android.content.Intent
import android.graphics.Rect
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
            swipePageUpForTargetSearch()
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
            swipePageUpForTargetSearch()
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

/** Starts one revisioned performance workload with an explicit comparison engine. */
internal fun MacrobenchmarkScope.startPerformanceScenarioAndWait(
    scenarioId: String,
    engine: String,
    shadowRenderPolicy: String? = null,
    constraintLayoutNodeCount: Int? = null,
    constraintLayoutWorkload: String? = null,
) {
    check((constraintLayoutNodeCount == null) == (constraintLayoutWorkload == null)) {
        "ConstraintLayout benchmark setup requires both node count and workload."
    }
    startDemoScenarioAndWait(scenarioId) {
        putExtra("performance_engine", engine)
        removeExtra("shadow_render_policy")
        removeExtra("constraint_layout_node_count")
        removeExtra("constraint_layout_workload")
        shadowRenderPolicy?.let { policy ->
            putExtra("shadow_render_policy", policy)
        }
        constraintLayoutNodeCount?.let { nodeCount ->
            putExtra("constraint_layout_node_count", nodeCount)
            putExtra("constraint_layout_workload", checkNotNull(constraintLayoutWorkload))
        }
    }
    waitForScenarioTarget(scenarioId, DemoTargetRole.Target)
    waitForPerformanceMeasurementSettle()
}

/** Waits outside the measured block until OEM launch-frequency boosting has expired. */
internal fun waitForPerformanceMeasurementSettle() {
    // OEM launch boosting can otherwise leak into the first measured gesture or mutation and make
    // the first iteration materially faster than the rest. Setup is outside the measured block, so
    // wait for the launch-frequency floor to expire before every performance iteration.
    SystemClock.sleep(PERFORMANCE_LAUNCH_BOOST_SETTLE_MILLIS)
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

private fun MacrobenchmarkScope.swipePageUpForTargetSearch() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.22f).toInt(),
        80,
    )
    SystemClock.sleep(TARGET_SEARCH_SCROLL_SETTLE_MILLIS)
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

private const val PERFORMANCE_LAUNCH_BOOST_SETTLE_MILLIS = 5_000L

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

/** Resolves a scenario-owned surface once during setup so measurement does not traverse accessibility. */
internal fun MacrobenchmarkScope.scenarioTargetBounds(
    scenarioId: String,
    role: DemoTargetRole,
): Rect = Rect(waitForScenarioTarget(scenarioId, role).visibleBounds)

/** Swipes inside bounds captured outside the measured block. */
internal fun MacrobenchmarkScope.swipeWithinBounds(
    bounds: Rect,
    direction: PageSwipeDirection,
) {
    val horizontalCenter = bounds.centerX()
    val verticalInset = (bounds.height() * 0.12f).toInt().coerceAtLeast(1)
    val top = bounds.top + verticalInset
    val bottom = bounds.bottom - verticalInset
    assertTrue("Expected non-empty scroll bounds", bottom > top)
    when (direction) {
        PageSwipeDirection.TowardBottom -> device.swipe(
            horizontalCenter,
            bottom,
            horizontalCenter,
            top,
            20,
        )

        PageSwipeDirection.TowardTop -> device.swipe(
            horizontalCenter,
            top,
            horizontalCenter,
            bottom,
            20,
        )
    }
    // UiAutomator's idle timeout is disabled for OEM reliability, so wait explicitly until the
    // previous nested-list gesture has stopped producing frames before injecting the next one.
    SystemClock.sleep(NESTED_SCROLL_GESTURE_SETTLE_MILLIS)
}

internal enum class PageSwipeDirection {
    TowardBottom,
    TowardTop,
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
        // Start below fixed edge-to-edge headers so the scrolling surface owns the gesture.
        (height * 0.22f).toInt(),
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
 * 导航动效 benchmark 使用的固定等待时长。
 * Fixed wait duration used by navigation motion benchmarks.
 */
private const val NAVIGATION_MOTION_WAIT_MILLIS = 650L
private const val TARGET_SEARCH_SCROLL_SETTLE_MILLIS = 100L
private const val LONG_FLING_SETTLE_MILLIS = 1_200L
private const val NESTED_SCROLL_GESTURE_SETTLE_MILLIS = 500L
