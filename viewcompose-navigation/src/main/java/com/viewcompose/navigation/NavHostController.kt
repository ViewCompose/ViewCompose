package com.viewcompose.navigation

import android.os.Bundle
import android.os.Looper
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.navigation.core.NavLaunchMode
import com.viewcompose.navigation.core.NavNoChangeReason
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackMutation
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.rememberSaveable

enum class NavFailurePhase {
    DestinationPreparation,
    DestinationRefresh,
    DestinationStage,
    StackCommit,
    CommitEffects,
}

data class NavFailure(
    val phase: NavFailurePhase,
    val failedEntry: NavEntry?,
    val frameReport: RenderFrameReport?,
    val cause: Throwable?,
    val stackCommitted: Boolean,
)

class NavHostException(
    val failure: NavFailure,
) : IllegalStateException(
    buildString {
        append("NavHost failed during ")
        append(failure.phase)
        failure.failedEntry?.let { entry ->
            append(" for ")
            append(entry.route)
            append(" (")
            append(entry.id)
            append(')')
        }
        if (failure.stackCommitted) {
            append(" after the back stack committed")
        }
        append('.')
    },
    failure.cause,
)

sealed interface NavResult {
    val snapshot: NavBackStackSnapshot

    data class Committed(
        override val snapshot: NavBackStackSnapshot,
        val mutation: NavStackMutation,
    ) : NavResult

    data class NoChange(
        override val snapshot: NavBackStackSnapshot,
        val reason: NavNoChangeReason,
    ) : NavResult

    data class Queued(
        override val snapshot: NavBackStackSnapshot,
        val command: NavCommand,
    ) : NavResult

    data class Failed(
        override val snapshot: NavBackStackSnapshot,
        val failure: NavFailure,
    ) : NavResult
}

/**
 * Stable application-facing handle for a single framework-owned navigation stack.
 *
 * A controller can be mounted by only one [NavHost] at a time. Commands require that host to be
 * attached so every stack mutation shares the destination render and lifecycle transaction. When
 * remembered with [rememberNavHostController], the stack, destination and graph instance IDs, route
 * arguments, and every destination/graph saved-state namespace survive host recreation.
 */
class NavHostController internal constructor(
    internal val backStackController: NavBackStackController,
    restoredDestinationState: Bundle? = null,
) {
    private var binding: NavHostBinding? = null
    private var retainedDestinationState: Bundle? = restoredDestinationState?.let(::Bundle)

    val snapshot: NavBackStackSnapshot
        get() = backStackController.snapshot()

    val isAttached: Boolean
        get() = binding != null

    @MainThread
    fun navigate(
        route: NavRoute,
        launchMode: NavLaunchMode = NavLaunchMode.Standard,
    ): NavResult {
        return execute(
            NavCommand.Push(
                route = route,
                launchMode = launchMode,
            ),
        )
    }

    @MainThread
    fun popBackStack(): NavResult = execute(NavCommand.Pop)

    @MainThread
    fun replaceTop(route: NavRoute): NavResult {
        return execute(NavCommand.ReplaceTop(route))
    }

    @MainThread
    fun reset(route: NavRoute): NavResult {
        return execute(NavCommand.Reset(route))
    }

    @MainThread
    fun execute(command: NavCommand): NavResult {
        requireMainThread()
        return checkNotNull(binding) {
            "NavHostController commands require an attached NavHost."
        }.navigate(command)
    }

    @MainThread
    internal fun bind(nextBinding: NavHostBinding) {
        requireMainThread()
        check(binding == null || binding === nextBinding) {
            "A NavHostController cannot be attached to multiple NavHost instances."
        }
        binding = nextBinding
    }

    @MainThread
    internal fun unbind(detachedBinding: NavHostBinding) {
        requireMainThread()
        if (binding === detachedBinding) {
            binding = null
        }
    }

    @MainThread
    internal fun stateForSave(): NavHostRestorableState {
        requireMainThread()
        val state = binding?.saveState()
            ?: NavHostRestorableState(
                snapshot = backStackController.snapshot(),
                destinationState = retainedDestinationState?.let(::Bundle),
            )
        check(state.snapshot == backStackController.snapshot()) {
            "NavHost runtime and controller back stacks diverged while saving state."
        }
        retainedDestinationState = state.destinationState?.let(::Bundle)
        return state.copy(
            destinationState = state.destinationState?.let(::Bundle),
        )
    }

    @MainThread
    internal fun destinationStateForHost(): Bundle? {
        requireMainThread()
        return retainedDestinationState?.let(::Bundle)
    }

    @MainThread
    internal fun retainState(state: NavHostRestorableState) {
        requireMainThread()
        check(state.snapshot == backStackController.snapshot()) {
            "NavHost cannot retain destination state for a different back stack."
        }
        retainedDestinationState = state.destinationState?.let(::Bundle)
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Navigation commands must run on the Android main thread."
        }
    }
}

internal interface NavHostBinding {
    fun navigate(command: NavCommand): NavResult

    fun saveState(): NavHostRestorableState
}

fun createNavHostController(
    startDestination: NavRoute,
): NavHostController {
    return createNavHostController(
        startDestination = startDestination,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

fun createNavHostController(
    graph: NavGraph,
): NavHostController {
    return createNavHostController(
        graph = graph,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

internal fun createNavHostController(
    startDestination: NavRoute,
    entryIdFactory: NavEntryIdFactory,
): NavHostController {
    return NavHostController(
        NavBackStackController.create(
            startDestination = startDestination,
            entryIdFactory = entryIdFactory,
        ),
    )
}

internal fun createNavHostController(
    graph: NavGraph,
    entryIdFactory: NavEntryIdFactory,
): NavHostController {
    return NavHostController(
        NavBackStackController.create(
            graph = graph,
            entryIdFactory = entryIdFactory,
        ),
    )
}

/**
 * Remembers a controller and restores its complete host state through the current saveable-state
 * registry. Invalid or incompatible restored data is discarded in favor of [startDestination].
 */
fun rememberNavHostController(
    startDestination: NavRoute,
): NavHostController {
    return rememberSaveable(
        startDestination,
        saver = navHostControllerSaver(startDestination),
    ) {
        createNavHostController(startDestination)
    }
}

/**
 * Remembers a controller whose destinations are resolved through [graph]. Entering a graph route
 * atomically opens its leaf start destination and records stable parent-graph instances on the
 * resulting entry. Destinations reuse common graph instances until that graph is entered again.
 */
fun rememberNavHostController(
    graph: NavGraph,
): NavHostController {
    return rememberSaveable(
        graph,
        saver = navHostControllerSaver(graph),
    ) {
        createNavHostController(graph)
    }
}
