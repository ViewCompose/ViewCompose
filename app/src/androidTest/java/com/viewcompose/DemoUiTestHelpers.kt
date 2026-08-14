package com.viewcompose

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ColorStateListDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.android.material.shape.MaterialShapeDrawable
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.renderer.R as RendererR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * RecyclerView 当前视口的首个可见 item 锚点。
 * Anchor for the first visible RecyclerView item in the current viewport.
 */
internal data class RecyclerViewportAnchor(
    val position: Int,
    val offset: Int,
)

/**
 * 启动 demo Activity 并设置测试所需主题模式。
 * Launches a demo Activity with the theme mode required by the test.
 */
internal fun <A : Activity> launchDemoActivity(
    activityClass: Class<A>,
    themeMode: DemoThemeMode = DemoThemeMode.Light,
): ActivityScenario<A> {
    DemoThemeSession.mode = themeMode
    return ActivityScenario.launch(activityClass).also { scenario ->
        scenario.moveToState(Lifecycle.State.RESUMED)
    }
}

/** Launches a strict scenario Activity without relying on a legacy page-index extra. */
internal fun <A : Activity> launchDemoScenarioActivity(
    activityClass: Class<A>,
    scenarioId: String,
    themeMode: DemoThemeMode = DemoThemeMode.Light,
): ActivityScenario<A> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return launchDemoActivity(
        intent = Intent(context, activityClass).putExtra(EXTRA_DEMO_SCENARIO_ID, scenarioId),
        themeMode = themeMode,
    )
}

/** Returns a required native view addressed by the scenario-owned Android resource ID bridge. */
internal fun <V : View> Activity.requireScenarioViewById(@IdRes id: Int): V {
    val view = findViewById<V>(id)
    assertNotNull("Expected to find view with resource ID: $id", view)
    return requireNotNull(view)
}

/** Returns a visible scenario resource target, scrolling attached RecyclerViews when necessary. */
internal fun <V : View> Activity.requireScenarioViewByIdVisible(
    @IdRes id: Int,
    maxScrollAttempts: Int = 24,
): V {
    val root = findViewById<ViewGroup>(android.R.id.content)
    fun visibleTarget(): V? = findViewById<V>(id)?.takeIf(::isViewVisible)

    visibleTarget()?.let { return it }
    findRecyclerViews(root)
        .filter { recyclerView -> recyclerView.isShown && recyclerView.height > 0 }
        .forEach { recyclerView ->
            val delta = (recyclerView.height * 0.7f).toInt().coerceAtLeast(1)
            fun scrollUntilVisible(direction: Int): V? {
                repeat(maxScrollAttempts) {
                    visibleTarget()?.let { return it }
                    if (!recyclerView.canScrollVertically(direction)) return null
                    recyclerView.scrollBy(0, direction * delta)
                }
                return visibleTarget()
            }

            scrollUntilVisible(direction = 1)?.let { return it }
            scrollUntilVisible(direction = -1)?.let { return it }
        }

    val target = visibleTarget()
    assertNotNull("Expected visible scenario resource target: $id", target)
    assertViewFullyVisible(requireNotNull(target))
    return requireNotNull(target)
}

/** Clicks a required scenario-owned native resource target on the Activity thread. */
internal fun Activity.clickScenarioViewById(@IdRes id: Int) {
    val target = requireScenarioViewById<View>(id)
    assertTrue("Expected resource target to accept click: $id", target.performClick())
}

/** Scrolls a RecyclerView as needed before clicking a scenario-owned resource target. */
internal fun Activity.clickScenarioViewByIdVisible(
    @IdRes id: Int,
    maxScrollAttempts: Int = 24,
) {
    val root = findViewById<ViewGroup>(android.R.id.content)
    fun visibleTarget(): View? = findViewById<View>(id)?.takeIf(::isViewVisible)

    visibleTarget()?.let { target ->
        assertTrue("Expected resource target to accept click: $id", target.performClick())
        return
    }
    findRecyclerViews(root)
        .filter { recyclerView -> recyclerView.isShown && recyclerView.height > 0 }
        .forEach { recyclerView ->
            val delta = (recyclerView.height * 0.7f).toInt().coerceAtLeast(1)
            fun scrollUntilVisible(direction: Int): View? {
                repeat(maxScrollAttempts) {
                    visibleTarget()?.let { return it }
                    if (!recyclerView.canScrollVertically(direction)) return null
                    recyclerView.scrollBy(0, direction * delta)
                }
                return visibleTarget()
            }

            scrollUntilVisible(direction = 1)?.let { target ->
                assertTrue("Expected resource target to accept click: $id", target.performClick())
                return
            }
            scrollUntilVisible(direction = -1)?.let { target ->
                assertTrue("Expected resource target to accept click: $id", target.performClick())
                return
            }
        }

    val target = visibleTarget()
    assertNotNull("Expected visible resource target: $id", target)
    assertTrue("Expected resource target to accept click: $id", target!!.performClick())
}

/**
 * 使用自定义 Intent 启动 demo Activity，并设置测试主题模式。
 * Launches a demo Activity from a custom Intent with the test theme mode.
 */
internal fun <A : Activity> launchDemoActivity(
    intent: Intent,
    themeMode: DemoThemeMode = DemoThemeMode.Light,
): ActivityScenario<A> {
    DemoThemeSession.mode = themeMode
    return ActivityScenario.launch<A>(intent).also { scenario ->
        scenario.moveToState(Lifecycle.State.RESUMED)
    }
}

/**
 * 等待 instrumentation idle，并额外跨过一帧以覆盖 ViewCompose 异步提交。
 * Waits for instrumentation idle and one extra frame to cover asynchronous ViewCompose commits.
 */
internal fun waitForUiIdle() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()

    val frameLatch = CountDownLatch(1)
    instrumentation.runOnMainSync {
        Choreographer.getInstance().postFrameCallback {
            frameLatch.countDown()
        }
    }
    frameLatch.await(2, TimeUnit.SECONDS)
    instrumentation.waitForIdleSync()
}

/** Saves the current device screenshot for visual acceptance or failure investigation. */
internal fun captureDeviceScreenshot(
    name: String,
    directoryName: String = "ui-test-screenshots",
): File {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.getExternalFilesDir(null), directoryName)
    if (!directory.exists()) {
        directory.mkdirs()
    }
    val output = File(directory, "$name.png")
    val captured = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        .takeScreenshot(output)
    assertTrue("Expected device screenshot capture to succeed: ${output.absolutePath}", captured)
    return output
}

/**
 * 通过 UiAutomator 点击指定文本。
 * Clicks the given text through UiAutomator.
 */
internal fun clickDeviceText(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val node = device.wait(Until.hasObject(By.text(text)), 5_000)
    assertTrue("Expected device text target: $text", node)
    val target = device.findObject(By.text(text))
    assertNotNull("Expected device object for text: $text", target)
    target!!.click()
    waitForUiIdle()
}

/**
 * 断言指定文本在设备可见区域内。
 * Asserts that the given text is visible on the device.
 */
internal fun assertDeviceTextVisible(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val node = device.wait(Until.hasObject(By.text(text)), 5_000)
    assertTrue("Expected device text target: $text", node)
    val target = device.findObject(By.text(text))
    assertNotNull("Expected visible device object for text: $text", target)
    val bounds = target!!.visibleBounds
    assertTrue("Expected visible width > 0 for device text: $text", bounds.width() > 0)
    assertTrue("Expected visible height > 0 for device text: $text", bounds.height() > 0)
}

/** Returns a visible device node addressed by an app-owned Android resource ID. */
internal fun requireDeviceResourceId(@IdRes id: Int): androidx.test.uiautomator.UiObject2 {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val resourceName = context.resources.getResourceEntryName(id)
    val target = UiDevice.getInstance(instrumentation).wait(
        Until.findObject(By.res(context.packageName, resourceName)),
        5_000,
    )
    assertNotNull("Expected device resource target: $resourceName", target)
    return requireNotNull(target)
}

/** Clicks an app-owned device resource target, including targets hosted in overlay windows. */
internal fun clickDeviceResourceId(@IdRes id: Int) {
    requireDeviceResourceId(id).click()
    waitForUiIdle()
}

/** Asserts that an app-owned device resource target has non-empty visible bounds. */
internal fun assertDeviceResourceIdVisible(@IdRes id: Int) {
    val target = requireDeviceResourceId(id)
    val bounds = target.visibleBounds
    assertTrue("Expected visible width > 0 for resource ID: $id", bounds.width() > 0)
    assertTrue("Expected visible height > 0 for resource ID: $id", bounds.height() > 0)
}

/**
 * 使用设备级可滚动容器把文本滚入视口。
 * Scrolls a text target into view through a device-level scrollable container.
 */
internal fun scrollDeviceTextIntoView(text: String) {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val scrollable = UiScrollable(UiSelector().scrollable(true))
    scrollable.setAsVerticalList()
    val found = scrollable.scrollTextIntoView(text) || scrollable.scrollIntoView(UiSelector().text(text))
    assertTrue("Expected to scroll text target into view: $text", found)
    waitForUiIdle()
}

/**
 * 使用设备级可滚动容器把 contentDescription 滚入视口。
 * Scrolls a contentDescription target into view through a device-level scrollable container.
 */
internal fun scrollDeviceDescriptionIntoView(description: String) {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val scrollable = UiScrollable(UiSelector().scrollable(true))
    scrollable.setAsVerticalList()
    val found = scrollable.scrollDescriptionIntoView(description) ||
        scrollable.scrollIntoView(UiSelector().description(description))
    assertTrue("Expected to scroll description target into view: $description", found)
    waitForUiIdle()
}

/**
 * 在 Activity View 树中按精确文本查找 TextView，找不到时直接失败。
 * Finds a TextView by exact text in the Activity View tree and fails fast when missing.
 */
internal fun Activity.requireTextView(text: String): TextView {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val view = findTextViewByText(root, text)
    assertNotNull("Expected to find TextView with text: $text", view)
    return view!!
}

/**
 * Finds an exact TextView, scrolling visible RecyclerViews until its attached item is visible.
 */
internal fun Activity.requireTextViewVisible(
    text: String,
    maxScrollAttempts: Int = 24,
): TextView {
    val root = findViewById<ViewGroup>(android.R.id.content)
    fun visibleTextView(): TextView? {
        return findTextViewsByText(root, text).firstOrNull(::isViewVisible)
    }

    visibleTextView()?.let { return it }
    findRecyclerViews(root)
        .filter { recyclerView -> recyclerView.isShown && recyclerView.height > 0 }
        .forEach { recyclerView ->
            val delta = (recyclerView.height * 0.7f).toInt().coerceAtLeast(1)
            fun scrollUntilVisible(direction: Int): TextView? {
                repeat(maxScrollAttempts) {
                    visibleTextView()?.let { return it }
                    if (!recyclerView.canScrollVertically(direction)) {
                        return null
                    }
                    recyclerView.scrollBy(0, direction * delta)
                }
                return visibleTextView()
            }

            scrollUntilVisible(direction = 1)?.let { return it }
            scrollUntilVisible(direction = -1)?.let { return it }
        }

    val view = findTextViewsByText(root, text).firstOrNull(::isViewVisible)
    assertNotNull("Expected to find visible TextView with text: $text", view)
    assertViewFullyVisible(view!!)
    return view
}

/**
 * 通过 ViewCompose testTag 查找可见 View。
 * Finds a visible View by ViewCompose testTag.
 */
internal fun Activity.requireViewByTestTag(tag: String): View {
    return requireViewByTestTagVisible(tag)
}

/**
 * 通过 ViewCompose testTag 查找可见 TextView。
 * Finds a visible TextView by ViewCompose testTag.
 */
internal fun Activity.requireTextViewByTestTag(tag: String): TextView {
    return requireTextViewByTestTagVisible(tag)
}

/**
 * 查找可见 testTag，必要时在 RecyclerView 中上下滚动搜索。
 * Finds a visible testTag, scrolling RecyclerViews up and down when needed.
 */
internal fun Activity.requireViewByTestTagVisible(
    tag: String,
    maxScrollAttempts: Int = 24,
): View {
    val root = findViewById<ViewGroup>(android.R.id.content)
    fun visibleTaggedView(): View? {
        return findViewsByTestTag(root, tag).firstOrNull(::isViewVisible)
    }

    visibleTaggedView()?.let { return it }
    findRecyclerViews(root)
        .filter { recyclerView -> recyclerView.isShown && recyclerView.height > 0 }
        .forEach { recyclerView ->
            val delta = (recyclerView.height * 0.7f).toInt().coerceAtLeast(1)
            fun scrollUntilVisible(direction: Int): View? {
                repeat(maxScrollAttempts) {
                    visibleTaggedView()?.let { return it }
                    if (!recyclerView.canScrollVertically(direction)) {
                        return null
                    }
                    recyclerView.scrollBy(0, direction * delta)
                }
                return visibleTaggedView()
            }

            scrollUntilVisible(direction = 1)?.let { return it }
            scrollUntilVisible(direction = -1)?.let { return it }
        }

    val view = findViewsByTestTag(root, tag).firstOrNull(::isViewVisible)
    assertNotNull("Expected to find view with testTag: $tag", view)
    assertViewFullyVisible(view!!)
    return view
}

/**
 * 查找可见 testTag 并断言它对应 TextView。
 * Finds a visible testTag and asserts that it maps to a TextView.
 */
internal fun Activity.requireTextViewByTestTagVisible(
    tag: String,
    maxScrollAttempts: Int = 24,
): TextView {
    val view = requireViewByTestTagVisible(tag, maxScrollAttempts)
    assertTrue("Expected testTag=$tag to map to TextView, but was ${view.javaClass.simpleName}", view is TextView)
    return view as TextView
}

/**
 * 点击 testTag 对应 View 或其最近的可点击父节点。
 * Clicks the View for a testTag or the nearest clickable parent.
 */
internal fun Activity.clickByTestTag(tag: String) {
    var current: View? = requireViewByTestTagVisible(tag)
    while (current != null && !current.isClickable) {
        current = current.parent as? View
    }
    assertNotNull("Expected clickable host for testTag: $tag", current)
    assertTrue("Expected click to be handled for testTag: $tag", current!!.performClick())
}

/**
 * 向 testTag 中心注入真实 down/up 触摸事件。
 * Injects real down/up touch events at the center of the tagged View.
 */
internal fun Activity.tapByTestTag(tag: String) {
    tapView(requireViewByTestTagVisible(tag))
}

/** Injects a real tap at the center of a strict scenario resource target. */
internal fun Activity.tapScenarioViewById(@IdRes id: Int) {
    tapView(requireScenarioViewByIdVisible<View>(id))
}

/**
 * Injects real down/up touch events at the center of the supplied View.
 */
internal fun Activity.tapView(view: View) {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    val x = location[0] + view.width * 0.5f
    val y = location[1] + view.height * 0.5f
    val downTime = SystemClock.uptimeMillis()
    dispatchGestureEvent(
        downTime = downTime,
        eventTime = downTime,
        action = MotionEvent.ACTION_DOWN,
        x = x,
        y = y,
    )
    dispatchGestureEvent(
        downTime = downTime,
        eventTime = downTime + 16L,
        action = MotionEvent.ACTION_UP,
        x = x,
        y = y,
    )
}

/**
 * Injects a real tap into the clickable parent hosting the exact text.
 */
internal fun Activity.tapTextView(text: String) {
    var current: View? = requireTextView(text)
    while (current != null && !current.isClickable) {
        current = current.parent as? View
    }
    assertNotNull("Expected clickable host for text: $text", current)
    tapView(requireNotNull(current))
}

/**
 * 向 testTag 中心注入一段真实拖拽手势。
 * Injects a real drag gesture starting at the center of the tagged View.
 */
internal fun Activity.dragByTestTag(
    tag: String,
    deltaX: Float,
    deltaY: Float = 0f,
    steps: Int = 8,
) {
    dragView(requireViewByTestTagVisible(tag), deltaX, deltaY, steps)
}

/** Injects a real drag gesture into a strict scenario resource target. */
internal fun Activity.dragScenarioViewById(
    @IdRes id: Int,
    deltaX: Float,
    deltaY: Float = 0f,
    steps: Int = 8,
) {
    dragView(requireScenarioViewByIdVisible(id), deltaX, deltaY, steps)
}

private fun Activity.dragView(
    view: View,
    deltaX: Float,
    deltaY: Float,
    steps: Int,
) {
    assertTrue("Expected drag steps >= 2", steps >= 2)
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    val startX = location[0] + view.width * 0.5f
    val startY = location[1] + view.height * 0.5f
    val endX = startX + deltaX
    val endY = startY + deltaY
    val downTime = SystemClock.uptimeMillis()
    dispatchGestureEvent(
        downTime = downTime,
        eventTime = downTime,
        action = MotionEvent.ACTION_DOWN,
        x = startX,
        y = startY,
    )
    for (index in 1 until steps) {
        val fraction = index.toFloat() / steps.toFloat()
        val eventTime = downTime + index * 16L
        dispatchGestureEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_MOVE,
            x = startX + (endX - startX) * fraction,
            y = startY + (endY - startY) * fraction,
        )
    }
    dispatchGestureEvent(
        downTime = downTime,
        eventTime = downTime + steps * 16L,
        action = MotionEvent.ACTION_UP,
        x = endX,
        y = endY,
    )
}

/**
 * 向 testTag 注入双指平移、旋转和缩放组合手势。
 * Injects a two-pointer transform gesture with pan, rotation, and zoom.
 */
internal fun Activity.transformByTestTag(
    tag: String,
    panX: Float = 120f,
    panY: Float = 72f,
    rotationDegrees: Float = 28f,
    zoomRatio: Float = 1.2f,
    steps: Int = 10,
) {
    transformView(
        view = requireViewByTestTagVisible(tag),
        panX = panX,
        panY = panY,
        rotationDegrees = rotationDegrees,
        zoomRatio = zoomRatio,
        steps = steps,
    )
}

/** Injects a two-pointer transform gesture into a strict scenario resource target. */
internal fun Activity.transformScenarioViewById(
    @IdRes id: Int,
    panX: Float = 120f,
    panY: Float = 72f,
    rotationDegrees: Float = 28f,
    zoomRatio: Float = 1.2f,
    steps: Int = 10,
) {
    transformView(
        view = requireScenarioViewByIdVisible(id),
        panX = panX,
        panY = panY,
        rotationDegrees = rotationDegrees,
        zoomRatio = zoomRatio,
        steps = steps,
    )
}

private fun Activity.transformView(
    view: View,
    panX: Float,
    panY: Float,
    rotationDegrees: Float,
    zoomRatio: Float,
    steps: Int,
) {
    assertTrue("Expected transform steps >= 2", steps >= 2)
    val centerX = view.width * 0.5f
    val centerY = view.height * 0.5f
    val startRadius = min(view.width, view.height) * 0.18f
    val endRadius = (startRadius * zoomRatio).coerceAtLeast(startRadius + 8f)
    val startAngleRad = Math.toRadians(20.0).toFloat()
    val endAngleRad = Math.toRadians((20f + rotationDegrees).toDouble()).toFloat()
    val downTime = SystemClock.uptimeMillis()

    val start = twoPointerCoords(
        centerX = centerX,
        centerY = centerY,
        radius = startRadius,
        angleRad = startAngleRad,
    )
    dispatchMultiTouchEvent(
        target = view,
        downTime = downTime,
        eventTime = downTime,
        actionMasked = MotionEvent.ACTION_DOWN,
        points = listOf(start.first),
    )
    dispatchMultiTouchEvent(
        target = view,
        downTime = downTime,
        eventTime = downTime + 8L,
        actionMasked = MotionEvent.ACTION_POINTER_DOWN,
        actionIndex = 1,
        points = listOf(start.first, start.second),
    )

    for (step in 1..steps) {
        val fraction = step.toFloat() / steps.toFloat()
        val currentCenterX = centerX + panX * fraction
        val currentCenterY = centerY + panY * fraction
        val currentRadius = startRadius + (endRadius - startRadius) * fraction
        val currentAngle = startAngleRad + (endAngleRad - startAngleRad) * fraction
        val pointers = twoPointerCoords(
            centerX = currentCenterX,
            centerY = currentCenterY,
            radius = currentRadius,
            angleRad = currentAngle,
        )
        dispatchMultiTouchEvent(
            target = view,
            downTime = downTime,
            eventTime = downTime + 8L + step * 16L,
            actionMasked = MotionEvent.ACTION_MOVE,
            points = listOf(pointers.first, pointers.second),
        )
    }

    val end = twoPointerCoords(
        centerX = centerX + panX,
        centerY = centerY + panY,
        radius = endRadius,
        angleRad = endAngleRad,
    )
    dispatchMultiTouchEvent(
        target = view,
        downTime = downTime,
        eventTime = downTime + 8L + (steps + 1) * 16L,
        actionMasked = MotionEvent.ACTION_POINTER_UP,
        actionIndex = 1,
        points = listOf(end.first, end.second),
    )
    dispatchMultiTouchEvent(
        target = view,
        downTime = downTime,
        eventTime = downTime + 8L + (steps + 2) * 16L,
        actionMasked = MotionEvent.ACTION_UP,
        points = listOf(end.first),
    )
}

/**
 * 聚焦 testTag 内的第一个 EditText。
 * Focuses the first EditText under the tagged host.
 */
internal fun Activity.focusInputByTestTag(tag: String) {
    val host = requireViewByTestTagVisible(tag)
    val input = findFirstEditText(host)
    assertNotNull("Expected EditText descendant for testTag: $tag", input)
    input!!.requestFocus()
}

/** Focuses the first EditText under a strict scenario-owned native resource target. */
internal fun Activity.focusInputByScenarioViewId(@IdRes id: Int) {
    val host = requireScenarioViewById<View>(id)
    val input = findFirstEditText(host)
    assertNotNull("Expected EditText descendant for scenario resource ID: $id", input)
    input!!.requestFocus()
}

/**
 * 读取第一个 RecyclerView 当前首个可见 item 的位置和偏移。
 * Reads the position and offset of the first visible item in the first RecyclerView.
 */
internal fun Activity.readFirstRecyclerAnchor(): RecyclerViewportAnchor? {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val recyclerView = findFirstRecyclerView(root) ?: return null
    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
    val position = layoutManager.findFirstVisibleItemPosition()
    if (position == RecyclerView.NO_POSITION) {
        return null
    }
    val anchorView = layoutManager.findViewByPosition(position)
    val offset = if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
        (anchorView?.left ?: recyclerView.paddingLeft) - recyclerView.paddingLeft
    } else {
        (anchorView?.top ?: recyclerView.paddingTop) - recyclerView.paddingTop
    }
    return RecyclerViewportAnchor(position = position, offset = offset)
}

/**
 * 点击精确文本对应 TextView 或其最近的可点击父节点。
 * Clicks the TextView for exact text or the nearest clickable parent.
 */
internal fun Activity.clickTextView(text: String) {
    var current: View? = requireTextView(text)
    while (current != null && !current.isClickable) {
        current = (current.parent as? View)
    }
    assertNotNull("Expected clickable host for text: $text", current)
    current!!.performClick()
}

/** Clicks an exact TextView after scrolling its owning RecyclerView item into the viewport. */
internal fun Activity.clickTextViewVisible(text: String) {
    var current: View? = requireTextViewVisible(text)
    while (current != null && !current.isClickable) {
        current = current.parent as? View
    }
    assertNotNull("Expected clickable host for visible text: $text", current)
    assertTrue("Expected click to be handled for visible text: $text", current!!.performClick())
}

/**
 * 按 contentDescription 查找 View，找不到时直接失败。
 * Finds a View by contentDescription and fails fast when missing.
 */
internal fun Activity.requireViewWithContentDescription(description: String): View {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val view = findViewByContentDescription(root, description)
    assertNotNull("Expected to find view with contentDescription: $description", view)
    return view!!
}

/**
 * 断言 View 至少有一部分真实可见且已经完成测量。
 * Asserts that a View is at least partially visible and measured.
 */
internal fun assertViewFullyVisible(view: View) {
    assertTrue("Expected view to be shown", view.isShown)
    val rect = Rect()
    val visible = view.getGlobalVisibleRect(rect)
    assertTrue("Expected view to have visible rect", visible)
    assertTrue("Expected visible width > 0", rect.width() > 0)
    assertTrue("Expected visible height > 0", rect.height() > 0)
    assertTrue("Expected measured width > 0", view.width > 0)
    assertTrue("Expected measured height > 0", view.height > 0)
}

/**
 * 断言 View 的完整尺寸都在全局可见区域内。
 * Asserts that the full View bounds are visible globally.
 */
internal fun assertViewCompletelyVisible(view: View) {
    assertViewFullyVisible(view)
    val rect = Rect()
    val visible = view.getGlobalVisibleRect(rect)
    assertTrue("Expected view to have global rect", visible)
    assertEquals("Expected full width to be visible", view.width, rect.width())
    assertEquals("Expected full height to be visible", view.height, rect.height())
}

/**
 * 断言 TextView 文本不会垂直溢出内容区域。
 * Asserts that TextView content does not overflow vertically.
 */
internal fun assertTextFitsVertically(textView: TextView) {
    val layout = textView.layout
    assertNotNull("Expected layout for text: ${textView.text}", layout)
    layout ?: return
    val contentBottom = layout.getLineBottom(layout.lineCount - 1)
    val availableHeight = textView.height - textView.compoundPaddingTop - textView.compoundPaddingBottom
    assertTrue(
        "Expected text to fit vertically for text: ${textView.text}",
        contentBottom <= availableHeight,
    )
}

/**
 * 断言 TextView 没有省略号且可用宽度有效。
 * Asserts that TextView has no ellipsis and a valid text width.
 */
internal fun assertTextNotEllipsized(textView: TextView) {
    val layout = textView.layout
    assertNotNull("Expected layout for text: ${textView.text}", layout)
    layout ?: return
    for (line in 0 until layout.lineCount) {
        assertEquals(
            "Expected no ellipsis for text: ${textView.text}",
            0,
            layout.getEllipsisCount(line),
        )
    }
    assertFalse(
        "Expected text to stay on-screen for text: ${textView.text}",
        textView.text.isNotEmpty() && textView.width <= textView.compoundPaddingLeft + textView.compoundPaddingRight,
    )
}

/**
 * 向 Activity 分发单指 MotionEvent，并确保事件对象被回收。
 * Dispatches a single-pointer MotionEvent to the Activity and always recycles it.
 */
private fun Activity.dispatchGestureEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    x: Float,
    y: Float,
) {
    val event = MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        x,
        y,
        0,
    )
    try {
        dispatchTouchEvent(event)
    } finally {
        event.recycle()
    }
}

/**
 * 构造并分发多指 MotionEvent。
 * Builds and dispatches a multi-pointer MotionEvent.
 */
private fun dispatchMultiTouchEvent(
    target: View,
    downTime: Long,
    eventTime: Long,
    actionMasked: Int,
    points: List<Pair<Float, Float>>,
    actionIndex: Int = 0,
) {
    val pointerCount = points.size
    val pointerProperties = Array(pointerCount) { index ->
        MotionEvent.PointerProperties().apply {
            id = index
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }
    val pointerCoords = Array(pointerCount) { index ->
        MotionEvent.PointerCoords().apply {
            x = points[index].first
            y = points[index].second
            pressure = 1f
            size = 1f
        }
    }
    val action = when (actionMasked) {
        MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
            actionMasked or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        else -> actionMasked
    }
    val event = MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        pointerCount,
        pointerProperties,
        pointerCoords,
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
    )
    try {
        target.dispatchTouchEvent(event)
    } finally {
        event.recycle()
    }
}

/**
 * 根据中心点、半径和角度生成一对双指坐标。
 * Builds a pair of two-pointer coordinates from center, radius, and angle.
 */
private fun twoPointerCoords(
    centerX: Float,
    centerY: Float,
    radius: Float,
    angleRad: Float,
): Pair<Pair<Float, Float>, Pair<Float, Float>> {
    val dx = cos(angleRad) * radius
    val dy = sin(angleRad) * radius
    return (centerX - dx to centerY - dy) to (centerX + dx to centerY + dy)
}

/**
 * 深度优先查找精确文本匹配的 TextView。
 * Finds a TextView with exact text using depth-first traversal.
 */
internal fun findTextViewByText(root: View, text: String): TextView? {
    if (root is TextView && root.text?.toString() == text) {
        return root
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            val match = findTextViewByText(root.getChildAt(index), text)
            if (match != null) {
                return match
            }
        }
    }
    return null
}

private fun findTextViewsByText(
    root: View,
    text: String,
): List<TextView> {
    val matches = mutableListOf<TextView>()
    fun collect(view: View) {
        if (view is TextView && view.text?.toString() == text) {
            matches += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
    }
    collect(root)
    return matches
}

/**
 * 深度优先查找 contentDescription 匹配的 View。
 * Finds a View with matching contentDescription using depth-first traversal.
 */
internal fun findViewByContentDescription(root: View, description: String): View? {
    if (root.contentDescription?.toString() == description) {
        return root
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            val match = findViewByContentDescription(root.getChildAt(index), description)
            if (match != null) {
                return match
            }
        }
    }
    return null
}

/**
 * 深度优先查找 ViewCompose testTag 匹配的 View。
 * Finds a ViewCompose testTag match using depth-first traversal.
 */
internal fun findViewByTestTag(root: View, tag: String): View? {
    if (root.getTag(RendererR.id.viewcompose_test_tag) == tag) {
        return root
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            val match = findViewByTestTag(root.getChildAt(index), tag)
            if (match != null) {
                return match
            }
        }
    }
    return null
}

private fun findViewsByTestTag(
    root: View,
    tag: String,
): List<View> {
    val matches = mutableListOf<View>()
    fun collect(view: View) {
        if (view.getTag(RendererR.id.viewcompose_test_tag) == tag) {
            matches += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
    }
    collect(root)
    return matches
}

private fun findFirstRecyclerView(root: View): RecyclerView? {
    if (root is RecyclerView) {
        return root
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            val match = findFirstRecyclerView(root.getChildAt(index))
            if (match != null) {
                return match
            }
        }
    }
    return null
}

private fun findRecyclerViews(root: View): List<RecyclerView> {
    val matches = mutableListOf<RecyclerView>()
    fun collect(view: View) {
        if (view is RecyclerView) {
            matches += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collect(view.getChildAt(index))
            }
        }
    }
    collect(root)
    return matches
}

private fun findFirstEditText(root: View): EditText? {
    if (root is EditText) {
        return root
    }
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            val match = findFirstEditText(root.getChildAt(index))
            if (match != null) {
                return match
            }
        }
    }
    return null
}

private fun isViewVisible(view: View): Boolean {
    if (!view.isShown) return false
    val rect = Rect()
    return view.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0
}

/**
 * 断言 View 背景解析出的最终填充色与预期一致。
 * Asserts that the resolved fill color from a View background matches expectation.
 */
internal fun assertViewBackgroundColor(view: View, expectedColor: Int) {
    val actual = resolveDrawableColor(view.background)
    assertNotNull(
        "Expected a deterministic fill color for ${view.background?.javaClass?.name}",
        actual,
    )
    assertEquals(
        "Expected background color to match theme token",
        expectedColor,
        actual,
    )
}

/**
 * 从常见 Android drawable 包装层中递归解析实际填充色。
 * Recursively resolves the actual fill color from common Android drawable wrappers.
 */
private fun resolveDrawableColor(drawable: Drawable?): Int? {
    return when (drawable) {
        null -> null
        is ColorDrawable -> drawable.color
        is ColorStateListDrawable -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.colorStateList.defaultColor
        } else {
            null
        }
        is RippleDrawable -> {
            resolveDrawableColor(drawable.getDrawable(0))
                ?: resolveDrawableColor(drawable.findDrawableByLayerId(android.R.id.mask))
        }
        is InsetDrawable -> resolveDrawableColor(drawable.drawable)
        is LayerDrawable -> {
            for (index in 0 until drawable.numberOfLayers) {
                val color = resolveDrawableColor(drawable.getDrawable(index))
                if (color != null) {
                    return color
                }
            }
            null
        }
        is MaterialShapeDrawable -> drawable.fillColor?.defaultColor
        is GradientDrawable -> drawable.color?.defaultColor
        else -> resolveDrawableCenterColor(drawable)
    }
}

/** Resolves one center pixel without allocating or scanning a view-sized bitmap. */
private fun resolveDrawableCenterColor(drawable: Drawable): Int {
    val originalBounds = Rect(drawable.bounds)
    val needsTemporaryBounds = originalBounds.isEmpty
    if (needsTemporaryBounds) {
        drawable.setBounds(0, 0, 3, 3)
    }
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    return try {
        val bounds = drawable.bounds
        Canvas(bitmap).apply {
            translate(-bounds.exactCenterX() + 0.5f, -bounds.exactCenterY() + 0.5f)
            drawable.draw(this)
        }
        bitmap.getPixel(0, 0)
    } finally {
        bitmap.recycle()
        if (needsTemporaryBounds) {
            drawable.bounds = originalBounds
        }
    }
}

/** Returns the alpha channel of a deterministically resolved background fill color. */
internal fun resolvedViewBackgroundAlpha(view: View): Int? {
    return resolveDrawableColor(view.background)?.let(android.graphics.Color::alpha)
}
