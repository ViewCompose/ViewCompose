package com.viewcompose

import android.provider.Settings
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
    override val demoTitleRes: Int = R.string.demo_environment_title

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
        items = listOf("theme", "language", "runtime", "routes"),
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
                    items = demoSegmentedItems(
                        "system" to stringResource(R.string.demo_environment_theme_system),
                        "light" to stringResource(R.string.demo_environment_theme_light),
                        "dark" to stringResource(R.string.demo_environment_theme_dark),
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

            "language" -> {
                val applicationLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val selectedLanguage = when {
                    applicationLocales.startsWith("zh", ignoreCase = true) -> 2
                    applicationLocales.startsWith("en", ignoreCase = true) -> 1
                    else -> 0
                }
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) {
                    Text(text = stringResource(R.string.demo_environment_language))
                    Text(
                        text = stringResource(
                            when (selectedLanguage) {
                                1 -> R.string.demo_environment_language_english
                                2 -> R.string.demo_environment_language_chinese
                                else -> R.string.demo_environment_language_system
                            },
                        ),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.testTag(DemoTestTags.SETTINGS_LANGUAGE_STATUS),
                    )
                    SegmentedControl(
                        items = demoSegmentedItems(
                            "system" to stringResource(R.string.demo_environment_language_option_system),
                            "english" to stringResource(R.string.demo_environment_language_option_english),
                            "chinese" to stringResource(R.string.demo_environment_language_option_chinese),
                        ),
                        selectedIndex = selectedLanguage,
                        onSelectionChange = { index ->
                            val locales = when (index) {
                                1 -> LocaleListCompat.forLanguageTags("en")
                                2 -> LocaleListCompat.forLanguageTags("zh-CN")
                                else -> LocaleListCompat.getEmptyLocaleList()
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        },
                        size = SegmentedControlSize.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DemoTestTags.SETTINGS_LANGUAGE_CONTROL),
                    )
                    Text(
                        text = stringResource(R.string.demo_environment_language_note),
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }

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
                        val scenario = DemoScenarioRegistry.require(
                            DemoScenarioIds.EnvironmentCrossActivityTheme.value,
                        )
                        root.context.startActivity(
                            DemoScenarioRegistry.createLaunchIntent(root.context, scenario),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.demo_environment_scope_note),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
    }
}
