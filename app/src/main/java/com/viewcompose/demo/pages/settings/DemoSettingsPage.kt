package com.viewcompose

import android.view.ViewGroup
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Environment
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.SegmentedControl
import com.viewcompose.widget.core.SegmentedControlSize
import com.viewcompose.widget.core.Switch
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp
import java.util.Locale

internal fun UiTreeBuilder.SettingsPage(
    themeModeState: MutableState<DemoThemeMode>,
    root: ViewGroup,
) {
    val debugModeState = remember { mutableStateOf(true) }
    val langIndexState = remember { mutableStateOf(0) }

    LazyColumn(
        items = listOf("theme", "environment", "stats", "debug", "language"),
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
                        DiagnosticFact("主题模式", DemoThemeTokens.modeLabel(themeModeState.value, root.context)),
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
