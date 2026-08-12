package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.RememberObserver
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val LaunchedEffectNoKeyMessage =
    "LaunchedEffect requires at least one key that defines when its coroutine is cancelled and " +
        "restarted."

/**
 * Rejects a launched effect without an explicit lifecycle identity.
 *
 * @param block unused suspending work; add at least one restart key
 */
@Deprecated(
    message = LaunchedEffectNoKeyMessage,
    level = DeprecationLevel.ERROR,
)
@Suppress("UNUSED_PARAMETER")
fun LaunchedEffect(
    block: suspend CoroutineScope.() -> Unit,
): Unit = error(LaunchedEffectNoKeyMessage)

/**
 * Launches composition-owned suspending work after a successful commit.
 *
 * [key1] and positional DSL structure define identity. A changed key requests cancellation of the
 * old job before launching its replacement. Leaving composition or disposing the render session
 * also cancels the job. An aborted candidate does not launch or cancel work.
 *
 * The job inherits the render session coroutine context and exception route. Cancellation is a
 * request: arbitrary non-cancellable cleanup may finish asynchronously after replacement launch.
 * Use [rememberCoroutineScope] for work initiated by event callbacks rather than moving event data
 * into effect keys. Resolve composition locals while declaring the effect: reading a missing
 * provider from the coroutine fails with a named diagnostic instead of selecting its default.
 *
 * @sample com.viewcompose.ui.foundation.samples.launchedEffectSample
 * @param key1 value compared by structural equality to decide whether to retain or restart work
 * @param block suspending work owned by this effect identity
 */
fun LaunchedEffect(
    key1: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    launchedEffectOf(arrayOf(key1), block)
}

/**
 * Launches composition-owned work identified by two structural keys.
 *
 * Commit, cancellation, ownership, and failure behavior match the one-key [LaunchedEffect]
 * overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.launchedEffectSample
 * @param key1 first value in the structural restart identity
 * @param key2 second value in the structural restart identity
 * @param block suspending work owned by this effect identity
 */
fun LaunchedEffect(
    key1: Any?,
    key2: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    launchedEffectOf(arrayOf(key1, key2), block)
}

/**
 * Launches composition-owned work identified by three structural keys.
 *
 * Commit, cancellation, ownership, and failure behavior match the one-key [LaunchedEffect]
 * overload.
 *
 * @sample com.viewcompose.ui.foundation.samples.launchedEffectSample
 * @param key1 first value in the structural restart identity
 * @param key2 second value in the structural restart identity
 * @param key3 third value in the structural restart identity
 * @param block suspending work owned by this effect identity
 */
fun LaunchedEffect(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    launchedEffectOf(arrayOf(key1, key2, key3), block)
}

/**
 * Launches composition-owned work identified by a non-empty structural key list.
 *
 * @sample com.viewcompose.ui.foundation.samples.launchedEffectSample
 * @param keys non-empty values forming the structural restart identity
 * @param block suspending work owned by this effect identity
 * @throws IllegalArgumentException when [keys] is empty through a dynamic spread
 */
fun LaunchedEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    require(keys.isNotEmpty()) { LaunchedEffectNoKeyMessage }
    launchedEffectOf(keys, block)
}

/**
 * Returns a stable coroutine scope cancelled when this remembered call leaves composition.
 *
 * The scope owns a normal child [Job] of the render session coroutine job. A child failure
 * therefore cancels sibling work in this remembered scope while the session supervisor continues
 * to isolate other composition-owned scopes. The scope is intended for event callbacks; jobs
 * should not be launched directly while composing.
 *
 * [getContext] runs only when the positional scope is first created. It may add a dispatcher,
 * coroutine name, or other non-Job element. Supplying a [Job] returns an already-failed scope so
 * work cannot detach from composition ownership; the call itself does not throw. Coroutines
 * launched from this scope must use composition-local values captured while declaring the scope.
 *
 * @sample com.viewcompose.ui.foundation.samples.rememberCoroutineScopeSample
 * @param getContext factory for additional non-Job coroutine context elements
 * @return one stable composition-owned scope, or a failed scope for a Job-bearing context
 */
fun rememberCoroutineScope(
    getContext: () -> CoroutineContext = { EmptyCoroutineContext },
): CoroutineScope {
    val parentContext = checkNotNull(ComposerContext.currentCoroutineContext()) {
        "rememberCoroutineScope requires an active composition."
    }
    return remember<RememberedCoroutineScope> {
        RememberedCoroutineScope(
            parentContext = parentContext,
            overlayContext = getContext(),
        )
    }
}

private fun launchedEffectOf(
    keys: Array<out Any?>,
    block: suspend CoroutineScope.() -> Unit,
) {
    val parentContext = checkNotNull(ComposerContext.currentCoroutineContext()) {
        "LaunchedEffect requires an active composition."
    }
    remember<LaunchedEffectObserver>(*keys) {
        LaunchedEffectObserver(
            parentContext = parentContext,
            block = block,
        )
    }
}

/** Starts and cancels one launched job with the owning remember lifecycle. */
private class LaunchedEffectObserver(
    parentContext: CoroutineContext,
    private val block: suspend CoroutineScope.() -> Unit,
) : RememberObserver {
    private val scope = CoroutineScope(parentContext + CompositionEffectContext.coroutineContext)
    private var job: Job? = null

    override fun onRemembered() {
        check(job == null) {
            "LaunchedEffect is already running."
        }
        job = scope.launch(block = block)
    }

    override fun onForgotten() {
        job?.cancel(LeftCompositionCancellationException())
        job = null
    }

    override fun onAbandoned() {
        job?.cancel(LeftCompositionCancellationException())
        job = null
    }
}

/** Scope returned by [rememberCoroutineScope], with one normal child job. */
private class RememberedCoroutineScope(
    parentContext: CoroutineContext,
    overlayContext: CoroutineContext,
) : CoroutineScope, RememberObserver {
    private val job: CompletableJob

    override val coroutineContext: CoroutineContext

    init {
        val suppliedJob = overlayContext[Job]
        job = if (suppliedJob == null) {
            Job(parentContext[Job])
        } else {
            Job().apply {
                completeExceptionally(
                    IllegalArgumentException(
                        "rememberCoroutineScope context must not contain a Job.",
                    ),
                )
            }
        }
        coroutineContext =
            parentContext.minusKey(Job) + overlayContext.minusKey(Job) + job +
                CompositionEffectContext.coroutineContext
    }

    override fun onRemembered() = Unit

    override fun onForgotten() {
        cancel(LeftCompositionCancellationException())
    }

    override fun onAbandoned() {
        cancel(LeftCompositionCancellationException())
    }
}

private class LeftCompositionCancellationException :
    CancellationException("Composition-owned coroutine left the composition.")
