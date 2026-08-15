package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.RememberObserver

private const val DisposableEffectNoKeyMessage =
    "DisposableEffect requires at least one key that defines when its current setup is disposed " +
        "and replaced."

/**
 * Builds the terminal cleanup result required by [DisposableEffect].
 *
 * The runtime invokes the returned cleanup at most once after a successful setup when a key
 * changes, the call leaves composition, or the owning render session is disposed. Cleanup runs
 * synchronously in the composition commit phase before replacement setup. A throwing cleanup is
 * reported as a committed-frame failure and is not retried.
 *
 * @sample com.viewcompose.ui.foundation.samples.disposableEffectSample
 */
class DisposableEffectScope internal constructor() {
    /**
     * Returns the cleanup operation paired with the current disposable-effect setup.
     *
     * @param onDispose synchronous cleanup that releases the resource or subscription acquired by
     * the setup block
     * @return the runtime-owned terminal result for this setup
     */
    fun onDispose(onDispose: () -> Unit): DisposableEffectResult =
        DisposableEffectResult(onDispose)
}

/**
 * Holds one runtime-owned cleanup operation produced by [DisposableEffectScope.onDispose].
 *
 * Consumers create this value only through `onDispose`. The owning composition invokes it at most
 * once and does not retry a throwing cleanup.
 */
fun interface DisposableEffectResult {
    /** Performs the paired terminal cleanup synchronously when invoked by the owning runtime. */
    fun dispose()
}

/**
 * Rejects a disposable effect without an explicit lifecycle identity.
 *
 * @param effect unused setup block; add at least one key and finish the block with `onDispose`
 */
@Deprecated(
    message = DisposableEffectNoKeyMessage,
    level = DeprecationLevel.ERROR,
)
@Suppress("UNUSED_PARAMETER")
fun DisposableEffect(
    effect: DisposableEffectScope.() -> DisposableEffectResult,
): Unit = error(DisposableEffectNoKeyMessage)

/**
 * Starts cleanup-aware synchronous work after a successful frame commits.
 *
 * [key1] and the positional DSL structure define identity. A changed key disposes the active setup
 * before starting its replacement. Leaving composition or disposing the render session also
 * disposes it. An aborted candidate neither starts a setup nor disposes the committed setup. Each
 * successful setup must return [DisposableEffectScope.onDispose] as its final expression.
 *
 * Setup and cleanup are serialized on the render session's composition thread. If setup throws,
 * no cleanup exists and the setup remains pending for retry on a later successful composition
 * commit, so setup must be retry-safe. If cleanup throws, it remains terminal; unrelated lifecycle
 * operations are still attempted. Resolve composition locals while declaring the effect: a missing
 * provider read from setup or cleanup fails with a named diagnostic instead of silently using its
 * default.
 *
 * @sample com.viewcompose.ui.foundation.samples.disposableEffectSample
 * @param key1 value compared by structural equality to decide whether to retain or replace setup
 * @param effect synchronous setup that returns its mandatory paired cleanup
 */
fun DisposableEffect(
    key1: Any?,
    effect: DisposableEffectScope.() -> DisposableEffectResult,
) {
    disposableEffectOf(
        keys = arrayOf(key1),
        effect = effect,
    )
}

/**
 * Starts cleanup-aware work identified by two structural keys.
 *
 * The complete lifecycle, rollback, ordering, and failure contract is defined by the one-key
 * [DisposableEffect] overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.disposableEffectSample
 * @param key1 first value in the structural restart identity
 * @param key2 second value in the structural restart identity
 * @param effect synchronous setup that returns its mandatory paired cleanup
 */
fun DisposableEffect(
    key1: Any?,
    key2: Any?,
    effect: DisposableEffectScope.() -> DisposableEffectResult,
) {
    disposableEffectOf(
        keys = arrayOf(key1, key2),
        effect = effect,
    )
}

/**
 * Starts cleanup-aware work identified by three structural keys.
 *
 * The complete lifecycle, rollback, ordering, and failure contract is defined by the one-key
 * [DisposableEffect] overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.disposableEffectSample
 * @param key1 first value in the structural restart identity
 * @param key2 second value in the structural restart identity
 * @param key3 third value in the structural restart identity
 * @param effect synchronous setup that returns its mandatory paired cleanup
 */
fun DisposableEffect(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    effect: DisposableEffectScope.() -> DisposableEffectResult,
) {
    disposableEffectOf(
        keys = arrayOf(key1, key2, key3),
        effect = effect,
    )
}

/**
 * Starts cleanup-aware work identified by a non-empty structural key list.
 *
 * The complete lifecycle, rollback, ordering, and failure contract is defined by the one-key
 * [DisposableEffect] overload. Prefer fixed-arity overloads for one to three keys.
 *
 * @sample com.viewcompose.ui.foundation.samples.disposableEffectSample
 * @param keys non-empty values forming the structural restart identity
 * @param effect synchronous setup that returns its mandatory paired cleanup
 * @throws IllegalArgumentException when [keys] is empty through a dynamic spread
 */
fun DisposableEffect(
    vararg keys: Any?,
    effect: DisposableEffectScope.() -> DisposableEffectResult,
) {
    require(keys.isNotEmpty()) { DisposableEffectNoKeyMessage }
    disposableEffectOf(
        keys = keys,
        effect = effect,
    )
}

private fun disposableEffectOf(
    keys: Array<out Any?>,
    effect: DisposableEffectScope.() -> DisposableEffectResult,
) {
    ComposerContext.requireCurrentComposer("DisposableEffect").remember(
        keys = keys.toList(),
    ) {
        DisposableEffectObserver(effect)
    }
}

private class DisposableEffectObserver(
    private val effect: DisposableEffectScope.() -> DisposableEffectResult,
) : RememberObserver {
    private var result: DisposableEffectResult? = null

    override fun onRemembered() {
        check(result == null) {
            "DisposableEffect setup is already active."
        }
        result = CompositionEffectContext.run {
            DisposableEffectScope().effect()
        }
    }

    override fun onForgotten() {
        val current = result
        result = null
        current?.let { cleanup ->
            CompositionEffectContext.run(cleanup::dispose)
        }
    }

    override fun onAbandoned() = Unit
}
