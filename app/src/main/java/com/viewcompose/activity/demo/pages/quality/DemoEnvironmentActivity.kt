package com.viewcompose

import android.provider.Settings
import android.view.ViewGroup
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlSize
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp
import java.util.Locale

/** Hosts global Demo controls separately from executable verification scenarios. */
class DemoEnvironmentActivity : DemoRenderActivity() {
    override val demoTitle: String
        get() = getString(R.string.demo_environment_title)

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.DemoEnvironmentPage(root)
    }
}

private fun UiTreeBuilder.DemoEnvironmentPage(root: ViewGroup) {
    val themeModeState = DemoThemeSession.modeState
    val animatorScale = runCatching {
        Settings.Global.getFloat(
            root.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    LazyColumn(
        items = listOf("theme", "runtime", "routes"),
        key = { item -> item },
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) { section ->
        when (section) {
            "theme" -> Column(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Text(text = stringResource(R.string.demo_environment_theme))
                Text(
                    text = themeModeState.value.name,
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.testTag(DemoTestTags.SETTINGS_THEME_STATUS),
                )
                SegmentedControl(
                    items = listOf(
                        stringResource(R.string.demo_environment_theme_system),
                        stringResource(R.string.demo_environment_theme_light),
                        stringResource(R.string.demo_environment_theme_dark),
                    ),
                    selectedIndex = themeModeState.value.ordinal,
                    onSelectionChange = { index ->
                        themeModeState.value = DemoThemeMode.entries[index]
                    },
                    size = SegmentedControlSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SETTINGS_THEME_CONTROL),
                )
            }

            "runtime" -> DiagnosticFactGroup(
                title = stringResource(R.string.demo_environment_runtime),
                facts = listOf(
                    DiagnosticFact(
                        stringResource(R.string.demo_environment_locale),
                        Environment.localeTags.joinToString().ifEmpty { "und" },
                    ),
                    DiagnosticFact(
                        stringResource(R.string.demo_environment_direction),
                        Environment.layoutDirection.name,
                    ),
                    DiagnosticFact(
                        stringResource(R.string.demo_environment_density),
                        String.format(Locale.US, "%.2fx", Environment.density.density),
                    ),
                    DiagnosticFact(
                        stringResource(R.string.demo_environment_font_scale),
                        String.format(Locale.US, "%.2fx", Environment.density.fontScale),
                    ),
                    DiagnosticFact(
                        stringResource(R.string.demo_environment_animator_scale),
                        String.format(Locale.US, "%.2fx", animatorScale),
                    ),
                ),
            )

            else -> Column(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 12.dp, bottom = 20.dp),
            ) {
                Button(
                    text = stringResource(R.string.demo_environment_resource_scenario),
                    onClick = {
                        val scenario = DemoScenarioRegistry.require(
                            DemoScenarioIds.EnvironmentResources.value,
                        )
                        root.context.startActivity(
                            DemoScenarioRegistry.createLaunchIntent(root.context, scenario),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    text = stringResource(R.string.demo_environment_cross_activity),
                    onClick = {
                        root.context.startActivity(ThemeSwitchActivity.newIntent(root.context))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SETTINGS_CROSS_ACTIVITY_THEME_ENTRY),
                )
                Text(
                    text = stringResource(R.string.demo_environment_scope_note),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
    }
}
