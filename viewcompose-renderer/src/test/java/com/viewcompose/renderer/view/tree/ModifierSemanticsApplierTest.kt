package com.viewcompose.renderer.view.tree

import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.viewcompose.ui.modifier.SemanticsConfiguration
import com.viewcompose.ui.modifier.SemanticsLiveRegion
import com.viewcompose.ui.modifier.SemanticsProgressRange
import com.viewcompose.ui.modifier.SemanticsRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierSemanticsApplierTest {
    @Test
    @Suppress("DEPRECATION")
    fun `applies structured semantics to native accessibility`() {
        val view = View(RuntimeEnvironment.getApplication())
        val semantics = SemanticsConfiguration(
            contentDescription = "Download",
            stateDescription = "In progress",
            paneTitle = "Downloads",
            error = "Network unavailable",
            clickLabel = "Retry",
            role = SemanticsRole.Button,
            liveRegion = SemanticsLiveRegion.Polite,
            progressRange = SemanticsProgressRange(
                current = 50f,
                start = 0f,
                endInclusive = 100f,
            ),
            heading = true,
            selected = true,
            checked = false,
            enabled = false,
            mergeDescendants = true,
        )

        ModifierSemanticsApplier.apply(view, semantics)

        assertEquals("Download", view.contentDescription)
        assertEquals("In progress", ViewCompat.getStateDescription(view))
        assertEquals("Downloads", ViewCompat.getAccessibilityPaneTitle(view))
        assertTrue(ViewCompat.isAccessibilityHeading(view))
        assertTrue(ViewCompat.isScreenReaderFocusable(view))
        assertEquals(
            View.ACCESSIBILITY_LIVE_REGION_POLITE,
            view.accessibilityLiveRegion,
        )
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_YES,
            view.importantForAccessibility,
        )

        val info = AccessibilityNodeInfoCompat.obtain()
        requireNotNull(ViewCompat.getAccessibilityDelegate(view))
            .onInitializeAccessibilityNodeInfo(view, info)

        assertEquals(Button::class.java.name, info.className.toString())
        assertTrue(info.isSelected)
        assertTrue(info.isCheckable)
        assertFalse(info.isChecked)
        assertFalse(info.isEnabled)
        assertTrue(info.isContentInvalid)
        assertEquals("Network unavailable", info.error)
        assertEquals(50f, info.rangeInfo?.current)
        assertEquals(
            "Retry",
            info.actionList.first { action ->
                action.id == AccessibilityNodeInfoCompat.ACTION_CLICK
            }.label,
        )
    }

    @Test
    fun `removing semantics restores native view accessibility`() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            contentDescription = "Native description"
        }
        ViewCompat.setStateDescription(view, "Native state")
        ViewCompat.setAccessibilityHeading(view, false)
        view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        val originalDelegate = ViewCompat.getAccessibilityDelegate(view)

        ModifierSemanticsApplier.apply(
            view,
            SemanticsConfiguration(
                contentDescription = "Override",
                stateDescription = "Override state",
                heading = true,
                liveRegion = SemanticsLiveRegion.Polite,
                role = SemanticsRole.Image,
            ),
        )
        ModifierSemanticsApplier.apply(view, SemanticsConfiguration.Empty)

        assertEquals("Native description", view.contentDescription)
        assertEquals("Native state", ViewCompat.getStateDescription(view))
        assertFalse(ViewCompat.isAccessibilityHeading(view))
        assertEquals(
            View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE,
            view.accessibilityLiveRegion,
        )
        assertSame(originalDelegate, ViewCompat.getAccessibilityDelegate(view))
    }

    @Test
    fun `hidden semantics hides the native subtree`() {
        val view = View(RuntimeEnvironment.getApplication())

        ModifierSemanticsApplier.apply(
            view,
            SemanticsConfiguration(hidden = true),
        )

        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            view.importantForAccessibility,
        )
    }
}
