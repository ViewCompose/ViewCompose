package com.viewcompose.ui.foundation

private const val SideEffectNoDynamicKeyMessage =
    "Keyed SideEffect requires at least one key. Use SideEffect { ... } for every successful frame."

/**
 * Runs synchronous publication work after every successful invocation commits.
 *
 * The callback runs after committed-value publication and all remember, disposable, and launched
 * lifecycle callbacks for the frame, but before renderer-owned native commit callbacks. An aborted
 * candidate discards it. Callbacks are serialized in declaration order; a failure is reported as a
 * committed-frame failure and does not prevent later callbacks from being attempted.
 *
 * Resolve composition locals before this call and capture their values. The provider stack is not
 * implicitly restored around the callback.
 *
 * @sample com.viewcompose.ui.foundation.samples.sideEffectSample
 * @param effect synchronous post-commit publication; it must not block the render thread
 */
fun SideEffect(effect: () -> Unit) {
    ComposerContext.requireCurrentComposer("SideEffect").sideEffect {
        CompositionEffectContext.run(effect)
    }
}

/**
 * Runs synchronous publication work on initial commit and when [key1] changes.
 *
 * Keys compare by structural equality within the positional DSL structure. Lifecycle ordering,
 * rollback, threading, and failure behavior match the unkeyed [SideEffect] overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.sideEffectSample
 * @param key1 value that controls whether this successful invocation schedules [effect]
 * @param effect synchronous post-commit publication
 */
fun SideEffect(
    key1: Any?,
    effect: () -> Unit,
) {
    keyedSideEffect(arrayOf(key1), effect)
}

/**
 * Runs [effect] on initial commit and when either structural key changes.
 *
 * Lifecycle ordering, rollback, threading, and failure behavior match the unkeyed [SideEffect]
 * overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.sideEffectSample
 * @param key1 first value controlling whether this successful invocation schedules [effect]
 * @param key2 second value controlling whether this successful invocation schedules [effect]
 * @param effect synchronous post-commit publication
 */
fun SideEffect(
    key1: Any?,
    key2: Any?,
    effect: () -> Unit,
) {
    keyedSideEffect(arrayOf(key1, key2), effect)
}

/**
 * Runs [effect] on initial commit and when any of three structural keys changes.
 *
 * Lifecycle ordering, rollback, threading, and failure behavior match the unkeyed [SideEffect]
 * overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.sideEffectSample
 * @param key1 first value controlling whether this successful invocation schedules [effect]
 * @param key2 second value controlling whether this successful invocation schedules [effect]
 * @param key3 third value controlling whether this successful invocation schedules [effect]
 * @param effect synchronous post-commit publication
 */
fun SideEffect(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    effect: () -> Unit,
) {
    keyedSideEffect(arrayOf(key1, key2, key3), effect)
}

/**
 * Runs [effect] on initial commit and when a non-empty structural key list changes.
 *
 * @sample com.viewcompose.ui.foundation.samples.sideEffectSample
 * @param keys non-empty values compared to the previous successful invocation
 * @param effect synchronous post-commit publication
 * @throws IllegalArgumentException when [keys] is empty
 */
fun SideEffect(
    vararg keys: Any?,
    effect: () -> Unit,
) {
    require(keys.isNotEmpty()) { SideEffectNoDynamicKeyMessage }
    keyedSideEffect(keys, effect)
}

private fun keyedSideEffect(
    keys: Array<out Any?>,
    effect: () -> Unit,
) {
    val composer = ComposerContext.requireCurrentComposer("SideEffect")
    composer.withKeys(keys.toList()) {
        composer.remember<KeyedSideEffectMarker>(keys = emptyList()) {
            composer.sideEffect {
                CompositionEffectContext.run(effect)
            }
            KeyedSideEffectMarker
        }
    }
}

private object KeyedSideEffectMarker
