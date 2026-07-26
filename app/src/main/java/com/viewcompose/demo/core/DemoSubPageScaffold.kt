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
            SideEffect {
                activity?.title = "$title · ${DemoThemeTokens.modeLabel(themeModeState.value, root.context)}"
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
            scaffoldContent()
        } else {
            UiTheme(tokens = overrideTheme, content = scaffoldContent)
        }
    }
}
