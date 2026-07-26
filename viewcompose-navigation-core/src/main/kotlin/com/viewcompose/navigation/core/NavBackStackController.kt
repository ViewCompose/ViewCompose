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
) {
    private var currentSnapshot = initialSnapshot
    private val allocatedEntryIds = initialSnapshot.entries
        .mapTo(linkedSetOf(), NavEntry::id)
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
                if (
                    command.launchMode == NavLaunchMode.SingleTop &&
                    before.top.route == command.route
                ) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                NavBackStackSnapshot(
                    before.entries + createEntry(command.route),
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
                if (before.top.route == command.route) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                NavBackStackSnapshot(
                    before.entries.dropLast(1) + createEntry(command.route),
                )
            }

            is NavCommand.Reset -> {
                if (before.entries.size == 1 && before.top.route == command.route) {
                    return NavPreparation.NoChange(
                        reason = NavNoChangeReason.AlreadyAtDestination,
                        snapshot = before,
                    )
                }
                NavBackStackSnapshot(listOf(createEntry(command.route)))
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

    private fun createEntry(route: NavRoute): NavEntry {
        val id = entryIdFactory.nextId()
        check(allocatedEntryIds.add(id)) {
            "NavEntryIdFactory returned an entry ID that was already allocated: $id"
        }
        return NavEntry(
            id = id,
            route = route,
        )
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
            )
        }

        fun restore(
            snapshot: NavBackStackSnapshot,
            entryIdFactory: NavEntryIdFactory = NavEntryIdFactory.random(),
        ): NavBackStackController {
            return NavBackStackController(
                initialSnapshot = snapshot,
                entryIdFactory = entryIdFactory,
            )
        }
    }
}
