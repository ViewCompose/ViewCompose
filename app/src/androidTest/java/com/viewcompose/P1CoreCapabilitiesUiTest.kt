package com.viewcompose

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viewcompose.gesture.nestedScroll
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.renderInto
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.focusRequester
import com.viewcompose.ui.modifier.focusable
import com.viewcompose.ui.modifier.onKeyEvent
import com.viewcompose.ui.modifier.onPreviewKeyEvent
import com.viewcompose.widget.core.RenderFailure
import com.viewcompose.widget.core.RenderFailureOperation
import com.viewcompose.widget.core.RenderFailurePhase
import com.viewcompose.widget.core.RenderFrameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1 能力的设备级集成测试。
 * Device-level integration tests for P1 capabilities.
 *
 * 覆盖焦点/硬件键、原生 nested scroll、AndroidView 失败回滚等必须走真实 View 管线的能力。
 * Covers focus/hardware keys, native nested scroll, and AndroidView rollback paths that require real Views.
 */
@RunWith(AndroidJUnit4::class)
class P1CoreCapabilitiesUiTest {
    @Test
    fun focusRequester_andHardwareKeyDispatch_useNativeViewPipeline() {
        launchDemoActivity(P1CoreCapabilitiesTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = attachedContainer(activity)
                val requester = FocusRequester()
                val events = mutableListOf<String>()
                val session = renderInto(container) {
                    AndroidView(
                        key = "focus-target",
                        factory = { context -> TextView(context) },
                        modifier = Modifier
                            .focusable()
                            .focusRequester(requester)
                            .onPreviewKeyEvent {
                                events += "preview:${it.nativeKeyCode}"
                                false
                            }
                            .onKeyEvent {
                                events += "bubble:${it.nativeKeyCode}"
                                true
                            },
                    )
                }

                try {
                    assertTrue(requester.requestFocus())
                    val target = container.getChildAt(0)
                    assertTrue(
                        target.dispatchKeyEvent(
                            AndroidKeyEvent(
                                AndroidKeyEvent.ACTION_DOWN,
                                AndroidKeyEvent.KEYCODE_A,
                            ),
                        ),
                    )
                    assertEquals(
                        listOf(
                            "preview:${AndroidKeyEvent.KEYCODE_A}",
                            "bubble:${AndroidKeyEvent.KEYCODE_A}",
                        ),
                        events,
                    )
                } finally {
                    session.dispose()
                    container.detach()
                }
            }
        }
    }

    @Test
    fun nestedScrollHost_participatesInNativeNestedScrolling() {
        launchDemoActivity(P1CoreCapabilitiesTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = attachedContainer(activity)
                val received = mutableListOf<ScrollDelta>()
                val connection = object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: ScrollDelta,
                        source: NestedScrollSource,
                    ): ScrollDelta {
                        received += available
                        return available
                    }
                }
                val session = renderInto(container) {
                    AndroidView(
                        key = "nested-child",
                        factory = { context -> View(context) },
                        modifier = Modifier.nestedScroll(connection),
                    )
                }

                try {
                    val host = container.getChildAt(0) as ViewGroup
                    val nestedParent = host as NestedScrollingParent3
                    val child = host.getChildAt(0)
                    assertTrue(
                        nestedParent.onStartNestedScroll(
                            child,
                            child,
                            ViewCompat.SCROLL_AXIS_VERTICAL,
                            ViewCompat.TYPE_TOUCH,
                        ),
                    )
                    val consumed = IntArray(2)
                    nestedParent.onNestedPreScroll(
                        child,
                        0,
                        18,
                        consumed,
                        ViewCompat.TYPE_TOUCH,
                    )

                    assertEquals(listOf(ScrollDelta(0f, 18f)), received)
                    assertEquals(0, consumed[0])
                    assertEquals(18, consumed[1])
                    nestedParent.onStopNestedScroll(child, ViewCompat.TYPE_TOUCH)
                } finally {
                    session.dispose()
                    container.detach()
                }
            }
        }
    }

    @Test
    fun failedAndroidViewUpdate_rollsBackAndDoesNotPublishCommitEffect() {
        launchDemoActivity(P1CoreCapabilitiesTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = attachedContainer(activity)
                val failures = mutableListOf<RenderFailure>()
                var value = "old"
                var failUpdate = false
                var commits = 0
                val session = renderInto(
                    container = container,
                    onRenderFailure = failures::add,
                ) {
                    val frameValue = value
                    AndroidView(
                        key = "transactional-native",
                        factory = { context -> TextView(context) },
                        update = { view ->
                            if (failUpdate) {
                                error("update failed")
                            }
                            (view as TextView).text = frameValue
                        },
                        onCommit = {
                            commits += 1
                        },
                    )
                }

                try {
                    val nativeView = container.getChildAt(0) as TextView
                    assertEquals("old", nativeView.text.toString())
                    assertEquals(1, commits)

                    value = "broken"
                    failUpdate = true
                    session.render()

                    assertEquals("old", nativeView.text.toString())
                    assertEquals(1, commits)
                    assertEquals(RenderFrameStatus.RolledBack, session.lastFrameReport?.status)
                    assertEquals(RenderFailurePhase.ViewTreeRender, failures.single().phase)
                    assertEquals(
                        RenderFailureOperation.AndroidViewUpdate,
                        failures.single().operation,
                    )
                    assertEquals("transactional-native", failures.single().nodeKey)

                    value = "new"
                    failUpdate = false
                    session.render()

                    assertEquals("new", nativeView.text.toString())
                    assertEquals(2, commits)
                    assertEquals(RenderFrameStatus.Committed, session.lastFrameReport?.status)
                } finally {
                    session.dispose()
                    container.detach()
                }
            }
        }
    }

    /**
     * 在测试 Activity 上挂载临时容器，用于直接驱动 renderInto。
     * Attaches a temporary container to the test Activity for direct renderInto driving.
     */
    private fun attachedContainer(activity: P1CoreCapabilitiesTestActivity): FrameLayout {
        return FrameLayout(activity).also { container ->
            activity.addContentView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun View.detach() {
        (parent as? ViewGroup)?.removeView(this)
    }
}
