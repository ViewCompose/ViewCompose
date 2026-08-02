package com.viewcompose.ui.modifier

/** Maps a node to a common control role consumed by accessibility and testing bridges. */
enum class SemanticsRole {
    Button,
    Checkbox,
    Switch,
    RadioButton,
    Image,
    Tab,
}

/**
 * Selects no announcement, polite queued announcement, or assertive interruption for live updates.
 */
enum class SemanticsLiveRegion {
    None,
    Polite,
    Assertive,
}

/**
 * Describes accessible progress within an inclusive finite or floating-point range.
 *
 * @property current current value inside `[start, endInclusive]`
 * @property start inclusive lower bound
 * @property endInclusive inclusive upper bound
 * @property steps non-negative number of discrete intermediate steps; `0` represents continuous progress
 * @throws IllegalArgumentException if bounds are reversed, current is outside the range, or steps is negative
 */
data class SemanticsProgressRange(
    val current: Float,
    val start: Float,
    val endInclusive: Float,
    val steps: Int = 0,
) {
    init {
        require(start <= endInclusive) {
            "Semantics progress range start must not exceed endInclusive."
        }
        require(current in start..endInclusive) {
            "Semantics progress current value must be inside the declared range."
        }
        require(steps >= 0) {
            "Semantics progress steps must be non-negative."
        }
    }
}

/**
 * Stores optional accessibility, testing, and semantic state for one node.
 *
 * `null` means unspecified and allows an earlier modifier, component default, or native bridge to
 * supply the value. [merge] applies later non-null values over earlier values.
 *
 * @property contentDescription localized description of non-text visual content
 * @property stateDescription localized description of current control state
 * @property paneTitle localized title announced when a pane appears
 * @property error localized validation error associated with the node
 * @property clickLabel localized label describing the click action
 * @property role semantic control role
 * @property liveRegion announcement policy for dynamic content
 * @property progressRange current progress and bounds
 * @property heading whether the node is an accessibility heading
 * @property selected whether a selectable node is selected
 * @property checked whether a checkable node is checked
 * @property enabled whether semantic actions are enabled
 * @property mergeDescendants whether descendant semantics are merged into this node
 * @property hidden whether the node is hidden from the accessibility tree
 */
data class SemanticsConfiguration(
    val contentDescription: String? = null,
    val stateDescription: String? = null,
    val paneTitle: String? = null,
    val error: String? = null,
    val clickLabel: String? = null,
    val role: SemanticsRole? = null,
    val liveRegion: SemanticsLiveRegion? = null,
    val progressRange: SemanticsProgressRange? = null,
    val heading: Boolean? = null,
    val selected: Boolean? = null,
    val checked: Boolean? = null,
    val enabled: Boolean? = null,
    val mergeDescendants: Boolean? = null,
    val hidden: Boolean? = null,
) {
    /** Whether every semantic property is unspecified. */
    val isEmpty: Boolean
        get() = this == Empty

    /**
     * Applies non-null values from [next] over this configuration.
     *
     * @param next later modifier configuration with higher precedence
     * @return a new merged configuration
     */
    fun merge(next: SemanticsConfiguration): SemanticsConfiguration {
        return SemanticsConfiguration(
            contentDescription = next.contentDescription ?: contentDescription,
            stateDescription = next.stateDescription ?: stateDescription,
            paneTitle = next.paneTitle ?: paneTitle,
            error = next.error ?: error,
            clickLabel = next.clickLabel ?: clickLabel,
            role = next.role ?: role,
            liveRegion = next.liveRegion ?: liveRegion,
            progressRange = next.progressRange ?: progressRange,
            heading = next.heading ?: heading,
            selected = next.selected ?: selected,
            checked = next.checked ?: checked,
            enabled = next.enabled ?: enabled,
            mergeDescendants = next.mergeDescendants ?: mergeDescendants,
            hidden = next.hidden ?: hidden,
        )
    }

    /** Provides common semantic configurations. */
    companion object {
        /** Configuration with every property unspecified. */
        val Empty = SemanticsConfiguration()
    }
}

/**
 * Collects mutable assignments inside the [semantics] DSL.
 *
 * This receiver is temporary and should not be retained. Every property mirrors
 * [SemanticsConfiguration]; leaving it `null` preserves earlier or platform-provided behavior.
 */
class SemanticsPropertyReceiver {
    /** Localized description of non-text visual content. */
    var contentDescription: String? = null

    /** Localized description of current control state. */
    var stateDescription: String? = null

    /** Localized title announced when a pane appears. */
    var paneTitle: String? = null

    /** Localized validation error associated with the node. */
    var error: String? = null

    /** Localized label describing the click action. */
    var clickLabel: String? = null

    /** Semantic control role, or `null` to preserve existing role resolution. */
    var role: SemanticsRole? = null

    /** Announcement policy for dynamic content. */
    var liveRegion: SemanticsLiveRegion? = null

    /** Current accessible progress and range. */
    var progressRange: SemanticsProgressRange? = null

    /** Whether the node is an accessibility heading. */
    var heading: Boolean? = null

    /** Whether a selectable node is selected. */
    var selected: Boolean? = null

    /** Whether a checkable node is checked. */
    var checked: Boolean? = null

    /** Whether semantic actions are enabled. */
    var enabled: Boolean? = null

    /** Whether descendant semantics are merged into this node. */
    var mergeDescendants: Boolean? = null

    /** Whether the node is hidden from the accessibility tree. */
    var hidden: Boolean? = null

    /** Marks semantic actions disabled by setting [enabled] to `false`. */
    fun disabled() {
        enabled = false
    }

    internal fun build(): SemanticsConfiguration {
        return SemanticsConfiguration(
            contentDescription = contentDescription,
            stateDescription = stateDescription,
            paneTitle = paneTitle,
            error = error,
            clickLabel = clickLabel,
            role = role,
            liveRegion = liveRegion,
            progressRange = progressRange,
            heading = heading,
            selected = selected,
            checked = checked,
            enabled = enabled,
            mergeDescendants = mergeDescendants,
            hidden = hidden,
        )
    }
}

/**
 * Appends accessibility and testing properties collected by [properties].
 *
 * The DSL block executes immediately while constructing an immutable configuration. Semantics
 * elements merge in chain order, with later non-null properties taking precedence. Passing
 * [mergeDescendants] as `false` leaves an earlier merge policy unchanged; `true` explicitly enables it.
 *
 * @receiver modifier chain to extend
 * @param mergeDescendants whether this element explicitly enables descendant merging
 * @param properties semantic assignments to collect
 * @return a new modifier chain
 */
fun Modifier.semantics(
    mergeDescendants: Boolean = false,
    properties: SemanticsPropertyReceiver.() -> Unit,
): Modifier {
    val receiver = SemanticsPropertyReceiver().apply(properties)
    if (mergeDescendants) {
        receiver.mergeDescendants = true
    }
    return then(
        SemanticsModifierElement(receiver.build()),
    )
}
