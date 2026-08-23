package com.viewcompose.renderer.view.tree

import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.viewcompose.renderer.view.container.DeclarativeAnimatedBoundsHostLayout
import com.viewcompose.text.TextDocument
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnimatedBoundsParentIntegrationTest {
    @Test
    fun `row column box and constraint parents publish one real retargeted rectangle`() {
        val cases = listOf(
            ParentCase("Row", ::rowStart, ::rowEnd),
            ParentCase("Column", ::columnStart, ::columnEnd),
            ParentCase("Box", ::boxStart, ::boxEnd),
            ParentCase("ConstraintLayout", ::constraintStart, ::constraintEnd),
        )

        cases.forEach { case ->
            assertParentRetargets(case)
        }
    }

    @Test
    fun `constraint logical anchors resolve before rtl bounds animation`() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val root = FrameLayout(context)
        val first = ViewTreeRenderer.renderInto(
            container = root,
            previous = emptyList(),
            nodes = listOf(constraintStart(UiLayoutDirection.Rtl)),
        )
        root.measureAndLayout()
        val host = root.requireBoundsHost()
        val start = host.currentBoundsForTest()

        ViewTreeRenderer.renderInto(
            container = root,
            previous = first.mountedNodes,
            nodes = listOf(constraintEnd(UiLayoutDirection.Rtl)),
        )
        root.measureAndLayout()

        val target = requireNotNull(host.targetBoundsForTest())
        assertTrue(
            "RTL start must resolve to the physical right: start=$start target=$target",
            start.left > target.left,
        )
        host.animatorForTest()!!.end()
        assertEquals(target, host.currentBoundsForTest())
    }

    @Test
    fun `density and font scale changes rebind content and retarget physical bounds`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val first = ViewTreeRenderer.renderInto(
            container = root,
            previous = emptyList(),
            nodes = listOf(environmentTextParent(density = 1f, fontScale = 1f)),
        )
        root.measureAndLayout()
        val host = root.requireBoundsHost()
        val start = host.currentBoundsForTest()
        val text = host.getChildAt(0) as TextView
        val startTextSize = text.textSize

        ViewTreeRenderer.renderInto(
            container = root,
            previous = first.mountedNodes,
            nodes = listOf(environmentTextParent(density = 1.5f, fontScale = 1.4f)),
        )
        root.measureAndLayout()

        val target = requireNotNull(host.targetBoundsForTest())
        assertTrue("Density must enlarge the physical target width: $start -> $target", target.width() > start.width())
        assertTrue("Font scale must rebind the native text size", text.textSize > startTextSize)
        assertEquals(start, host.currentBoundsForTest())
        host.animatorForTest()!!.end()
        assertEquals(target, host.currentBoundsForTest())
    }

    private fun assertParentRetargets(case: ParentCase) {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val first = ViewTreeRenderer.renderInto(
            container = root,
            previous = emptyList(),
            nodes = listOf(case.start()),
        )
        root.measureAndLayout()
        val host = root.requireBoundsHost()
        val start = host.currentBoundsForTest()

        ViewTreeRenderer.renderInto(
            container = root,
            previous = first.mountedNodes,
            nodes = listOf(case.end()),
        )
        root.measureAndLayout()

        assertSame("${case.name} must reuse the committed owner", host, root.requireBoundsHost())
        val target = requireNotNull(host.targetBoundsForTest())
        assertNotEquals("${case.name} must change real bounds", start, target)
        assertEquals("${case.name} must retain the sampled start before the first frame", start, host.currentBoundsForTest())
        val animator = requireNotNull(host.animatorForTest())
        animator.currentPlayTime = 500L
        val midpoint = host.currentBoundsForTest()
        assertNotEquals("${case.name} midpoint must leave the start", start, midpoint)
        assertNotEquals("${case.name} midpoint must precede the target", target, midpoint)
        val hitRect = Rect()
        host.getHitRect(hitRect)
        assertEquals(midpoint, hitRect)
        animator.end()
        assertEquals(target, host.currentBoundsForTest())
    }

    private fun rowStart(): VNode = linearParent(
        type = NodeType.Row,
        children = listOf(spacer(width = 20, height = 40), animatedTarget()),
    )

    private fun rowEnd(): VNode = linearParent(
        type = NodeType.Row,
        children = listOf(spacer(width = 120, height = 40), animatedTarget(width = 140, height = 64)),
    )

    private fun columnStart(): VNode = linearParent(
        type = NodeType.Column,
        children = listOf(spacer(width = 40, height = 20), animatedTarget()),
    )

    private fun columnEnd(): VNode = linearParent(
        type = NodeType.Column,
        children = listOf(spacer(width = 40, height = 100), animatedTarget(width = 140, height = 64)),
    )

    private fun boxStart(): VNode = boxParent(
        animatedTarget(
            extra = BoxAlignModifierElement(BoxAlignment.TopStart),
        ),
    )

    private fun boxEnd(): VNode = boxParent(
        animatedTarget(
            width = 140,
            height = 64,
            extra = BoxAlignModifierElement(BoxAlignment.BottomEnd),
        ),
    )

    private fun constraintStart(
        direction: UiLayoutDirection = UiLayoutDirection.Ltr,
    ): VNode = constraintParent(
        direction = direction,
        target = constrainedTarget(
            width = 80,
            height = 40,
            horizontalAnchor = ConstraintAnchor.Start,
            verticalAnchor = ConstraintAnchor.Top,
        ),
    )

    private fun constraintEnd(
        direction: UiLayoutDirection = UiLayoutDirection.Ltr,
    ): VNode = constraintParent(
        direction = direction,
        target = constrainedTarget(
            width = 140,
            height = 64,
            horizontalAnchor = ConstraintAnchor.End,
            verticalAnchor = ConstraintAnchor.Bottom,
        ),
    )

    private fun linearParent(type: NodeType, children: List<VNode>): VNode = VNode(
        type = type,
        key = "parent",
        spec = when (type) {
            NodeType.Row -> RowNodeProps(
                spacing = 0.dp,
                arrangement = MainAxisArrangement.Start,
                verticalAlignment = VerticalAlignment.Top,
            )

            NodeType.Column -> ColumnNodeProps(
                spacing = 0.dp,
                arrangement = MainAxisArrangement.Start,
                horizontalAlignment = HorizontalAlignment.Start,
            )

            else -> error("Unsupported linear parent: $type")
        },
        modifier = parentModifier(),
        children = children,
        environment = environment(),
    )

    private fun boxParent(target: VNode): VNode = VNode(
        type = NodeType.Box,
        key = "parent",
        spec = BoxNodeProps(contentAlignment = BoxAlignment.TopStart),
        modifier = parentModifier(),
        children = listOf(target),
        environment = environment(),
    )

    private fun environmentTextParent(density: Float, fontScale: Float): VNode {
        val environment = environment(density = density, fontScale = fontScale)
        return VNode(
            type = NodeType.Box,
            key = "environment-parent",
            spec = BoxNodeProps(contentAlignment = BoxAlignment.TopStart),
            modifier = parentModifier(),
            children = listOf(
                VNode(
                    type = NodeType.Text,
                    key = "environment-target",
                    spec = TextNodeProps(
                        document = TextDocument.plain("Environment scaling"),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Start,
                        textColor = 0xFF000000.toInt(),
                        textSizeSp = 20.sp,
                    ),
                    modifier = Modifier
                        .width(120.dp)
                        .then(AnimateBoundsModifierElement(Timing)),
                    environment = environment,
                ),
            ),
            environment = environment,
        )
    }

    private fun constraintParent(direction: UiLayoutDirection, target: VNode): VNode = VNode(
        type = NodeType.ConstraintLayout,
        key = "parent",
        spec = ConstraintLayoutNodeProps(),
        modifier = parentModifier(),
        children = listOf(target.copy(environment = environment(direction))),
        environment = environment(direction),
    )

    private fun constrainedTarget(
        width: Int,
        height: Int,
        horizontalAnchor: ConstraintAnchor,
        verticalAnchor: ConstraintAnchor,
    ): VNode {
        val horizontalLink = ConstraintAnchorLink(ConstraintAnchorTarget.parent(horizontalAnchor))
        val verticalLink = ConstraintAnchorLink(ConstraintAnchorTarget.parent(verticalAnchor))
        val constraint = ConstraintItemSpec(
            start = horizontalLink.takeIf { horizontalAnchor == ConstraintAnchor.Start },
            end = horizontalLink.takeIf { horizontalAnchor == ConstraintAnchor.End },
            top = verticalLink.takeIf { verticalAnchor == ConstraintAnchor.Top },
            bottom = verticalLink.takeIf { verticalAnchor == ConstraintAnchor.Bottom },
            width = ConstraintDimension.Fixed(width.dp),
            height = ConstraintDimension.Fixed(height.dp),
        )
        return textNode(
            key = "target",
            modifier = Modifier
                .layoutId("target")
                .then(ConstraintModifierElement(constraint = constraint, referenceId = "target"))
                .then(AnimateBoundsModifierElement(Timing)),
        )
    }

    private fun spacer(width: Int, height: Int): VNode = textNode(
        key = "spacer",
        modifier = Modifier.width(width.dp).height(height.dp),
    )

    private fun animatedTarget(
        width: Int = 80,
        height: Int = 40,
        extra: com.viewcompose.ui.modifier.ModifierElement? = null,
    ): VNode {
        var modifier = Modifier
            .width(width.dp)
            .height(height.dp)
        if (extra != null) modifier = modifier.then(extra)
        modifier = modifier.then(AnimateBoundsModifierElement(Timing))
        return textNode(key = "target", modifier = modifier)
    }

    private fun textNode(key: Any, modifier: Modifier): VNode = VNode(
        type = NodeType.Text,
        key = key,
        spec = TextNodeProps(
            document = TextDocument.plain(key.toString()),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Start,
            textColor = 0xFF000000.toInt(),
            textSizeSp = 14.sp,
        ),
        modifier = modifier,
        environment = environment(),
    )

    private fun parentModifier(): Modifier = Modifier.width(300.dp).height(240.dp)

    private fun environment(
        direction: UiLayoutDirection = UiLayoutDirection.Ltr,
        density: Float = 1f,
        fontScale: Float = 1f,
    ): UiEnvironmentValues = UiEnvironmentValues.Default.copy(
        density = UiDensity(density = density, fontScale = fontScale),
        layoutDirection = direction,
    )

    private fun FrameLayout.measureAndLayout() {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        measure(widthSpec, heightSpec)
        layout(0, 0, 400, 400)
    }

    private fun View.requireBoundsHost(): DeclarativeAnimatedBoundsHostLayout {
        if (this is DeclarativeAnimatedBoundsHostLayout) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val match = runCatching { getChildAt(index).requireBoundsHost() }.getOrNull()
                if (match != null) return match
            }
        }
        error("Animated bounds host not found")
    }

    private data class ParentCase(
        val name: String,
        val start: () -> VNode,
        val end: () -> VNode,
    )

    private companion object {
        val Timing = ContentSizeTweenSpecModel(
            durationMillis = 1_000,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
    }
}
