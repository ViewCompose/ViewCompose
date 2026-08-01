package com.viewcompose

import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp

@ViewComposePreview(name = "Demo catalog", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewDemoCatalog() {
    DemoCatalogPage(root = null)
}

internal fun UiTreeBuilder.DemoCatalogPage(
    root: ViewGroup?,
) {
    val activity = root?.context?.findAppCompatActivity()
    LazyColumn(
        items = listOf("intro", "available", "planned"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "intro" -> DemoSection(
                title = "功能模块",
                subtitle = "Demo 现在按模块级手动测试路径组织，每个 Activity 对应一个框架关注点和一个适合 benchmark 的入口。",
            ) {
                Text(
                    text = "结构遵循 Compose Tutorials 思路: foundations、state、layouts、input、collections、interop、diagnostics，以及 gestures、animation、graphics、navigation 等规划章节。",
                )
                Text(
                    text = "使用此目录作为手动回归和 macrobenchmark 流程的稳定入口。",
                    color = TextDefaults.secondaryColor(),
                )
            }

            "available" -> DemoSection(
                title = "已实现模块",
                subtitle = "每张卡片打开一个独立 Activity，状态、诊断和 benchmark 设置不依赖一个巨大的页内 pager。",
            ) {
                AVAILABLE_DEMO_MODULES.forEach { module ->
                    ModuleCard(
                        module = module,
                        actionLabel = "打开 ${module.title}",
                        actionVariant = ButtonVariant.Primary,
                        onClick = {
                            val target = module.activityClass ?: return@ModuleCard
                            activity?.startActivity(Intent(activity, target))
                        },
                    )
                }
            }

            else -> DemoSection(
                title = "规划中模块",
                subtitle = "这些章节保持可见，使 demo 路线图在实现落地前就与 Compose 能力地图保持一致。",
            ) {
                PLANNED_DEMO_MODULES.forEach { module ->
                    ModuleCard(
                        module = module,
                        actionLabel = "预览",
                        actionVariant = ButtonVariant.Outlined,
                        onClick = {
                            val target = module.activityClass ?: return@ModuleCard
                            activity?.startActivity(Intent(activity, target))
                        },
                    )
                }
            }
        }
    }
}

private fun UiTreeBuilder.ModuleCard(
    module: DemoModule,
    actionLabel: String,
    actionVariant: ButtonVariant,
    onClick: () -> Unit,
) {
    Surface(
        variant = if (module.status == DemoModuleStatus.Available) {
            SurfaceVariant.Default
        } else {
            SurfaceVariant.Variant
        },
        modifier = Modifier
            .fillMaxWidth()
            .margin(bottom = 12.dp),
    ) {
        Column(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(text = module.title)
            Text(
                text = module.subtitle,
                color = TextDefaults.secondaryColor(),
            )
            Text(
                text = "手动焦点: ${module.manualFocus}",
                color = TextDefaults.secondaryColor(),
            )
            Text(
                text = "Benchmark 路径: ${module.benchmarkPath}",
                color = TextDefaults.secondaryColor(),
            )
            Button(
                text = actionLabel,
                variant = actionVariant,
                onClick = onClick,
                modifier = Modifier
                    .margin(top = 4.dp)
                    .testTag(DemoTestTags.catalogModuleButton(module.key)),
            )
        }
    }
}
