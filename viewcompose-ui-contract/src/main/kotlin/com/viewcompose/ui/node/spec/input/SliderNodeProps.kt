package com.viewcompose.ui.node.spec

/**
 * Slider 节点的数值范围、步进和回调属性。
 * Value range, steps, and callback properties for a Slider node.
 */
data class SliderNodeProps(
    val min: Int,
    val max: Int,
    val value: Int,
    val enabled: Boolean,
    val thumbColor: Int,
    val trackColor: Int,
    val onValueChange: ((Int) -> Unit)?,
) : NodeSpec
