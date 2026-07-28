package com.viewcompose.renderer.layout

/*
 * 测试职责：覆盖 renderer layout 中的 Modifier Parent Data Validator 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Parent Data Validator behavior in renderer layout and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.LayoutIdModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierParentDataValidatorTest {
    @Test
    fun `weight is valid inside linear layout`() {
        val warnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Row,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier.then(WeightModifierElement(1f)),
            ),
        )

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `weight warns outside linear layout`() {
        val warnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Box,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier.then(WeightModifierElement(1f)),
            ),
        )

        assertEquals(listOf("Modifier.weight() only applies to children of Row/Column."), warnings)
    }

    @Test
    fun `box align warns outside box layout`() {
        val warnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Row,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier.then(BoxAlignModifierElement(BoxAlignment.Center)),
            ),
        )

        assertEquals(listOf("Modifier.align(BoxAlignment) only applies to children of Box/Surface."), warnings)
    }

    @Test
    fun `row and column align only pass in matching orientation`() {
        val verticalWarnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Row,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier.then(VerticalAlignModifierElement(VerticalAlignment.Center)),
            ),
        )
        val horizontalWarnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Column,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier.then(HorizontalAlignModifierElement(HorizontalAlignment.Center)),
            ),
        )
        val mismatchedVerticalWarnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Column,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier.then(VerticalAlignModifierElement(VerticalAlignment.Center)),
            ),
        )

        assertTrue(verticalWarnings.isEmpty())
        assertTrue(horizontalWarnings.isEmpty())
        assertEquals(
            listOf("Modifier.align(VerticalAlignment) only applies to children of Row."),
            mismatchedVerticalWarnings,
        )
    }

    @Test
    fun `constraint parent data warns outside constraint layout`() {
        val warnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.Row,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier
                    .then(LayoutIdModifierElement("item-1"))
                    .then(
                        ConstraintModifierElement(
                            referenceId = "item-1",
                            constraint = ConstraintItemSpec(
                                start = ConstraintAnchorLink(
                                    target = ConstraintAnchorTarget.parent(ConstraintAnchor.Start),
                                ),
                            ),
                        ),
                    ),
            ),
        )

        assertEquals(
            listOf("ConstraintLayout parent-data modifiers only apply to children of ConstraintLayout."),
            warnings,
        )
    }

    @Test
    fun `constraint parent data is valid under constraint layout host`() {
        val warnings = ModifierParentDataValidator.validate(
            parent = ParentDataHost.ConstraintLayout,
            node = VNode(
                type = NodeType.Text,
                spec = EmptyNodeSpec,
                modifier = Modifier
                    .then(LayoutIdModifierElement("item-2"))
                    .then(
                        ConstraintModifierElement(
                            referenceId = "item-2",
                            constraint = ConstraintItemSpec(
                                top = ConstraintAnchorLink(
                                    target = ConstraintAnchorTarget.parent(ConstraintAnchor.Top),
                                ),
                            ),
                        ),
                    ),
            ),
        )

        assertTrue(warnings.isEmpty())
    }
}
