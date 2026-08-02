package com.viewcompose.ui.node.spec

/**
 * Immutable renderer properties for an integer slider.
 *
 * @property min inclusive lower bound
 * @property max inclusive upper bound
 * @property value externally controlled current value
 * @property enabled whether the slider accepts input
 * @property thumbColor draggable thumb color
 * @property trackColor slider track color
 * @property onValueChange callback receiving an accepted value
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
