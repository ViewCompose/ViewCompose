package com.viewcompose.demo.automation

import com.viewcompose.demo.contract.DemoAutomationTarget
import com.viewcompose.host.android.nativeView
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag

/** Applies one locale-independent target to both Demo automation bridges. */
internal fun Modifier.demoAutomationTarget(target: DemoAutomationTarget): Modifier {
    return testTag(target.testTag).nativeView(
        key = "demo-automation:${target.resourceName}",
    ) { view ->
        if (view.id != target.androidViewId) {
            view.id = target.androidViewId
        }
    }
}
