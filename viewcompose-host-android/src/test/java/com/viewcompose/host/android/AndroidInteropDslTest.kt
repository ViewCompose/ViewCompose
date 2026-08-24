package com.viewcompose.host.android

/*
 * 测试职责：覆盖 Android host 中的 Android Interop Dsl 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Android Interop Dsl behavior in Android host and guards the contract against regressions.
 */

import android.view.View
import com.viewcompose.host.android.graphics.androidGraphics
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.foundation.buildVNodeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class AndroidInteropDslTest {
    @Test
    fun `android view emits android view node spec`() {
        val tree = buildVNodeTree {
            AndroidView(
                factory = { _: android.content.Context ->
                    throw IllegalStateException("factory should not be invoked during tree build")
                },
                update = { _: View -> Unit },
            )
        }

        val node = tree.single()
        assertEquals(NodeType.AndroidView, node.type)
        assertTrue(node.spec is AndroidViewNodeProps)
        val spec = node.spec as AndroidViewNodeProps
        assertEquals(null, spec.onReset)
        assertEquals(null, spec.onRelease)
        assertEquals(null, spec.onCommit)
        assertEquals(AndroidViewLifecycleMode.None.name, spec.lifecycleMode)
    }

    @Test
    fun `android view stores reset release and commit callbacks`() {
        val tree = buildVNodeTree {
            AndroidView(
                factory = { _: android.content.Context ->
                    throw IllegalStateException("factory should not be invoked during tree build")
                },
                onReset = { _: View -> Unit },
                onRelease = { _: View -> Unit },
                onCommit = { _: View -> Unit },
            )
        }

        val spec = tree.single().spec as AndroidViewNodeProps
        assertTrue(spec.onReset != null)
        assertTrue(spec.onRelease != null)
        assertTrue(spec.onCommit != null)
    }

    @Test
    fun `callback construction key participates in physical identity`() {
        fun spec(constructionKey: Any): AndroidViewNodeProps = buildVNodeTree {
            AndroidView(
                factory = { context -> View(context) },
                constructionKey = constructionKey,
            )
        }.single().spec as AndroidViewNodeProps

        assertEquals(spec("style-a").constructionIdentity, spec("style-a").constructionIdentity)
        assertNotEquals(spec("style-a").constructionIdentity, spec("style-b").constructionIdentity)
    }

    @Test
    fun `typed construction identity uses adapter implementation class and construction key`() {
        fun spec(adapter: RecordingAdapter, constructionKey: Any): AndroidViewNodeProps =
            buildVNodeTree {
                AndroidView(
                    adapter = adapter,
                    state = "bound",
                    constructionKey = constructionKey,
                )
            }.single().spec as AndroidViewNodeProps

        val firstAdapter = RecordingAdapter(mutableListOf())
        val secondAdapter = RecordingAdapter(mutableListOf())

        assertEquals(
            spec(firstAdapter, "style-a").constructionIdentity,
            spec(secondAdapter, "style-a").constructionIdentity,
        )
        assertNotEquals(
            spec(firstAdapter, "style-a").constructionIdentity,
            spec(secondAdapter, "style-b").constructionIdentity,
        )
    }

    @Test
    fun `typed adapter receives state environment reset reason and lifecycle callbacks`() {
        val events = mutableListOf<String>()
        val adapter = RecordingAdapter(events)
        val environment = UiEnvironmentValues.Default.copy(resourceRevision = 7L)
        val spec = buildVNodeTree {
            AndroidView(
                adapter = adapter,
                state = "bound",
                key = "logical",
                constructionKey = "style",
            )
        }.single().spec as AndroidViewNodeProps

        val context: android.app.Application = RuntimeEnvironment.getApplication()
        val view = spec.factory(context, environment) as View
        spec.update?.invoke(view, environment)
        spec.onReset?.invoke(view, environment)
        spec.onCommit?.invoke(view, environment)
        spec.onRelease?.invoke(view)

        assertEquals(
            listOf("create:7", "update:bound:7", "reset:MountedTreeReuse:7", "commit:bound:7", "release"),
            events,
        )
        assertSame(context, view.context)
        assertEquals(RecordingAdapter::class.java.name, spec.adapterName)
        assertEquals(AndroidViewLifecycleMode.AdapterManaged.name, spec.lifecycleMode)
    }

    @Test
    fun `nativeView adds native view modifier element`() {
        val modifier = Modifier.nativeView(key = "host") { _: View -> Unit }
        assertEquals(1, modifier.elements.size)
        assertTrue(modifier.elements.single() is NativeViewElement)
    }

    @Test
    fun `androidGraphics adds native view modifier element`() {
        val modifier = Modifier.androidGraphics(key = "graphics") { _: View -> Unit }
        assertEquals(1, modifier.elements.size)
        assertTrue(modifier.elements.single() is NativeViewElement)
    }

    private class RecordingAdapter(
        private val events: MutableList<String>,
    ) : AndroidViewAdapter<View, String> {
        override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable
        override val lifecycleMode: AndroidViewLifecycleMode = AndroidViewLifecycleMode.AdapterManaged

        override fun create(scope: AndroidViewCreateScope): View {
            events += "create:${scope.environment.resourceRevision}"
            return View(scope.context)
        }

        override fun update(scope: AndroidViewUpdateScope<View>, state: String) {
            events += "update:$state:${scope.environment.resourceRevision}"
        }

        override fun onReset(
            scope: AndroidViewResetScope<View>,
            reason: AndroidViewResetReason,
        ) {
            events += "reset:$reason:${scope.environment.resourceRevision}"
        }

        override fun onCommit(scope: AndroidViewCommitScope<View>, state: String) {
            events += "commit:$state:${scope.environment.resourceRevision}"
        }

        override fun onRelease(view: View) {
            events += "release"
        }
    }
}
