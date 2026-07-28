package com.viewcompose.navigation.core

/**
 * push 命令的入栈模式。
 * Stack insertion mode for push commands.
 */
enum class NavLaunchMode {
    Standard,
    SingleTop,
}

/**
 * 导航控制器支持的状态变更命令。
 * State mutation commands supported by the navigation controller.
 */
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
     * 以一个原子事务变更目标 stack 并选中它。
     * Mutates a destination stack and selects it as one atomic transaction.
     *
     * 该命令携带已由 graph 解析过的 route；应用通常通过 host deep-link API 间接创建。
     * This command carries a graph-resolved route. Applications normally create it through their
     * host's deep-link API instead of constructing it directly.
     */
    data class OpenDeepLink(
        val route: NavRoute,
        val targetStackId: NavStackId? = null,
        val launchMode: NavDeepLinkLaunchMode = NavDeepLinkLaunchMode.Reset,
    ) : NavCommand

    /**
     * 回到 selection history 中最新的 stack，且不把当前 stack 再写回历史。
     * Returns to the newest stack in selection history without adding the current stack back.
     *
     * 应用通常在 [NavRootBackBehavior.PreviousStack] 配置下通过系统 Back 触达该命令。
     * Applications normally reach this command through system Back when
     * [NavRootBackBehavior.PreviousStack] is configured.
     */
    data object PopStackHistory : NavCommand
}

/**
 * prepare 没有产生状态变化时的原因。
 * Reason why prepare produced no state change.
 */
enum class NavNoChangeReason {
    CannotPopRoot,
    AlreadyAtDestination,
    AlreadySelectedStack,
}

/**
 * 导航命令预执行结果。
 * Result of preparing a navigation command.
 */
sealed interface NavPreparation {
    data class Ready(
        val transaction: NavTransaction,
    ) : NavPreparation

    data class NoChange(
        val reason: NavNoChangeReason,
        val snapshot: NavBackStackSnapshot,
    ) : NavPreparation
}

/**
 * 一个导航事务中新增和移除的 entry 摘要。
 * Summary of entries added and removed by one navigation transaction.
 */
data class NavStackMutation(
    val added: List<NavEntry>,
    val removed: List<NavEntry>,
    val previousTop: NavEntry,
    val nextTop: NavEntry,
)

/**
 * 导航事务状态。
 * Navigation transaction state.
 */
enum class NavTransactionStatus {
    Prepared,
    Committed,
    RolledBack,
}

/**
 * 可提交或回滚的导航事务。
 * Navigation transaction that can be committed or rolled back.
 *
 * 事务在 prepare 阶段已经计算好 afterState，只有 commit 才会发布到 controller。
 * The transaction computes afterState during prepare, and only commit publishes it to the controller.
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

/**
 * 纯 Kotlin 导航回退栈控制器。
 * Pure Kotlin navigation back-stack controller.
 *
 * 所有状态变更先 prepare 成事务，再由 host 渲染成功后 commit；失败路径调用 rollback。
 * All state changes are prepared as transactions first, then committed by the host after rendering succeeds; failure paths roll back.
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
    fun resolveDeepLink(uri: String): NavDeepLinkResolution {
        return deepLinkResolver?.invoke(uri) ?: NavDeepLinkResolution.Unsupported
    }

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
        // mutation 覆盖所有保留 stack，而不只活跃 stack，方便 host 同步 owner/session 生命周期。
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
        // 只复用共同 graph 前缀；进入新 graph 时从该层开始创建新的 graph owner。
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
        // deep link 同时变更目标 stack 内容与 activeStackId，保证切栈和入栈不可分割。
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
                deepLinkResolver = null,
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
                deepLinkResolver = graph::resolveDeepLink,
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
                deepLinkResolver = null,
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
                deepLinkResolver = graph::resolveDeepLink,
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
                deepLinkResolver = null,
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
                // 每个 stack 都独立创建根 destination 和 graph owner，避免多栈间共享生命周期状态。
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
            // 恢复的 route 必须仍解析到同一 destination 和 graph hierarchy，否则旧 owner 状态已不可安全复用。
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
