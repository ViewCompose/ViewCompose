package com.viewcompose

import com.viewcompose.demo.automation.DemoCatalogAutomation
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioCategory
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.contract.DemoVerificationKind
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.ChipVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import java.util.Locale

@ViewComposePreview(name = "Scenario catalog", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewDemoCatalog() {
    DemoCatalogPage(onLaunch = {})
}

internal fun UiTreeBuilder.DemoCatalogPage(
    onLaunch: (DemoScenarioSpec) -> Unit,
) {
    val queryState = rememberTextFieldState()
    val filtersExpandedState = remember { mutableStateOf(false) }
    val categoryState = remember { mutableStateOf<DemoScenarioCategory?>(null) }
    val kindState = remember { mutableStateOf<DemoVerificationKind?>(null) }
    val localizedScenarios = DemoScenarioRegistry.all().map { scenario ->
        LocalizedDemoScenario(
            scenario = scenario,
            title = stringResource(scenario.titleRes),
            summary = stringResource(scenario.summaryRes),
        )
    }
    val visibleScenarios = filterDemoScenarios(
        scenarios = localizedScenarios,
        query = queryState.text,
        category = categoryState.value,
        kind = kindState.value,
    )
    val activeFilterCount = listOfNotNull(categoryState.value, kindState.value).size

    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(left = 12.dp, top = 8.dp, right = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_catalog_ready),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.demoAutomationTarget(
                DemoCatalogAutomation.contract.require(DemoAutomationRole.Ready),
            ),
        )
        SearchBar(
            state = queryState,
            placeholder = stringResource(R.string.demo_catalog_search_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                text = if (activeFilterCount == 0) {
                    stringResource(R.string.demo_catalog_filters)
                } else {
                    stringResource(R.string.demo_catalog_filters_active, activeFilterCount)
                },
                variant = ButtonVariant.Outlined,
                onClick = { filtersExpandedState.value = !filtersExpandedState.value },
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_catalog_clear_filters),
                variant = ButtonVariant.Text,
                enabled = activeFilterCount > 0 || queryState.text.isNotEmpty(),
                onClick = {
                    queryState.clearText()
                    categoryState.value = null
                    kindState.value = null
                },
            )
        }
        if (filtersExpandedState.value) {
            CatalogFilters(
                categoryState = categoryState,
                kindState = kindState,
            )
        }
        Text(
            text = stringResource(R.string.demo_catalog_results, visibleScenarios.size),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.demoAutomationTarget(
                DemoCatalogAutomation.contract.require(DemoAutomationRole.State),
            ),
        )
        LazyColumn(
            items = visibleScenarios,
            key = { item -> item.scenario.id.value },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .demoAutomationTarget(
                    DemoCatalogAutomation.contract.require(DemoAutomationRole.Target),
                ),
        ) { item ->
            ScenarioCatalogRow(item = item, onLaunch = onLaunch)
        }
        if (visibleScenarios.isEmpty()) {
            Text(
                text = stringResource(R.string.demo_catalog_empty),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

private fun UiTreeBuilder.CatalogFilters(
    categoryState: MutableState<DemoScenarioCategory?>,
    kindState: MutableState<DemoVerificationKind?>,
) {
    FlowRow(
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            label = stringResource(R.string.demo_catalog_all_categories),
            variant = ChipVariant.Filter,
            selected = categoryState.value == null,
            onClick = { categoryState.value = null },
        )
        DemoScenarioCategory.entries.forEach { category ->
            Chip(
                label = stringResource(category.titleResource()),
                variant = ChipVariant.Filter,
                selected = categoryState.value == category,
                onClick = { categoryState.value = category },
            )
        }
    }
    FlowRow(
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            label = stringResource(R.string.demo_catalog_all_kinds),
            variant = ChipVariant.Filter,
            selected = kindState.value == null,
            onClick = { kindState.value = null },
        )
        DemoVerificationKind.entries.forEach { kind ->
            Chip(
                label = stringResource(kind.titleResource()),
                variant = ChipVariant.Filter,
                selected = kindState.value == kind,
                onClick = { kindState.value = kind },
            )
        }
    }
}

private fun UiTreeBuilder.ScenarioCatalogRow(
    item: LocalizedDemoScenario,
    onLaunch: (DemoScenarioSpec) -> Unit,
) {
    Surface(
        key = item.scenario.id.value,
        modifier = Modifier
            .fillMaxWidth()
            .margin(bottom = 8.dp),
    ) {
        Column(
            spacing = 5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(text = item.title)
            Text(
                text = item.scenario.id.value,
                style = UiTextStyle(fontSizeSp = 12.sp),
                color = TextDefaults.secondaryColor(),
            )
            Text(
                text = item.summary,
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
            )
            FlowRow(
                horizontalSpacing = 6.dp,
                verticalSpacing = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                item.scenario.verificationKinds.forEach { kind ->
                    Text(
                        text = stringResource(kind.titleResource()),
                        style = UiTextStyle(fontSizeSp = 11.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }
            Button(
                text = stringResource(R.string.demo_catalog_open),
                onClick = { onLaunch(item.scenario) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("demo.catalog.launch.${item.scenario.id.value}"),
            )
        }
    }
}

internal data class LocalizedDemoScenario(
    val scenario: DemoScenarioSpec,
    val title: String,
    val summary: String,
)

internal fun filterDemoScenarios(
    scenarios: List<LocalizedDemoScenario>,
    query: String,
    category: DemoScenarioCategory?,
    kind: DemoVerificationKind?,
): List<LocalizedDemoScenario> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return scenarios.filter { item ->
        (category == null || item.scenario.category == category) &&
            (kind == null || kind in item.scenario.verificationKinds) &&
            (
                normalizedQuery.isEmpty() ||
                    item.scenario.id.value.contains(normalizedQuery) ||
                    item.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    item.summary.lowercase(Locale.ROOT).contains(normalizedQuery)
                )
    }
}

private fun DemoScenarioCategory.titleResource(): Int = when (this) {
    DemoScenarioCategory.Runtime -> R.string.demo_catalog_category_runtime
    DemoScenarioCategory.Rendering -> R.string.demo_catalog_category_rendering
    DemoScenarioCategory.Collections -> R.string.demo_catalog_category_collections
    DemoScenarioCategory.Input -> R.string.demo_catalog_category_input
    DemoScenarioCategory.AndroidIntegration -> R.string.demo_catalog_category_android
    DemoScenarioCategory.Navigation -> R.string.demo_catalog_category_navigation
    DemoScenarioCategory.DesignSystems -> R.string.demo_catalog_category_design
    DemoScenarioCategory.Performance -> R.string.demo_catalog_category_performance
}

private fun DemoVerificationKind.titleResource(): Int = when (this) {
    DemoVerificationKind.Manual -> R.string.demo_catalog_kind_manual
    DemoVerificationKind.Visual -> R.string.demo_catalog_kind_visual
    DemoVerificationKind.Benchmark -> R.string.demo_catalog_kind_benchmark
}
