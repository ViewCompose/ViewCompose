package com.viewcompose.renderer.view.container

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.viewcompose.renderer.R
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeclarativeConstraintLayoutEnvironmentTest {
    @Test
    fun `constraint dimensions resolve again when local density changes`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context)
        val child = View(context).apply {
            setTag(R.id.viewcompose_constraint_layout_id, "child")
        }
        layout.addView(child)
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "child" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(20.dp),
                    height = ConstraintDimension.Fixed(10.dp),
                    start = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Start),
                        margin = 4.dp,
                    ),
                    top = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Top),
                    ),
                ),
            ),
        )

        layout.installEnvironment(density = 2f)
        layout.applyConstraintsNow()
        assertResolvedLayoutParams(child, width = 40, height = 20, startMargin = 8)

        layout.installEnvironment(density = 3f)
        layout.requestConstraintRebuild()
        layout.applyConstraintsNow()
        assertResolvedLayoutParams(child, width = 60, height = 30, startMargin = 12)
    }

    private fun DeclarativeConstraintLayout.installEnvironment(density: Float) {
        setTag(
            R.id.viewcompose_environment_values,
            UiEnvironmentValues.Default.copy(
                density = UiDensity(
                    density = density,
                    fontScale = 1f,
                ),
            ),
        )
    }

    private fun assertResolvedLayoutParams(
        child: View,
        width: Int,
        height: Int,
        startMargin: Int,
    ) {
        val params = child.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(width, params.width)
        assertEquals(height, params.height)
        assertEquals(startMargin, params.marginStart)
    }
}
