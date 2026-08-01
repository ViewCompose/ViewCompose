package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.preview.tooling.ViewComposePreview
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
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.widget.core.IconButton
import com.viewcompose.widget.core.NavigationBar
import com.viewcompose.widget.core.Scaffold
import com.viewcompose.widget.core.SideEffect
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.TopAppBar
import com.viewcompose.widget.core.TopAppBarDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.rememberSaveable

@ViewComposePreview(name = "System navigation", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewSystemNavigation() {
    SystemNavigationDemoPage(
        root = null,
        externalDeepLinkOutcome = mutableStateOf("尚未接收外部 Deep Link"),
        diagnosticsEnabled = false,
        onControllerReady = {},
        onExit = {},
    )
}

internal fun UiTreeBuilder.SystemNavigationDemoPage(
    root: ViewGroup?,
    externalDeepLinkOutcome: MutableState<String>,
    diagnosticsEnabled: Boolean,
    onControllerReady: (NavHostController) -> Unit,
    onExit: () -> Unit,
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
    val lastEvent = rememberSaveable(key = "system-navigation-last-event") {
        mutableStateOf("等待操作")
    }
    val controller = rememberNavHostController(
        stackConfiguration = SystemNavigationDemoModel.StackConfiguration,
        graph = SystemNavigationDemoModel.Graph,
    )
    val stackState = controller.navigationState.value
    val selectedStackIndex = SystemNavigationDemoModel.StackIds
        .indexOf(stackState.activeStackId)
        .coerceAtLeast(0)

    SideEffect {
        onControllerReady(controller)
        root?.context?.findAppCompatActivity()?.title =
            "系统导航 · ${stackState.activeStackId.value}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "系统导航 · ${SystemNavigationDemoModel.stackLabel(stackState.activeStackId)}",
                navigationIcon = {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.ic_arrow_back),
                        contentDescription = "退出系统导航 Demo",
                        onClick = onExit,
                        tint = TopAppBarDefaults.titleColor(),
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
                        .toDemoDescription("切换到 ${SystemNavigationDemoModel.stackLabel(target)}")
                },
                modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_TAB_BAR),
            ) {
                Item(
                    label = "首页",
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                )
                Item(
                    label = "发现",
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                )
                Item(
                    label = "账户",
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .systemBarsInsetsPadding()
            .backgroundColor(Theme.colors.background),
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
                lastEvent.value = failure.toDemoDescription()
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag(DemoTestTags.SYSTEM_NAV_HOST),
        ) { entry ->
            SystemNavigationDestinationPage(
                controller = controller,
                entry = entry,
                adaptivePanes = adaptivePanes,
                systemBackEnabled = systemBackEnabled,
                motionEnabled = motionEnabled,
                lastEvent = lastEvent,
                externalDeepLinkOutcome = externalDeepLinkOutcome,
            )
        }
    }
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

    fun stackLabel(stackId: NavStackId): String {
        return when (stackId) {
            HomeStack -> "首页"
            DiscoverStack -> "发现"
            AccountStack -> "账户"
            else -> stackId.value
        }
    }

    fun routeLabel(route: String): String {
        return when (route) {
            HomeRoute -> "首页总览"
            HomeDetailRoute -> "首页详情"
            CartRoute -> "Checkout · Cart"
            ReceiptRoute -> "Checkout · Receipt"
            DiscoverRoute -> "发现"
            SearchResultRoute -> "搜索结果"
            ProfileRoute -> "账户资料"
            SecurityRoute -> "安全设置"
            SettingsRoute -> "账户设置"
            ReplacementRoute -> "ReplaceTop 验收页"
            else -> route
        }
    }

    fun rootRoute(stackId: NavStackId): NavRoute {
        return when (stackId) {
            HomeStack -> NavRoute(HomeRoute)
            DiscoverStack -> NavRoute(DiscoverRoute)
            AccountStack -> NavRoute(AccountGraphRoute)
            else -> error("Unknown demo stack: $stackId")
        }
    }
}

internal fun NavResult.toDemoDescription(action: String = "导航"): String {
    return when (this) {
        is NavResult.Committed -> {
            "$action：已提交 · ${mutation.previousTop.route.name} → ${mutation.nextTop.route.name}"
        }

        is NavResult.NoChange -> "$action：无变化 · $reason"
        is NavResult.Queued -> "$action：已排队"
        is NavResult.Failed -> "$action：失败 · ${failure.phase}"
    }
}

private fun NavFailure.toDemoDescription(): String {
    val route = failedEntry?.route?.name ?: "unknown"
    return "导航失败：$phase · route=$route · committed=$stackCommitted"
}
