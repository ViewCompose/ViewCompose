package com.viewcompose.widget.constraintlayout

/*
 * 测试职责：覆盖 constraintlayout widget 中的 Constraint Layout Dsl 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Constraint Layout Dsl behavior in constraintlayout widget and guards the contract against regressions.
 */

import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.LayoutIdModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintFlowOrientation
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintGuidelineDirection
import com.viewcompose.ui.node.spec.ConstraintGuidelinePosition
import com.viewcompose.ui.node.spec.ConstraintHelperVisibility
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.buildVNodeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintLayoutDslTest {
    @Test
    fun `constraint layout emits node props and helper metadata`() {
        val tree = buildVNodeTree {
            ConstraintLayout {
                val (title, subtitle) = createRefs("title", "subtitle")
                val topGuide = createGuidelineFromTop(fraction = 0.2f, id = "guide-top")
                createHorizontalChain(
                    title,
                    subtitle,
                    style = ConstraintChainStyle.Packed,
                    bias = 0.35f,
                )
                Text(
                    text = "Title",
                    modifier = Modifier.constrainAs(title) {
                        topToTop(topGuide)
                        startToStart(parent)
                    },
                )
                Text(
                    text = "Subtitle",
                    modifier = Modifier.constrain("subtitle") {
                        topToBottom(title, margin = 8.dp)
                        startToStart(parent)
                    },
                )
            }
        }

        val node = tree.single()
        assertEquals(NodeType.ConstraintLayout, node.type)
        val spec = node.spec as ConstraintLayoutNodeProps
        assertEquals(1, spec.helpers.guidelines.size)
        assertEquals(ConstraintGuidelineDirection.FromTop, spec.helpers.guidelines.single().direction)
        assertTrue(spec.helpers.guidelines.single().position is ConstraintGuidelinePosition.Fraction)
        assertEquals(1, spec.helpers.chains.size)
        assertEquals(ConstraintChainOrientation.Horizontal, spec.helpers.chains.single().orientation)
        assertEquals(2, node.children.size)
        val firstChildConstraint = node.children[0].modifier.elements.filterIsInstance<ConstraintModifierElement>().single()
        assertEquals("title", firstChildConstraint.referenceId)
    }

    @Test
    fun `constrain shortcut keeps layoutId and constraint metadata aligned`() {
        val ref = ConstraintReference("hero")
        val byRef = Modifier.constrainAs(ref) {
            startToStart(parent)
            endToEnd(parent)
        }
        val byId = Modifier.constrain("hero") {
            startToStart(parent)
            endToEnd(parent)
        }

        val byRefLayoutId = byRef.elements.filterIsInstance<LayoutIdModifierElement>().single()
        val byIdLayoutId = byId.elements.filterIsInstance<LayoutIdModifierElement>().single()
        val byRefConstraint = byRef.elements.filterIsInstance<ConstraintModifierElement>().single()
        val byIdConstraint = byId.elements.filterIsInstance<ConstraintModifierElement>().single()

        assertEquals("hero", byRefLayoutId.layoutId)
        assertEquals("hero", byIdLayoutId.layoutId)
        assertEquals(byRefConstraint.constraint, byIdConstraint.constraint)
        assertEquals("hero", byRefConstraint.referenceId)
        assertEquals("hero", byIdConstraint.referenceId)
    }

    @Test
    fun `decoupled constraintSet collects constraints and helpers`() {
        val set: ConstraintSetSpec = constraintSet {
            val hero = createRef("hero")
            val details = createRef("details")
            val topGuide = createGuidelineFromTop(0.3f, id = "guide")
            createVerticalChain(
                hero,
                details,
                weights = listOf(1f, 2f),
                style = ConstraintChainStyle.SpreadInside,
            )
            constrain("hero") {
                topToTop(topGuide)
                startToStart(parent)
            }
            constrain("details") {
                topToBottom(hero, margin = 12.dp)
                startToStart(parent)
            }
        }

        assertEquals(2, set.constraints.size)
        assertEquals(1, set.helpers.guidelines.size)
        assertEquals(1, set.helpers.chains.size)
        assertEquals("guide", set.helpers.guidelines.single().id)
        assertEquals(listOf(1f, 2f), set.helpers.chains.single().weights)
    }

    @Test
    fun `constraint layout emits virtual helper metadata`() {
        val tree = buildVNodeTree {
            ConstraintLayout {
                val (a, b, c) = createRefs("a", "b", "c")
                createFlow(
                    a,
                    b,
                    c,
                    id = "flow-main",
                    orientation = ConstraintFlowOrientation.Vertical,
                    wrapMode = ConstraintFlowWrapMode.Chain,
                    horizontalGap = 6.dp,
                    verticalGap = 8.dp,
                    maxElementsWrap = 2,
                )
                createGroup(
                    a,
                    b,
                    id = "group-main",
                    visibility = ConstraintHelperVisibility.Gone,
                )
                createLayer(
                    a,
                    b,
                    id = "layer-main",
                    rotation = 12f,
                    translationX = 14.dp,
                )
                createPlaceholder(
                    content = c,
                    id = "placeholder-main",
                    emptyVisibility = ConstraintHelperVisibility.Invisible,
                )
                Text(text = "A", modifier = Modifier.constrainAs(a) { startToStart(parent) })
                Text(text = "B", modifier = Modifier.constrainAs(b) { topToBottom(a) })
                Text(text = "C", modifier = Modifier.constrainAs(c) { topToBottom(b) })
            }
        }

        val spec = tree.single().spec as ConstraintLayoutNodeProps
        assertEquals(1, spec.helpers.flows.size)
        assertEquals("flow-main", spec.helpers.flows.single().id)
        assertEquals(ConstraintFlowOrientation.Vertical, spec.helpers.flows.single().orientation)
        assertEquals(ConstraintFlowWrapMode.Chain, spec.helpers.flows.single().wrapMode)
        assertEquals(1, spec.helpers.groups.size)
        assertEquals(ConstraintHelperVisibility.Gone, spec.helpers.groups.single().visibility)
        assertEquals(1, spec.helpers.layers.size)
        assertEquals(12f, spec.helpers.layers.single().rotation)
        assertEquals(1, spec.helpers.placeholders.size)
        assertEquals("c", spec.helpers.placeholders.single().contentId)
    }

    @Test
    fun `constraintSet collects virtual helper metadata`() {
        val set = constraintSet {
            val (a, b) = createRefs("a", "b")
            createFlow(a, b, id = "flow-set")
            createGroup(a, b, id = "group-set")
            createLayer(a, b, id = "layer-set", scaleX = 1.2f, scaleY = 0.8f)
            createPlaceholder(content = b, id = "placeholder-set")
            constrain("a") {
                startToStart(parent)
                topToTop(parent)
            }
        }

        assertEquals(listOf("flow-set"), set.helpers.flows.map { it.id })
        assertEquals(listOf("group-set"), set.helpers.groups.map { it.id })
        assertEquals(listOf("layer-set"), set.helpers.layers.map { it.id })
        assertEquals(listOf("placeholder-set"), set.helpers.placeholders.map { it.id })
    }

    @Test
    fun `constrain scope emits advanced dimensions baseline and circle fields`() {
        val spec = Modifier.constrain("hero") {
            startToStart(parent, margin = 8.dp)
            endToEnd(parent, margin = 8.dp)
            topToTop(parent)
            width = com.viewcompose.ui.node.spec.ConstraintDimension.FillToConstraints
            height = com.viewcompose.ui.node.spec.ConstraintDimension.FillToConstraints
            widthMin = 120.dp
            widthMax = 360.dp
            widthPercent = 0.6f
            heightMin = 80.dp
            heightMax = 400.dp
            heightPercent = 0.5f
            constrainedWidth = true
            constrainedHeight = true
            baselineToTop(parent, margin = 6.dp)
            val target = ConstraintReference("avatar")
            circular(target = target, radius = 72.dp, angle = 45f)
        }.elements.filterIsInstance<ConstraintModifierElement>().single().constraint

        assertEquals(120.dp, spec.widthMin)
        assertEquals(360.dp, spec.widthMax)
        assertEquals(0.6f, spec.widthPercent)
        assertEquals(80.dp, spec.heightMin)
        assertEquals(400.dp, spec.heightMax)
        assertEquals(0.5f, spec.heightPercent)
        assertEquals(true, spec.constrainedWidth)
        assertEquals(true, spec.constrainedHeight)
        assertEquals(
            com.viewcompose.ui.node.spec.ConstraintAnchor.Top,
            spec.baselineToTop?.target?.anchor,
        )
        assertEquals("avatar", spec.circle?.targetId)
    }

    @Test
    fun `create chain fails fast when weights size mismatches refs`() {
        try {
            constraintSet {
                val (a, b) = createRefs("a", "b")
                createHorizontalChain(a, b, weights = listOf(1f))
            }
            throw AssertionError("Expected IllegalArgumentException for mismatched chain weights size")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("weights size") == true)
        }
    }

    @Test
    fun `helper apis fail fast outside constraint layout scope`() {
        val builder = UiTreeBuilder()
        try {
            builder.createGuidelineFromTop(10.dp)
            throw AssertionError("Expected an IllegalArgumentException for out-of-scope helper API")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("ConstraintLayout helper APIs") == true)
        }
    }
}
