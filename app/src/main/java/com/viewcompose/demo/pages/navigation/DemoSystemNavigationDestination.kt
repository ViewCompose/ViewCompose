package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.collectAsStateWithLifecycle
import com.viewcompose.navigation.LocalNavGraphOwnerScope
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.ProvideNavGraphOwner
import com.viewcompose.navigation.core.NavDeepLinkLaunchMode
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavLaunchMode
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackSelectionMode
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.viewmodel.savedStateHandle
import com.viewcompose.viewmodel.viewModel
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.produceState
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.unit.sp
import java.util.concurrent.atomic.AtomicInteger

internal fun UiTreeBuilder.SystemNavigationDestinationPage(
    controller: NavHostController,
    entry: NavEntry,
    adaptivePanes: MutableState<Boolean>,
    systemBackEnabled: MutableState<Boolean>,
    motionEnabled: MutableState<Boolean>,
    lastEvent: MutableState<String>,
    externalDeepLinkOutcome: MutableState<String>,
    scenario: DemoScenarioSpec? = null,
) {
    val saveableCounter = rememberSaveable(key = "system-navigation-entry-counter") {
        mutableStateOf(0)
    }
    val savedStateHandle = savedStateHandle(key = "system-navigation-entry-handle")
    val handleCounter = savedStateHandle
        .getStateFlow("counter", 0)
        .collectAsStateWithLifecycle()
    val entryViewModel = viewModel<SystemNavigationEntryViewModel>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState = if (lifecycleOwner == null) {
        "NONE"
    } else {
        produceState(
            initialValue = lifecycleOwner.lifecycle.currentState,
            lifecycleOwner,
        ) {
            val observer = LifecycleEventObserver { _, _ ->
                value = lifecycleOwner.lifecycle.currentState
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            value = lifecycleOwner.lifecycle.currentState
            awaitDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }.value.name
    }
    val stackState = controller.navigationState.value
    val sections = listOf(
        "status",
        "state",
        "actions",
        "route-actions",
        "capabilities",
    )

    val sectionContent: UiTreeBuilder.(String) -> Unit = { section ->
        when (section) {
            "status" -> DemoSection(
                title = SystemNavigationDemoModel.routeLabel(entry.route.name),
                subtitle = "真实 NavHost destination；每个页面由独立 Lifecycle、ViewModelStore 与 SavedStateRegistry 管理。",
            ) {
                Text(
                    text = "route=${entry.route.name} · entry=${entry.id.value.take(8)} · lifecycle=$lifecycleState",
                    modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_STATUS),
                )
                Text(
                    text = "arguments=${entry.route.arguments.toReadableText()}",
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = "graph=${entry.graphEntries.joinToString(" → ") { graph -> "${graph.route.name}#${graph.id.value.take(6)}" }}",
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = stackState.toReadableText(),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = "最近操作：${lastEvent.value}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .testTag(DemoTestTags.SYSTEM_NAV_LAST_EVENT)
                        .scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Text(
                    text = externalDeepLinkOutcome.value,
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_EXTERNAL_DEEP_LINK),
                )
            }

            "state" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "页面所有权与状态恢复",
                subtitle = "分别观察 rememberSaveable、SavedStateHandle、ViewModel 和父图共享状态。",
            ) {
                Text(
                    text = "rememberSaveable=${saveableCounter.value} · " +
                        "SavedStateHandle=${handleCounter.value} · " +
                        "ViewModel#${entryViewModel.instanceId}=${entryViewModel.counter.value}",
                    modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_COUNTER_STATUS),
                )
                Button(
                    text = "rememberSaveable +1",
                    onClick = { saveableCounter.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_SAVEABLE_INCREMENT),
                )
                Button(
                    text = "SavedStateHandle +1",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        savedStateHandle["counter"] = handleCounter.value + 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_HANDLE_INCREMENT),
                )
                Button(
                    text = "ViewModel +1",
                    variant = ButtonVariant.Outlined,
                    onClick = entryViewModel::increment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_VIEW_MODEL_INCREMENT),
                )
                GraphOwnerStateBlock()
                Text(
                    text = "验证方式：切换 Tab/压栈后，数值与 ViewModel 实例应保持；旋转后 entry/graph ID 和所有可保存数值保持，ViewModel 实例 ID 可更新。Pop 后重新进入应获得新 entry。",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "actions" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "事务化栈操作",
                subtitle = "Push、SingleTop、Pop、ReplaceTop、Reset 都经过同一准备/提交/回滚管线。",
            ) {
                Button(
                    text = "Push 下一页面",
                    onClick = {
                        lastEvent.value = controller
                            .navigate(
                                route = nextRoute(stackState.activeStackId, stackState.activeStack.entries.size),
                                launchMode = NavLaunchMode.Standard,
                            )
                            .toDemoDescription("Push")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_PUSH)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = "SingleTop 当前页面",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .navigate(entry.route, NavLaunchMode.SingleTop)
                            .toDemoDescription("SingleTop")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_SINGLE_TOP),
                )
                Button(
                    text = "Pop",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .popBackStack()
                            .toDemoDescription("Pop")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_POP),
                )
                Button(
                    text = "ReplaceTop 为验收页",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .replaceTop(NavRoute(SystemNavigationDemoModel.ReplacementRoute))
                            .toDemoDescription("ReplaceTop")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_REPLACE),
                )
                Button(
                    text = "Reset 当前 Tab",
                    variant = ButtonVariant.Text,
                    onClick = {
                        lastEvent.value = controller
                            .reset(SystemNavigationDemoModel.rootRoute(stackState.activeStackId))
                            .toDemoDescription("Reset")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_RESET)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                )
            }

            "route-actions" -> RouteSpecificActions(
                controller = controller,
                entry = entry,
                lastEvent = lastEvent,
            )

            else -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "平台、Tab 与自适应能力",
                subtitle = "覆盖独立返回栈、PreviousStack Back、predictive Back、动态窗格和宿主配置更新。",
            ) {
                Button(
                    text = "准备三窗格样例（当前 Tab）",
                    onClick = {
                        lastEvent.value = seedAdaptiveStack(
                            controller = controller,
                            stackId = stackState.activeStackId,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_SEED_ADAPTIVE),
                )
                Button(
                    text = if (adaptivePanes.value) {
                        "自适应窗格：开（点击切为单窗格）"
                    } else {
                        "自适应窗格：关（点击启用）"
                    },
                    onClick = {
                        adaptivePanes.value = !adaptivePanes.value
                        lastEvent.value = "窗格策略切换为 " +
                            if (adaptivePanes.value) "Adaptive" else "Single"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_ADAPTIVE_TOGGLE),
                )
                Button(
                    text = if (motionEnabled.value) {
                        "转场动画：开（点击关闭）"
                    } else {
                        "转场动画：关（点击开启）"
                    },
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        motionEnabled.value = !motionEnabled.value
                        lastEvent.value = "转场动画 ${if (motionEnabled.value) "开启" else "关闭"}"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_MOTION_TOGGLE),
                )
                Button(
                    text = if (systemBackEnabled.value) {
                        "系统 Back：由 NavHost 接管"
                    } else {
                        "系统 Back：已禁用（点击恢复）"
                    },
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        systemBackEnabled.value = !systemBackEnabled.value
                        lastEvent.value = "systemBackEnabled=${systemBackEnabled.value}"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_BACK_TOGGLE),
                )
                Button(
                    text = "账户 Tab：切换并 PopToRoot",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .selectStack(
                                stackId = SystemNavigationDemoModel.AccountStack,
                                selectionMode = NavStackSelectionMode.PopToRoot,
                            )
                            .toDemoDescription("账户 PopToRoot")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_ACCOUNT_POP_ROOT),
                )
                ChecklistGroup(
                    title = "人工验收路径",
                    items = listOf(
                        "三个底部 Tab 分别 Push 页面，来回切换后确认各自返回栈和计数独立保留。",
                        "在 Tab 根页面按系统 Back，应按 selectionHistory 返回上一个 Tab；历史耗尽后才退出 Activity。",
                        "Android 13+ 从屏幕边缘慢滑，确认 predictive Back 进度、取消和提交动画。",
                        "横屏且自适应开启时，最近 2~3 个 entry 应成为并列原生 View；返回和旋转不重建 owner。",
                        "关闭系统 Back 后按返回会委托 Activity，因此请先完成其它验收。",
                    ),
                )
            }
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DemoTestTags.SYSTEM_NAV_DESTINATION),
    ) {
        sections.forEach { section ->
            item(
                key = section,
                contentRevision = when (section) {
                    "status" -> Triple(section, lifecycleState, stackState)
                    "actions" -> section to stackState
                    "capabilities" -> section to stackState.activeStackId
                    else -> section
                },
            ) {
                sectionContent(section)
            }
        }
    }
}

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}

private fun UiTreeBuilder.GraphOwnerStateBlock() {
    val graphScope = LocalNavGraphOwnerScope.current
    val graphEntry = graphScope?.entries?.lastOrNull()
    if (graphEntry == null) {
        Text(text = "当前 destination 没有图 owner。")
        return
    }
    ProvideNavGraphOwner(graphEntry.route.name) {
        val graphHandle = savedStateHandle(key = "system-navigation-graph-handle")
        val graphCounter = graphHandle
            .getStateFlow("counter", 0)
            .collectAsStateWithLifecycle()
        Column(
            spacing = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(top = 4.dp),
        ) {
            Text(
                text = "父图共享：${graphEntry.route.name}#" +
                    "${graphEntry.id.value.take(6)} · counter=${graphCounter.value}",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_GRAPH_STATUS),
            )
            Button(
                text = "父图共享状态 +1",
                variant = ButtonVariant.Text,
                onClick = { graphHandle["counter"] = graphCounter.value + 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DemoTestTags.SYSTEM_NAV_GRAPH_INCREMENT),
            )
        }
    }
}

private fun UiTreeBuilder.RouteSpecificActions(
    controller: NavHostController,
    entry: NavEntry,
    lastEvent: MutableState<String>,
) {
    when (controller.activeStackId) {
        SystemNavigationDemoModel.HomeStack -> ScenarioSection(
            kind = ScenarioKind.Core,
            title = "嵌套 Checkout 图",
            subtitle = "进入图路由会解析 startDestination，并为 cart/receipt 共享一个 checkout 图 owner。",
        ) {
            Button(
                text = "进入 Checkout 图",
                onClick = {
                    lastEvent.value = controller
                        .navigate(NavRoute(SystemNavigationDemoModel.CheckoutGraphRoute))
                        .toDemoDescription("进入 Checkout")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DemoTestTags.SYSTEM_NAV_ENTER_GRAPH),
            )
            Button(
                text = "前往 Receipt",
                variant = ButtonVariant.Outlined,
                onClick = {
                    lastEvent.value = controller
                        .navigate(NavRoute(SystemNavigationDemoModel.ReceiptRoute))
                        .toDemoDescription("打开 Receipt")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SystemNavigationDemoModel.DiscoverStack -> ScenarioSection(
            kind = ScenarioKind.Stress,
            title = "严格 Deep Link",
            subtitle = "URI 只匹配图中 allowlist；参数按声明类型解析，切栈与目标栈更新原子提交。",
        ) {
            DeepLinkButton(
                text = "合法 Search · Reset",
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_VALID,
            ) {
                lastEvent.value = controller
                    .navigateDeepLink(SystemNavigationDemoModel.SearchDeepLink)
                    .toDemoDescription()
            }
            DeepLinkButton(
                text = "合法 Search · Push",
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_PUSH,
            ) {
                lastEvent.value = controller
                    .navigateDeepLink(
                        uri = SystemNavigationDemoModel.SearchDeepLink,
                        launchMode = NavDeepLinkLaunchMode.Push,
                    )
                    .toDemoDescription()
            }
            DeepLinkButton(
                text = "非法 Int 参数（应拒绝）",
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_INVALID,
            ) {
                lastEvent.value = controller
                    .navigateDeepLink(SystemNavigationDemoModel.InvalidSearchDeepLink)
                    .toDemoDescription()
            }
            DeepLinkButton(
                text = "未注册 URI（应 NoMatch）",
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_NO_MATCH,
            ) {
                lastEvent.value = controller
                    .navigateDeepLink(SystemNavigationDemoModel.NoMatchDeepLink)
                    .toDemoDescription()
            }
        }

        else -> ScenarioSection(
            kind = ScenarioKind.Guide,
            title = "账户图与原生 Intent",
            subtitle = "账户目标共享 account-graph owner；ACTION_VIEW Intent 进入同一严格 Deep Link 事务。",
        ) {
            DeepLinkButton(
                text = "Security Deep Link · ReplaceTop",
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_SECURITY,
            ) {
                lastEvent.value = controller
                    .navigateDeepLink(
                        uri = SystemNavigationDemoModel.SecurityDeepLink,
                        launchMode = NavDeepLinkLaunchMode.ReplaceTop,
                    )
                    .toDemoDescription()
            }
            Button(
                text = "打开账户设置",
                variant = ButtonVariant.Outlined,
                onClick = {
                    lastEvent.value = controller
                        .navigate(NavRoute(SystemNavigationDemoModel.SettingsRoute))
                        .toDemoDescription("打开设置")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "外部验证：adb shell am start -a android.intent.action.VIEW " +
                    "-d '${SystemNavigationDemoModel.SecurityDeepLink}' com.gzq.uiframework",
                style = UiTextStyle(fontSizeSp = 12.sp),
                color = TextDefaults.secondaryColor(),
            )
        }
    }
}

private fun UiTreeBuilder.DeepLinkButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    Button(
        text = text,
        variant = ButtonVariant.Outlined,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

private fun nextRoute(
    stackId: com.viewcompose.navigation.core.NavStackId,
    stackSize: Int,
): NavRoute {
    return when (stackId) {
        SystemNavigationDemoModel.HomeStack -> NavRoute(
            name = SystemNavigationDemoModel.HomeDetailRoute,
            arguments = mapOf(
                SystemNavigationDemoModel.ItemIdArgument to NavValue.IntValue(stackSize),
            ),
        )

        SystemNavigationDemoModel.DiscoverStack -> NavRoute(
            name = SystemNavigationDemoModel.SearchResultRoute,
            arguments = mapOf(
                SystemNavigationDemoModel.QueryArgument to NavValue.Text("manual"),
                SystemNavigationDemoModel.PageArgument to NavValue.IntValue(stackSize),
            ),
        )

        else -> NavRoute(
            name = SystemNavigationDemoModel.SecurityRoute,
            arguments = mapOf(
                SystemNavigationDemoModel.UserIdArgument to NavValue.LongValue(1000L + stackSize),
            ),
        )
    }
}

private fun seedAdaptiveStack(
    controller: NavHostController,
    stackId: com.viewcompose.navigation.core.NavStackId,
): String {
    val destinations = when (stackId) {
        SystemNavigationDemoModel.HomeStack -> listOf(
            NavRoute(
                name = SystemNavigationDemoModel.HomeDetailRoute,
                arguments = mapOf(
                    SystemNavigationDemoModel.ItemIdArgument to NavValue.IntValue(101),
                ),
            ),
            NavRoute(SystemNavigationDemoModel.CheckoutGraphRoute),
        )

        SystemNavigationDemoModel.DiscoverStack -> listOf(
            NavRoute(
                name = SystemNavigationDemoModel.SearchResultRoute,
                arguments = mapOf(
                    SystemNavigationDemoModel.QueryArgument to NavValue.Text("pane-secondary"),
                    SystemNavigationDemoModel.PageArgument to NavValue.IntValue(1),
                ),
            ),
            NavRoute(
                name = SystemNavigationDemoModel.SearchResultRoute,
                arguments = mapOf(
                    SystemNavigationDemoModel.QueryArgument to NavValue.Text("pane-primary"),
                    SystemNavigationDemoModel.PageArgument to NavValue.IntValue(2),
                ),
            ),
        )

        else -> listOf(
            NavRoute(
                name = SystemNavigationDemoModel.SecurityRoute,
                arguments = mapOf(
                    SystemNavigationDemoModel.UserIdArgument to NavValue.LongValue(42L),
                ),
            ),
            NavRoute(SystemNavigationDemoModel.SettingsRoute),
        )
    }
    val results = buildList {
        add(controller.reset(SystemNavigationDemoModel.rootRoute(stackId)))
        destinations.forEach { route ->
            add(controller.navigate(route, NavLaunchMode.Standard))
        }
    }
    return "三窗格样例：" + results.joinToString(" / ") { result ->
        result.toDemoDescription()
    }
}

private fun Map<String, NavValue>.toReadableText(): String {
    if (isEmpty()) {
        return "{}"
    }
    return entries.joinToString(prefix = "{", postfix = "}") { (name, value) ->
        "$name=${value.toReadableText()}"
    }
}

private fun NavValue.toReadableText(): String {
    return when (this) {
        NavValue.Null -> "null"
        is NavValue.Text -> value
        is NavValue.IntValue -> value.toString()
        is NavValue.LongValue -> value.toString()
        is NavValue.BooleanValue -> value.toString()
        is NavValue.FloatValue -> value.toString()
        is NavValue.DoubleValue -> value.toString()
    }
}

private fun com.viewcompose.navigation.core.NavStackSetSnapshot.toReadableText(): String {
    val stackText = SystemNavigationDemoModel.StackIds.joinToString(" · ") { stackId ->
        val routes = this[stackId]
            ?.entries
            ?.joinToString("→") { entry -> entry.route.name }
            ?: "missing"
        "${stackId.value}=[$routes]"
    }
    val history = selectionHistory.joinToString("→") { stackId -> stackId.value }
        .ifEmpty { "empty" }
    return "active=${activeStackId.value} · history=$history\n$stackText"
}

internal class SystemNavigationEntryViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val instanceId: Int = nextInstanceId.incrementAndGet()
    val counter = mutableStateOf(savedStateHandle[COUNTER_KEY] ?: 0)

    fun increment() {
        counter.value += 1
        savedStateHandle[COUNTER_KEY] = counter.value
    }

    private companion object {
        const val COUNTER_KEY = "view-model-counter"
        val nextInstanceId = AtomicInteger(0)
    }
}
