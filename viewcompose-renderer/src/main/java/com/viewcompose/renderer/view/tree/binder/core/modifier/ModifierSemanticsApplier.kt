package com.viewcompose.renderer.view.tree

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Switch
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.viewcompose.renderer.R
import com.viewcompose.ui.modifier.SemanticsConfiguration
import com.viewcompose.ui.modifier.SemanticsLiveRegion
import com.viewcompose.ui.modifier.SemanticsRole

internal object ModifierSemanticsApplier {
    fun apply(
        view: View,
        semantics: SemanticsConfiguration,
    ) {
        val existingState = view.getTag(R.id.viewcompose_semantics_state) as? SemanticsViewState
        if (semantics.isEmpty) {
            existingState?.restore(view)
            view.setTag(R.id.viewcompose_semantics_state, null)
            return
        }

        val state = existingState ?: SemanticsViewState.capture(view).also { captured ->
            view.setTag(R.id.viewcompose_semantics_state, captured)
        }
        view.contentDescription = semantics.contentDescription ?: state.contentDescription
        ViewCompat.setStateDescription(
            view,
            semantics.stateDescription ?: state.stateDescription,
        )
        ViewCompat.setAccessibilityPaneTitle(
            view,
            semantics.paneTitle ?: state.paneTitle,
        )
        ViewCompat.setAccessibilityHeading(
            view,
            semantics.heading ?: state.heading,
        )
        view.accessibilityLiveRegion =
            semantics.liveRegion?.toAndroidValue() ?: state.liveRegion
        ViewCompat.setScreenReaderFocusable(
            view,
            semantics.mergeDescendants ?: state.screenReaderFocusable,
        )
        view.importantForAccessibility = when {
            semantics.hidden == true ->
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            semantics.mergeDescendants == true ->
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
            else -> state.importantForAccessibility
        }
        val delegate = if (semantics.hasNodeInfoProperties()) {
            SemanticsAccessibilityDelegate(
                original = state.accessibilityDelegate,
                semantics = semantics,
            )
        } else {
            state.accessibilityDelegate
        }
        ViewCompat.setAccessibilityDelegate(view, delegate)
    }

    private fun SemanticsConfiguration.hasNodeInfoProperties(): Boolean {
        return role != null ||
            error != null ||
            clickLabel != null ||
            progressRange != null ||
            selected != null ||
            checked != null ||
            enabled != null
    }

    private fun SemanticsLiveRegion.toAndroidValue(): Int {
        return when (this) {
            SemanticsLiveRegion.None -> View.ACCESSIBILITY_LIVE_REGION_NONE
            SemanticsLiveRegion.Polite -> View.ACCESSIBILITY_LIVE_REGION_POLITE
            SemanticsLiveRegion.Assertive -> View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }
    }

    private data class SemanticsViewState(
        val contentDescription: CharSequence?,
        val stateDescription: CharSequence?,
        val paneTitle: CharSequence?,
        val heading: Boolean,
        val liveRegion: Int,
        val screenReaderFocusable: Boolean,
        val importantForAccessibility: Int,
        val accessibilityDelegate: AccessibilityDelegateCompat?,
    ) {
        fun restore(view: View) {
            view.contentDescription = contentDescription
            ViewCompat.setStateDescription(view, stateDescription)
            ViewCompat.setAccessibilityPaneTitle(view, paneTitle)
            ViewCompat.setAccessibilityHeading(view, heading)
            view.accessibilityLiveRegion = liveRegion
            ViewCompat.setScreenReaderFocusable(view, screenReaderFocusable)
            view.importantForAccessibility = importantForAccessibility
            ViewCompat.setAccessibilityDelegate(view, accessibilityDelegate)
        }

        companion object {
            fun capture(view: View): SemanticsViewState {
                return SemanticsViewState(
                    contentDescription = view.contentDescription,
                    stateDescription = ViewCompat.getStateDescription(view),
                    paneTitle = ViewCompat.getAccessibilityPaneTitle(view),
                    heading = ViewCompat.isAccessibilityHeading(view),
                    liveRegion = view.accessibilityLiveRegion,
                    screenReaderFocusable = ViewCompat.isScreenReaderFocusable(view),
                    importantForAccessibility = view.importantForAccessibility,
                    accessibilityDelegate = ViewCompat.getAccessibilityDelegate(view),
                )
            }
        }
    }

    private class SemanticsAccessibilityDelegate(
        private val original: AccessibilityDelegateCompat?,
        private val semantics: SemanticsConfiguration,
    ) : AccessibilityDelegateCompat() {
        @Suppress("DEPRECATION")
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat,
        ) {
            if (original != null) {
                original.onInitializeAccessibilityNodeInfo(host, info)
            } else {
                super.onInitializeAccessibilityNodeInfo(host, info)
            }
            semantics.role?.let { role ->
                info.className = role.androidClassName()
            }
            semantics.selected?.let { selected ->
                info.isSelected = selected
            }
            semantics.checked?.let { checked ->
                info.isCheckable = true
                info.isChecked = checked
            }
            semantics.enabled?.let { enabled ->
                info.isEnabled = enabled
            }
            semantics.error?.let { error ->
                info.isContentInvalid = true
                info.error = error
            }
            semantics.progressRange?.let { range ->
                info.rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                    AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT,
                    range.start,
                    range.endInclusive,
                    range.current,
                )
            }
            semantics.clickLabel?.let { label ->
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        label,
                    ),
                )
            }
        }

        override fun performAccessibilityAction(
            host: View,
            action: Int,
            args: Bundle?,
        ): Boolean {
            return original?.performAccessibilityAction(host, action, args)
                ?: super.performAccessibilityAction(host, action, args)
        }
    }

    private fun SemanticsRole.androidClassName(): String {
        return when (this) {
            SemanticsRole.Button,
            SemanticsRole.Tab,
            -> Button::class.java.name
            SemanticsRole.Checkbox -> CheckBox::class.java.name
            SemanticsRole.Switch -> Switch::class.java.name
            SemanticsRole.RadioButton -> RadioButton::class.java.name
            SemanticsRole.Image -> ImageView::class.java.name
        }
    }
}
