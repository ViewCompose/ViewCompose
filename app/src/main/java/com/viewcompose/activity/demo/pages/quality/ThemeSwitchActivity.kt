package com.viewcompose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.IdRes
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.nativeView
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp

/** Hosts the deterministic primary Session for cross-Activity theme propagation verification. */
class ThemeSwitchActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_theme_switch_title

    private var originalThemeMode: DemoThemeMode = DemoThemeMode.System

    override fun onCreate(savedInstanceState: Bundle?) {
        originalThemeMode = savedInstanceState
            ?.getInt(STATE_ORIGINAL_THEME_MODE, DemoThemeMode.System.ordinal)
            ?.let { ordinal -> DemoThemeMode.entries.getOrElse(ordinal) { DemoThemeMode.System } }
            ?: DemoThemeSession.mode.also {
                DemoThemeSession.mode = DemoThemeMode.Light
            }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_ORIGINAL_THEME_MODE, originalThemeMode.ordinal)
        super.onSaveInstanceState(outState)
    }

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = requireNotNull(currentScenario()) {
            "ThemeSwitchActivity requires environment.cross-activity-theme"
        }
        builder.CrossActivityThemePage(root, scenario)
    }

    override fun onDestroy() {
        val restoreOriginalTheme = isFinishing && !isChangingConfigurations
        super.onDestroy()
        if (restoreOriginalTheme) {
            DemoThemeSession.mode = originalThemeMode
        }
    }

    private companion object {
        const val STATE_ORIGINAL_THEME_MODE = "cross_activity_theme_original_mode"
    }
}

/** Hosts the second independent render Session used by the cross-Activity fixture. */
class ThemeSwitchSecondaryActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_theme_switch_secondary_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.CrossActivityThemeSecondaryPage(root)
    }

    companion object {
        internal fun newIntent(context: Context): Intent =
            Intent(context, ThemeSwitchSecondaryActivity::class.java)
    }
}

private fun UiTreeBuilder.CrossActivityThemePage(
    root: ViewGroup,
    scenario: DemoScenarioSpec,
) {
    val themeModeState = DemoThemeSession.modeState
    val currentMode = themeModeState.value
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(
                R.string.demo_cross_activity_theme_primary_state,
                DemoThemeTokens.modeLabel(currentMode, root.context),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .scenarioTarget(scenario, DemoAutomationRole.State),
        )
        Text(
            text = stringResource(R.string.demo_cross_activity_theme_goal),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier
                .fillMaxWidth()
                .scenarioTarget(scenario, DemoAutomationRole.Target),
        )
        Button(
            text = stringResource(R.string.demo_cross_activity_theme_toggle_primary),
            onClick = { themeModeState.value = currentMode.toggledExplicitMode() },
            modifier = Modifier
                .fillMaxWidth()
                .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
        )
        Button(
            text = stringResource(R.string.demo_cross_activity_theme_open_secondary),
            onClick = {
                root.context.startActivity(ThemeSwitchSecondaryActivity.newIntent(root.context))
            },
            modifier = Modifier
                .fillMaxWidth()
                .scenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
        )
        Button(
            text = stringResource(R.string.demo_cross_activity_theme_reset),
            onClick = { themeModeState.value = DemoThemeMode.Light },
            modifier = Modifier
                .fillMaxWidth()
                .scenarioTarget(scenario, DemoAutomationRole.Reset),
        )
    }
}

private fun UiTreeBuilder.CrossActivityThemeSecondaryPage(root: ViewGroup) {
    val themeModeState = DemoThemeSession.modeState
    val currentMode = themeModeState.value
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(
                R.string.demo_cross_activity_theme_secondary_state,
                DemoThemeTokens.modeLabel(currentMode, root.context),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .androidResourceId(R.id.demo_cross_activity_theme_secondary_state),
        )
        Text(
            text = stringResource(R.string.demo_cross_activity_theme_secondary_goal),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            text = stringResource(R.string.demo_cross_activity_theme_toggle_secondary),
            onClick = { themeModeState.value = currentMode.toggledExplicitMode() },
            modifier = Modifier
                .fillMaxWidth()
                .androidResourceId(R.id.demo_cross_activity_theme_secondary_action),
        )
        Button(
            text = stringResource(R.string.demo_cross_activity_theme_return),
            onClick = { root.context.findAppCompatActivity()?.finish() },
            modifier = Modifier
                .fillMaxWidth()
                .androidResourceId(R.id.demo_cross_activity_theme_secondary_return),
        )
    }
}

private fun DemoThemeMode.toggledExplicitMode(): DemoThemeMode =
    if (this == DemoThemeMode.Dark) DemoThemeMode.Light else DemoThemeMode.Dark

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
): Modifier = demoAutomationTarget(scenario.automation.require(role))

private fun Modifier.androidResourceId(@IdRes id: Int): Modifier = nativeView(
    key = "demo-resource-id:$id",
) { view ->
    if (view.id != id) {
        view.id = id
    }
}
