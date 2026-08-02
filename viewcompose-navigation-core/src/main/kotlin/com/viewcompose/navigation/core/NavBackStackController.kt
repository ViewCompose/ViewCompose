package com.viewcompose.navigation.core

/** Stack insertion mode for [NavCommand.Push]. */
enum class NavLaunchMode {
    Standard,
    SingleTop,
}

/** Immutable state-mutation command accepted by [NavBackStackController.prepare]. */
sealed interface NavCommand {
    /**
     * Appends a resolved destination to the active stack.
     *
     * @property route destination or graph route to resolve
     * @property launchMode duplicate-top policy
     */
    data class Push(
        val route: NavRoute,
        val launchMode: NavLaunchMode = NavLaunchMode.Standard,
    ) : NavCommand

    /** Removes the active stack's top entry when it is not the root. */
    data object Pop : NavCommand

    /**
     * Replaces the active top while retaining entries beneath it.
     *
     * @property route destination or graph route to resolve
     */
    data class ReplaceTop(
        val route: NavRoute,
    ) : NavCommand

    /**
     * Replaces the complete active stack with one newly allocated root entry.
     *
     * @property route destination or graph route to resolve
     */
    data class Reset(
        val route: NavRoute,
    ) : NavCommand

    /**
     * Selects an independently retained stack.
     *
     * @property stackId declared target stack
     * @property selectionMode policy applied to its retained entries before selection
     */
    data class SelectStack(
        val stackId: NavStackId,
        val selectionMode: NavStackSelectionMode = NavStackSelectionMode.Preserve,
    ) : NavCommand

    /**
     * Mutates a destination stack and selects it as one atomic transaction.
     *
     * This command carries a graph-resolved route. Applications normally create it through their
     * host's deep-link API instead of constructing it directly.
     *
     * @property route route produced by [NavDeepLinkMatch]
     * @property targetStackId explicit target or `null` to mutate the active stack
     * @property launchMode mutation applied inside the target stack
     */
    data class OpenDeepLink(
        val route: NavRoute,
        val targetStackId: NavStackId? = null,
        val launchMode: NavDeepLinkLaunchMode = NavDeepLinkLaunchMode.Reset,
    ) : NavCommand

    /**
     * Returns to the newest stack in selection history without adding the current stack back.
     *
     * Applications normally reach this command through system Back when
     * [NavRootBackBehavior.PreviousStack] is configured.
     */
    data object PopStackHistory : NavCommand
}

/** Reason why preparing a valid command produced no state mutation. */
enum class NavNoChangeReason {
    CannotPopRoot,
    AlreadyAtDestination,
    AlreadySelectedStack,
}

/** Exhaustive result of preparing a navigation command. */
sealed interface NavPreparation {
    /**
     * Command produced a pending transaction that the host must finish.
     *
     * @property transaction single pending transaction owned by the controller
     */
    data class Ready(
        val transaction: NavTransaction,
    ) : NavPreparation

    /**
     * Command was valid but its requested state was already effective.
     *
     * @property reason no-change category
     * @property snapshot unchanged active-stack snapshot
     */
    data class NoChange(
        val reason: NavNoChangeReason,
        val snapshot: NavBackStackSnapshot,
    ) : NavPreparation
}

/**
 * Destination-owner delta produced by one navigation transaction.
 *
 * The lists span every retained stack, not only the active one, so a host can create and destroy
 * platform owners consistently during stack selection and deep-link transactions.
 *
 * @property added newly retained destination entries in resulting stack order
 * @property removed no-longer-retained destination entries in previous stack order
 * @property previousTop active destination before the transaction
 * @property nextTop active destination after the transaction
 */
data class NavStackMutation(
    val added: List<NavEntry>,
    val removed: List<NavEntry>,
    val previousTop: NavEntry,
    val nextTop: NavEntry,
)

/** Terminal state of a prepared navigation transaction. */
enum class NavTransactionStatus {
    Prepared,
    Committed,
    RolledBack,
}

/**
 * Single-use navigation transaction that can be committed or rolled back.
 *
 * Preparation computes [after] and [mutation] without publishing state. A host should apply its
 * View/lifecycle transaction first and call [commit] only after success. [rollback] releases the
 * pending slot without changing state. Closing a still-prepared transaction rolls it back, making
 * Kotlin `use` a safe failure boundary. Calls are synchronized and a terminal transaction rejects
 * subsequent completion attempts.
 *
 * @sample com.viewcompose.navigation.core.samples.transactionalNavigationSample
 * @property command command represented by this transaction
 * @property before active-stack snapshot before publication
 * @property after prospective active-stack snapshot
 * @property mutation complete retained-entry delta
 */
class NavTransaction internal constructor(
    private val owner: NavBackStackController,
    internal val transactionId: Long,
    internal val beforeState: NavStackSetSnapshot,
    internal val afterState: NavStackSetSnapshot,
    val command: NavCommand,
    val before: NavBackStackSnapshot,
    val after: NavBackStackSnapshot,
    val mutation: NavStackMutation,
) : AutoCloseable {
    @Volatile
    /** Current single-use transaction status. */
    var status: NavTransactionStatus = NavTransactionStatus.Prepared
        private set

    @Synchronized
    /**
     * Atomically publishes the prepared state and returns the new active stack.
     *
     * @throws IllegalStateException if this transaction is terminal, not pending, or state changed
     */
    fun commit(): NavBackStackSnapshot {
        check(status == NavTransactionStatus.Prepared) {
            "Navigation transaction $transactionId is already $status."
        }
        val committed = owner.commit(this)
        status = NavTransactionStatus.Committed
        return committed
    }

    @Synchronized
    /**
     * Discards the prepared state and releases the controller for another command.
     *
     * @throws IllegalStateException if this transaction is already terminal or is not pending
     */
    fun rollback() {
        check(status == NavTransactionStatus.Prepared) {
            "Navigation transaction $transactionId is already $status."
        }
        owner.rollback(this)
        status = NavTransactionStatus.RolledBack
    }

    @Synchronized
    /** Rolls back when still prepared; does nothing after commit or explicit rollback. */
    override fun close() {
        if (status == NavTransactionStatus.Prepared) {
            rollback()
        }
    }
}

/**
 * Thread-safe, platform-neutral controller for transactional single- or multi-stack navigation.
 *
 * Every mutation is first [prepare]d. Only [NavTransaction.commit] publishes its precomputed state;
 * rendering or lifecycle failures call [NavTransaction.rollback]. At most one transaction may be
 * pending. Snapshots are immutable and safe to hand to platform save adapters after commit.
 *
 * Controllers created with a [NavGraph] validate routes, own graph-scoped identities, and resolve
 * deep links. Graphless controllers accept direct routes but report [NavDeepLinkResolution.Unsupported].
 *
 * @sample com.viewcompose.navigation.core.samples.transactionalNavigationSample
 * @property rootBackBehavior configured behavior when the active stack is at its root
 */
class NavBackStackController private constructor(
    initialState: NavStackSetSnapshot,
    private val entryIdFactory: NavEntryIdFactory,
    private val routeResolver: (NavRoute) -> NavGraphResolution,
    private val deepLinkResolver: ((String) -> NavDeepLinkResolution)?,
    val rootBackBehavior: NavRootBackBehavior,
) {
    private var currentState = initialState
    private val allocatedEntryIds = linkedSetOf<NavEntryId>().apply {
        initialState.allEntries.forEach { entry ->
            add(entry.id)
            entry.graphEntries.forEach { graphEntry -> add(graphEntry.id) }
        }
    }
    private var pendingTransactionId: Long? = null
    private var nextTransactionId = 0L

    @Synchronized
    /** Returns the currently committed active-stack snapshot. */
    fun snapshot(): NavBackStackSnapshot = currentState.activeStack

    @Synchronized
    /** Returns the currently committed complete retained-stack state. */
    fun stackStateSnapshot(): NavStackSetSnapshot = currentState

    @Synchronized
    /**
     * Returns the committed snapshot for [stackId].
     *
     * @throws IllegalArgumentException if the stack is not declared
     */
    fun stackSnapshot(stackId: NavStackId): NavBackStackSnapshot {
        return requireNotNull(currentState[stackId]) {
            "Navigation stack '$stackId' is not declared."
        }
    }

    @Synchronized
    /** Returns all committed destination entries in configured stack order. */
    fun retainedEntries(): List<NavEntry> = currentState.allEntries

    @Synchronized
    /** Resolves [uri] through the bound graph, or returns `Unsupported` for a graphless controller. */
    fun resolveDeepLink(uri: String): NavDeepLinkResolution {
        return deepLinkResolver?.invoke(uri) ?: NavDeepLinkResolution.Unsupported
    }

    @Synchronized
    /**
     * Returns the command that should handle system Back, or `null` to delegate to the outer host.
     *
     * This method does not mutate state. A non-root stack pops first; root behavior then follows
     * [rootBackBehavior] and available selection history.
     */
    fun systemBackCommand(): NavCommand? {
        return when {
            currentState.activeStack.entries.size > 1 -> NavCommand.Pop
            rootBackBehavior == NavRootBackBehavior.PreviousStack &&
                currentState.selectionHistory.isNotEmpty() -> NavCommand.PopStackHistory
            else -> null
        }
    }

    @Synchronized
    /**
     * Computes [command] against committed state without publishing it.
     *
     * @return a single pending transaction, or a structured no-change result
     * @throws IllegalStateException if another transaction is pending or command preconditions fail
     * @throws IllegalArgumentException if a route or stack is unknown
     */
    fun prepare(command: NavCommand): NavPreparation {
        check(pendingTransactionId == null) {
            "A navigation transaction is already prepared. Commit or roll it back first."
        }
        val beforeState = currentState
        val before = beforeState.activeStack
        val afterState = when (command) {
            is NavCommand.Push -> {
                val resolved = routeResolver(command.route)
                if (
                    command.launchMode == NavLaunchMode.SingleTop &&
                    before.top.matches(resolved)
                ) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                beforeState.withActiveStack(
                    NavBackStackSnapshot(
                        before.entries + createEntry(
                            resolved = resolved,
                            previousEntry = before.top,
                        ),
                    ),
                )
            }

            NavCommand.Pop -> {
                if (before.entries.size == 1) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.CannotPopRoot,
                        snapshot = before,
                    )
                }
                beforeState.withActiveStack(
                    NavBackStackSnapshot(before.entries.dropLast(1)),
                )
            }

            is NavCommand.ReplaceTop -> {
                val resolved = routeResolver(command.route)
                if (before.top.matches(resolved)) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                beforeState.withActiveStack(
                    NavBackStackSnapshot(
                        before.entries.dropLast(1) + createEntry(
                            resolved = resolved,
                            previousEntry = before.top,
                        ),
                    ),
                )
            }

            is NavCommand.Reset -> {
                val resolved = routeResolver(command.route)
                if (before.entries.size == 1 && before.top.matches(resolved)) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                beforeState.withActiveStack(
                    NavBackStackSnapshot(
                        listOf(
                            createEntry(
                                resolved = resolved,
                                previousEntry = null,
                            ),
                        ),
                    ),
                )
            }

            is NavCommand.SelectStack -> {
                prepareStackSelection(
                    beforeState = beforeState,
                    command = command,
                ) ?: return NavPreparation.NoChange(
                    reason = NavNoChangeReason.AlreadySelectedStack,
                    snapshot = before,
                )
            }

            is NavCommand.OpenDeepLink -> {
                prepareDeepLinkNavigation(
                    beforeState = beforeState,
                    command = command,
                ) ?: return NavPreparation.NoChange(
                    reason = NavNoChangeReason.AlreadyAtDestination,
                    snapshot = before,
                )
            }

            NavCommand.PopStackHistory -> {
                check(before.entries.size == 1) {
                    "Navigation stack history can be popped only from the active stack root."
                }
                val targetStackId = beforeState.selectionHistory.lastOrNull()
                    ?: return NavPreparation.NoChange(
                        reason = NavNoChangeReason.CannotPopRoot,
                        snapshot = before,
                    )
                beforeState.selectPreviousStack(targetStackId)
            }
        }
        val after = afterState.activeStack
        val transactionId = ++nextTransactionId
        pendingTransactionId = transactionId
        // Mutation spans all retained stacks, not only the active stack, so hosts can sync owner/session lifetimes.
        return NavPreparation.Ready(
            transaction = NavTransaction(
                owner = this,
                transactionId = transactionId,
                beforeState = beforeState,
                afterState = afterState,
                command = command,
                before = before,
                after = after,
                mutation = mutationBetween(beforeState, afterState),
            ),
        )
    }

    @Synchronized
    internal fun commit(transaction: NavTransaction): NavBackStackSnapshot {
        requirePrepared(transaction)
        check(currentState == transaction.beforeState) {
            "Navigation state changed while transaction ${transaction.transactionId} was prepared."
        }
        currentState = transaction.afterState
        pendingTransactionId = null
        return currentState.activeStack
    }

    @Synchronized
    internal fun rollback(transaction: NavTransaction) {
        requirePrepared(transaction)
        pendingTransactionId = null
    }

    private fun requirePrepared(transaction: NavTransaction) {
        check(transaction.status == NavTransactionStatus.Prepared) {
            "Navigation transaction ${transaction.transactionId} is already ${transaction.status}."
        }
        check(pendingTransactionId == transaction.transactionId) {
            "Navigation transaction ${transaction.transactionId} is not the controller's prepared transaction."
        }
    }

    private fun createEntry(
        resolved: NavGraphResolution,
        previousEntry: NavEntry?,
    ): NavEntry {
        val id = entryIdFactory.nextId()
        check(allocatedEntryIds.add(id)) {
            "NavEntryIdFactory returned an entry ID that was already allocated: $id"
        }
        val graphEntries = createGraphEntries(
            ownerEntryId = id,
            resolved = resolved,
            previousEntry = previousEntry,
        )
        return NavEntry(
            id = id,
            route = resolved.destination,
            graphEntries = graphEntries,
        )
    }

    private fun createGraphEntries(
        ownerEntryId: NavEntryId,
        resolved: NavGraphResolution,
        previousEntry: NavEntry?,
    ): List<NavGraphEntry> {
        val previousGraphEntries = previousEntry?.graphEntries.orEmpty()
        val commonPrefixSize = previousGraphEntries
            .zip(resolved.graphPath)
            .takeWhile { (entry, route) -> entry.route.name == route.name }
            .size
        val enteredGraphIndex = resolved.enteredGraphRoute?.let { enteredRoute ->
            resolved.graphPath.indexOfFirst { route -> route.name == enteredRoute }
                .also { index ->
                    check(index >= 0) {
                        "Entered navigation graph '$enteredRoute' is missing from its resolved path."
                    }
                }
        }
        val reusablePrefixSize = minOf(
            commonPrefixSize,
            enteredGraphIndex ?: commonPrefixSize,
        )
        // Reuse only the common graph prefix; when entering a new graph, create graph owners from that layer onward.
        val retained = previousGraphEntries.take(reusablePrefixSize)
        val created = resolved.graphPath
            .drop(reusablePrefixSize)
            .mapIndexed { relativeIndex, route ->
                val pathIndex = reusablePrefixSize + relativeIndex
                NavGraphEntry(
                    id = graphEntryId(
                        ownerEntryId = ownerEntryId,
                        pathIndex = pathIndex,
                    ).also { graphEntryId ->
                        check(allocatedEntryIds.add(graphEntryId)) {
                            "Navigation graph entry ID was already allocated: $graphEntryId"
                        }
                    },
                    route = route,
                )
            }
        return retained + created
    }

    private fun NavEntry.matches(resolved: NavGraphResolution): Boolean {
        return route == resolved.destination &&
            graphHierarchy == resolved.hierarchy
    }

    private fun prepareStackSelection(
        beforeState: NavStackSetSnapshot,
        command: NavCommand.SelectStack,
    ): NavStackSetSnapshot? {
        val targetSnapshot = requireNotNull(beforeState[command.stackId]) {
            "Navigation stack '${command.stackId}' is not declared."
        }
        val selectedSnapshot = when (command.selectionMode) {
            NavStackSelectionMode.Preserve -> targetSnapshot
            NavStackSelectionMode.PopToRoot -> {
                NavBackStackSnapshot(listOf(targetSnapshot.entries.first()))
            }
        }
        if (
            command.stackId == beforeState.activeStackId &&
            selectedSnapshot == targetSnapshot
        ) {
            return null
        }
        return beforeState.replaceAndSelectStack(
            targetStackId = command.stackId,
            targetSnapshot = selectedSnapshot,
        )
    }

    private fun prepareDeepLinkNavigation(
        beforeState: NavStackSetSnapshot,
        command: NavCommand.OpenDeepLink,
    ): NavStackSetSnapshot? {
        val targetStackId = command.targetStackId ?: beforeState.activeStackId
        val targetSnapshot = requireNotNull(beforeState[targetStackId]) {
            "Navigation stack '$targetStackId' is not declared."
        }
        val resolved = routeResolver(command.route)
        val nextSnapshot = when (command.launchMode) {
            NavDeepLinkLaunchMode.Push -> {
                NavBackStackSnapshot(
                    targetSnapshot.entries + createEntry(
                        resolved = resolved,
                        previousEntry = targetSnapshot.top,
                    ),
                )
            }

            NavDeepLinkLaunchMode.SingleTop -> {
                if (targetSnapshot.top.matches(resolved)) {
                    targetSnapshot
                } else {
                    NavBackStackSnapshot(
                        targetSnapshot.entries + createEntry(
                            resolved = resolved,
                            previousEntry = targetSnapshot.top,
                        ),
                    )
                }
            }

            NavDeepLinkLaunchMode.ReplaceTop -> {
                if (targetSnapshot.top.matches(resolved)) {
                    targetSnapshot
                } else {
                    NavBackStackSnapshot(
                        targetSnapshot.entries.dropLast(1) + createEntry(
                            resolved = resolved,
                            previousEntry = targetSnapshot.top,
                        ),
                    )
                }
            }

            NavDeepLinkLaunchMode.Reset -> {
                if (
                    targetSnapshot.entries.size == 1 &&
                    targetSnapshot.top.matches(resolved)
                ) {
                    targetSnapshot
                } else {
                    NavBackStackSnapshot(
                        listOf(
                            createEntry(
                                resolved = resolved,
                                previousEntry = null,
                            ),
                        ),
                    )
                }
            }
        }
        val afterState = beforeState.replaceAndSelectStack(
            targetStackId = targetStackId,
            targetSnapshot = nextSnapshot,
        )
        // A deep link changes the target stack and activeStackId together so stack selection and mutation are indivisible.
        return afterState.takeUnless { state -> state == beforeState }
    }

    private fun NavStackSetSnapshot.replaceAndSelectStack(
        targetStackId: NavStackId,
        targetSnapshot: NavBackStackSnapshot,
    ): NavStackSetSnapshot {
        requireNotNull(this[targetStackId]) {
            "Navigation stack '$targetStackId' is not declared."
        }
        val nextStacks = LinkedHashMap(stacks).apply {
            put(targetStackId, targetSnapshot)
        }
        val nextHistory = if (targetStackId == activeStackId) {
            selectionHistory
        } else {
            selectionHistory
                .filterNot { stackId -> stackId == targetStackId } +
                activeStackId
        }
        return NavStackSetSnapshot(
            activeStackId = targetStackId,
            stacks = nextStacks,
            selectionHistory = nextHistory,
        )
    }

    private fun mutationBetween(
        before: NavStackSetSnapshot,
        after: NavStackSetSnapshot,
    ): NavStackMutation {
        val beforeIds = before.allEntries.mapTo(hashSetOf(), NavEntry::id)
        val afterIds = after.allEntries.mapTo(hashSetOf(), NavEntry::id)
        return NavStackMutation(
            added = after.allEntries.filter { it.id !in beforeIds },
            removed = before.allEntries.filter { it.id !in afterIds },
            previousTop = before.activeStack.top,
            nextTop = after.activeStack.top,
        )
    }

    private fun NavStackSetSnapshot.withActiveStack(
        snapshot: NavBackStackSnapshot,
    ): NavStackSetSnapshot {
        return NavStackSetSnapshot(
            activeStackId = activeStackId,
            stacks = LinkedHashMap(stacks).apply {
                put(activeStackId, snapshot)
            },
            selectionHistory = selectionHistory,
        )
    }

    private fun NavStackSetSnapshot.selectPreviousStack(
        targetStackId: NavStackId,
    ): NavStackSetSnapshot {
        check(selectionHistory.lastOrNull() == targetStackId) {
            "Previous navigation stack must be the newest selection-history entry."
        }
        return NavStackSetSnapshot(
            activeStackId = targetStackId,
            stacks = stacks,
            selectionHistory = selectionHistory.dropLast(1),
        )
    }

    /** Creation and restoration entry points. */
    companion object {
        /**
         * Creates a graphless single-stack controller at [startDestination].
         *
         * @param startDestination immutable root route
         * @param entryIdFactory identity source retained for future entries
         */
        fun create(
            startDestination: NavRoute,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            return create(
                configuration = NavStackConfiguration.single(startDestination),
                entryIdFactory = entryIdFactory,
            )
        }

        /**
         * Creates a graph-aware single-stack controller at [NavGraph.startDestination].
         *
         * @param graph immutable route and deep-link registry
         * @param entryIdFactory identity source retained for future entries
         */
        fun create(
            graph: NavGraph,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            return create(
                configuration = NavStackConfiguration.single(graph.startDestination),
                graph = graph,
                entryIdFactory = entryIdFactory,
            )
        }

        /**
         * Creates a graphless controller from a single- or multi-stack [configuration].
         *
         * Initial routes are accepted directly and deep-link resolution is unsupported.
         *
         * @param configuration retained stack declarations and root-Back policy
         * @param entryIdFactory identity source retained for future entries
         */
        fun create(
            configuration: NavStackConfiguration,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            val resolver = directResolver()
            return NavBackStackController(
                initialState = createInitialState(
                    configuration = configuration,
                    entryIdFactory = entryIdFactory,
                    routeResolver = resolver,
                ),
                entryIdFactory = entryIdFactory,
                routeResolver = resolver,
                deepLinkResolver = null,
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

        /**
         * Creates a graph-aware controller from [configuration].
         *
         * Every stack start route is resolved through [graph] and receives independent graph-owner
         * identities even when multiple stacks begin inside the same graph.
         *
         * @param configuration retained stack declarations and root-Back policy
         * @param graph immutable route and deep-link registry
         * @param entryIdFactory identity source retained for future entries
         * @throws IllegalArgumentException if a configured start route is not registered
         */
        fun create(
            configuration: NavStackConfiguration,
            graph: NavGraph,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            return NavBackStackController(
                initialState = createInitialState(
                    configuration = configuration,
                    entryIdFactory = entryIdFactory,
                    routeResolver = graph::resolve,
                ),
                entryIdFactory = entryIdFactory,
                routeResolver = graph::resolve,
                deepLinkResolver = graph::resolveDeepLink,
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

        /**
         * Restores a graphless single-stack controller from [snapshot].
         *
         * @throws IllegalArgumentException if the snapshot contains graph-owner entries
         */
        fun restore(
            snapshot: NavBackStackSnapshot,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            require(snapshot.entries.all { entry -> entry.graphEntries.isEmpty() }) {
                "A graph-owned back stack must be restored with its NavGraph."
            }
            val state = singleStackState(snapshot)
            val resolver = directResolver()
            return NavBackStackController(
                initialState = state,
                entryIdFactory = entryIdFactory,
                routeResolver = resolver,
                deepLinkResolver = null,
                rootBackBehavior = NavRootBackBehavior.Delegate,
            )
        }

        /**
         * Restores a graph-aware single-stack controller and validates every saved route hierarchy.
         *
         * @throws IllegalArgumentException if a saved route is missing, changed destination, or moved graphs
         */
        fun restore(
            snapshot: NavBackStackSnapshot,
            graph: NavGraph,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            val state = singleStackState(snapshot)
            validateGraphState(state, graph)
            return NavBackStackController(
                initialState = state,
                entryIdFactory = entryIdFactory,
                routeResolver = graph::resolve,
                deepLinkResolver = graph::resolveDeepLink,
                rootBackBehavior = NavRootBackBehavior.Delegate,
            )
        }

        /**
         * Restores graphless multi-stack [state] against the current [configuration].
         *
         * @throws IllegalArgumentException if stack IDs differ or state contains graph owners
         */
        fun restore(
            state: NavStackSetSnapshot,
            configuration: NavStackConfiguration,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            requireConfigurationMatches(state, configuration)
            require(state.allEntries.all { entry -> entry.graphEntries.isEmpty() }) {
                "Graph-owned back stacks must be restored with their NavGraph."
            }
            val resolver = directResolver()
            return NavBackStackController(
                initialState = state,
                entryIdFactory = entryIdFactory,
                routeResolver = resolver,
                deepLinkResolver = null,
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

        /**
         * Restores graph-aware multi-stack [state] and validates configuration and graph compatibility.
         *
         * @throws IllegalArgumentException if stack IDs differ or any saved route hierarchy changed
         */
        fun restore(
            state: NavStackSetSnapshot,
            configuration: NavStackConfiguration,
            graph: NavGraph,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            requireConfigurationMatches(state, configuration)
            validateGraphState(state, graph)
            return NavBackStackController(
                initialState = state,
                entryIdFactory = entryIdFactory,
                routeResolver = graph::resolve,
                deepLinkResolver = graph::resolveDeepLink,
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

        private fun createInitialState(
            configuration: NavStackConfiguration,
            entryIdFactory: NavEntryIdFactory,
            routeResolver: (NavRoute) -> NavGraphResolution,
        ): NavStackSetSnapshot {
            val stacks = linkedMapOf<NavStackId, NavBackStackSnapshot>()
            configuration.stacks.forEach { stack ->
                val rootId = entryIdFactory.nextId()
                val resolvedStart = routeResolver(stack.startDestination)
                // Each stack creates independent root destination and graph owners to avoid sharing lifecycle state across stacks.
                stacks[stack.id] = NavBackStackSnapshot(
                    entries = listOf(
                        NavEntry(
                            id = rootId,
                            route = resolvedStart.destination,
                            graphEntries = freshGraphEntries(
                                ownerEntryId = rootId,
                                graphPath = resolvedStart.graphPath,
                            ),
                        ),
                    ),
                )
            }
            return NavStackSetSnapshot(
                activeStackId = configuration.initialStackId,
                stacks = stacks,
            )
        }

        private fun directResolver(): (NavRoute) -> NavGraphResolution {
            return { route ->
                NavGraphResolution(
                    destination = route,
                    graphPath = emptyList(),
                )
            }
        }

        private fun singleStackState(
            snapshot: NavBackStackSnapshot,
        ): NavStackSetSnapshot {
            return NavStackSetSnapshot(
                activeStackId = NavStackId.Default,
                stacks = linkedMapOf(NavStackId.Default to snapshot),
            )
        }

        private fun requireConfigurationMatches(
            state: NavStackSetSnapshot,
            configuration: NavStackConfiguration,
        ) {
            require(state.stacks.keys == configuration.stacks.mapTo(linkedSetOf(), NavStackSpec::id)) {
                "Restored navigation stack IDs do not match the current configuration."
            }
        }

        private fun validateGraphState(
            state: NavStackSetSnapshot,
            graph: NavGraph,
        ) {
            // Restored routes must still resolve to the same destination and graph hierarchy, otherwise old owner state is unsafe to reuse.
            state.allEntries.forEach { entry ->
                val resolved = graph.resolve(entry.route)
                require(entry.route == resolved.destination) {
                    "Restored destination '${entry.route}' no longer resolves to itself."
                }
                require(entry.graphHierarchy == resolved.hierarchy) {
                    "Restored destination '${entry.route}' moved to a different navigation graph."
                }
            }
        }

        private fun freshGraphEntries(
            ownerEntryId: NavEntryId,
            graphPath: List<NavRoute>,
        ): List<NavGraphEntry> {
            return graphPath.mapIndexed { index, route ->
                NavGraphEntry(
                    id = graphEntryId(
                        ownerEntryId = ownerEntryId,
                        pathIndex = index,
                    ),
                    route = route,
                )
            }
        }

        private fun graphEntryId(
            ownerEntryId: NavEntryId,
            pathIndex: Int,
        ): NavEntryId {
            return NavEntryId("${ownerEntryId.value}#graph:$pathIndex")
        }
    }
}
