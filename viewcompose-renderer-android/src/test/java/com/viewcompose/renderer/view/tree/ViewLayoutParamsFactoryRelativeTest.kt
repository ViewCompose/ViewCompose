package com.viewcompose.renderer.view.tree

/*
 * Test responsibility: proves logical margins use the captured VNode direction under native
 * ConstraintLayout parent data instead of reading mutable platform-global configuration.
 */

import androidx.constraintlayout.widget.ConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.marginRelative
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ViewLayoutParamsFactoryRelativeTest {
    @Test
    fun `relative margin maps through captured direction under ConstraintLayout`() {
        val parent = DeclarativeConstraintLayout(RuntimeEnvironment.getApplication())

        val ltr = createParams(parent, UiLayoutDirection.Ltr)
        val rtl = createParams(parent, UiLayoutDirection.Rtl)

        assertEquals(11, ltr.leftMargin)
        assertEquals(13, ltr.rightMargin)
        assertEquals(13, rtl.leftMargin)
        assertEquals(11, rtl.rightMargin)
    }

    private fun createParams(
        parent: DeclarativeConstraintLayout,
        layoutDirection: UiLayoutDirection,
    ): ConstraintLayout.LayoutParams {
        val node = VNode(
            type = NodeType.Spacer,
            spec = EmptyNodeSpec,
            modifier = Modifier.marginRelative(start = 11.dp, end = 13.dp),
            environment = UiEnvironmentValues(layoutDirection = layoutDirection),
        )
        return ViewLayoutParamsFactory.createLayoutParams(
            parent = parent,
            node = node,
            warningTag = "RelativeMarginTest",
            emittedModifierWarnings = mutableSetOf(),
        ) as ConstraintLayout.LayoutParams
    }
}
