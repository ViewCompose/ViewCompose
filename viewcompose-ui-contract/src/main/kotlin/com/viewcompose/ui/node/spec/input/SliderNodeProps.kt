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
 * @property step positive interval between accepted values; the range and current value must align
 * @property onValueChangeStarted callback invoked before a user touch, key, or accessibility interaction
 * @property onValueChangeFinished callback invoked after that interaction completes
 * @throws IllegalArgumentException when the range, step, or value cannot form an exact native step index
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
    val step: Int = 1,
    val onValueChangeStarted: (() -> Unit)? = null,
    val onValueChangeFinished: (() -> Unit)? = null,
) : NodeSpec {
    init {
        require(step > 0) { "Slider step must be positive." }
        require(max >= min) { "Slider max must not be smaller than min." }
        require(value in min..max) { "Slider value must be inside the inclusive range." }
        val range = max.toLong() - min.toLong()
        require(range % step.toLong() == 0L) { "Slider range must be exactly divisible by step." }
        require((value.toLong() - min.toLong()) % step.toLong() == 0L) {
            "Slider value must align to min plus a whole number of steps."
        }
        require(range / step.toLong() <= Int.MAX_VALUE) {
            "Slider step count must fit the Android progress range."
        }
    }
}
