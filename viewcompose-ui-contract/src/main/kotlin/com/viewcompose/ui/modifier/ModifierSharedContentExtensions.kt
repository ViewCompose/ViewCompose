package com.viewcompose.ui.modifier

import com.viewcompose.ui.shared.SharedContentKey
import com.viewcompose.ui.shared.SharedContentMode

/**
 * Publishes one endpoint of element-style visual continuity under [key].
 *
 * A shared-aware host pairs exactly one outgoing and one incoming endpoint with the same key and
 * mode. Missing, duplicate, or mismatched endpoints fall back to the host's ordinary transition.
 * Outside a shared-aware host this marker has no visual effect. Later shared-content elements on
 * the same chain replace earlier ones.
 *
 * @sample com.viewcompose.ui.samples.sharedContentModifierSample
 * @receiver modifier chain to extend
 * @param key destination-pair-local identity for this endpoint
 * @return a new modifier chain
 */
fun Modifier.sharedElement(key: SharedContentKey): Modifier {
    return then(
        SharedContentModifierElement(
            key = key,
            mode = SharedContentMode.Element,
        ),
    )
}

/**
 * Publishes one endpoint of bounds-and-crossfade visual continuity under [key].
 *
 * A shared-aware host pairs exactly one outgoing and one incoming endpoint with the same key and
 * mode. Missing, duplicate, or mismatched endpoints fall back to the host's ordinary transition.
 * Outside a shared-aware host this marker has no visual effect. Later shared-content elements on
 * the same chain replace earlier ones.
 *
 * @sample com.viewcompose.ui.samples.sharedContentModifierSample
 * @receiver modifier chain to extend
 * @param key destination-pair-local identity for this endpoint
 * @return a new modifier chain
 */
fun Modifier.sharedBounds(key: SharedContentKey): Modifier {
    return then(
        SharedContentModifierElement(
            key = key,
            mode = SharedContentMode.Bounds,
        ),
    )
}

/**
 * Renderer-neutral shared-content endpoint metadata.
 *
 * Applications should construct this element through [sharedElement] or [sharedBounds].
 *
 * @property key destination-pair-local shared identity
 * @property mode requested snapshot continuity policy
 */
data class SharedContentModifierElement(
    val key: SharedContentKey,
    val mode: SharedContentMode,
) : ModifierElement
