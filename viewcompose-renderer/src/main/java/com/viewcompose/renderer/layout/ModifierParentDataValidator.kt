package com.viewcompose.renderer.layout

import android.view.ViewGroup
import android.widget.LinearLayout
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.LayoutIdModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout

/**
 * 支持 parent-data modifier 的父容器分类。
 * Parent container categories that support parent-data modifiers.
 */
internal enum class ParentDataHost {
    Row,
    Column,
    Box,
    ConstraintLayout,
    Other,
}

/**
 * 校验 parent-data modifier 是否放在正确父容器下。
 * Validates whether parent-data modifiers are placed under the correct parent container.
 */
internal object ModifierParentDataValidator {
    /**
     * 根据实际 Android parent 推导父容器类型并执行校验。
     * Infers parent container type from the Android parent and validates modifiers.
     */
    fun validate(
        parent: ViewGroup,
        node: VNode,
    ): List<String> {
        return validate(
            parent = when {
                parent is DeclarativeBoxLayout -> ParentDataHost.Box
                parent is DeclarativeLinearLayout && parent.orientation == LinearLayout.HORIZONTAL -> {
                    ParentDataHost.Row
                }

                parent is DeclarativeLinearLayout -> {
                    ParentDataHost.Column
                }

                parent is DeclarativeConstraintLayout -> {
                    ParentDataHost.ConstraintLayout
                }

                else -> ParentDataHost.Other
            },
            node = node,
        )
    }

    /**
     * 在已知父容器分类下校验 VNode modifier。
     * Validates VNode modifiers under a known parent container category.
     */
    fun validate(
        parent: ParentDataHost,
        node: VNode,
    ): List<String> {
        val warnings = mutableListOf<String>()
        val hasWeight = node.modifier.elements.any { it is WeightModifierElement }
        val hasBoxAlign = node.modifier.elements.any { it is BoxAlignModifierElement }
        val hasHorizontalAlign = node.modifier.elements.any { it is HorizontalAlignModifierElement }
        val hasVerticalAlign = node.modifier.elements.any { it is VerticalAlignModifierElement }
        val hasLayoutId = node.modifier.elements.any { it is LayoutIdModifierElement }
        val hasConstraint = node.modifier.elements.any { it is ConstraintModifierElement }

        if (hasWeight && parent !in setOf(ParentDataHost.Row, ParentDataHost.Column)) {
            warnings += "Modifier.weight() only applies to children of Row/Column."
        }
        if (hasBoxAlign && parent != ParentDataHost.Box) {
            warnings += "Modifier.align(BoxAlignment) only applies to children of Box/Surface."
        }
        if (hasHorizontalAlign) {
            if (parent != ParentDataHost.Column) {
                warnings += "Modifier.align(HorizontalAlignment) only applies to children of Column."
            }
        }
        if (hasVerticalAlign) {
            if (parent != ParentDataHost.Row) {
                warnings += "Modifier.align(VerticalAlignment) only applies to children of Row."
            }
        }
        if ((hasLayoutId || hasConstraint) && parent != ParentDataHost.ConstraintLayout) {
            warnings += "ConstraintLayout parent-data modifiers only apply to children of ConstraintLayout."
        }
        return warnings
    }
}
