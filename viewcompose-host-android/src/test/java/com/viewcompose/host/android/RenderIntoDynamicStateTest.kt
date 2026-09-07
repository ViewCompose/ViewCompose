package com.viewcompose.host.android

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.viewcompose.host.android.resources.AndroidResourceEnvironment
import com.viewcompose.ui.foundation.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenderIntoDynamicStateTest {
    @Test
    fun `retained session renders external state updates without replacing native siblings`() {
        val context = RuntimeEnvironment.getApplication()
        val screen = FrameLayout(context)
        val nativeSibling = TextView(context).apply { text = "Native animation" }
        val container = FrameLayout(context)
        screen.addView(nativeSibling)
        screen.addView(container)
        var state = ScanState(filesCount = 0, scanning = true)

        val session = renderInto(container) {
            AndroidResourceEnvironment(context = container.context) {
                Text(
                    text = if (state.scanning) "${state.filesCount} photos" else "Complete",
                    key = "scan-progress",
                )
            }
        }
        val mountedText = container.singleTextView()
        assertEquals("0 photos", mountedText.text.toString())

        state = ScanState(filesCount = 3, scanning = true)
        session.render()

        assertSame(mountedText, container.singleTextView())
        assertEquals("3 photos", mountedText.text.toString())
        assertSame(nativeSibling, screen.getChildAt(0))

        state = ScanState(filesCount = 3, scanning = false)
        session.render()
        assertSame(mountedText, container.singleTextView())
        assertEquals("Complete", mountedText.text.toString())

        session.dispose()
        assertEquals(0, container.childCount)
        assertSame(nativeSibling, screen.getChildAt(0))
        assertThrows(IllegalStateException::class.java, session::render)
    }

    private fun ViewGroup.singleTextView(): TextView {
        val texts = mutableListOf<TextView>()
        fun collect(view: View) {
            if (view is TextView) texts += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(this)
        return texts.single()
    }

    private data class ScanState(
        val filesCount: Int,
        val scanning: Boolean,
    )
}
