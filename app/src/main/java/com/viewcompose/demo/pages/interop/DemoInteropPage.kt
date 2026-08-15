package com.viewcompose

import android.widget.TextView
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Interop · Android View", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInteropAndroidView() {
    InteropPage()
}

/** A strict fixture for native View update and theme-local propagation. */
internal fun UiTreeBuilder.InteropPage(scenario: DemoScenarioSpec? = null) {
    val alternateContentState = remember { mutableStateOf(false) }

    LazyColumn(
        items = listOf("android_view"),
        key = { section -> section },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "android_view" -> {
                // Dynamic reads belong to the mounted lazy-item Session. Reading them before
                // LazyColumn would freeze ordinary captured values behind this stable item key.
                val isAlternate = alternateContentState.value
                val modeLabel = stringResource(
                    if (isAlternate) {
                        R.string.demo_interop_mode_alternate
                    } else {
                        R.string.demo_interop_mode_primary
                    },
                )
                val nativeText = stringResource(
                    if (isAlternate) {
                        R.string.demo_interop_native_alternate
                    } else {
                        R.string.demo_interop_native_primary
                    },
                )
                val nativeContentDescription =
                    stringResource(R.string.demo_interop_native_description)
                val nativeTextColor = Theme.colors.onSurface

                ScenarioSection(
                    kind = ScenarioKind.Benchmark,
                    title = stringResource(R.string.demo_scenario_interop_android_view_title),
                    subtitle = stringResource(R.string.demo_scenario_interop_android_view_summary),
                ) {
                    Text(
                        text = stringResource(R.string.demo_interop_state, modeLabel),
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .interopScenarioTarget(scenario, DemoAutomationRole.State),
                    )
                    Button(
                        text = stringResource(
                            if (isAlternate) {
                                R.string.demo_interop_action_primary
                            } else {
                                R.string.demo_interop_action_alternate
                            },
                        ),
                        onClick = { alternateContentState.value = !alternateContentState.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .interopScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_interop_reset),
                        onClick = { alternateContentState.value = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 12.dp)
                            .interopScenarioTarget(scenario, DemoAutomationRole.Reset),
                    )
                    Text(
                        text = stringResource(R.string.demo_interop_declarative_mirror, nativeText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .interopScenarioTarget(scenario, DemoAutomationRole.SecondaryTarget),
                    )
                    AndroidView(
                        key = "interop_android_view_target",
                        factory = { context ->
                            TextView(context).apply {
                                includeFontPadding = false
                                textSize = 16f
                            }
                        },
                        update = { view ->
                            (view as TextView).apply {
                                text = nativeText
                                contentDescription = nativeContentDescription
                                setTextColor(nativeTextColor)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .interopScenarioTarget(scenario, DemoAutomationRole.Target),
                    )
                    Text(
                        text = stringResource(R.string.demo_interop_theme_note),
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.margin(top = 8.dp),
                    )
                }
            }

            else -> error("Unsupported interop section: $section")
        }
    }
}

private fun Modifier.interopScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
