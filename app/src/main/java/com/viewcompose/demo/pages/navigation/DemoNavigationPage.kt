package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.BottomAppBar
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.FloatingActionButton
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.NavigationBarOverrides
import com.viewcompose.ui.foundation.Scaffold
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

@ViewComposePreview(name = "Navigation · App bars", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewNavigationAppBars() {
    NavigationPage(NavigationFixture.AppBars)
}

@ViewComposePreview(name = "Navigation · Navigation bar", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewNavigationBar() {
    NavigationPage(NavigationFixture.NavigationBar)
}

@ViewComposePreview(name = "Navigation · Scaffold", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewNavigationScaffold() {
    NavigationPage(NavigationFixture.Scaffold)
}

internal enum class NavigationFixture(
    val scenarioId: DemoScenarioId,
) {
    AppBars(DemoScenarioIds.ComponentAppBars),
    NavigationBar(DemoScenarioIds.ComponentNavigationBar),
    Scaffold(DemoScenarioIds.ComponentScaffold),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): NavigationFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported navigation-component scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.NavigationPage(
    fixture: NavigationFixture,
    scenario: DemoScenarioSpec? = null,
) {
    val generation = rememberSaveable(key = "navigation-session-generation") {
        mutableStateOf(0)
    }
    key(generation.value) {
        when (fixture) {
            NavigationFixture.AppBars -> NavigationAppBarsFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            NavigationFixture.NavigationBar -> NavigationBarFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            NavigationFixture.Scaffold -> NavigationScaffoldFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )
        }
    }
}

private fun UiTreeBuilder.NavigationAppBarsFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val actionCount = rememberSaveable(key = "navigation-app-bars-action-count") {
        mutableStateOf(0)
    }
    NavigationFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> NavigationFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(R.string.demo_navigation_action_count, actionCount.value)
                },
                onReset = onReset,
            ) {
                TopAppBar(
                    title = stringResource(R.string.demo_navigation_app_bar_page_title),
                    navigationIcon = {
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = stringResource(
                                R.string.demo_navigation_app_bar_back_description,
                            ),
                            onClick = { actionCount.value += 1 },
                        )
                    },
                    actions = {
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = stringResource(
                                R.string.demo_navigation_app_bar_primary_description,
                            ),
                            onClick = { actionCount.value += 1 },
                            modifier = Modifier.navigationScenarioTarget(
                                scenario,
                                DemoAutomationRole.PrimaryAction,
                            ),
                        )
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = stringResource(
                                R.string.demo_navigation_app_bar_more_description,
                            ),
                            onClick = { actionCount.value += 1 },
                        )
                    },
                    modifier = Modifier.navigationScenarioTarget(
                        scenario,
                        DemoAutomationRole.Target,
                    ),
                )
            }

            else -> NavigationAppBarVariants(onClick = { actionCount.value += 1 })
        }
    }
}

private fun UiTreeBuilder.NavigationAppBarVariants(onClick: () -> Unit) {
    Column(
        spacing = 10.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp, bottom = 24.dp),
    ) {
        FixtureTitle(R.string.demo_navigation_app_bar_variants)
        TopAppBar(title = stringResource(R.string.demo_navigation_app_bar_simple_title))
        BottomAppBar {
            listOf(
                R.string.demo_navigation_bottom_bar_home_description,
                R.string.demo_navigation_bottom_bar_saved_description,
                R.string.demo_navigation_bottom_bar_settings_description,
            ).forEach { descriptionRes ->
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(descriptionRes),
                    onClick = onClick,
                )
            }
        }
    }
}

private fun UiTreeBuilder.NavigationBarFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val selectedIndex = rememberSaveable(key = "navigation-bar-selected-index") {
        mutableStateOf(0)
    }
    NavigationFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> NavigationFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(
                        R.string.demo_navigation_selected_index,
                        selectedIndex.value,
                    )
                },
                onReset = onReset,
            ) {
                NavigationBar(
                    selectedIndex = selectedIndex.value,
                    onItemSelected = { selectedIndex.value = it },
                    modifier = Modifier.navigationScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                ) {
                    Item(
                        label = stringResource(R.string.demo_navigation_bar_home),
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        label = stringResource(R.string.demo_navigation_bar_search),
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        label = stringResource(R.string.demo_navigation_bar_messages),
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        badgeCount = 3,
                    )
                }
            }

            else -> NavigationBarVariants(
                scenario = scenario,
                selectedIndex = selectedIndex.value.coerceIn(0, 2),
                onItemSelected = { selectedIndex.value = it },
            )
        }
    }
}

private fun UiTreeBuilder.NavigationBarVariants(
    scenario: DemoScenarioSpec?,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    Column(
        spacing = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp, bottom = 24.dp)
            .navigationScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        FixtureTitle(R.string.demo_navigation_bar_custom_variant)
        NavigationBar(
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            overrides = NavigationBarOverrides(
                containerColor = Theme.colors.surfaceVariant,
                selectedIconColor = Theme.colors.secondary,
                selectedLabelColor = Theme.colors.secondary,
                indicatorColor = Theme.colors.background,
            ),
        ) {
            Item(
                label = stringResource(R.string.demo_navigation_bar_feed),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
            Item(
                label = stringResource(R.string.demo_navigation_bar_discover),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                badgeCount = 12,
            )
            Item(
                label = stringResource(R.string.demo_navigation_bar_notifications),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
        }
    }
}

private fun UiTreeBuilder.NavigationScaffoldFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val actionCount = rememberSaveable(key = "navigation-scaffold-action-count") {
        mutableStateOf(0)
    }
    val selectedIndex = rememberSaveable(key = "navigation-scaffold-selected-index") {
        mutableStateOf(0)
    }
    NavigationFixtureList(generation, listOf("control")) {
        NavigationFixtureHeader(
            scenario = scenario,
            state = {
                stringResource(
                    R.string.demo_navigation_scaffold_state,
                    actionCount.value,
                    selectedIndex.value,
                )
            },
            onReset = onReset,
            stateBeforeAction = true,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.demo_navigation_scaffold_title),
                    )
                },
                bottomBar = {
                    NavigationBar(
                        selectedIndex = selectedIndex.value,
                        onItemSelected = { selectedIndex.value = it },
                    ) {
                        Item(
                            label = stringResource(R.string.demo_navigation_bar_home),
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        )
                        Item(
                            label = stringResource(R.string.demo_navigation_bar_messages),
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            badgeCount = 5,
                        )
                        Item(
                            label = stringResource(R.string.demo_navigation_bar_profile),
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { actionCount.value += 1 },
                        modifier = Modifier.navigationScenarioTarget(
                            scenario,
                            DemoAutomationRole.PrimaryAction,
                        ),
                    ) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = stringResource(
                                R.string.demo_navigation_scaffold_add_description,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .navigationScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Column(
                    spacing = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.demo_navigation_scaffold_content),
                        style = Theme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.demo_navigation_scaffold_content_summary),
                        style = Theme.typography.bodyMedium,
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }
        }
    }
}

private fun UiTreeBuilder.NavigationFixtureList(
    generation: Int,
    sections: List<String>,
    content: UiTreeBuilder.(String) -> Unit,
) {
    LazyColumn(
        items = sections,
        key = { section -> "$generation:$section" },
        modifier = Modifier.fillMaxSize(),
        itemContent = content,
    )
}

private fun UiTreeBuilder.NavigationFixtureHeader(
    scenario: DemoScenarioSpec?,
    state: UiTreeBuilder.() -> String,
    onReset: () -> Unit,
    stateBeforeAction: Boolean = false,
    primaryAction: UiTreeBuilder.() -> Unit,
) {
    Column(
        spacing = 10.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
    ) {
        scenario?.let {
            Text(
                text = stringResource(it.summaryRes),
                style = Theme.typography.bodyMedium,
                color = TextDefaults.secondaryColor(),
            )
        }
        if (stateBeforeAction) {
            NavigationFixtureState(scenario, state)
            NavigationFixtureReset(scenario, onReset)
        }
        primaryAction()
        if (!stateBeforeAction) {
            NavigationFixtureState(scenario, state)
            NavigationFixtureReset(scenario, onReset)
        }
    }
}

private fun UiTreeBuilder.NavigationFixtureState(
    scenario: DemoScenarioSpec?,
    state: UiTreeBuilder.() -> String,
) {
    Text(
        // Dynamic copy must resolve in the lazy item's Session so state invalidation reaches it.
        text = state(),
        style = Theme.typography.bodyMedium,
        color = TextDefaults.secondaryColor(),
        modifier = Modifier.navigationScenarioTarget(scenario, DemoAutomationRole.State),
    )
}

private fun UiTreeBuilder.NavigationFixtureReset(
    scenario: DemoScenarioSpec?,
    onReset: () -> Unit,
) {
    Button(
        text = stringResource(R.string.demo_navigation_reset),
        variant = ButtonVariant.Outlined,
        onClick = onReset,
        modifier = Modifier.navigationScenarioTarget(scenario, DemoAutomationRole.Reset),
    )
}

private fun UiTreeBuilder.FixtureTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = Theme.typography.titleMedium,
        color = Theme.colors.onSurface,
    )
}

private fun Modifier.navigationScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
