package com.viewcompose

import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.collectAsStateWithLifecycle
import com.viewcompose.navigation.LocalNavGraphOwnerScope
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.ProvideNavGraphOwner
import com.viewcompose.navigation.core.NavDeepLinkLaunchMode
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavLaunchMode
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSelectionMode
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.produceState
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import com.viewcompose.viewmodel.savedStateHandle
import com.viewcompose.viewmodel.viewModel
import java.util.concurrent.atomic.AtomicInteger

internal fun UiTreeBuilder.SystemNavigationDestinationPage(
    controller: NavHostController,
    entry: NavEntry,
    adaptivePanes: MutableState<Boolean>,
    systemBackEnabled: MutableState<Boolean>,
    motionEnabled: MutableState<Boolean>,
    lastEvent: MutableState<SystemNavigationEvent>,
    externalDeepLinkOutcome: MutableState<SystemNavigationDeepLinkOutcome>,
    scenario: DemoScenarioSpec? = null,
    onReset: () -> Unit,
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
        stringResource(R.string.demo_system_nav_lifecycle_none)
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
        "automation",
        "status",
        "state",
        "actions",
        "route-actions",
        "capabilities",
    )

    val sectionContent: UiTreeBuilder.(String) -> Unit = { section ->
        when (section) {
            "automation" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_system_nav_fixture_title),
                subtitle = stringResource(R.string.demo_system_nav_fixture_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_system_nav_recent_event,
                        systemNavigationEventText(lastEvent.value),
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_push),
                    onClick = {
                        lastEvent.value = controller
                            .navigate(
                                route = nextRoute(
                                    stackState.activeStackId,
                                    stackState.activeStack.entries.size,
                                ),
                                launchMode = NavLaunchMode.Standard,
                            )
                            .toDemoEvent(SystemNavigationAction.Push)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_reset_fixture),
                    variant = ButtonVariant.Text,
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                )
            }

            "status" -> DemoSection(
                title = stringResource(SystemNavigationDemoModel.routeLabelRes(entry.route.name)),
                subtitle = stringResource(R.string.demo_system_nav_status_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_system_nav_status_route,
                        entry.route.name,
                        entry.id.value.take(8),
                        lifecycleState,
                    ),
                    modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_STATUS),
                )
                Text(
                    text = stringResource(
                        R.string.demo_system_nav_status_arguments,
                        entry.route.arguments.toReadableText(),
                    ),
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = stringResource(
                        R.string.demo_system_nav_status_graph,
                        entry.graphEntries.joinToString(" → ") { graph ->
                            "${graph.route.name}#${graph.id.value.take(6)}"
                        },
                    ),
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = stackSnapshotText(stackState),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = systemNavigationDeepLinkText(
                        externalDeepLinkOutcome.value,
                        sourceRes = R.string.demo_system_nav_source_external,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_EXTERNAL_DEEP_LINK),
                )
            }

            "state" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_system_nav_state_title),
                subtitle = stringResource(R.string.demo_system_nav_state_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_system_nav_counter_state,
                        saveableCounter.value,
                        handleCounter.value,
                        entryViewModel.instanceId,
                        entryViewModel.counter.value,
                    ),
                    modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_COUNTER_STATUS),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_increment_saveable),
                    onClick = { saveableCounter.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_SAVEABLE_INCREMENT),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_increment_handle),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        savedStateHandle["counter"] = handleCounter.value + 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_HANDLE_INCREMENT),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_increment_view_model),
                    variant = ButtonVariant.Outlined,
                    onClick = entryViewModel::increment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_VIEW_MODEL_INCREMENT),
                )
                GraphOwnerStateBlock()
            }

            "actions" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_system_nav_actions_title),
                subtitle = stringResource(R.string.demo_system_nav_actions_summary),
            ) {
                Button(
                    text = stringResource(R.string.demo_system_nav_single_top),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .navigate(entry.route, NavLaunchMode.SingleTop)
                            .toDemoEvent(SystemNavigationAction.SingleTop)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_SINGLE_TOP),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_pop),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .popBackStack()
                            .toDemoEvent(SystemNavigationAction.Pop)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_POP),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_replace_top),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .replaceTop(NavRoute(SystemNavigationDemoModel.ReplacementRoute))
                            .toDemoEvent(SystemNavigationAction.ReplaceTop)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_REPLACE),
                )
            }

            "route-actions" -> RouteSpecificActions(
                controller = controller,
                lastEvent = lastEvent,
            )

            else -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = stringResource(R.string.demo_system_nav_capabilities_title),
                subtitle = stringResource(R.string.demo_system_nav_capabilities_summary),
            ) {
                Button(
                    text = stringResource(R.string.demo_system_nav_seed_adaptive),
                    onClick = {
                        lastEvent.value = SystemNavigationEvent.AdaptiveStackSeeded(
                            seedAdaptiveStack(
                                controller = controller,
                                stackId = stackState.activeStackId,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_SEED_ADAPTIVE),
                )
                Button(
                    text = stringResource(
                        if (adaptivePanes.value) {
                            R.string.demo_system_nav_adaptive_on
                        } else {
                            R.string.demo_system_nav_adaptive_off
                        },
                    ),
                    onClick = {
                        adaptivePanes.value = !adaptivePanes.value
                        lastEvent.value = SystemNavigationEvent.PanePolicyChanged(
                            adaptive = adaptivePanes.value,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_ADAPTIVE_TOGGLE),
                )
                Button(
                    text = stringResource(
                        if (motionEnabled.value) {
                            R.string.demo_system_nav_motion_on
                        } else {
                            R.string.demo_system_nav_motion_off
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        motionEnabled.value = !motionEnabled.value
                        lastEvent.value = SystemNavigationEvent.MotionChanged(
                            enabled = motionEnabled.value,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_MOTION_TOGGLE),
                )
                Button(
                    text = stringResource(
                        if (systemBackEnabled.value) {
                            R.string.demo_system_nav_back_on
                        } else {
                            R.string.demo_system_nav_back_off
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        systemBackEnabled.value = !systemBackEnabled.value
                        lastEvent.value = SystemNavigationEvent.SystemBackChanged(
                            enabled = systemBackEnabled.value,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_BACK_TOGGLE),
                )
                Button(
                    text = stringResource(R.string.demo_system_nav_account_pop_root),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        lastEvent.value = controller
                            .selectStack(
                                stackId = SystemNavigationDemoModel.AccountStack,
                                selectionMode = NavStackSelectionMode.PopToRoot,
                            )
                            .toDemoEvent(SystemNavigationAction.AccountPopToRoot)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SYSTEM_NAV_ACCOUNT_POP_ROOT),
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
                    "automation" -> listOf(section, stackState, lastEvent.value)
                    "status" -> listOf(
                        section,
                        lifecycleState,
                        stackState,
                        externalDeepLinkOutcome.value,
                    )
                    "actions" -> section to stackState
                    "capabilities" -> listOf(
                        section,
                        stackState.activeStackId,
                        adaptivePanes.value,
                        motionEnabled.value,
                        systemBackEnabled.value,
                    )
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
        Text(text = stringResource(R.string.demo_system_nav_no_graph_owner))
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
                text = stringResource(
                    R.string.demo_system_nav_graph_state,
                    graphEntry.route.name,
                    graphEntry.id.value.take(6),
                    graphCounter.value,
                ),
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.testTag(DemoTestTags.SYSTEM_NAV_GRAPH_STATUS),
            )
            Button(
                text = stringResource(R.string.demo_system_nav_increment_graph),
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
    lastEvent: MutableState<SystemNavigationEvent>,
) {
    when (controller.activeStackId) {
        SystemNavigationDemoModel.HomeStack -> ScenarioSection(
            kind = ScenarioKind.Core,
            title = stringResource(R.string.demo_system_nav_checkout_title),
            subtitle = stringResource(R.string.demo_system_nav_checkout_summary),
        ) {
            Button(
                text = stringResource(R.string.demo_system_nav_enter_checkout),
                onClick = {
                    lastEvent.value = controller
                        .navigate(NavRoute(SystemNavigationDemoModel.CheckoutGraphRoute))
                        .toDemoEvent(SystemNavigationAction.EnterCheckout)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DemoTestTags.SYSTEM_NAV_ENTER_GRAPH),
            )
            Button(
                text = stringResource(R.string.demo_system_nav_open_receipt),
                variant = ButtonVariant.Outlined,
                onClick = {
                    lastEvent.value = controller
                        .navigate(NavRoute(SystemNavigationDemoModel.ReceiptRoute))
                        .toDemoEvent(SystemNavigationAction.OpenReceipt)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SystemNavigationDemoModel.DiscoverStack -> ScenarioSection(
            kind = ScenarioKind.Stress,
            title = stringResource(R.string.demo_system_nav_deep_link_title),
            subtitle = stringResource(R.string.demo_system_nav_deep_link_summary),
        ) {
            DeepLinkButton(
                text = stringResource(R.string.demo_system_nav_deep_link_valid_reset),
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_VALID,
            ) {
                lastEvent.value = SystemNavigationEvent.DeepLink(
                    controller.navigateDeepLink(SystemNavigationDemoModel.SearchDeepLink)
                        .toDemoOutcome(),
                )
            }
            DeepLinkButton(
                text = stringResource(R.string.demo_system_nav_deep_link_valid_push),
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_PUSH,
            ) {
                lastEvent.value = SystemNavigationEvent.DeepLink(
                    controller.navigateDeepLink(
                        uri = SystemNavigationDemoModel.SearchDeepLink,
                        launchMode = NavDeepLinkLaunchMode.Push,
                    ).toDemoOutcome(),
                )
            }
            DeepLinkButton(
                text = stringResource(R.string.demo_system_nav_deep_link_invalid),
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_INVALID,
            ) {
                lastEvent.value = SystemNavigationEvent.DeepLink(
                    controller.navigateDeepLink(SystemNavigationDemoModel.InvalidSearchDeepLink)
                        .toDemoOutcome(),
                )
            }
            DeepLinkButton(
                text = stringResource(R.string.demo_system_nav_deep_link_no_match),
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_NO_MATCH,
            ) {
                lastEvent.value = SystemNavigationEvent.DeepLink(
                    controller.navigateDeepLink(SystemNavigationDemoModel.NoMatchDeepLink)
                        .toDemoOutcome(),
                )
            }
        }

        else -> ScenarioSection(
            kind = ScenarioKind.Guide,
            title = stringResource(R.string.demo_system_nav_account_title),
            subtitle = stringResource(R.string.demo_system_nav_account_summary),
        ) {
            DeepLinkButton(
                text = stringResource(R.string.demo_system_nav_security_deep_link),
                tag = DemoTestTags.SYSTEM_NAV_DEEP_LINK_SECURITY,
            ) {
                lastEvent.value = SystemNavigationEvent.DeepLink(
                    controller.navigateDeepLink(
                        uri = SystemNavigationDemoModel.SecurityDeepLink,
                        launchMode = NavDeepLinkLaunchMode.ReplaceTop,
                    ).toDemoOutcome(),
                )
            }
            Button(
                text = stringResource(R.string.demo_system_nav_open_settings),
                variant = ButtonVariant.Outlined,
                onClick = {
                    lastEvent.value = controller
                        .navigate(NavRoute(SystemNavigationDemoModel.SettingsRoute))
                        .toDemoEvent(SystemNavigationAction.OpenSettings)
                },
                modifier = Modifier.fillMaxWidth(),
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
    stackId: NavStackId,
    stackSize: Int,
): NavRoute = when (stackId) {
    SystemNavigationDemoModel.HomeStack -> NavRoute(
        name = SystemNavigationDemoModel.HomeDetailRoute,
        arguments = mapOf(
            SystemNavigationDemoModel.ItemIdArgument to NavValue.IntValue(stackSize),
        ),
    )

    SystemNavigationDemoModel.DiscoverStack -> NavRoute(
        name = SystemNavigationDemoModel.SearchResultRoute,
        arguments = mapOf(
            SystemNavigationDemoModel.QueryArgument to NavValue.Text(MANUAL_QUERY),
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

private fun seedAdaptiveStack(
    controller: NavHostController,
    stackId: NavStackId,
): List<SystemNavigationResultSummary> {
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
                    SystemNavigationDemoModel.QueryArgument to NavValue.Text(PANE_SECONDARY_QUERY),
                    SystemNavigationDemoModel.PageArgument to NavValue.IntValue(1),
                ),
            ),
            NavRoute(
                name = SystemNavigationDemoModel.SearchResultRoute,
                arguments = mapOf(
                    SystemNavigationDemoModel.QueryArgument to NavValue.Text(PANE_PRIMARY_QUERY),
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
    return buildList {
        add(controller.reset(SystemNavigationDemoModel.rootRoute(stackId)).toDemoSummary())
        destinations.forEach { route ->
            add(controller.navigate(route, NavLaunchMode.Standard).toDemoSummary())
        }
    }
}

private fun UiTreeBuilder.systemNavigationEventText(event: SystemNavigationEvent): String =
    when (event) {
        SystemNavigationEvent.Waiting -> stringResource(R.string.demo_system_nav_event_waiting)
        is SystemNavigationEvent.Result -> systemNavigationResultText(
            result = event.result,
            action = systemNavigationActionText(event.action, event.detail),
        )
        is SystemNavigationEvent.DeepLink -> systemNavigationDeepLinkText(
            outcome = event.outcome,
            sourceRes = R.string.demo_system_nav_source_internal,
        )
        is SystemNavigationEvent.HostFailure -> stringResource(
            R.string.demo_system_nav_event_host_failure,
            event.phase,
            event.route,
            event.stackCommitted,
        )
        is SystemNavigationEvent.PanePolicyChanged -> stringResource(
            if (event.adaptive) {
                R.string.demo_system_nav_event_pane_adaptive
            } else {
                R.string.demo_system_nav_event_pane_single
            },
        )
        is SystemNavigationEvent.MotionChanged -> stringResource(
            if (event.enabled) {
                R.string.demo_system_nav_event_motion_enabled
            } else {
                R.string.demo_system_nav_event_motion_disabled
            },
        )
        is SystemNavigationEvent.SystemBackChanged -> stringResource(
            if (event.enabled) {
                R.string.demo_system_nav_event_back_enabled
            } else {
                R.string.demo_system_nav_event_back_disabled
            },
        )
        is SystemNavigationEvent.AdaptiveStackSeeded -> stringResource(
            R.string.demo_system_nav_event_seeded,
            event.results.joinToString(" / ") { result ->
                systemNavigationResultText(result)
            },
        )
    }

private fun UiTreeBuilder.systemNavigationActionText(
    action: SystemNavigationAction,
    detail: String?,
): String = when (action) {
    SystemNavigationAction.SwitchStack -> {
        val stack = SystemNavigationDemoModel.StackIds.single { it.value == detail }
        stringResource(
            R.string.demo_system_nav_action_switch,
            stringResource(SystemNavigationDemoModel.stackLabelRes(stack)),
        )
    }
    SystemNavigationAction.Push -> stringResource(R.string.demo_system_nav_action_push)
    SystemNavigationAction.SingleTop -> stringResource(R.string.demo_system_nav_action_single_top)
    SystemNavigationAction.Pop -> stringResource(R.string.demo_system_nav_action_pop)
    SystemNavigationAction.ReplaceTop -> stringResource(R.string.demo_system_nav_action_replace_top)
    SystemNavigationAction.EnterCheckout -> stringResource(R.string.demo_system_nav_action_checkout)
    SystemNavigationAction.OpenReceipt -> stringResource(R.string.demo_system_nav_action_receipt)
    SystemNavigationAction.AccountPopToRoot -> stringResource(R.string.demo_system_nav_action_account_root)
    SystemNavigationAction.OpenSettings -> stringResource(R.string.demo_system_nav_action_settings)
}

private fun UiTreeBuilder.systemNavigationResultText(
    result: SystemNavigationResultSummary,
    action: String? = null,
): String {
    val detail = when (result) {
        is SystemNavigationResultSummary.Committed -> stringResource(
            R.string.demo_system_nav_result_committed_detail,
            result.previousRoute,
            result.nextRoute,
        )
        is SystemNavigationResultSummary.NoChange -> stringResource(
            R.string.demo_system_nav_result_no_change_detail,
            result.reason,
        )
        SystemNavigationResultSummary.Queued -> stringResource(
            R.string.demo_system_nav_result_queued_detail,
        )
        is SystemNavigationResultSummary.Failed -> stringResource(
            R.string.demo_system_nav_result_failed_detail,
            result.phase,
        )
    }
    return action?.let {
        stringResource(R.string.demo_system_nav_result_with_action, it, detail)
    } ?: detail
}

private fun UiTreeBuilder.systemNavigationDeepLinkText(
    outcome: SystemNavigationDeepLinkOutcome,
    sourceRes: Int,
): String {
    val source = stringResource(sourceRes)
    return when (outcome) {
        SystemNavigationDeepLinkOutcome.None -> stringResource(
            R.string.demo_system_nav_deep_link_none,
            source,
        )
        SystemNavigationDeepLinkOutcome.ControllerUnavailable -> stringResource(
            R.string.demo_system_nav_deep_link_controller_unavailable,
            source,
        )
        is SystemNavigationDeepLinkOutcome.Navigated -> stringResource(
            R.string.demo_system_nav_deep_link_navigated,
            source,
            outcome.uriPattern,
            outcome.route,
            systemNavigationResultText(outcome.result),
        )
        SystemNavigationDeepLinkOutcome.NoMatch -> stringResource(
            R.string.demo_system_nav_deep_link_no_match_result,
            source,
        )
        is SystemNavigationDeepLinkOutcome.Rejected -> outcome.argumentName?.let { argument ->
            stringResource(
                R.string.demo_system_nav_deep_link_rejected_argument,
                source,
                outcome.reason,
                argument,
            )
        } ?: stringResource(
            R.string.demo_system_nav_deep_link_rejected,
            source,
            outcome.reason,
        )
        SystemNavigationDeepLinkOutcome.Unsupported -> stringResource(
            R.string.demo_system_nav_deep_link_unsupported,
            source,
        )
    }
}

private fun Map<String, NavValue>.toReadableText(): String {
    if (isEmpty()) return "{}"
    return entries.joinToString(prefix = "{", postfix = "}") { (name, value) ->
        "$name=${value.toReadableText()}"
    }
}

private fun NavValue.toReadableText(): String = when (this) {
    NavValue.Null -> "null"
    is NavValue.Text -> value
    is NavValue.IntValue -> value.toString()
    is NavValue.LongValue -> value.toString()
    is NavValue.BooleanValue -> value.toString()
    is NavValue.FloatValue -> value.toString()
    is NavValue.DoubleValue -> value.toString()
}

private fun UiTreeBuilder.stackSnapshotText(snapshot: NavStackSetSnapshot): String {
    val missing = stringResource(R.string.demo_system_nav_stack_missing)
    val stackText = SystemNavigationDemoModel.StackIds.joinToString(" · ") { stackId ->
        val routes = snapshot[stackId]
            ?.entries
            ?.joinToString("→") { entry -> entry.route.name }
            ?: missing
        "${stackId.value}=[$routes]"
    }
    val history = snapshot.selectionHistory.joinToString("→") { stackId -> stackId.value }
        .ifEmpty { stringResource(R.string.demo_system_nav_history_empty) }
    return stringResource(
        R.string.demo_system_nav_stack_snapshot,
        snapshot.activeStackId.value,
        history,
        stackText,
    )
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

private const val MANUAL_QUERY = "manual"
private const val PANE_SECONDARY_QUERY = "pane-secondary"
private const val PANE_PRIMARY_QUERY = "pane-primary"
