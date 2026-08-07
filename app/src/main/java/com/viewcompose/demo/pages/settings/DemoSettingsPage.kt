package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlSize
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp
import java.util.Locale

@ViewComposePreview(name = "Settings · Light", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewSettingsLight() {
    SettingsPage(
        themeModeState = mutableStateOf(DemoThemeMode.Light),
        root = null,
    )
}

@ViewComposePreview(
    name = "Settings · Dark",
    group = "Demo/Pages",
    theme = PreviewTheme.Dark,
)
internal fun UiTreeBuilder.PreviewSettingsDark() {
    SettingsPage(
        themeModeState = mutableStateOf(DemoThemeMode.Dark),
        root = null,
    )
}

internal fun UiTreeBuilder.SettingsPage(
    themeModeState: MutableState<DemoThemeMode>,
    root: ViewGroup?,
) {
    val debugModeState = remember { mutableStateOf(true) }
    val langIndexState = remember { mutableStateOf(0) }

    LazyColumn(
        items = listOf(
            "theme",
            "material3-default",
            "design-system",
            "environment",
            "stats",
            "debug",
            "language",
        ),
        key = { it },
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) { section ->
        when (section) {
            "theme" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "主题切换",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 16.dp, bottom = 8.dp),
                )
                SegmentedControl(
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = themeModeState.value.ordinal,
                    onSelectionChange = { index ->
                        val mode = DemoThemeMode.entries[index]
                        DemoThemeSession.mode = mode
                        themeModeState.value = mode
                    },
                    size = SegmentedControlSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            "material3-default" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "主题与 Token 验证",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp),
                )
                Text(
                    text = "使用同一组组件分别验证 Android XML、Material3 静态基线和 Demo 自定义 Token；页面内会标注来源与关键色值，便于截图诊断。",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Button(
                    text = "验证 Android XML 主题",
                    onClick = {
                        root?.context?.startActivity(
                            Material3DefaultThemeActivity.newIntent(
                                context = root.context,
                                source = DemoThemeSource.AndroidXml,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SETTINGS_THEME_XML_ENTRY),
                )
                Button(
                    text = "验证 Material3 静态基线",
                    onClick = {
                        root?.context?.startActivity(
                            Material3DefaultThemeActivity.newIntent(
                                context = root.context,
                                source = DemoThemeSource.Material3Defaults,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SETTINGS_MATERIAL3_DEFAULT_ENTRY),
                )
                Button(
                    text = "验证 Demo 自定义 Token",
                    onClick = {
                        root?.context?.startActivity(
                            Material3DefaultThemeActivity.newIntent(
                                context = root.context,
                                source = DemoThemeSource.DemoCustom,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SETTINGS_THEME_CUSTOM_ENTRY),
                )
            }

            "design-system" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "多设计系统高保真验证",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp),
                )
                Text(
                    text = "五组件切片会显示设计系统、Token 来源、Recipe、动效和能力降级，便于截图定位 Material 泄漏或错误回退。",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Button(
                    text = "打开多设计系统验证",
                    onClick = {
                        root?.context?.startActivity(
                            DemoDesignSystemVerificationActivity.newIntent(root.context),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.SETTINGS_DESIGN_SYSTEM_ENTRY),
                )
            }

            "environment" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "环境信息",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                DiagnosticFactGroup(
                    title = "运行时环境",
                    facts = listOf(
                        DiagnosticFact("区域设置", Environment.localeTags.firstOrNull() ?: "und"),
                        DiagnosticFact("布局方向", Environment.layoutDirection.name),
                        DiagnosticFact("密度", "${"%.2f".format(Locale.US, Environment.density.density)}x"),
                        DiagnosticFact(
                            "主题模式",
                            root?.context?.let { context ->
                                DemoThemeTokens.modeLabel(themeModeState.value, context)
                            } ?: DemoThemeTokens.modeLabel(
                                mode = themeModeState.value,
                                isSystemDark = themeModeState.value == DemoThemeMode.Dark,
                            ),
                        ),
                    ),
                )
            }

            "stats" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "模块统计",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                DiagnosticFactGroup(
                    title = "Demo 模块",
                    facts = listOf(
                        DiagnosticFact("已实现模块", "${AVAILABLE_DEMO_MODULES.size}"),
                        DiagnosticFact("规划模块", "${PLANNED_DEMO_MODULES.size}"),
                        DiagnosticFact("总计", "${DEMO_MODULES.size}"),
                    ),
                )
            }

            "debug" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "调试模式",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                Switch(
                    text = "启用调试日志",
                    checked = debugModeState.value,
                    onCheckedChange = { debugModeState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "调试日志输出到 ViewComposeSample tag",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 4.dp),
                )
            }

            "language" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "语言切换",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                SegmentedControl(
                    items = listOf("中文", "English"),
                    selectedIndex = langIndexState.value,
                    onSelectionChange = { langIndexState.value = it },
                    size = SegmentedControlSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "语言切换功能暂未实现",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 4.dp),
                )
            }
        }
    }
}
