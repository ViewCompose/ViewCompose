package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.image.coil.CoilRemoteImageLoader
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.IconButton
import com.viewcompose.widget.core.ProvideRemoteImageLoader
import com.viewcompose.widget.core.Scaffold
import com.viewcompose.widget.core.SideEffect
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.TopAppBar
import com.viewcompose.widget.core.TopAppBarDefaults
import com.viewcompose.widget.core.UiTheme
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.remember

/**
 * demo 子页面共享脚手架，提供返回按钮、系统栏内边距和主题覆盖。
 * Shared scaffold for demo sub-pages, providing back navigation, system-bar padding, and theme override.
 */
internal fun UiTreeBuilder.DemoSubPageScaffold(
    root: ViewGroup,
    title: String,
    content: (UiTreeBuilder) -> Unit,
) {
    val themeModeState = remember { mutableStateOf(DemoThemeSession.mode) }
    val remoteImageLoader = remember { CoilRemoteImageLoader(root.context.applicationContext) }
    val activity = root.context.findAppCompatActivity()
    val overrideTheme = when (themeModeState.value) {
        DemoThemeMode.System -> null
        DemoThemeMode.Light -> DemoThemeTokens.light
        DemoThemeMode.Dark -> DemoThemeTokens.dark
    }
    ProvideRemoteImageLoader(remoteImageLoader) {
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
        if (overrideTheme == null) {
            // System 模式使用上层解析后的主题，避免在子页重复包一层相同 token。
            // System mode uses the already resolved parent theme and avoids wrapping identical tokens.
            scaffoldContent()
        } else {
            UiTheme(tokens = overrideTheme, content = scaffoldContent)
        }
    }
}
