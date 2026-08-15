package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Badge
import com.viewcompose.ui.foundation.BadgeOverrides
import com.viewcompose.ui.foundation.BadgedBox
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Card
import com.viewcompose.ui.foundation.CardVariant
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.ChipVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.ExtendedFloatingActionButton
import com.viewcompose.ui.foundation.ExtendedFloatingActionButtonOverrides
import com.viewcompose.ui.foundation.FabSize
import com.viewcompose.ui.foundation.FloatingActionButton
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.ListItem
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(
    name = "ActionsPage · Light",
    group = "Demo",
    widthDp = 411,
    theme = PreviewTheme.Light,
)
@ViewComposePreview(
    name = "ActionsPage · Dark",
    group = "Demo",
    widthDp = 411,
    theme = PreviewTheme.Dark,
)
fun UiTreeBuilder.PreviewActionsPage() {
    ActionsPage(ActionsFixture.Card)
}

@ViewComposePreview(name = "Actions · FAB", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewActionsFab() {
    ActionsPage(ActionsFixture.Fab)
}

@ViewComposePreview(name = "Actions · Chip", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewActionsChip() {
    ActionsPage(ActionsFixture.Chip)
}

@ViewComposePreview(name = "Actions · List items", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewActionsListItems() {
    ActionsPage(ActionsFixture.ListItem)
}

internal enum class ActionsFixture(
    val scenarioId: DemoScenarioId,
) {
    Card(DemoScenarioIds.ComponentCard),
    Fab(DemoScenarioIds.ComponentFab),
    Chip(DemoScenarioIds.ComponentChip),
    ListItem(DemoScenarioIds.ComponentListItem),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): ActionsFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported action-component scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.ActionsPage(
    fixture: ActionsFixture,
    scenario: DemoScenarioSpec? = null,
) {
    val generation = rememberSaveable(key = "actions-session-generation") {
        mutableStateOf(0)
    }
    key(generation.value) {
        when (fixture) {
            ActionsFixture.Card -> ActionsCardFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ActionsFixture.Fab -> ActionsFabFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ActionsFixture.Chip -> ActionsChipFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ActionsFixture.ListItem -> ActionsListItemFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )
        }
    }
}

private fun UiTreeBuilder.ActionsCardFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val clicks = rememberSaveable(key = "actions-card-clicks") { mutableStateOf(0) }
    ActionsFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> ActionsFixtureHeader(
                scenario = scenario,
                state = { stringResource(R.string.demo_actions_click_count, clicks.value) },
                onReset = onReset,
            ) {
                Card(
                    variant = CardVariant.Elevated,
                    onClick = { clicks.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .actionsScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.demo_actions_card_primary),
                            style = UiTextStyle(fontSizeSp = 16.sp),
                        )
                        Text(
                            text = stringResource(R.string.demo_actions_card_primary_summary),
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
            }

            else -> ActionsCardVariants(scenario, onClick = { clicks.value += 1 })
        }
    }
}

private fun UiTreeBuilder.ActionsCardVariants(
    scenario: DemoScenarioSpec?,
    onClick: () -> Unit,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp, bottom = 24.dp)
            .actionsScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        FixtureTitle(R.string.demo_actions_card_variants)
        Card(
            variant = CardVariant.Filled,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CardCopy(
                R.string.demo_actions_card_filled,
                R.string.demo_actions_card_filled_summary,
            )
        }
        Card(
            variant = CardVariant.Outlined,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CardCopy(
                R.string.demo_actions_card_outlined,
                R.string.demo_actions_card_outlined_summary,
            )
        }
        Card(
            variant = CardVariant.Filled,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CardCopy(
                R.string.demo_actions_card_disabled,
                R.string.demo_actions_card_disabled_summary,
            )
        }
        Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Button(
                text = stringResource(R.string.demo_actions_text_button),
                onClick = onClick,
                variant = ButtonVariant.Text,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_actions_text_button_disabled),
                onClick = {},
                variant = ButtonVariant.Text,
                enabled = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun UiTreeBuilder.CardCopy(
    titleRes: Int,
    summaryRes: Int,
) {
    Column(spacing = 4.dp, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = stringResource(titleRes), style = UiTextStyle(fontSizeSp = 16.sp))
        Text(
            text = stringResource(summaryRes),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}

private fun UiTreeBuilder.ActionsFabFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val clicks = rememberSaveable(key = "actions-fab-clicks") { mutableStateOf(0) }
    ActionsFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> ActionsFixtureHeader(
                scenario = scenario,
                state = { stringResource(R.string.demo_actions_click_count, clicks.value) },
                onReset = onReset,
            ) {
                FloatingActionButton(
                    onClick = { clicks.value += 1 },
                    size = FabSize.Medium,
                    modifier = Modifier.actionsScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                ) {
                    Icon(
                        source = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = stringResource(
                            R.string.demo_actions_fab_primary_description,
                        ),
                    )
                }
            }

            else -> ActionsFabVariants(scenario, onClick = { clicks.value += 1 })
        }
    }
}

private fun UiTreeBuilder.ActionsFabVariants(
    scenario: DemoScenarioSpec?,
    onClick: () -> Unit,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp, bottom = 24.dp)
            .actionsScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        FixtureTitle(R.string.demo_actions_fab_variants)
        Row(
            spacing = 16.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                FabSize.Small to R.string.demo_actions_fab_small_description,
                FabSize.Medium to R.string.demo_actions_fab_medium_description,
                FabSize.Large to R.string.demo_actions_fab_large_description,
            ).forEach { (size, descriptionRes) ->
                FloatingActionButton(onClick = onClick, size = size) {
                    Icon(
                        source = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = stringResource(descriptionRes),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.demo_actions_fab_size_summary),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        ExtendedFloatingActionButton(
            text = stringResource(R.string.demo_actions_fab_new_project),
            onClick = onClick,
            icon = ImageSource.Resource(R.drawable.demo_media_icon),
        )
        ExtendedFloatingActionButton(
            text = stringResource(R.string.demo_actions_fab_custom_color),
            onClick = onClick,
            icon = ImageSource.Resource(R.drawable.demo_media_icon),
            overrides = ExtendedFloatingActionButtonOverrides(
                containerColor = Theme.colors.secondary,
                contentColor = Theme.colors.background,
            ),
        )
    }
}

private fun UiTreeBuilder.ActionsChipFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val selected = rememberSaveable(key = "actions-chip-selected") { mutableStateOf(false) }
    ActionsFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> ActionsFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(
                        R.string.demo_actions_filter_state,
                        stringResource(
                            if (selected.value) {
                                R.string.demo_actions_state_on
                            } else {
                                R.string.demo_actions_state_off
                            },
                        ),
                    )
                },
                onReset = onReset,
            ) {
                Chip(
                    label = stringResource(R.string.demo_actions_chip_primary),
                    onClick = { selected.value = !selected.value },
                    variant = ChipVariant.Filter,
                    selected = selected.value,
                    modifier = Modifier.actionsScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                )
            }

            else -> ActionsChipVariants(scenario)
        }
    }
}

private fun UiTreeBuilder.ActionsChipVariants(scenario: DemoScenarioSpec?) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp, bottom = 24.dp)
            .actionsScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        FixtureTitle(R.string.demo_actions_chip_variants)
        FlowRow(horizontalSpacing = 8.dp, verticalSpacing = 8.dp) {
            Chip(
                label = stringResource(R.string.demo_actions_chip_assist),
                onClick = {},
                variant = ChipVariant.Assist,
                leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
            Chip(
                label = stringResource(R.string.demo_actions_chip_input),
                onClick = {},
                variant = ChipVariant.Input,
                onTrailingIconClick = {},
            )
            Chip(
                label = stringResource(R.string.demo_actions_chip_suggestion),
                onClick = {},
                variant = ChipVariant.Suggestion,
            )
        }
        Divider()
        FlowRow(horizontalSpacing = 8.dp, verticalSpacing = 8.dp) {
            Chip(
                label = stringResource(R.string.demo_actions_chip_selected),
                onClick = {},
                variant = ChipVariant.Filter,
                selected = true,
            )
            Chip(
                label = stringResource(R.string.demo_actions_chip_unselected),
                onClick = {},
                variant = ChipVariant.Filter,
            )
            Chip(
                label = stringResource(R.string.demo_actions_chip_disabled),
                onClick = {},
                variant = ChipVariant.Assist,
                enabled = false,
            )
            Chip(
                label = stringResource(R.string.demo_actions_chip_selected_disabled),
                onClick = {},
                variant = ChipVariant.Filter,
                selected = true,
                enabled = false,
            )
        }
    }
}

private fun UiTreeBuilder.ActionsListItemFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val clicks = rememberSaveable(key = "actions-list-clicks") { mutableStateOf(0) }
    ActionsFixtureList(generation, listOf("control", "list", "badges")) { section ->
        when (section) {
            "control" -> ActionsFixtureHeader(
                scenario = scenario,
                state = { stringResource(R.string.demo_actions_click_count, clicks.value) },
                onReset = onReset,
            ) {
                ListItem(
                    headlineText = stringResource(R.string.demo_actions_list_primary),
                    supportingText = stringResource(R.string.demo_actions_list_primary_summary),
                    onClick = { clicks.value += 1 },
                    modifier = Modifier.actionsScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                )
            }

            "list" -> ActionsListVariants(scenario)
            else -> ActionsBadgeVariants()
        }
    }
}

private fun UiTreeBuilder.ActionsListVariants(scenario: DemoScenarioSpec?) {
    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp)
            .actionsScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        FixtureTitle(R.string.demo_actions_list_variants)
        ListItem(headlineText = stringResource(R.string.demo_actions_list_single_line))
        ListItem(
            headlineText = stringResource(R.string.demo_actions_list_supporting),
            supportingText = stringResource(R.string.demo_actions_list_supporting_summary),
        )
        ListItem(
            headlineText = stringResource(R.string.demo_actions_list_complete),
            supportingText = stringResource(R.string.demo_actions_list_complete_summary),
            overlineText = stringResource(R.string.demo_actions_list_overline),
            leadingContent = {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(
                        R.string.demo_actions_list_icon_description,
                    ),
                )
            },
            trailingContent = {
                Text(
                    text = stringResource(R.string.demo_actions_list_details),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
            },
        )
    }
}

private fun UiTreeBuilder.ActionsBadgeVariants() {
    Column(
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp, bottom = 24.dp),
    ) {
        FixtureTitle(R.string.demo_actions_badge_variants)
        Row(
            spacing = 24.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BadgeSample(
                labelRes = R.string.demo_actions_badge_number,
                contentDescriptionRes = R.string.demo_actions_badge_number_description,
                badge = { Badge(count = 99) },
            )
            BadgeSample(
                labelRes = R.string.demo_actions_badge_dot,
                contentDescriptionRes = R.string.demo_actions_badge_dot_description,
                badge = { Badge() },
            )
            Column(spacing = 4.dp) {
                BadgedBox(
                    badge = {
                        Badge(
                            count = 3,
                            overrides = BadgeOverrides(containerColor = Theme.colors.secondary),
                        )
                    },
                ) {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = stringResource(
                            R.string.demo_actions_badge_custom_description,
                        ),
                        onClick = {},
                    )
                }
                Text(
                    text = stringResource(R.string.demo_actions_badge_custom),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
    }
}

private fun UiTreeBuilder.BadgeSample(
    labelRes: Int,
    contentDescriptionRes: Int,
    badge: UiTreeBuilder.() -> Unit,
) {
    Column(spacing = 4.dp) {
        BadgedBox(badge = badge) {
            Icon(
                source = ImageSource.Resource(R.drawable.demo_media_icon),
                contentDescription = stringResource(contentDescriptionRes),
            )
        }
        Text(
            text = stringResource(labelRes),
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}

private fun UiTreeBuilder.ActionsFixtureList(
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

private fun UiTreeBuilder.ActionsFixtureHeader(
    scenario: DemoScenarioSpec?,
    state: UiTreeBuilder.() -> String,
    onReset: () -> Unit,
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
        primaryAction()
        Text(
            // Resolve dynamic copy in the lazy item's Session. Resolving it in the parent would
            // leave a stable key/revision free to retain the previous captured String.
            text = state(),
            style = Theme.typography.bodyMedium,
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.actionsScenarioTarget(scenario, DemoAutomationRole.State),
        )
        Button(
            text = stringResource(R.string.demo_actions_reset),
            variant = ButtonVariant.Outlined,
            onClick = onReset,
            modifier = Modifier.actionsScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
    }
}

private fun UiTreeBuilder.FixtureTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = Theme.typography.titleMedium,
        color = Theme.colors.onSurface,
    )
}

private fun Modifier.actionsScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
