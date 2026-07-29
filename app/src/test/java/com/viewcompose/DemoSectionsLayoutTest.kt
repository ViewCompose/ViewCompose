package com.viewcompose

/*
 * 测试职责：覆盖 demo 诊断事实区的真实 Android 测量结果，防止包裹内容的祖先再次把权重值压成窄列。
 * Test responsibility: covers real Android measurement for diagnostic facts so a wrap-content ancestor
 * cannot squeeze the weighted value back into a narrow column.
 */

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.viewcompose.host.android.renderInto
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.UiEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoSectionsLayoutTest {
    @Test
    fun `diagnostic value keeps usable width in a phone sized column`() {
        val context = RuntimeEnvironment.getApplication()
        val host = FrameLayout(context)
        val valueText = "snapshot|5|1760000000000|尚未捕获|1|1"
        val session = renderInto(host) {
            UiEnvironment(androidContext = context) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DiagnosticFactGroup(
                        title = "Renderer",
                        facts = listOf(
                            DiagnosticFact(
                                label = "探针 Key",
                                value = valueText,
                            ),
                        ),
                    )
                }
            }
        }

        try {
            host.measure(
                View.MeasureSpec.makeMeasureSpec(PHONE_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)

            val label = host.requireTextView("探针 Key")
            val value = host.requireTextView(valueText)
            val factRow = label.parent as LinearLayout

            assertSame(factRow, value.parent)
            assertEquals(LinearLayout.HORIZONTAL, factRow.orientation)
            assertTrue(
                "Expected a usable weighted value width, actual=${value.measuredWidth}",
                value.measuredWidth >= MIN_VALUE_WIDTH_PX,
            )
        } finally {
            session.dispose()
        }
    }

    private fun View.requireTextView(text: String): TextView {
        if (this is TextView && this.text.toString() == text) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                runCatching { child.requireTextView(text) }
                    .getOrNull()
                    ?.let { return it }
            }
        }
        error("TextView not found: $text")
    }

    private companion object {
        const val PHONE_WIDTH_PX = 360
        const val MIN_VALUE_WIDTH_PX = 160
    }
}
