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
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.rememberSaveable

/**
 * 标记 NavHost 事务失败时所在的执行阶段。
 * Marks the execution phase where a NavHost transaction failed.
 */
enum class NavFailurePhase {
    DestinationPreparation,
    DestinationRefresh,
    DestinationStage,
    StackCommit,
    CommitEffects,
}

/**
 * 描述一次导航渲染或提交失败的完整上下文。
 * Describes the full context captured when navigation rendering or commit fails.
 */
data class NavFailure(
    val phase: NavFailurePhase,
    val failedEntry: NavEntry?,
    val frameReport: RenderFrameReport?,
    val cause: Throwable?,
    val stackCommitted: Boolean,
)

/**
 * 将 [NavFailure] 包装成运行时异常，便于未处理失败沿调用栈显式暴露。
 * Wraps [NavFailure] as a runtime exception so unhandled failures surface explicitly.
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

/**
 * 导航命令的同步结果，始终携带产出时的栈快照。
 * Synchronous result of a navigation command, always carrying the observed stack snapshot.
 */
sealed interface NavResult {
    /**
     * 结果产生时完整的已提交多栈状态。
     * Complete committed multi-stack state observed when this result was produced.
     */
    val stackState: NavStackSetSnapshot

    /**
     * 面向单目的地调用方的当前活跃栈投影。
     * Active-stack projection for destination-oriented call sites.
     */
    val snapshot: NavBackStackSnapshot
        get() = stackState.activeStack

    /**
     * 命令已改变栈，并返回具体的栈变更摘要。
     * The command changed the stack and returns the concrete mutation summary.
     */
    data class Committed(
        override val stackState: NavStackSetSnapshot,
        val mutation: NavStackMutation,
    ) : NavResult

    /**
     * 命令合法但没有造成状态变化。
     * The command was valid but did not change navigation state.
     */
    data class NoChange(
        override val stackState: NavStackSetSnapshot,
        val reason: NavNoChangeReason,
    ) : NavResult

    /**
     * 命令在转场期间被排队，稍后由宿主运行时串行执行。
     * The command was queued during a transition and will run serially later.
     */
    data class Queued(
        override val stackState: NavStackSetSnapshot,
    ) : NavResult

    /**
     * 命令失败，失败上下文可用于日志、降级或测试断言。
     * The command failed; the failure context can drive logging, fallback, or tests.
     */
    data class Failed(
        override val stackState: NavStackSetSnapshot,
        val failure: NavFailure,
    ) : NavResult
}

/**
 * 深链解析与导航的结果集合。
 * Result set for deep-link resolution and navigation.
 */
sealed interface NavDeepLinkResult {
    /**
     * 深链已匹配并尝试执行对应导航命令。
     * The deep link matched and attempted the corresponding navigation command.
     */
    data class Navigated(
        val match: NavDeepLinkMatch,
        val navigationResult: NavResult,
    ) : NavDeepLinkResult

    /**
     * 当前图中没有任何深链规则匹配该 URI。
     * No deep-link rule in the current graph matched the URI.
     */
    data object NoMatch : NavDeepLinkResult

    /**
     * URI 命中规则但因白名单、安全或参数校验被拒绝。
     * The URI matched a rule but was rejected by allowlist, safety, or argument checks.
     */
    data class Rejected(
        val rejection: NavDeepLinkRejection,
    ) : NavDeepLinkResult

    /**
     * 控制器未绑定导航图，因此不支持图级深链解析。
     * This controller was created without a navigation graph and cannot resolve graph deep links.
     */
    data object Unsupported : NavDeepLinkResult
}

/**
 * 面向应用层的稳定控制器，管理一个或多个框架持有的导航栈。
 * Stable application-facing handle for one or more framework-owned navigation stacks.
 *
 * 同一个控制器同一时间只能挂载到一个 [NavHost]。导航命令要求宿主已连接，以保证每次栈变更、
 * 目的地渲染和生命周期变更处于同一个事务中。
 * A controller can be mounted by only one [NavHost] at a time. Commands require that host to be
 * attached so every stack mutation shares the destination render and lifecycle transaction.
 *
 * 通过 [rememberNavHostController] 记忆时，栈、目的地/图实例 ID、路由参数以及每个目的地/图
 * 的保存状态命名空间都会随宿主重建而恢复。
 * When remembered with [rememberNavHostController], the stack, destination and graph instance IDs,
 * route arguments, and every destination/graph saved-state namespace survive host recreation.
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

    /**
     * 当前活跃栈的即时快照。
     * Immediate snapshot of the currently active stack.
     */
    val snapshot: NavBackStackSnapshot
        get() = backStackController.snapshot()

    /**
     * 所有栈的即时快照，包含当前活跃栈和选择历史。
     * Immediate snapshot of all stacks, including the active stack and selection history.
     */
    val stackState: NavStackSetSnapshot
        get() = backStackController.stackStateSnapshot()

    /**
     * 可观察的完整栈状态，供选中标签 UI 和导航诊断使用。
     * Observable complete stack state for selected-tab UI and navigation diagnostics.
     */
    val navigationState: State<NavStackSetSnapshot>
        get() = mutableNavigationState

    /**
     * 当前展示的栈 ID。
     * ID of the stack currently presented by the host.
     */
    val activeStackId: NavStackId
        get() = stackState.activeStackId

    /**
     * 控制器是否已绑定到一个活动 [NavHost]。
     * Whether this controller is currently bound to an active [NavHost].
     */
    val isAttached: Boolean
        get() = binding != null

    /**
     * 返回指定栈的快照，不会切换当前活跃栈。
     * Returns a snapshot for [stackId] without changing the active stack.
     */
    fun stackSnapshot(stackId: NavStackId): NavBackStackSnapshot {
        return backStackController.stackSnapshot(stackId)
    }

    /**
     * 将 [route] 推入当前活跃栈，并按 [launchMode] 处理复用或新建目的地。
     * Pushes [route] onto the active stack, applying [launchMode] reuse or creation rules.
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

    /**
     * 弹出当前活跃栈顶部目的地。
     * Pops the top destination from the active stack.
     */
    @MainThread
    fun popBackStack(): NavResult = execute(NavCommand.Pop)

    /**
     * 原子替换当前活跃栈的顶部目的地。
     * Atomically replaces the top destination on the active stack.
     */
    @MainThread
    fun replaceTop(route: NavRoute): NavResult {
        return execute(NavCommand.ReplaceTop(route))
    }

    /**
     * 将当前活跃栈重置为单个 [route]。
     * Resets the active stack to a single [route].
     */
    @MainThread
    fun reset(route: NavRoute): NavResult {
        return execute(NavCommand.Reset(route))
    }

    /**
     * 原子展示 [stackId]，同时保留其他栈及其 owner。
     * Atomically presents [stackId] while retaining every other stack and its owners.
     */
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
     * 解析导航图白名单 URI，并原子更新和选择目标栈。
     * Resolves an allowlisted graph URI and atomically updates and selects its destination stack.
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

    /**
     * 解析 Android [Uri]，行为与字符串深链入口一致。
     * Resolves an Android [Uri] with the same behavior as the string deep-link entry point.
     */
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

    /**
     * 将原生 Android VIEW intent 映射为同一套严格的图级深链事务。
     * Maps a native Android VIEW intent into the same strict graph deep-link transaction.
     */
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
    /**
     * 在宿主事务内执行命令。
     * Executes a command inside the host transaction.
     */
    fun navigate(command: NavCommand): NavResult

    /**
     * 读取宿主当前可恢复状态。
     * Reads the host's current restorable state.
     */
    fun saveState(): NavHostRestorableState
}

/**
 * 创建以单个起始目的地启动的控制器，不绑定导航图。
 * Creates a controller started from a single destination without graph resolution.
 */
fun createNavHostController(
    startDestination: NavRoute,
): NavHostController {
    return createNavHostController(
        startDestination = startDestination,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

/**
 * 创建由 [graph] 解析目的地和深链的控制器。
 * Creates a controller whose destinations and deep links are resolved by [graph].
 */
fun createNavHostController(
    graph: NavGraph,
): NavHostController {
    return createNavHostController(
        graph = graph,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

/**
 * 创建多栈控制器，不使用图级路由解析。
 * Creates a multi-stack controller without graph-based route resolution.
 */
fun createNavHostController(
    stackConfiguration: NavStackConfiguration,
): NavHostController {
    return createNavHostController(
        stackConfiguration = stackConfiguration,
        entryIdFactory = NavEntryIdFactory.random(),
    )
}

/**
 * 创建多栈控制器，并通过共享 [graph] 解析各栈目的地。
 * Creates a multi-stack controller that resolves every stack through shared [graph].
 */
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
 * 记忆控制器，并通过当前 saveable-state registry 恢复完整宿主状态。
 * Remembers a controller and restores its complete host state through the current saveable-state
 * registry.
 *
 * 无效或不兼容的恢复数据会被丢弃，重新使用 [startDestination] 初始化。
 * Invalid or incompatible restored data is discarded in favor of [startDestination].
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
 * 记忆一个通过 [graph] 解析目的地的控制器。
 * Remembers a controller whose destinations are resolved through [graph].
 *
 * 进入图路由会原子打开其叶子起始目的地，并在结果 entry 上记录稳定的父图实例。
 * Entering a graph route atomically opens its leaf start destination and records stable parent-graph
 * instances on the resulting entry.
 *
 * 目的地会复用公共图实例，直到再次显式进入该图。
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

/**
 * 记忆相互独立保留的导航栈，不启用图级路由解析。
 * Remembers independently retained navigation stacks without graph-based route resolution.
 */
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

/**
 * 记忆相互独立保留的导航栈，并让这些栈共享 [graph] 路由解析。
 * Remembers independently retained navigation stacks whose routes share [graph].
 */
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
