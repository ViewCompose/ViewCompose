package com.viewcompose

import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(
    name = "AboutPage · Light",
    group = "Demo",
    widthDp = 411,
    heightDp = 891,
    theme = PreviewTheme.Light,
)
internal fun UiTreeBuilder.AboutPage() {
    LazyColumn(
        items = listOf("title", "architecture", "stats", "version", "links"),
        key = { it },
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) { section ->
        when (section) {
            "title" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "ViewCompose",
                    style = UiTextStyle(fontSizeSp = 24.sp),
                    modifier = Modifier.margin(top = 16.dp, bottom = 4.dp),
                )
                Text(
                    text = "Android 声明式 UI 框架",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "architecture" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "架构概览",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                DiagnosticFactGroup(
                    title = "渲染管线",
                    facts = listOf(
                        DiagnosticFact("输入", "VNode 树 (DSL 构建)"),
                        DiagnosticFact("处理", "Renderer (diff + reconcile)"),
                        DiagnosticFact("输出", "Android View 层级"),
                    ),
                )
                DiagnosticFactGroup(
                    title = "模块分层",
                    facts = listOf(
                        DiagnosticFact("ui-renderer", "NodeSpec, Modifier, Renderer"),
                        DiagnosticFact("ui-widget-core", "Defaults, DSL, Theme"),
                    ),
                )
            }

            "stats" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "组件统计",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                DiagnosticFactGroup(
                    title = "框架能力",
                    facts = listOf(
                        DiagnosticFact("DSL 函数", "68"),
                        DiagnosticFact("Modifier", "32"),
                        DiagnosticFact("Defaults 对象", "27"),
                        DiagnosticFact("NodeType", "32"),
                    ),
                )
            }

            "version" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "版本信息",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                DiagnosticFactGroup(
                    title = "版本",
                    facts = listOf(
                        DiagnosticFact("版本号", "开发中"),
                        DiagnosticFact("构建类型", "Debug"),
                    ),
                )
            }

            "links" -> Column(
                spacing = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "链接",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.margin(top = 20.dp, bottom = 8.dp),
                )
                Text(
                    text = "GitHub: placeholder",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
    }
}
