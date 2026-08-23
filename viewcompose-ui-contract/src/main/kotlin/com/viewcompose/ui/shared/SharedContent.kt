package com.viewcompose.ui.shared

/**
 * Type-safe identity used to pair shared content between two navigation destinations.
 *
 * Keys are scoped to one outgoing/incoming destination pair rather than a process or navigation
 * graph. Each endpoint must publish the key exactly once with the same [SharedContentMode].
 *
 * @sample com.viewcompose.ui.samples.sharedContentModifierSample
 * @property value non-blank stable value compared across the two destination trees
 */
@JvmInline
value class SharedContentKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Shared content keys must not be blank."
        }
    }
}

/** Defines which visual continuity policy a shared-content endpoint requests. */
enum class SharedContentMode {
    /** Moves one source snapshot to the destination bounds before revealing live target content. */
    Element,

    /** Crossfades source and target snapshots while their common bounds move. */
    Bounds,
}

/**
 * Keyed-tag slot shared by renderer and navigation-host artifacts for shared-content metadata.
 *
 * The hexadecimal value is part of the cross-module Android interoperability contract and must
 * remain stable for independently versioned renderer and navigation artifacts.
 */
const val SHARED_CONTENT_TAG_KEY: Int = 0x5643A002
