package com.viewcompose

import android.view.ViewGroup
import androidx.annotation.StringRes
import coil3.ImageLoader
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.image.coil.CoilImageLoaderAdapter
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.Scaffold
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.foundation.TopAppBarDefaults
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

/**
 * demo 子页面共享脚手架，提供返回按钮、系统栏内边距和主题覆盖。
 * Shared scaffold for demo sub-pages, providing back navigation, system-bar padding, and theme override.
 */
internal fun UiTreeBuilder.DemoSubPageScaffold(
    root: ViewGroup,
    @StringRes titleRes: Int,
    scenario: DemoScenarioSpec? = null,
    content: (UiTreeBuilder) -> Unit,
) {
    val themeModeState = DemoThemeSession.modeState
    val coilImageLoader = remember {
        ImageLoader.Builder(root.context.applicationContext).build()
    }
    val imageLoader = remember { CoilImageLoaderAdapter(coilImageLoader) }
    DisposableEffect(coilImageLoader) {
        onDispose(coilImageLoader::shutdown)
    }
    val activity = root.context.findAppCompatActivity()
    val themeTokens = DemoThemeTokens.select(
        mode = themeModeState.value,
        isSystemDark = DemoThemeTokens.isSystemDark(root.context),
    )
    ProvideImageLoader(imageLoader) {
        val scaffoldContent: UiTreeBuilder.() -> Unit = {
            val currentTheme = Theme.current
            val resolvedTitle = stringResource(scenario?.titleRes ?: titleRes)
            val windowTitle = stringResource(
                R.string.demo_activity_title_format,
                resolvedTitle,
                DemoThemeTokens.modeLabel(themeModeState.value, root.context),
            )
            val rootModifier = Modifier
                .fillMaxSize()
                .systemBarsInsetsPadding()
                .backgroundColor(Theme.colors.background)
                .let { modifier ->
                    scenario?.automation?.get(DemoAutomationRole.Root)?.let {
                        modifier.demoAutomationTarget(it)
                    } ?: modifier
                }
            SideEffect {
                // 子页标题展示当前主题模式，帮助手工验收时确认 token 覆盖是否生效。
                // Sub-page titles include the theme mode so manual QA can confirm token overrides.
                activity?.title = windowTitle
                activity?.applyDemoThemeWindowAppearance(currentTheme)
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = resolvedTitle,
                        navigationIcon = {
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.demo_back),
                                onClick = { activity?.finish() },
                                tint = TopAppBarDefaults.titleColor(),
                            )
                        },
                    )
                },
                modifier = rootModifier,
            ) {
                Column(
                    spacing = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    if (scenario != null) {
                        Text(
                            text = scenario.benchmark?.let { benchmark ->
                                stringResource(
                                    R.string.demo_scenario_ready_format,
                                    scenario.id.value,
                                    benchmark.workloadRevision,
                                )
                            } ?: stringResource(
                                R.string.demo_scenario_ready_unversioned_format,
                                scenario.id.value,
                            ),
                            color = TextDefaults.secondaryColor(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .demoAutomationTarget(
                                    scenario.automation.require(DemoAutomationRole.Ready),
                                ),
                        )
                        if (scenario.benchmark != null && !scenario.mutable) {
                            Text(
                                text = stringResource(
                                    R.string.demo_scenario_workload_revision_format,
                                    scenario.benchmark.workloadRevision,
                                ),
                                color = TextDefaults.secondaryColor(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .demoAutomationTarget(
                                        scenario.automation.require(DemoAutomationRole.State),
                                    ),
                            )
                        }
                    }
                    content(this)
                }
            }
        }
        UiTheme(tokens = themeTokens, content = scaffoldContent)
    }
}
