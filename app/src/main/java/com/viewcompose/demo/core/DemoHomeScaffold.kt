package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.image.coil.CoilRemoteImageLoader
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.HorizontalPager
import com.viewcompose.widget.core.NavigationBar
import com.viewcompose.widget.core.ProvideRemoteImageLoader
import com.viewcompose.widget.core.Scaffold
import com.viewcompose.widget.core.SideEffect
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTheme
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.remember

internal fun UiTreeBuilder.DemoHomeScaffold(
    root: ViewGroup,
) {
    val themeModeState = remember { mutableStateOf(DemoThemeSession.mode) }
    val remoteImageLoader = remember { CoilRemoteImageLoader(root.context.applicationContext) }
    val activity = root.context.findAppCompatActivity()
    val themeTokens = DemoThemeTokens.resolve(
        mode = themeModeState.value,
        context = root.context,
    )
    ProvideRemoteImageLoader(remoteImageLoader) {
        val scaffoldContent: UiTreeBuilder.() -> Unit = {
            val navIndex = remember { mutableStateOf(0) }
            val diagnosticsPageState = remember { mutableStateOf(0) }
            SideEffect {
                activity?.title = "ViewCompose · ${DemoThemeTokens.modeLabel(themeModeState.value, root.context)}"
            }
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        selectedIndex = navIndex.value,
                        onItemSelected = { navIndex.value = it },
                        modifier = Modifier.testTag(DemoTestTags.HOME_NAVIGATION_BAR),
                    ) {
                        Item(label = "目录", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                        Item(label = "诊断", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                        Item(label = "设置", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                        Item(label = "关于", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundColor(Theme.colors.background)
                    .systemBarsInsetsPadding(),
            ) {
                HorizontalPager(
                    currentPage = navIndex.value,
                    onPageChanged = { navIndex.value = it },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Page(key = "catalog") { DemoCatalogPage(root) }
                    Page(key = "diagnostics") {
                        DiagnosticsPage(
                            root = root,
                            selectedPageState = diagnosticsPageState,
                        )
                    }
                    Page(key = "settings") { SettingsPage(themeModeState, root) }
                    Page(key = "about") { AboutPage() }
                }
            }
        }
        UiTheme(tokens = themeTokens, content = scaffoldContent)
    }
}
