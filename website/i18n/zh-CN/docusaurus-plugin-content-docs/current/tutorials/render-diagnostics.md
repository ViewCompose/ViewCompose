---
translation_source: tutorials/render-diagnostics.md
translation_source_hash: 085f8c496c3b46f34921a9d00c0039e5d33921fcb81845f30dee3098cf8a2979
translation_status: current
schema_version: 2
document_id: tutorial.render-diagnostics
doc_type: tutorial
owner:
  kind: project
  id: diagnostics
version_lane: released
capability_ids:
  - diagnostics.correlated-events
  - diagnostics.session-inspection
  - diagnostics.node-timing
  - diagnostics.failure-aggregation
  - renderer.diagnostics
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-diagnostics
  - viewcompose-preview
sample_ids:
  - tutorial.render-diagnostics-dependencies
  - tutorial.render-diagnostics
  - module.diagnostics-dependency
  - module.diagnostics-failure-aggregation
expected_result: 显示可见的渲染计数摘要，并提供按请求执行的 Session 检查与有界脱敏故障快照。
verification_action: 运行样例并点击 Sample render stats，确认摘要只在显式操作后发生变化。
---

# 读取渲染诊断

## 必需依赖

本页可以独立使用。宿主诊断和 `RenderStats` 位于基础应用模块，不需要额外的诊断产物：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="render-diagnostics-dependencies" sample_id="tutorial.render-diagnostics-dependencies" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
repositories { mavenCentral() }

dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha01")
    implementation("androidx.activity:activity:1.12.4")
    implementation("com.google.android.material:material:1.13.0")
}
```

## 按需读取 renderer 计数器

创建 `RenderDiagnosticsTutorialActivity.kt`：

{/* tutorial-sample sample_id="tutorial.render-diagnostics" source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/RenderDiagnosticsTutorialActivity.kt" region="render-diagnostics" */}
```kotlin
package com.viewcompose.samples.tutorials

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFrameCompleted
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.remember
import java.util.concurrent.atomic.AtomicReference

class RenderDiagnosticsTutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestStats = AtomicReference(RenderStats())
        val diagnostics = RenderDiagnostics(
            collection = RenderDiagnosticCollection(
                lifecycle = false,
                failures = false,
                frameLevel = RenderFrameDiagnosticLevel.Stats,
            ),
            sink = { event ->
                if (event is RenderFrameCompleted) {
                    event.stats?.let(latestStats::set)
                }
            },
        )
        setMaterial3UiContent(
            debug = true,
            debugTag = "RenderTutorial",
            diagnostics = diagnostics,
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

诊断 Sink 会在权威 Frame 完成后同步运行，因此应在 Composition 外保存不可变 Stats 快照，只从显式
UI 事件读取。如果每个 `RenderFrameCompleted` 事件都直接写入界面观察的状态，会产生渲染—观察—
再渲染循环，并污染正在测量的计数器。

## 不构建 Frame Tree 地统计生产故障

应用需要有界的重复故障计数时，添加可选产物：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="diagnostics-module-dependency" sample_id="module.diagnostics-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin title="build.gradle.kts"
dependencies {
    implementation("com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01")
}
```

使用一个由应用持有的聚合器作为仅故障 Sink：

{/* compiled-region source="viewcompose-diagnostics/src/test/samples/com/viewcompose/diagnostics/samples/RenderFailureAggregationSamples.kt" region="diagnostics-failure-aggregation" sample_id="module.diagnostics-failure-aggregation" build_target=":viewcompose-diagnostics:compileDebugUnitTestKotlin" */}
```kotlin
fun boundedFailureAggregationSample(
    install: (RenderDiagnostics) -> Unit,
): BoundedRenderFailureAggregator {
    val aggregator = BoundedRenderFailureAggregator()
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = aggregator,
    )
    install(diagnostics)
    return aggregator
}
```

应在 Sink 投递之外调度 Snapshot 与导出。Snapshot 只包含有界脱敏指纹和安全框架上下文，不包含
原始 `Throwable`、消息、原始 Node Key、应用栈帧、文件或行号。框架不选择调度器、存储系统、
用户同意模型、上传端点或遥测厂商。

## 从 Android Studio 检查同一个应用

添加 `debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")`，运行可调试应用，
再在 Android Studio 中选择 `Inspect Device Diagnostics`。只需选择一次 Host Session；摘要会把最近
已提交帧与后续回滚尝试分开，并且只显示安全的 Failure Phase、Recovery、异常类型和可选 Android
View Operation。

使用 `Session sources` 返回该 Activity，使用 `Mounted nodes` 加载并按需高亮真实 View 边界，只在
复现有界交互时使用 `Finite timing`。另一次渲染完成后点击 `Refresh snapshot`；Inspector 不会轮询或
记录持续 History。如需确定性的 Timing Target，Demo 的 `Diagnostics → Renderer` Route 提供可见的
`0/8` 到 `8/8` 工作负载。

## 验证结果

点击 `Sample render stats`，确认出现稳定的计数器摘要。如果加入了可选 Preview 制品，还应确认
Inspector 能显示所选 Session、源码与最近已提交帧，同时不会改变运行页面。Debug 诊断本身可能增加
工作量，性能结论应使用 Release 基准。编译命令：

```bash
./gradlew :samples:tutorials:assembleDebug
```

失败 Hook、脱敏、渲染 Trace 和日志策略请查看[诊断指南](../tooling/diagnostics.md)与
[诊断模块手册](https://docs.viewcompose.com/zh-CN/modules/viewcompose-diagnostics)。
