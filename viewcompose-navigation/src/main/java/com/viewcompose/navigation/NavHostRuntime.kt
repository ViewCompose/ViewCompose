package com.viewcompose.navigation

import android.app.Application
import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.UiLocalSnapshot

internal data class NavHostRuntimeConfig(
    val localSnapshot: UiLocalSnapshot,
    val lifecycleOwner: LifecycleOwner,
    val transitionSpec: NavTransitionSpec,
    val panePolicy: NavPanePolicy,
    val systemBackEnabled: Boolean,
    val onFailure: ((NavFailure) -> Unit)?,
    val contentKey: Any?,
    val content: NavDestinationContent,
)

internal class NavHostRuntime private constructor(
    val hostView: NavHostView,
    private val controller: NavHostController,
    private val coordinator: TransactionalNavHostCoordinator,
    private val ownerStore: NavEntryOwnerStore,
    private val transitionSpecHolder: TransitionSpecHolder,
) : NavHostBinding {
    private var stagedConfig: NavHostRuntimeConfig? = null
    private var committedConfig: NavHostRuntimeConfig? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var destroyed = false
    private val backAdapter = AndroidNavHostBackAdapter(
        hostView = hostView,
        canHandleBack = {
            !destroyed &&
                coordinator.state == NavHostCoordinatorState.Attached &&
                controller.backStackController.systemBackCommand() != null
        },
        isPreviewActive = { previewId ->
            coordinator.activeBackPreview?.id == previewId
        },
        onBackPressed = {
            controller.backStackController.systemBackCommand()?.let { command ->
                publishNavigationResult(coordinator.navigate(command))
            }
            Unit
        },
        onBackStarted = { event ->
            coordinator.beginBackPreview(event)?.id
        },
        onBackProgressed = { previewId, event ->
            coordinator.updateBackPreview(previewId, event)
            Unit
        },
        onBackCancelled = { previewId ->
            coordinator.cancelBackPreview(previewId)
            Unit
        },
        onBackCommitted = { previewId ->
            publishNavigationResult(coordinator.commitBackPreview(previewId))
            Unit
        },
    )
    private val lifecycleObserver = LifecycleEventObserver { owner, _ ->
        if (destroyed || boundLifecycleOwner !== owner) {
            return@LifecycleEventObserver
        }
        val hostState = owner.lifecycle.currentState.toNavHostLifecycleState()
        if (hostState == NavHostLifecycleState.Destroyed) {
            destroy()
        } else {
            coordinator.moveHostTo(hostState)
        }
    }

    fun stage(config: NavHostRuntimeConfig) {
        stagedConfig = config
    }

    fun commitStaged() {
        check(!destroyed) {
            "A destroyed NavHost cannot accept another committed configuration."
        }
        val config = checkNotNull(stagedConfig) {
            "NavHost must be updated before its render transaction commits."
        }
        val previousConfig = committedConfig
        transitionSpecHolder.value = config.transitionSpec
        committedConfig = config
        applyPanePolicy(config.panePolicy)
        when (coordinator.state) {
            NavHostCoordinatorState.Detached -> attach(config)
            NavHostCoordinatorState.Attached -> {
                coordinator.updateRenderEnvironment(
                    localSnapshot = config.localSnapshot,
                    content = config.content,
                )
                if (
                    previousConfig == null ||
                    previousConfig.contentKey != config.contentKey
                ) {
                    refresh(config)
                }
            }
            NavHostCoordinatorState.Attaching -> {
                error("NavHost cannot commit configuration while attachment is still running.")
            }
            NavHostCoordinatorState.Failed -> {
                error("A failed NavHost must be destroyed before it can render again.")
            }
            NavHostCoordinatorState.Destroyed -> {
                error("A destroyed NavHost cannot render again.")
            }
        }
        backAdapter.updateEnabled(config.systemBackEnabled)
    }

    override fun navigate(command: NavCommand): NavResult {
        return publishNavigationResult(coordinator.navigate(command))
    }

    internal fun onHostWidthChanged(widthPixels: Int) {
        if (destroyed) {
            return
        }
        val policy = committedConfig?.panePolicy
            ?: stagedConfig?.panePolicy
            ?: return
        applyPanePolicy(
            policy = policy,
            widthPixels = widthPixels,
        )
    }

    private fun publishNavigationResult(
        result: NavHostNavigationResult,
    ): NavResult {
        val publicResult = result.toPublicResult(coordinator.stackState)
        controller.syncNavigationState()
        backAdapter.onNavigationStateChanged()
        if (publicResult is NavResult.Failed) {
            committedConfig?.onFailure?.invoke(publicResult.failure)
        }
        return publicResult
    }

    override fun saveState(): NavHostRestorableState {
        check(!destroyed) {
            "A destroyed NavHost cannot save state."
        }
        return NavHostRestorableState(
            stackState = controller.backStackController.stackStateSnapshot(),
            destinationState = ownerStore.performSave(
                retainedEntryIds = controller.backStackController.retainedEntries()
                    .flatMapTo(linkedSetOf()) { entry ->
                        entry.graphEntries.map { graphEntry -> graphEntry.id } + entry.id
                    },
            ),
        )
    }

    fun destroy() {
        if (destroyed) {
            return
        }
        backAdapter.destroy()
        val retainedState = runCatching(::saveState)
        destroyed = true
        boundLifecycleOwner?.let { owner ->
            owner.lifecycle.removeObserver(lifecycleObserver)
        }
        boundLifecycleOwner = null
        try {
            coordinator.destroy()
        } finally {
            try {
                retainedState.getOrNull()?.let(controller::retainState)
            } finally {
                controller.unbind(this)
                committedConfig = null
                stagedConfig = null
            }
        }
        retainedState.getOrThrow()
    }

    private fun attach(config: NavHostRuntimeConfig) {
        val lifecycleState = config.lifecycleOwner.lifecycle.currentState
        check(lifecycleState != Lifecycle.State.DESTROYED) {
            "NavHost cannot attach to a destroyed LifecycleOwner."
        }
        controller.bind(this)
        boundLifecycleOwner = config.lifecycleOwner
        config.lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        var attached = false
        try {
            coordinator.moveHostTo(lifecycleState.toNavHostLifecycleState())
            when (
                val result = coordinator.attach(
                    localSnapshot = config.localSnapshot,
                    content = config.content,
                )
            ) {
                is NavHostAttachmentResult.Attached -> {
                    attached = true
                    backAdapter.attach(config.lifecycleOwner)
                }

                is NavHostAttachmentResult.Failed -> {
                    deliverFailure(
                        NavFailure(
                            phase = result.phase.toPublicPhase(),
                            failedEntry = result.entry,
                            frameReport = result.frameReport,
                            cause = result.cause,
                            stackCommitted = false,
                        ),
                        config = config,
                    )
                }
            }
        } finally {
            if (!attached) {
                config.lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                boundLifecycleOwner = null
                controller.unbind(this)
            }
        }
    }

    private fun refresh(config: NavHostRuntimeConfig) {
        check(boundLifecycleOwner === config.lifecycleOwner) {
            "A mounted NavHost cannot change LifecycleOwner without being recreated."
        }
        val result = coordinator.refresh(
            localSnapshot = config.localSnapshot,
            content = config.content,
        )
        if (result.failedEntryIds.isEmpty()) {
            return
        }
        val retainedEntries = coordinator.activeTransition
            ?.retainedEntries
            ?: controller.backStackController.retainedEntries()
        val entriesById = retainedEntries.associateBy(NavEntry::id)
        result.failedEntryIds.forEach { entryId ->
            val report = result.reports[entryId]
            deliverFailure(
                NavFailure(
                    phase = NavFailurePhase.DestinationRefresh,
                    failedEntry = entriesById[entryId],
                    frameReport = report,
                    cause = report?.failures?.firstOrNull()?.cause,
                    stackCommitted = false,
                ),
                config = config,
            )
        }
    }

    private fun deliverFailure(
        failure: NavFailure,
        config: NavHostRuntimeConfig,
    ) {
        val handler = config.onFailure
        if (handler == null) {
            throw NavHostException(failure)
        }
        handler(failure)
    }

    private fun applyPanePolicy(
        policy: NavPanePolicy,
        widthPixels: Int = hostView.width,
    ) {
        val density = hostView.resources.displayMetrics.density
        hostView.paneSpacingPixels = policy.resolveSpacingPixels(density)
        coordinator.updatePaneStrategy(
            strategy = policy.strategy,
            maxPaneCount = policy.resolvePaneCount(
                widthPixels = widthPixels,
                density = density,
            ),
        )
    }

    companion object {
        fun create(
            context: Context,
            controller: NavHostController,
            initialConfig: NavHostRuntimeConfig,
            overlayHostFactory: (ViewGroup) -> OverlayHost,
            debug: Boolean,
            debugTag: String,
        ): NavHostRuntime {
            require(debugTag.isNotBlank()) {
                "NavHost debugTag must not be blank."
            }
            val hostView = NavHostView(context)
            val ownerStore = NavEntryOwnerStore(
                application = context.applicationContext as? Application,
                restoredState = controller.destinationStateForHost(),
            )
            val transitionSpecHolder = TransitionSpecHolder(initialConfig.transitionSpec)
            val sessionStore = NavDestinationSessionStore(
                hostView = hostView,
                ownerStore = ownerStore,
                overlayHost = overlayHostFactory(hostView),
                debug = debug,
                debugTag = debugTag,
            )
            val coordinator = TransactionalNavHostCoordinator(
                controller = controller.backStackController,
                ownerStore = ownerStore,
                sessionStore = sessionStore,
                initialHostLifecycleState = initialConfig.lifecycleOwner
                    .lifecycle
                    .currentState
                    .toNavHostLifecycleState(),
                transitionDriver = AndroidViewNavHostTransitionDriver(
                    sessionStore = sessionStore,
                    specProvider = { transitionSpecHolder.value },
                ),
                initialPaneStrategy = initialConfig.panePolicy.strategy,
                initialMaxPaneCount = initialConfig.panePolicy.resolvePaneCount(
                    widthPixels = hostView.width,
                    density = hostView.resources.displayMetrics.density,
                ),
            )
            return NavHostRuntime(
                hostView = hostView,
                controller = controller,
                coordinator = coordinator,
                ownerStore = ownerStore,
                transitionSpecHolder = transitionSpecHolder,
            ).also { runtime ->
                runtime.stage(initialConfig)
                hostView.runtime = runtime
            }
        }
    }
}

internal class TransitionSpecHolder(
    var value: NavTransitionSpec,
)

private fun NavHostNavigationResult.toPublicResult(
    currentState: NavStackSetSnapshot,
): NavResult {
    return when (this) {
        is NavHostNavigationResult.Committed -> {
            NavResult.Committed(
                stackState = currentState,
                mutation = mutation,
            )
        }

        is NavHostNavigationResult.NoChange -> {
            NavResult.NoChange(
                stackState = currentState,
                reason = reason,
            )
        }

        is NavHostNavigationResult.Queued -> {
            NavResult.Queued(
                stackState = currentState,
            )
        }

        is NavHostNavigationResult.Failed -> {
            NavResult.Failed(
                stackState = currentState,
                failure = NavFailure(
                    phase = phase.toPublicPhase(),
                    failedEntry = failedEntry,
                    frameReport = frameReport,
                    cause = cause,
                    stackCommitted = stackCommitted,
                ),
            )
        }
    }
}

private fun NavHostFailurePhase.toPublicPhase(): NavFailurePhase {
    return when (this) {
        NavHostFailurePhase.DestinationPreparation -> NavFailurePhase.DestinationPreparation
        NavHostFailurePhase.DestinationRefresh -> NavFailurePhase.DestinationRefresh
        NavHostFailurePhase.DestinationStage -> NavFailurePhase.DestinationStage
        NavHostFailurePhase.StackCommit -> NavFailurePhase.StackCommit
        NavHostFailurePhase.CommitEffects -> NavFailurePhase.CommitEffects
    }
}

private fun Lifecycle.State.toNavHostLifecycleState(): NavHostLifecycleState {
    return when (this) {
        Lifecycle.State.DESTROYED -> NavHostLifecycleState.Destroyed
        Lifecycle.State.INITIALIZED -> NavHostLifecycleState.Initialized
        Lifecycle.State.CREATED -> NavHostLifecycleState.Created
        Lifecycle.State.STARTED -> NavHostLifecycleState.Started
        Lifecycle.State.RESUMED -> NavHostLifecycleState.Resumed
    }
}
