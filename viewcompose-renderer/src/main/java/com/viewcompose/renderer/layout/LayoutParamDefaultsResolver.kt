package com.viewcompose.renderer.layout

import android.view.ViewGroup
import android.widget.LinearLayout
import com.viewcompose.ui.node.NodeType

/**
 * Selects default LayoutParams dimensions for nodes without explicit size modifiers.
 * Chooses default LayoutParams sizes for nodes without explicit size modifiers.
 */
internal object LayoutParamDefaultsResolver {
    /**
     * Resolves the default width.
     * Resolves default width.
     */
    fun defaultWidth(
        nodeType: NodeType,
        useLinearLikeDefaults: Boolean,
        linearOrientation: Int? = null,
    ): Int {
        if (useLinearLikeDefaults) {
            return when (nodeType) {
                NodeType.Spacer -> 0
                NodeType.Divider -> if (linearOrientation == LinearLayout.VERTICAL) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }

                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        return when (nodeType) {
            NodeType.Text,
            NodeType.TextField,
            NodeType.Checkbox,
            NodeType.Switch,
            NodeType.RadioButton,
            NodeType.CircularProgressIndicator,
            NodeType.Button,
            NodeType.IconButton,
            NodeType.Image,
            NodeType.SegmentedControl,
            -> ViewGroup.LayoutParams.WRAP_CONTENT

            NodeType.Spacer -> 0
            NodeType.Divider -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    /**
     * Resolves the default height.
     * Resolves default height.
     */
    fun defaultHeight(
        nodeType: NodeType,
        useLinearLikeDefaults: Boolean,
        linearOrientation: Int? = null,
    ): Int {
        if (useLinearLikeDefaults) {
            return when (nodeType) {
                NodeType.Spacer -> 0
                NodeType.Divider -> if (linearOrientation == LinearLayout.HORIZONTAL) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }

                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        return when (nodeType) {
            NodeType.Spacer -> 0
            NodeType.Divider -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }
}
