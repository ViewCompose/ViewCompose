package com.viewcompose

import android.view.ViewGroup
import coil3.ImageLoader
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
    title: String,
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
            SideEffect {
                // 子页标题展示当前主题模式，帮助手工验收时确认 token 覆盖是否生效。
                // Sub-page titles include the theme mode so manual QA can confirm token overrides.
                activity?.title = "$title · ${DemoThemeTokens.modeLabel(themeModeState.value, root.context)}"
                activity?.applyDemoThemeWindowAppearance(currentTheme)
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = title,
                        navigationIcon = {
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                onClick = { activity?.finish() },
                                tint = TopAppBarDefaults.titleColor(),
                            )
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsInsetsPadding()
                    .backgroundColor(Theme.colors.background),
            ) {
                Column(
                    spacing = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    content(this)
                }
            }
        }
        UiTheme(tokens = themeTokens, content = scaffoldContent)
    }
}
