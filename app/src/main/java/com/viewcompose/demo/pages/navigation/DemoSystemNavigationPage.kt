package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.navigation.NavDeepLinkResult
import com.viewcompose.navigation.NavFailure
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavPanePolicy
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.core.NavDeepLink
import com.viewcompose.navigation.core.NavDeepLinkArgumentType
import com.viewcompose.navigation.core.NavRootBackBehavior
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSelectionMode
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.navGraph
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.Scaffold
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.node.ImageSource

@ViewComposePreview(name = "System navigation", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewSystemNavigation() {
    SystemNavigationDemoPage(
        root = null,
        externalDeepLinkOutcome = mutableStateOf(SystemNavigationDeepLinkOutcome.None),
        diagnosticsEnabled = false,
        sharedContentEnabled = true,
        onControllerReady = {},
        onExit = {},
    )
}

internal fun UiTreeBuilder.SystemNavigationDemoPage(
    root: ViewGroup?,
    externalDeepLinkOutcome: MutableState<SystemNavigationDeepLinkOutcome>,
    diagnosticsEnabled: Boolean,
    sharedContentEnabled: Boolean = true,
    scenario: DemoScenarioSpec? = null,
    onControllerReady: (NavHostController) -> Unit,
    onExit: () -> Unit,
) {
    val sessionGeneration = rememberSaveable(key = "system-navigation-session-generation") {
        mutableStateOf(0)
    }
    key(sessionGeneration.value) {
        SystemNavigationSession(
            root = root,
            externalDeepLinkOutcome = externalDeepLinkOutcome,
            diagnosticsEnabled = diagnosticsEnabled,
            sharedContentEnabled = sharedContentEnabled,
            scenario = scenario,
            onControllerReady = onControllerReady,
            onExit = onExit,
            onReset = {
                externalDeepLinkOutcome.value = SystemNavigationDeepLinkOutcome.None
                sessionGeneration.value += 1
            },
        )
    }
}

private fun UiTreeBuilder.SystemNavigationSession(
    root: ViewGroup?,
    externalDeepLinkOutcome: MutableState<SystemNavigationDeepLinkOutcome>,
    diagnosticsEnabled: Boolean,
    sharedContentEnabled: Boolean,
    scenario: DemoScenarioSpec?,
    onControllerReady: (NavHostController) -> Unit,
    onExit: () -> Unit,
    onReset: () -> Unit,
) {
    val adaptivePanes = rememberSaveable(key = "system-navigation-adaptive-panes") {
        mutableStateOf(true)
    }
    val systemBackEnabled = rememberSaveable(key = "system-navigation-system-back") {
        mutableStateOf(true)
    }
    val motionEnabled = rememberSaveable(key = "system-navigation-motion") {
        mutableStateOf(true)
    }
    val lastEvent = remember { mutableStateOf<SystemNavigationEvent>(SystemNavigationEvent.Waiting) }
    val controller = rememberNavHostController(
        stackConfiguration = SystemNavigationDemoModel.StackConfiguration,
        graph = SystemNavigationDemoModel.Graph,
    )
    val stackState = controller.navigationState.value
    val selectedStackIndex = SystemNavigationDemoModel.StackIds
        .indexOf(stackState.activeStackId)
        .coerceAtLeast(0)
    val activeStackLabel = stringResource(
        SystemNavigationDemoModel.stackLabelRes(stackState.activeStackId),
    )
    val windowTitle = stringResource(R.string.demo_system_nav_window_title, activeStackLabel)

    SideEffect {
        onControllerReady(controller)
        root?.context?.findAppCompatActivity()?.title = windowTitle
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.demo_system_nav_toolbar_title, activeStackLabel),
                navigationIcon = {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.demo_system_nav_exit_description),
                        onClick = onExit,
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar(
                selectedIndex = selectedStackIndex,
                onItemSelected = { selectedIndex ->
                    val target = SystemNavigationDemoModel.StackIds[selectedIndex]
                    lastEvent.value = controller
                        .selectStack(
                            stackId = target,
                            selectionMode = NavStackSelectionMode.Preserve,
                        )
                        .toDemoEvent(
                            action = SystemNavigationAction.SwitchStack,
                            detail = target.value,
                        )
                },
                modifier = Modifier
                    .scenarioTarget(scenario, DemoAutomationRole.Ready),
            ) {
                Item(
                    key = "home",
                    label = stringResource(R.string.demo_system_nav_stack_home),
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                )
                Item(
                    key = "discover",
                    label = stringResource(R.string.demo_system_nav_stack_discover),
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                )
                Item(
                    key = "account",
                    label = stringResource(R.string.demo_system_nav_stack_account),
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .systemBarsInsetsPadding()
            .backgroundColor(Theme.colors.background)
            .scenarioTarget(scenario, DemoAutomationRole.Root),
    ) {
        NavHost(
            controller = controller,
            transitionSpec = if (motionEnabled.value) {
                NavTransitionSpec.Default
            } else {
                NavTransitionSpec.None
            },
            panePolicy = if (adaptivePanes.value) {
                SystemNavigationDemoModel.AdaptivePanePolicy
            } else {
                NavPanePolicy.Single
            },
            systemBackEnabled = systemBackEnabled.value,
            debug = diagnosticsEnabled,
            debugTag = "SystemNavigationDemoHost",
            onFailure = { failure ->
                lastEvent.value = failure.toDemoEvent()
            },
            modifier = Modifier
                .fillMaxSize()
                .scenarioTarget(scenario, DemoAutomationRole.Target),
        ) { entry ->
            SystemNavigationDestinationPage(
                controller = controller,
                entry = entry,
                adaptivePanes = adaptivePanes,
                systemBackEnabled = systemBackEnabled,
                motionEnabled = motionEnabled,
                lastEvent = lastEvent,
                externalDeepLinkOutcome = externalDeepLinkOutcome,
                sharedContentEnabled = sharedContentEnabled,
                scenario = scenario,
                onReset = onReset,
            )
        }
    }
}

internal sealed interface SystemNavigationEvent {
    data object Waiting : SystemNavigationEvent

    data class Result(
        val action: SystemNavigationAction,
        val result: SystemNavigationResultSummary,
        val detail: String? = null,
    ) : SystemNavigationEvent

    data class DeepLink(
        val outcome: SystemNavigationDeepLinkOutcome,
    ) : SystemNavigationEvent

    data class HostFailure(
        val phase: String,
        val route: String,
        val stackCommitted: Boolean,
    ) : SystemNavigationEvent

    data class PanePolicyChanged(val adaptive: Boolean) : SystemNavigationEvent

    data class MotionChanged(val enabled: Boolean) : SystemNavigationEvent

    data class SystemBackChanged(val enabled: Boolean) : SystemNavigationEvent

    data class AdaptiveStackSeeded(
        val results: List<SystemNavigationResultSummary>,
    ) : SystemNavigationEvent
}

internal enum class SystemNavigationAction {
    SwitchStack,
    Push,
    SingleTop,
    Pop,
    ReplaceTop,
    EnterCheckout,
    OpenReceipt,
    AccountPopToRoot,
    OpenSettings,
}

internal sealed interface SystemNavigationResultSummary {
    data class Committed(
        val previousRoute: String,
        val nextRoute: String,
    ) : SystemNavigationResultSummary

    data class NoChange(val reason: String) : SystemNavigationResultSummary

    data object Queued : SystemNavigationResultSummary

    data class Failed(val phase: String) : SystemNavigationResultSummary
}

internal sealed interface SystemNavigationDeepLinkOutcome {
    data object None : SystemNavigationDeepLinkOutcome

    data object ControllerUnavailable : SystemNavigationDeepLinkOutcome

    data class Navigated(
        val uriPattern: String,
        val route: String,
        val result: SystemNavigationResultSummary,
    ) : SystemNavigationDeepLinkOutcome

    data object NoMatch : SystemNavigationDeepLinkOutcome

    data class Rejected(
        val reason: String,
        val argumentName: String?,
    ) : SystemNavigationDeepLinkOutcome

    data object Unsupported : SystemNavigationDeepLinkOutcome
}

internal fun NavResult.toDemoEvent(
    action: SystemNavigationAction,
    detail: String? = null,
): SystemNavigationEvent = SystemNavigationEvent.Result(
    action = action,
    result = toDemoSummary(),
    detail = detail,
)

internal fun NavResult.toDemoSummary(): SystemNavigationResultSummary = when (this) {
    is NavResult.Committed -> SystemNavigationResultSummary.Committed(
        previousRoute = mutation.previousTop.route.name,
        nextRoute = mutation.nextTop.route.name,
    )

    is NavResult.NoChange -> SystemNavigationResultSummary.NoChange(reason.toString())
    is NavResult.Queued -> SystemNavigationResultSummary.Queued
    is NavResult.Failed -> SystemNavigationResultSummary.Failed(failure.phase.toString())
}

internal fun NavDeepLinkResult.toDemoOutcome(): SystemNavigationDeepLinkOutcome = when (this) {
    is NavDeepLinkResult.Navigated -> SystemNavigationDeepLinkOutcome.Navigated(
        uriPattern = match.deepLink.uriPattern,
        route = match.route.name,
        result = navigationResult.toDemoSummary(),
    )

    NavDeepLinkResult.NoMatch -> SystemNavigationDeepLinkOutcome.NoMatch
    is NavDeepLinkResult.Rejected -> SystemNavigationDeepLinkOutcome.Rejected(
        reason = rejection.reason.toString(),
        argumentName = rejection.argumentName,
    )

    NavDeepLinkResult.Unsupported -> SystemNavigationDeepLinkOutcome.Unsupported
}

private fun NavFailure.toDemoEvent(): SystemNavigationEvent.HostFailure =
    SystemNavigationEvent.HostFailure(
        phase = phase.toString(),
        route = failedEntry?.route?.name ?: "unknown",
        stackCommitted = stackCommitted,
    )

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}

internal object SystemNavigationDemoModel {
    val HomeStack = NavStackId("home")
    val DiscoverStack = NavStackId("discover")
    val AccountStack = NavStackId("account")
    val StackIds = listOf(HomeStack, DiscoverStack, AccountStack)

    const val RootGraphRoute = "demo-root"
    const val CheckoutGraphRoute = "checkout"
    const val AccountGraphRoute = "account-graph"

    const val HomeRoute = "home"
    const val HomeDetailRoute = "home-detail"
    const val CartRoute = "cart"
    const val ReceiptRoute = "receipt"
    const val DiscoverRoute = "discover"
    const val SearchResultRoute = "search-result"
    const val ProfileRoute = "profile"
    const val SecurityRoute = "security"
    const val SettingsRoute = "settings"
    const val ReplacementRoute = "replacement"

    const val ItemIdArgument = "itemId"
    const val QueryArgument = "query"
    const val PageArgument = "page"
    const val UserIdArgument = "userId"

    const val HomeDeepLink = "viewcompose://demo/home"
    const val SearchDeepLink = "viewcompose://demo/search/navigation?page=2"
    const val InvalidSearchDeepLink = "viewcompose://demo/search/navigation?page=wrong"
    const val SecurityDeepLink = "viewcompose://demo/account/42"
    const val NoMatchDeepLink = "viewcompose://demo/not-registered"

    val StackConfiguration = NavStackConfiguration(
        initialStackId = HomeStack,
        stacks = listOf(
            NavStackSpec(HomeStack, NavRoute(HomeRoute)),
            NavStackSpec(DiscoverStack, NavRoute(DiscoverRoute)),
            NavStackSpec(AccountStack, NavRoute(AccountGraphRoute)),
        ),
        rootBackBehavior = NavRootBackBehavior.PreviousStack,
    )

    val Graph = navGraph(
        route = RootGraphRoute,
        startDestination = NavRoute(HomeRoute),
    ) {
        destination(
            route = HomeRoute,
            deepLinks = listOf(
                NavDeepLink(
                    uriPattern = HomeDeepLink,
                    targetStackId = HomeStack,
                ),
            ),
        )
        destination(route = HomeDetailRoute)
        navigation(
            route = CheckoutGraphRoute,
            startDestination = NavRoute(CartRoute),
        ) {
            destination(route = CartRoute)
            destination(route = ReceiptRoute)
        }
        destination(route = DiscoverRoute)
        destination(
            route = SearchResultRoute,
            deepLinks = listOf(
                NavDeepLink(
                    uriPattern = "viewcompose://demo/search/{$QueryArgument}?page={$PageArgument}",
                    argumentTypes = mapOf(
                        QueryArgument to NavDeepLinkArgumentType.Text,
                        PageArgument to NavDeepLinkArgumentType.Int,
                    ),
                    targetStackId = DiscoverStack,
                ),
            ),
        )
        navigation(
            route = AccountGraphRoute,
            startDestination = NavRoute(ProfileRoute),
        ) {
            destination(
                route = ProfileRoute,
                deepLinks = listOf(
                    NavDeepLink(
                        uriPattern = "viewcompose://demo/profile",
                        targetStackId = AccountStack,
                    ),
                ),
            )
            destination(
                route = SecurityRoute,
                deepLinks = listOf(
                    NavDeepLink(
                        uriPattern = "viewcompose://demo/account/{$UserIdArgument}",
                        argumentTypes = mapOf(
                            UserIdArgument to NavDeepLinkArgumentType.Long,
                        ),
                        targetStackId = AccountStack,
                    ),
                ),
            )
            destination(route = SettingsRoute)
        }
        destination(route = ReplacementRoute)
    }

    val AdaptivePanePolicy = NavPanePolicy(
        minPaneWidthDp = 280f,
        maxPaneCount = 3,
        paneSpacingDp = 8f,
    )

    fun stackLabelRes(stackId: NavStackId): Int = when (stackId) {
        HomeStack -> R.string.demo_system_nav_stack_home
        DiscoverStack -> R.string.demo_system_nav_stack_discover
        AccountStack -> R.string.demo_system_nav_stack_account
        else -> error("Unknown demo stack: $stackId")
    }

    fun routeLabelRes(route: String): Int = when (route) {
        HomeRoute -> R.string.demo_system_nav_route_home
        HomeDetailRoute -> R.string.demo_system_nav_route_home_detail
        CartRoute -> R.string.demo_system_nav_route_cart
        ReceiptRoute -> R.string.demo_system_nav_route_receipt
        DiscoverRoute -> R.string.demo_system_nav_route_discover
        SearchResultRoute -> R.string.demo_system_nav_route_search_result
        ProfileRoute -> R.string.demo_system_nav_route_profile
        SecurityRoute -> R.string.demo_system_nav_route_security
        SettingsRoute -> R.string.demo_system_nav_route_settings
        ReplacementRoute -> R.string.demo_system_nav_route_replacement
        else -> error("Unknown demo route: $route")
    }

    fun rootRoute(stackId: NavStackId): NavRoute = when (stackId) {
        HomeStack -> NavRoute(HomeRoute)
        DiscoverStack -> NavRoute(DiscoverRoute)
        AccountStack -> NavRoute(AccountGraphRoute)
        else -> error("Unknown demo stack: $stackId")
    }
}
