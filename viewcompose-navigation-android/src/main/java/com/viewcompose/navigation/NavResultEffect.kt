package com.viewcompose.navigation

import androidx.lifecycle.Lifecycle
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.currentStateAsState
import com.viewcompose.navigation.core.NavResultKey
import com.viewcompose.ui.foundation.SideEffect

/**
 * Consumes the oldest pending [key] result after the nearest destination reaches `RESUMED`.
 *
 * This effect must run while declaring [NavHost] destination content. It uses the same nearest
 * [LocalLifecycleOwner] as every other Lifecycle DSL API and schedules no observer or recomposition
 * outside that owner. A pending value is removed only in a successful frame side effect, before
 * [onResult] is invoked; the callback is therefore at-most-once even when it throws. Use
 * [NavResultInbox.peek] and [NavResultInbox.consume] for retry or explicit acknowledgement policy.
 *
 * Delivery during transition leaves the incoming destination at `STARTED`; the lifecycle state
 * read here invalidates the destination when terminal settlement reaches `RESUMED`. Key and codec
 * mismatches fail before a side effect is scheduled and preserve the payload.
 *
 * @param key stable typed result key to consume
 * @param onResult synchronous main-thread callback for the decoded value
 * @throws IllegalStateException outside navigation content or without a Lifecycle owner
 */
fun <T> NavResultEffect(
    key: NavResultKey<T>,
    onResult: (T) -> Unit,
) {
    val context = checkNotNull(LocalNavDestinationContext.current) {
        "NavResultEffect must run inside NavHost destination content."
    }
    val lifecycleOwner = checkNotNull(LocalLifecycleOwner.current) {
        "NavResultEffect requires the nearest destination LifecycleOwner."
    }
    val resumed = lifecycleOwner.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)
    if (!resumed) return
    val pending = context.results.pendingRecord(key) ?: return
    SideEffect {
        context.results.consumePending(key, pending.sequence)?.let { consumed ->
            onResult(consumed.value)
        }
    }
}
