package com.viewcompose

import android.content.Intent
import android.view.ViewGroup
import coil3.ImageLoader
import com.viewcompose.demo.automation.DemoCatalogAutomation
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.image.coil.CoilImageLoaderAdapter
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.Scaffold
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.foundation.TopAppBarDefaults
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.node.ImageSource

/** Root host for the executable scenario catalog and its generated utility panels. */
internal fun UiTreeBuilder.DemoHomeScaffold(
    root: ViewGroup,
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
        UiTheme(tokens = themeTokens) {
            val currentTheme = Theme.current
            val catalogTitle = stringResource(R.string.demo_catalog_title)
            SideEffect {
                activity?.title = "$catalogTitle · ${DemoThemeTokens.modeLabel(themeModeState.value, root.context)}"
                activity?.applyDemoThemeWindowAppearance(currentTheme)
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = catalogTitle,
                        actions = {
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.ic_demo_environment),
                                contentDescription = stringResource(R.string.demo_catalog_environment),
                                onClick = {
                                    activity?.startActivity(
                                        Intent(root.context, DemoEnvironmentActivity::class.java),
                                    )
                                },
                                tint = TopAppBarDefaults.titleColor(),
                                modifier = Modifier.demoAutomationTarget(
                                    DemoCatalogAutomation.contract.require(
                                        DemoAutomationRole.PrimaryAction,
                                    ),
                                ),
                            )
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.ic_demo_build_info),
                                contentDescription = stringResource(R.string.demo_catalog_build_info),
                                onClick = {
                                    activity?.startActivity(
                                        Intent(root.context, DemoBuildInfoActivity::class.java),
                                    )
                                },
                                tint = TopAppBarDefaults.titleColor(),
                                modifier = Modifier.demoAutomationTarget(
                                    DemoCatalogAutomation.contract.require(
                                        DemoAutomationRole.SecondaryAction,
                                    ),
                                ),
                            )
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundColor(Theme.colors.background)
                    .systemBarsInsetsPadding()
                    .demoAutomationTarget(
                        DemoCatalogAutomation.contract.require(DemoAutomationRole.Root),
                    ),
            ) {
                DemoCatalogPage { scenario ->
                    activity?.startActivity(
                        DemoScenarioRegistry.createLaunchIntent(root.context, scenario),
                    )
                }
            }
        }
    }
}
