package com.viewcompose

import android.view.ViewGroup
import coil3.ImageLoader
import com.viewcompose.image.coil.CoilImageLoaderAdapter
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.Scaffold
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberSaveable

/**
 * demo 首页根脚手架，承载目录、诊断、设置和关于四个顶层页面。
 * Root scaffold for the demo home, hosting catalog, diagnostics, settings, and about pages.
 */
internal fun UiTreeBuilder.DemoHomeScaffold(
    root: ViewGroup,
) {
    val themeModeState = DemoThemeSession.modeState
    val coilImageLoader = remember {
        ImageLoader.Builder(root.context.applicationContext).build()
    }
    val imageLoader = remember { CoilImageLoaderAdapter(coilImageLoader) }
    DisposableEffect(coilImageLoader) {
        return@DisposableEffect coilImageLoader::shutdown
    }
    val activity = root.context.findAppCompatActivity()
    val themeTokens = DemoThemeTokens.select(
        mode = themeModeState.value,
        isSystemDark = DemoThemeTokens.isSystemDark(root.context),
    )
    ProvideImageLoader(imageLoader) {
        val scaffoldContent: UiTreeBuilder.() -> Unit = {
            // 首页导航页签需要跨渲染恢复，避免旋转或重建后跳回目录页。
            // The home tab index is saveable so rotation or recreation does not reset to the catalog.
            val navIndex = rememberSaveable(key = "demo-home-navigation-index") {
                mutableStateOf(0)
            }
            val diagnosticsPageState = remember { mutableStateOf(0) }
            val currentTheme = Theme.current
            SideEffect {
                // Activity chrome 属于宿主副作用，跟随当前主题 token 在每帧提交后同步。
                // Activity chrome is a host side effect and is synchronized after each committed frame.
                activity?.title = "ViewCompose · ${DemoThemeTokens.modeLabel(themeModeState.value, root.context)}"
                activity?.applyDemoThemeWindowAppearance(currentTheme)
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
