package com.viewcompose.ui.node.spec

/**
 * Immutable renderer properties for an integer slider.
 *
 * @property min inclusive lower bound
 * @property max inclusive upper bound
 * @property value externally controlled current value
 * @property enabled whether the slider accepts input
 * @property thumbColor draggable thumb color
 * @property trackColor active slider track color
 * @property onValueChange callback receiving an accepted value
 * @property inactiveTrackColor track color after the current value; defaults to [trackColor] for
 * renderers and direct constructors that do not distinguish the two track segments
 */
data class SliderNodeProps(
    val min: Int,
    val max: Int,
    val value: Int,
    val enabled: Boolean,
    val thumbColor: Int,
    val trackColor: Int,
    val onValueChange: ((Int) -> Unit)?,
    val inactiveTrackColor: Int = trackColor,
) : NodeSpec
