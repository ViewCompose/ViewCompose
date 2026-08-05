---
title: 读取渲染诊断
sidebar_position: 14
translation_source: tutorials/render-diagnostics.md
translation_source_hash: 2d99ffc2a757cd5bb023007db3281ee2c6f0e886ced467ec599ec1316be5f7f7
translation_status: current
---

# 读取渲染诊断

## 必需依赖

本页可以独立使用。宿主诊断和 `RenderStats` 位于基础应用模块，不需要额外的诊断产物：

```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 按需读取 renderer 计数器

创建 `RenderDiagnosticsTutorialActivity.kt`：

{/* tutorial-sample source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/RenderDiagnosticsTutorialActivity.kt" region="render-diagnostics" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.remember
import java.util.concurrent.atomic.AtomicReference

class RenderDiagnosticsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestStats = AtomicReference(RenderStats())
        setUiContent(
            debug = true,
            debugTag = "RenderTutorial",
            onRenderStats = latestStats::set,
        ) {
            val summary = remember { mutableStateOf("No sample yet") }

            Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) {
                Button(
                    "Sample render stats",
                    onClick = {
                        val stats = latestStats.get()
                        summary.value =
                            "${stats.inserts} inserts, ${stats.reuses} reuses, " +
                                "${stats.patchedNodes} patches"
                    },
                )
                Text(summary.value)
            }
        }
    }
}
```
{/* tutorial-sample-end */}

`onRenderStats` 在 frame 后运行，因此应在 composition 外保存不可变快照，只从显式事件读取。
如果每次回调都直接写入界面观察的状态，会产生渲染—观察—再渲染循环，并污染正在测量的计数器。

## 验证结果

点击 `Sample render stats`，确认出现稳定的计数器摘要。debug 诊断本身可能增加工作量，性能结论
应使用 release 基准。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

失败 hook、渲染 trace 和日志策略请查看[诊断指南](../tooling/diagnostics.md)。
