package com.viewcompose.host.android

/*
 * 测试职责：覆盖 Android host 中的 Android Interop Dsl 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Android Interop Dsl behavior in Android host and guards the contract against regressions.
 */

import android.view.View
import com.viewcompose.host.android.graphics.androidGraphics
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.widget.core.buildVNodeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
