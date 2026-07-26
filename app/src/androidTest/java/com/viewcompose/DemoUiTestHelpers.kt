package com.viewcompose

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import androidx.lifecycle.Lifecycle
import com.google.android.material.shape.MaterialShapeDrawable
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

internal data class RecyclerViewportAnchor(
    val position: Int,
    val offset: Int,
)

internal fun <A : Activity> launchDemoActivity(
    activityClass: Class<A>,
    themeMode: DemoThemeMode = DemoThemeMode.Light,
): ActivityScenario<A> {
    DemoThemeSession.mode = themeMode
    return ActivityScenario.launch(activityClass).also { scenario ->
        scenario.moveToState(Lifecycle.State.RESUMED)
    }
}

internal fun <A : Activity> launchDemoActivity(
    intent: Intent,
    themeMode: DemoThemeMode = DemoThemeMode.Light,
): ActivityScenario<A> {
    DemoThemeSession.mode = themeMode
    return ActivityScenario.launch<A>(intent).also { scenario ->
        scenario.moveToState(Lifecycle.State.RESUMED)
    }
}

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

internal fun captureDeviceScreenshot(name: String) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.getExternalFilesDir(null), "ui-test-screenshots")
    if (!directory.exists()) {
        directory.mkdirs()
    }
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        .takeScreenshot(File(directory, "$name.png"))
}

internal fun clickDeviceText(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val node = device.wait(Until.hasObject(By.text(text)), 5_000)
    assertTrue("Expected device text target: $text", node)
    val target = device.findObject(By.text(text))
    assertNotNull("Expected device object for text: $text", target)
    target!!.click()
    waitForUiIdle()
}

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

internal fun scrollDeviceTextIntoView(text: String) {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val scrollable = UiScrollable(UiSelector().scrollable(true))
    scrollable.setAsVerticalList()
    val found = scrollable.scrollTextIntoView(text) || scrollable.scrollIntoView(UiSelector().text(text))
    assertTrue("Expected to scroll text target into view: $text", found)
    waitForUiIdle()
}

internal fun scrollDeviceDescriptionIntoView(description: String) {
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val scrollable = UiScrollable(UiSelector().scrollable(true))
    scrollable.setAsVerticalList()
    val found = scrollable.scrollDescriptionIntoView(description) ||
        scrollable.scrollIntoView(UiSelector().description(description))
    assertTrue("Expected to scroll description target into view: $description", found)
    waitForUiIdle()
}

internal fun Activity.requireTextView(text: String): TextView {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val view = findTextViewByText(root, text)
    assertNotNull("Expected to find TextView with text: $text", view)
    return view!!
}

internal fun Activity.requireViewByTestTag(tag: String): View {
    return requireViewByTestTagVisible(tag)
}

internal fun Activity.requireTextViewByTestTag(tag: String): TextView {
    return requireTextViewByTestTagVisible(tag)
}

internal fun Activity.requireViewByTestTagVisible(
    tag: String,
    maxScrollAttempts: Int = 24,
): View {
    val root = findViewById<ViewGroup>(android.R.id.content)
    repeat(maxScrollAttempts) {
        val view = findViewByTestTag(root, tag)
        if (view != null && isViewVisible(view)) {
            return view
        }
        val recyclerView = findFirstRecyclerView(root) ?: return@repeat
        val delta = (recyclerView.height * 0.7f).toInt().coerceAtLeast(1)
        recyclerView.scrollBy(0, delta)
    }
    val view = findViewByTestTag(root, tag)
    assertNotNull("Expected to find view with testTag: $tag", view)
    assertViewFullyVisible(view!!)
    return view
}

internal fun Activity.requireTextViewByTestTagVisible(
    tag: String,
    maxScrollAttempts: Int = 24,
): TextView {
    val view = requireViewByTestTagVisible(tag, maxScrollAttempts)
    assertTrue("Expected testTag=$tag to map to TextView, but was ${view.javaClass.simpleName}", view is TextView)
    return view as TextView
}

internal fun Activity.clickByTestTag(tag: String) {
    var current: View? = requireViewByTestTagVisible(tag)
    while (current != null && !current.isClickable) {
        current = current.parent as? View
    }
    assertNotNull("Expected clickable host for testTag: $tag", current)
    current!!.performClick()
}

internal fun Activity.tapByTestTag(tag: String) {
    val view = requireViewByTestTagVisible(tag)
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

internal fun Activity.dragByTestTag(
    tag: String,
    deltaX: Float,
    deltaY: Float = 0f,
    steps: Int = 8,
) {
    val view = requireViewByTestTagVisible(tag)
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

internal fun Activity.transformByTestTag(
    tag: String,
    panX: Float = 120f,
    panY: Float = 72f,
    rotationDegrees: Float = 28f,
    zoomRatio: Float = 1.2f,
    steps: Int = 10,
) {
    val view = requireViewByTestTagVisible(tag)
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

internal fun Activity.focusInputByTestTag(tag: String) {
    val host = requireViewByTestTagVisible(tag)
    val input = findFirstEditText(host)
    assertNotNull("Expected EditText descendant for testTag: $tag", input)
    input!!.requestFocus()
}

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

internal fun Activity.clickTextView(text: String) {
    var current: View? = requireTextView(text)
    while (current != null && !current.isClickable) {
        current = (current.parent as? View)
    }
    assertNotNull("Expected clickable host for text: $text", current)
    current!!.performClick()
}

internal fun Activity.requireViewWithContentDescription(description: String): View {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val view = findViewByContentDescription(root, description)
    assertNotNull("Expected to find view with contentDescription: $description", view)
    return view!!
}

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

internal fun assertViewCompletelyVisible(view: View) {
    assertViewFullyVisible(view)
    val rect = Rect()
    val visible = view.getGlobalVisibleRect(rect)
    assertTrue("Expected view to have global rect", visible)
    assertEquals("Expected full width to be visible", view.width, rect.width())
    assertEquals("Expected full height to be visible", view.height, rect.height())
}

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

internal fun assertViewBackgroundColor(view: View, expectedColor: Int) {
    val actual = resolveDrawableColor(view.background)
    assertNotNull("Expected background drawable color for ${view.javaClass.simpleName}", actual)
    assertEquals(
        "Expected background color to match theme token",
        expectedColor,
        actual,
    )
}

private fun resolveDrawableColor(drawable: Drawable?): Int? {
    return when (drawable) {
        null -> null
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
        else -> null
    }
}
