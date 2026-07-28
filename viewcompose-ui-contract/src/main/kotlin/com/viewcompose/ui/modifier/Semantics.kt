package com.viewcompose.ui.modifier

/**
 * renderer 和无障碍桥接使用的语义角色。
 * Semantic roles consumed by the renderer and accessibility bridge.
 */
enum class SemanticsRole {
    Button,
    Checkbox,
    Switch,
    RadioButton,
    Image,
    Tab,
}

enum class SemanticsLiveRegion {
    None,
    Polite,
    Assertive,
}

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
    val isEmpty: Boolean
        get() = this == Empty

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

    companion object {
        val Empty = SemanticsConfiguration()
    }
}

class SemanticsPropertyReceiver {
    var contentDescription: String? = null
    var stateDescription: String? = null
    var paneTitle: String? = null
    var error: String? = null
    var clickLabel: String? = null
    var role: SemanticsRole? = null
    var liveRegion: SemanticsLiveRegion? = null
    var progressRange: SemanticsProgressRange? = null
    var heading: Boolean? = null
    var selected: Boolean? = null
    var checked: Boolean? = null
    var enabled: Boolean? = null
    var mergeDescendants: Boolean? = null
    var hidden: Boolean? = null

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
