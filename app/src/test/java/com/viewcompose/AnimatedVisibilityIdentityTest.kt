package com.viewcompose

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.host.android.renderInto
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Text
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimatedVisibilityIdentityTest {
    @Test
    fun `visibility toggle retains the following native button`() {
        val container = FrameLayout(RuntimeEnvironment.getApplication())
        var completed = false
        val session = renderInto(container) {
            Text("Read the tutorial")
            AnimatedVisibility(visible = completed) {
                Text("Completed")
            }
            Button(
                text = if (completed) "Reopen Read the tutorial" else "Complete Read the tutorial",
                onClick = {},
            )
        }

        val before = container.requireTextView("Complete Read the tutorial")
        completed = true
        session.render()
        session.render()
        val after = container.requireTextView("Reopen Read the tutorial")

        assertSame(before, after)

        completed = false
        session.render()
        session.render()
        val reopened = container.requireTextView("Complete Read the tutorial")

        assertSame(before, reopened)
        session.dispose()
    }

    private fun View.requireTextView(text: String): TextView {
        return requireNotNull(findTextView(text)) {
            "No TextView found with text=$text"
        }
    }

    private fun View.findTextView(text: String): TextView? {
        if (this is TextView && this.text.toString() == text) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val match = getChildAt(index).findTextView(text)
                if (match != null) return match
            }
        }
        return null
    }
}
