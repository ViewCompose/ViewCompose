package com.viewcompose.navigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.annotation.MainThread
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavDeepLinkLaunchMode
import com.viewcompose.navigation.core.NavDeepLinkMatch
import com.viewcompose.navigation.core.NavDeepLinkRejection
import com.viewcompose.navigation.core.NavDeepLinkResolution
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.navigation.core.NavLaunchMode
import com.viewcompose.navigation.core.NavNoChangeReason
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackSelectionMode
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavStackMutation
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.rememberSaveable

/** Execution phase in which a [NavHost] transaction failed. */
enum class NavFailurePhase {
    DestinationPreparation,
    DestinationRefresh,
    DestinationStage,
    StackCommit,
    CommitEffects,
}

/**
 * Complete diagnostic context captured when navigation rendering or commit fails.
 *
 * @property phase operation that failed
 * @property failedEntry destination being prepared or refreshed, when applicable
 * @property frameReport renderer report associated with the failure, when available
 * @property cause original rendering, owner, or commit exception
 * @property stackCommitted whether pure navigation state was already published before the failure
 */
data class NavFailure(
    val phase: NavFailurePhase,
    val failedEntry: NavEntry?,
    val frameReport: RenderFrameReport?,
    val cause: Throwable?,
    val stackCommitted: Boolean,
)

/**
 * Runtime exception that surfaces an otherwise unhandled [NavFailure].
 *
 * @property failure complete structured failure context
 */
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

/** Synchronous result of a navigation command with the committed state observed at return time. */
sealed interface NavResult {
    /** Complete committed multi-stack state observed when this result was produced. */
    val stackState: NavStackSetSnapshot

    /** Active-stack projection for destination-oriented call sites. */
    val snapshot: NavBackStackSnapshot
        get() = stackState.activeStack

    /**
     * The command changed and committed navigation state.
     *
     * @property stackState complete committed state after the command
     * @property mutation concrete entry-owner delta applied by the host
     */
    data class Committed(
        override val stackState: NavStackSetSnapshot,
        val mutation: NavStackMutation,
    ) : NavResult

    /**
     * The command was valid but did not change navigation state.
     *
     * @property stackState unchanged committed state
     * @property reason structured no-change category
     */
    data class NoChange(
        override val stackState: NavStackSetSnapshot,
        val reason: NavNoChangeReason,
    ) : NavResult

    /**
     * The command was queued during a transition and will run serially later.
     *
     * @property stackState committed state before the queued command runs
     */
    data class Queued(
        override val stackState: NavStackSetSnapshot,
    ) : NavResult

    /**
     * The command failed; the context can drive logging, fallback, or test assertions.
     *
     * @property stackState committed state retained after failure handling
     * @property failure structured failure context
     */
    data class Failed(
        override val stackState: NavStackSetSnapshot,
        val failure: NavFailure,
    ) : NavResult
}

/** Exhaustive result of strict deep-link resolution and optional navigation. */
sealed interface NavDeepLinkResult {
    /**
     * The deep link matched and attempted the corresponding navigation command.
     *
     * @property match winning graph declaration and decoded route
     * @property navigationResult synchronous host transaction result
     */
    data class Navigated(
        val match: NavDeepLinkMatch,
        val navigationResult: NavResult,
    ) : NavDeepLinkResult

    /** No deep-link rule in the current graph matched the URI. */
    data object NoMatch : NavDeepLinkResult

    /**
     * The URI entered a rule domain but was rejected by safety or argument checks.
     *
     * @property rejection structured resolver diagnostic
     */
    data class Rejected(
        val rejection: NavDeepLinkRejection,
    ) : NavDeepLinkResult

    /** This controller was created without a navigation graph and cannot resolve graph deep links. */
    data object Unsupported : NavDeepLinkResult
}

/**
 * Stable application-facing handle for one or more framework-owned navigation stacks.
 *
 * A controller can be mounted by only one [NavHost] at a time. Commands require that host to be
 * attached so every stack mutation shares the destination render and lifecycle transaction.
 *
 * When remembered with [rememberNavHostController], the stack, destination and graph instance IDs,
 * route arguments, and every destination/graph saved-state namespace survive host recreation.
 * All commands are main-thread APIs and may return [NavResult.Queued] while a visual transition is
 * settling. Observe [navigationState] for the eventual committed state rather than treating a
 * queued result as completion.
 *
 * @sample com.viewcompose.navigation.samples.navHostControllerSample
 */
class NavHostController internal constructor(
    internal val backStackController: NavBackStackController,
    restoredDestinationState: Bundle? = null,
) {
    private var binding: NavHostBinding? = null
    private var retainedDestinationState: Bundle? = restoredDestinationState?.let(::Bundle)
    private val mutableNavigationState = mutableStateOf(
        backStackController.stackStateSnapshot(),
    )

    /** Immediate snapshot of the currently active stack. */
    val snapshot: NavBackStackSnapshot
        get() = backStackController.snapshot()

    /** Immediate snapshot of all stacks, including the active stack and selection history. */
    val stackState: NavStackSetSnapshot
        get() = backStackController.stackStateSnapshot()

    /** Observable complete stack state for selected-tab UI and navigation diagnostics. */
    val navigationState: State<NavStackSetSnapshot>
        get() = mutableNavigationState

    /** ID of the stack currently presented by the host. */
    val activeStackId: NavStackId
        get() = stackState.activeStackId

    /** Whether this controller is currently bound to an active [NavHost]. */
    val isAttached: Boolean
        get() = binding != null

    /**
     * Returns a snapshot for [stackId] without changing the active stack.
     *
     * @throws IllegalArgumentException if [stackId] is not declared
     */
    fun stackSnapshot(stackId: NavStackId): NavBackStackSnapshot {
        return backStackController.stackSnapshot(stackId)
    }

    /**
     * Pushes [route] onto the active stack, applying [launchMode] reuse or creation rules.
     *
     * @throws IllegalStateException when called off the main thread or without an attached host
     */
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

    /** Pops the active top, or returns `NoChange` when the stack is at its root. */
    @MainThread
    fun popBackStack(): NavResult = execute(NavCommand.Pop)

    /** Atomically replaces the top destination on the active stack. */
    @MainThread
    fun replaceTop(route: NavRoute): NavResult {
        return execute(NavCommand.ReplaceTop(route))
    }

    /** Resets the active stack to a single newly owned [route]. */
    @MainThread
    fun reset(route: NavRoute): NavResult {
        return execute(NavCommand.Reset(route))
    }

    /** Atomically presents [stackId] while retaining every other stack and its owners. */
    @MainThread
    fun selectStack(
        stackId: NavStackId,
        selectionMode: NavStackSelectionMode = NavStackSelectionMode.Preserve,
    ): NavResult {
        return execute(
            NavCommand.SelectStack(
                stackId = stackId,
                selectionMode = selectionMode,
            ),
        )
    }

    /**
     * Resolves an allowlisted graph URI and atomically updates and selects its destination stack.
     *
     * Input query parameters not declared by the matched pattern are ignored. They cannot become
     * route arguments or override the declaration's target stack or the caller's [launchMode].
     *
     * @return a resolver diagnostic, or `Navigated` containing the host transaction result
     */
    @MainThread
    fun navigateDeepLink(
        uri: String,
        launchMode: NavDeepLinkLaunchMode = NavDeepLinkLaunchMode.Reset,
    ): NavDeepLinkResult {
        requireMainThread()
        return when (val resolution = backStackController.resolveDeepLink(uri)) {
            is NavDeepLinkResolution.Matched -> {
                NavDeepLinkResult.Navigated(
                    match = resolution.match,
                    navigationResult = execute(
                        NavCommand.OpenDeepLink(
                            route = resolution.match.route,
                            targetStackId = resolution.match.deepLink.targetStackId,
                            launchMode = launchMode,
                        ),
                    ),
                )
            }

            NavDeepLinkResolution.NoMatch -> NavDeepLinkResult.NoMatch
            is NavDeepLinkResolution.Rejected -> {
                NavDeepLinkResult.Rejected(resolution.rejection)
            }

            NavDeepLinkResolution.Unsupported -> NavDeepLinkResult.Unsupported
        }
    }

    /** Resolves an Android [Uri] with the same behavior as the string entry point. */
    @MainThread
    fun navigateDeepLink(
        uri: Uri,
        launchMode: NavDeepLinkLaunchMode = NavDeepLinkLaunchMode.Reset,
    ): NavDeepLinkResult {
        return navigateDeepLink(
            uri = uri.toString(),
            launchMode = launchMode,
        )
    }

    /** Maps an Android `ACTION_VIEW` [Intent] into the same strict graph deep-link transaction. */
    @MainThread
    fun navigateDeepLink(
        intent: Intent,
        launchMode: NavDeepLinkLaunchMode = NavDeepLinkLaunchMode.Reset,
    ): NavDeepLinkResult {
        requireMainThread()
        val uri = intent.data
        if (intent.action != Intent.ACTION_VIEW || uri == null) {
            return NavDeepLinkResult.NoMatch
        }
        return navigateDeepLink(
            uri = uri,
            launchMode = launchMode,
        )
    }

    @MainThread
    internal fun execute(command: NavCommand): NavResult {
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
                stackState = backStackController.stackStateSnapshot(),
                destinationState = retainedDestinationState?.let(::Bundle),
            )
        check(state.stackState == backStackController.stackStateSnapshot()) {
            "NavHost runtime and controller navigation stacks diverged while saving state."
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
        check(state.stackState == backStackController.stackStateSnapshot()) {
            "NavHost cannot retain destination state for different navigation stacks."
        }
        retainedDestinationState = state.destinationState?.let(::Bundle)
    }

    @MainThread
    internal fun syncNavigationState() {
        requireMainThread()
        mutableNavigationState.value = backStackController.stackStateSnapshot()
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Navigation commands must run on the Android main thread."
        }
    }
}

internal interface NavHostBinding {
    /** Executes a command inside the host transaction. */
    fun navigate(command: NavCommand): NavResult

    /** Reads the host's current restorable state. */
    fun saveState(): NavHostRestorableState
}

/** Creates an unattached single-stack controller without graph resolution or deep links. */
fun createNavHostController(
    startDestination: NavRoute,
): NavHostController {
    return createNavHostController(
        startDestination = startDestination,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

/** Creates an unattached single-stack controller whose routes and deep links use [graph]. */
fun createNavHostController(
    graph: NavGraph,
): NavHostController {
    return createNavHostController(
        graph = graph,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

/** Creates an unattached multi-stack controller without graph-based route resolution. */
fun createNavHostController(
    stackConfiguration: NavStackConfiguration,
): NavHostController {
    return createNavHostController(
        stackConfiguration = stackConfiguration,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

/** Creates an unattached multi-stack controller that resolves every stack through shared [graph]. */
fun createNavHostController(
    stackConfiguration: NavStackConfiguration,
    graph: NavGraph,
): NavHostController {
    return createNavHostController(
        stackConfiguration = stackConfiguration,
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

internal fun createNavHostController(
    stackConfiguration: NavStackConfiguration,
    entryIdFactory: NavEntryIdFactory,
): NavHostController {
    return NavHostController(
        NavBackStackController.create(
            configuration = stackConfiguration,
            entryIdFactory = entryIdFactory,
        ),
    )
}

internal fun createNavHostController(
    stackConfiguration: NavStackConfiguration,
    graph: NavGraph,
    entryIdFactory: NavEntryIdFactory,
): NavHostController {
    return NavHostController(
        NavBackStackController.create(
            configuration = stackConfiguration,
            graph = graph,
            entryIdFactory = entryIdFactory,
        ),
    )
}

/**
 * Remembers a controller and restores its complete host state through the current saveable-state
 * registry.
 *
 * Invalid or incompatible restored data is discarded in favor of [startDestination].
 *
 * @sample com.viewcompose.navigation.samples.rememberedNavHostSample
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
 * Remembers a controller whose destinations are resolved through [graph].
 *
 * Entering a graph route atomically opens its leaf start destination and records stable parent-graph
 * instances on the resulting entry.
 *
 * Destinations reuse common graph instances until that graph is entered again.
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

/** Remembers independently retained navigation stacks without graph-based route resolution. */
fun rememberNavHostController(
    stackConfiguration: NavStackConfiguration,
): NavHostController {
    return rememberSaveable(
        stackConfiguration,
        saver = navHostControllerSaver(stackConfiguration),
    ) {
        createNavHostController(stackConfiguration)
    }
}

/** Remembers independently retained navigation stacks whose routes share [graph]. */
fun rememberNavHostController(
    stackConfiguration: NavStackConfiguration,
    graph: NavGraph,
): NavHostController {
    return rememberSaveable(
        stackConfiguration,
        graph,
        saver = navHostControllerSaver(
            stackConfiguration = stackConfiguration,
            graph = graph,
        ),
    ) {
        createNavHostController(
            stackConfiguration = stackConfiguration,
            graph = graph,
        )
    }
}
