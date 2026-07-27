package com.viewcompose.navigation.core

enum class NavLaunchMode {
    Standard,
    SingleTop,
}

sealed interface NavCommand {
    data class Push(
        val route: NavRoute,
        val launchMode: NavLaunchMode = NavLaunchMode.Standard,
    ) : NavCommand

    data object Pop : NavCommand

    data class ReplaceTop(
        val route: NavRoute,
    ) : NavCommand

    data class Reset(
        val route: NavRoute,
    ) : NavCommand

    data class SelectStack(
        val stackId: NavStackId,
        val selectionMode: NavStackSelectionMode = NavStackSelectionMode.Preserve,
    ) : NavCommand

    /**
     * Returns to the newest stack in selection history without adding the current stack back.
     *
     * Applications normally reach this command through system Back when
     * [NavRootBackBehavior.PreviousStack] is configured.
     */
    data object PopStackHistory : NavCommand
}

enum class NavNoChangeReason {
    CannotPopRoot,
    AlreadyAtDestination,
    AlreadySelectedStack,
}

sealed interface NavPreparation {
    data class Ready(
        val transaction: NavTransaction,
    ) : NavPreparation

    data class NoChange(
        val reason: NavNoChangeReason,
        val snapshot: NavBackStackSnapshot,
    ) : NavPreparation
}

data class NavStackMutation(
    val added: List<NavEntry>,
    val removed: List<NavEntry>,
    val previousTop: NavEntry,
    val nextTop: NavEntry,
)

enum class NavTransactionStatus {
    Prepared,
    Committed,
    RolledBack,
}

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
    var status: NavTransactionStatus = NavTransactionStatus.Prepared
        private set

    @Synchronized
    fun commit(): NavBackStackSnapshot {
        check(status == NavTransactionStatus.Prepared) {
            "Navigation transaction $transactionId is already $status."
        }
        val committed = owner.commit(this)
        status = NavTransactionStatus.Committed
        return committed
    }

    @Synchronized
    fun rollback() {
        check(status == NavTransactionStatus.Prepared) {
            "Navigation transaction $transactionId is already $status."
        }
        owner.rollback(this)
        status = NavTransactionStatus.RolledBack
    }

    @Synchronized
    override fun close() {
        if (status == NavTransactionStatus.Prepared) {
            rollback()
        }
    }
}

class NavBackStackController private constructor(
    initialState: NavStackSetSnapshot,
    private val entryIdFactory: NavEntryIdFactory,
    private val routeResolver: (NavRoute) -> NavGraphResolution,
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
    fun snapshot(): NavBackStackSnapshot = currentState.activeStack

    @Synchronized
    fun stackStateSnapshot(): NavStackSetSnapshot = currentState

    @Synchronized
    fun stackSnapshot(stackId: NavStackId): NavBackStackSnapshot {
        return requireNotNull(currentState[stackId]) {
            "Navigation stack '$stackId' is not declared."
        }
    }

    @Synchronized
    fun retainedEntries(): List<NavEntry> = currentState.allEntries

    @Synchronized
    fun systemBackCommand(): NavCommand? {
        return when {
            currentState.activeStack.entries.size > 1 -> NavCommand.Pop
            rootBackBehavior == NavRootBackBehavior.PreviousStack &&
                currentState.selectionHistory.isNotEmpty() -> NavCommand.PopStackHistory
            else -> null
        }
    }

    @Synchronized
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
        val nextStacks = LinkedHashMap(beforeState.stacks).apply {
            put(command.stackId, selectedSnapshot)
        }
        val nextHistory = if (command.stackId == beforeState.activeStackId) {
            beforeState.selectionHistory
        } else {
            beforeState.selectionHistory
                .filterNot { stackId -> stackId == command.stackId } +
                beforeState.activeStackId
        }
        return NavStackSetSnapshot(
            activeStackId = command.stackId,
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

    companion object {
        fun create(
            startDestination: NavRoute,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            return create(
                configuration = NavStackConfiguration.single(startDestination),
                entryIdFactory = entryIdFactory,
            )
        }

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
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

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
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

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
                rootBackBehavior = NavRootBackBehavior.Delegate,
            )
        }

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
                rootBackBehavior = NavRootBackBehavior.Delegate,
            )
        }

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
                rootBackBehavior = configuration.rootBackBehavior,
            )
        }

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
