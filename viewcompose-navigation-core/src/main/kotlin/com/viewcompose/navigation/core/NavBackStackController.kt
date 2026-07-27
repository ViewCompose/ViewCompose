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
}

enum class NavNoChangeReason {
    CannotPopRoot,
    AlreadyAtDestination,
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
    initialSnapshot: NavBackStackSnapshot,
    private val entryIdFactory: NavEntryIdFactory,
    private val routeResolver: (NavRoute) -> NavGraphResolution,
) {
    private var currentSnapshot = initialSnapshot
    private val allocatedEntryIds = linkedSetOf<NavEntryId>().apply {
        initialSnapshot.entries.forEach { entry ->
            add(entry.id)
            entry.graphEntries.forEach { graphEntry -> add(graphEntry.id) }
        }
    }
    private var pendingTransactionId: Long? = null
    private var nextTransactionId = 0L

    @Synchronized
    fun snapshot(): NavBackStackSnapshot = currentSnapshot

    @Synchronized
    fun prepare(command: NavCommand): NavPreparation {
        check(pendingTransactionId == null) {
            "A navigation transaction is already prepared. Commit or roll it back first."
        }
        val before = currentSnapshot
        val after = when (command) {
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
                NavBackStackSnapshot(
                    before.entries + createEntry(
                        resolved = resolved,
                        previousEntry = before.top,
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
                NavBackStackSnapshot(before.entries.dropLast(1))
            }

            is NavCommand.ReplaceTop -> {
                val resolved = routeResolver(command.route)
                if (before.top.matches(resolved)) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                NavBackStackSnapshot(
                    before.entries.dropLast(1) + createEntry(
                        resolved = resolved,
                        previousEntry = before.top,
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
        val transactionId = ++nextTransactionId
        pendingTransactionId = transactionId
        return NavPreparation.Ready(
            transaction = NavTransaction(
                owner = this,
                transactionId = transactionId,
                command = command,
                before = before,
                after = after,
                mutation = mutationBetween(before, after),
            ),
        )
    }

    @Synchronized
    internal fun commit(transaction: NavTransaction): NavBackStackSnapshot {
        requirePrepared(transaction)
        check(currentSnapshot == transaction.before) {
            "Navigation state changed while transaction ${transaction.transactionId} was prepared."
        }
        currentSnapshot = transaction.after
        pendingTransactionId = null
        return currentSnapshot
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

    private fun mutationBetween(
        before: NavBackStackSnapshot,
        after: NavBackStackSnapshot,
    ): NavStackMutation {
        val beforeIds = before.entries.mapTo(hashSetOf(), NavEntry::id)
        val afterIds = after.entries.mapTo(hashSetOf(), NavEntry::id)
        return NavStackMutation(
            added = after.entries.filter { it.id !in beforeIds },
            removed = before.entries.filter { it.id !in afterIds },
            previousTop = before.top,
            nextTop = after.top,
        )
    }

    companion object {
        fun create(
            startDestination: NavRoute,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            val rootId = entryIdFactory.nextId()
            val directResolver: (NavRoute) -> NavGraphResolution = { route ->
                NavGraphResolution(
                    destination = route,
                    graphPath = emptyList(),
                )
            }
            return NavBackStackController(
                initialSnapshot = NavBackStackSnapshot(
                    entries = listOf(
                        NavEntry(
                            id = rootId,
                            route = startDestination,
                        ),
                    ),
                ),
                entryIdFactory = entryIdFactory,
                routeResolver = directResolver,
            )
        }

        fun create(
            graph: NavGraph,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            val rootId = entryIdFactory.nextId()
            val resolvedStart = graph.resolve(graph.startDestination)
            val rootGraphEntries = freshGraphEntries(
                ownerEntryId = rootId,
                graphPath = resolvedStart.graphPath,
            )
            return NavBackStackController(
                initialSnapshot = NavBackStackSnapshot(
                    entries = listOf(
                        NavEntry(
                            id = rootId,
                            route = resolvedStart.destination,
                            graphEntries = rootGraphEntries,
                        ),
                    ),
                ),
                entryIdFactory = entryIdFactory,
                routeResolver = graph::resolve,
            )
        }

        fun restore(
            snapshot: NavBackStackSnapshot,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            require(snapshot.entries.all { entry -> entry.graphEntries.isEmpty() }) {
                "A graph-owned back stack must be restored with its NavGraph."
            }
            return NavBackStackController(
                initialSnapshot = snapshot,
                entryIdFactory = entryIdFactory,
                routeResolver = { route ->
                    NavGraphResolution(
                        destination = route,
                        graphPath = emptyList(),
                    )
                },
            )
        }

        fun restore(
            snapshot: NavBackStackSnapshot,
            graph: NavGraph,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            snapshot.entries.forEach { entry ->
                val resolved = graph.resolve(entry.route)
                require(entry.route == resolved.destination) {
                    "Restored destination '${entry.route}' no longer resolves to itself."
                }
                require(entry.graphHierarchy == resolved.hierarchy) {
                    "Restored destination '${entry.route}' moved to a different navigation graph."
                }
            }
            return NavBackStackController(
                initialSnapshot = snapshot,
                entryIdFactory = entryIdFactory,
                routeResolver = graph::resolve,
            )
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
