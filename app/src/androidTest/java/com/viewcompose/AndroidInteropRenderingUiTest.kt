package com.viewcompose

import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viewcompose.renderer.view.tree.ViewTreeRenderer
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AndroidView/nativeView 互操作渲染的设备级回归测试。
 * Device-level regression tests for AndroidView/nativeView interop rendering.
 */
@RunWith(AndroidJUnit4::class)
class AndroidInteropRenderingUiTest {
    @Test
    fun nativeViewConfig_isAppliedAfterSpecAndModifierPatch() {
        launchDemoActivity(InteropActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = FrameLayout(activity)
                val first = textNode(
                    text = "first",
                    nativeKey = "first-config",
                    configuredTag = "first",
                )
                val initial = ViewTreeRenderer.renderInto(
                    container = container,
                    previous = emptyList(),
                    nodes = listOf(first),
                )
                val textView = initial.mountedNodes.single().view as TextView
                assertEquals("first", textView.tag)

                val second = textNode(
                    text = "second",
                    nativeKey = "second-config",
                    configuredTag = "second",
                )
                val patched = ViewTreeRenderer.renderInto(
                    container = container,
                    previous = initial.mountedNodes,
                    nodes = listOf(second),
                )

                assertSame(textView, patched.mountedNodes.single().view)
                assertEquals("second", textView.text.toString())
                assertEquals("second", textView.tag)
                ViewTreeRenderer.disposeMounted(container, patched.mountedNodes)
            }
        }
    }

    @Test
    fun androidView_reuseAndDisposeInvokeLifecycleCallbacksOnce() {
        launchDemoActivity(InteropActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = FrameLayout(activity)
                var factoryCalls = 0
                var updates = 0
                var resets = 0
                var releases = 0

                val initial = ViewTreeRenderer.renderInto(
                    container = container,
                    previous = emptyList(),
                    nodes = listOf(
                        androidViewNode(
                            factory = {
                                factoryCalls += 1
                                TextView(activity)
                            },
                            update = { updates += 1 },
                        ),
                    ),
                )
                val nativeView = initial.mountedNodes.single().view

                val reused = ViewTreeRenderer.renderInto(
                    container = container,
                    previous = initial.mountedNodes,
                    nodes = listOf(
                        androidViewNode(
                            factory = {
                                factoryCalls += 1
                                TextView(activity)
                            },
                            update = { updates += 1 },
                            onReset = { resets += 1 },
                            onRelease = { releases += 1 },
                        ),
                    ),
                )

                assertSame(nativeView, reused.mountedNodes.single().view)
                assertEquals(1, factoryCalls)
                assertEquals(2, updates)
                assertEquals(1, resets)

                ViewTreeRenderer.disposeMounted(container, reused.mountedNodes)
                ViewTreeRenderer.disposeMounted(container, reused.mountedNodes)

                assertEquals(1, releases)
            }
        }
    }

    private fun textNode(
        text: String,
        nativeKey: Any,
        configuredTag: String,
    ): VNode {
        return VNode(
            type = NodeType.Text,
            spec = TextNodeProps(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14,
            ),
            modifier = Modifier.then(
                NativeViewElement(stableKey = nativeKey) { view ->
                    (view as TextView).tag = configuredTag
                },
            ),
        )
    }

    private fun androidViewNode(
        factory: () -> TextView,
        update: (TextView) -> Unit,
        onReset: ((TextView) -> Unit)? = null,
        onRelease: ((TextView) -> Unit)? = null,
    ): VNode {
        return VNode(
            type = NodeType.AndroidView,
            key = "native-view",
            spec = AndroidViewNodeProps(
                factory = { factory() },
                update = { view -> update(view as TextView) },
                onReset = onReset?.let { reset ->
                    { view -> reset(view as TextView) }
                },
                onRelease = onRelease?.let { release ->
                    { view -> release(view as TextView) }
                },
            ),
        )
    }
}
