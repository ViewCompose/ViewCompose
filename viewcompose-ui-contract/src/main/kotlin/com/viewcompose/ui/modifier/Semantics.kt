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

/** Selects whether an accessibility collection allows no, one, or multiple selected items. */
enum class SemanticsCollectionSelectionMode {
    /** The collection does not expose item selection. */
    None,

    /** At most one item is selected. */
    Single,

    /** Multiple items may be selected. */
    Multiple,
}

/**
 * Describes the dimensions and selection policy of one accessibility collection.
 *
 * Attach this value to the semantic parent of a related list, tab row, navigation bar, segmented
 * control, or grid. Child nodes describe their positions with [SemanticsCollectionItemInfo]. Row
 * and column counts are logical counts and remain unchanged by right-to-left physical placement.
 * Renderers map the snapshot to their platform collection metadata without owning selection state.
 *
 * This is a Q3 immutable semantics contract.
 *
 * @sample com.viewcompose.ui.samples.collectionSemanticsSample
 * @property rowCount non-negative number of logical rows
 * @property columnCount non-negative number of logical columns
 * @property hierarchical whether items may contain nested collections
 * @property selectionMode supported selection cardinality for the complete collection
 * @throws IllegalArgumentException when either count is negative
 */
data class SemanticsCollectionInfo(
    val rowCount: Int,
    val columnCount: Int,
    val hierarchical: Boolean = false,
    val selectionMode: SemanticsCollectionSelectionMode = SemanticsCollectionSelectionMode.None,
) {
    init {
        require(rowCount >= 0) { "Semantics collection rowCount must be non-negative." }
        require(columnCount >= 0) { "Semantics collection columnCount must be non-negative." }
    }
}

/**
 * Describes one node's logical position and span inside an accessibility collection.
 *
 * Attach this value to a semantic child whose parent supplies [SemanticsCollectionInfo]. The
 * child's existing [SemanticsConfiguration.selected] and [SemanticsConfiguration.heading] values
 * provide its selection and heading metadata, so callers keep one source of truth. Indexes use
 * logical collection order and do not reverse for right-to-left physical placement.
 *
 * This is a Q3 immutable semantics contract.
 *
 * @sample com.viewcompose.ui.samples.collectionSemanticsSample
 * @property rowIndex zero-based logical row containing the item
 * @property columnIndex zero-based logical column containing the item
 * @property rowSpan positive number of rows occupied by the item
 * @property columnSpan positive number of columns occupied by the item
 * @throws IllegalArgumentException when an index is negative or a span is not positive
 */
data class SemanticsCollectionItemInfo(
    val rowIndex: Int,
    val columnIndex: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
) {
    init {
        require(rowIndex >= 0) { "Semantics collection item rowIndex must be non-negative." }
        require(rowSpan > 0) { "Semantics collection item rowSpan must be positive." }
        require(columnIndex >= 0) { "Semantics collection item columnIndex must be non-negative." }
        require(columnSpan > 0) { "Semantics collection item columnSpan must be positive." }
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
 * @property collectionInfo dimensions and selection policy when this node owns a collection
 * @property collectionItemInfo logical position and span when this node belongs to a collection
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
    val collectionInfo: SemanticsCollectionInfo? = null,
    val collectionItemInfo: SemanticsCollectionItemInfo? = null,
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
            collectionInfo = next.collectionInfo ?: collectionInfo,
            collectionItemInfo = next.collectionItemInfo ?: collectionItemInfo,
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

    /** Dimensions and selection policy when the modified node owns a collection. */
    var collectionInfo: SemanticsCollectionInfo? = null

    /** Logical position and span when the modified node belongs to a collection. */
    var collectionItemInfo: SemanticsCollectionItemInfo? = null

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
            collectionInfo = collectionInfo,
            collectionItemInfo = collectionItemInfo,
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
