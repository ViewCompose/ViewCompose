---
translation_source: tooling/preview.md
translation_source_hash: df17d794dc6ec5660e2a07f99103c078dae63efaed3b2fa077b64dc3fc6b2c49
translation_status: current
---

# ViewCompose 预览

ViewCompose 推荐使用第一方原生静态预览工具链。它通过 Layoutlib 把参与编译的 ViewCompose DSL
渲染为 Android View，不要求业务模块接入 Compose。已有项目需要复用 Compose 工具时，仍可把
Compose Preview 桥接作为补充方案。

## 原生预览工具链如何协作

| 组成部分 | 职责 |
| --- | --- |
| `com.viewcompose.preview` Gradle 插件 | 发现参与编译的预览入口、准备 Android Variant 输入，并启动渲染任务。 |
| `viewcompose-preview-core` | 在 debug Classpath 提供 `@ViewComposePreview`、配置模型与共享预览协议。 |
| `viewcompose-preview-worker-host` 与 `viewcompose-preview-runner` | 在 Android Studio 进程之外运行 Layoutlib 和应用渲染代码，生成 PNG 与结构化诊断产物。 |
| `ViewCompose Preview` Android Studio 插件 | 提供 Gutter Action、预览工具窗口与 Gallery、增量刷新、源码导航和诊断检查。 |

Gradle 插件与 Android Studio 插件需要分别安装。添加 Maven 依赖并声明
`id("com.viewcompose.preview")` 只完成构建侧配置，不会安装 IDE 界面。

## 安装原生静态预览

### 1. 配置 Android 模块

应用预览 Gradle 插件，并把相关产物限制在 debug 或仅工具使用的 Configuration：

```kotlin title="build.gradle.kts"
plugins {
    id("com.viewcompose.preview") version "0.1.0-alpha03"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha03")
    add(
        "viewComposePreviewWorkerHost",
        "com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha03",
    )
    add(
        "viewComposePreviewRunner",
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha04",
    )
}
```

当前预览示例已对这组版本完成联合验证。ViewCompose 产物独立演进；混用其他版本前请检查
[已发布模块目录](../modules/README.md)。基础原生预览不需要 Compose Compiler Plugin、Compose
`buildFeatures` 或 `viewcompose-preview` 桥接产物。

### 2. 安装 Android Studio 插件

在 Android Studio 中打开 `Settings | Plugins | Marketplace`，搜索并安装
`ViewCompose Preview`。如果 IDE 提示，请重启 Android Studio。只有完成这项 IDE 安装，才能获得
`ViewCompose Preview` 工具窗口、Gutter 渲染动作、Gallery、源码导航、增量刷新与诊断能力。

当前 Marketplace 版本线为 `1.1.0`，面向 Android Studio `261.*` Build Family 发布。IDE 插件与
Maven 产物、Gradle 插件独立版本化。

### 3. 声明预览入口

为公开的顶层 DSL 函数添加 Annotation。编译后的函数只能接收 `UiTreeBuilder` Receiver，并返回
`Unit`。以下入口复制自参与编译的计数器示例：

```kotlin title="CounterPreview.kt"
package com.viewcompose.samples.counter

import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.foundation.UiTreeBuilder

@ViewComposePreview(
    name = "Counter · Light",
    group = "Samples/Getting started",
)
@ViewComposePreview(
    name = "Counter · Dark",
    group = "Samples/Getting started",
    theme = PreviewTheme.Dark,
)
fun UiTreeBuilder.CounterPreview() {
    CounterScreen()
}
```

`@ViewComposePreview` 可以重复声明，也可以通过源码可见的自定义 Meta-annotation 使用。配置可描述
主题、Locale、布局方向、Density、Font Scale、Viewport 与 API Level。预览入口和仅用于预览的
Theme Provider 如果不参与应用运行，应放在 debug 或专用 Preview Source Set 中。

### 4. 在 Android Studio 中渲染

同步项目，打开 Kotlin 文件，然后点击带 Annotation 函数旁的 ViewCompose 预览 Gutter 图标。
也可以打开 `View | Tool Windows | ViewCompose Preview`，再通过 Gallery 选择入口。插件会调用
Gradle Discovery 与 Render Pipeline，并展示原生 Android View 结果及其配置变体。

工具窗口可见时，保存仅修改源码的内容会走增量刷新；修改 Signature、Resource、Manifest 或依赖后，
应使用完整更新动作。检查面板会展示原生 View Tree、VNode Structure、Layout Bound、Composition、
Patch Activity、Phase Timing 与可导航到源码的诊断。应用代码和 Layoutlib 在有界 Worker 进程中
执行，不会装入 Android Studio 进程。

## 保持应用主题一致

原生 Runner 会根据预览配置解析 Android Resource Qualifier 与 ViewCompose Environment Value。
如果默认 Android Theme Bridge 不够用，请实现 `PreviewThemeProvider`，并用
`@ViewComposePreviewThemeProvider` 标记一个 Provider。它必须返回相互匹配的 Android Theme
`Context` 与 `UiThemeTokens`，使原生 View 和 DSL 使用同一套主题。

Provider API 由仅用于 debug 的 `viewcompose-preview` 产物提供：

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")
}
```

Provider 契约与生命周期规则请参阅
[Preview Integration 模块](../modules/viewcompose-preview/README.md)。

原生静态 Preview 与 Compose 桥接都会安装应用 Host 使用的同一个
`AndroidResourceEnvironment`。`stringResource`、`colorResource`、`dimensionResource` 等调用从
Preview 限定的 Context 解析，因此 Locale、Density、Direction 与 Night Qualifier 会和原生 View
一致。静态帧关闭 Callback 观察，因为确定性 Configuration 替换由 Preview Descriptor 负责。

## Compose Preview 桥接（可选补充）

只有项目确实需要复用已有 Compose Preview Surface 时，才使用 Compose 桥接。它通过
`AndroidView` 在 Compose 中嵌入 ViewCompose；这不是推荐的原生预览主路径，也不能替代
ViewCompose 插件提供的 Gallery、源码导航、应用 Theme Provider、静态产物和结构化诊断。

使用方模块需要启用 Compose，并把桥接放到仅开发使用的 Classpath：

```kotlin title="build.gradle.kts（可选 Compose 桥接）"
plugins {
    alias(libs.plugins.kotlin.compose)
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")
}
```

然后使用 `com.viewcompose.preview` 中的 `ViewComposePreview` 或
`ViewComposePreviewWithRoot` 包装 DSL：

```kotlin
@Preview
@Composable
fun composePreviewBridgeSample() {
    ViewComposePreview {
        Text("ViewCompose")
    }
}
```

这个桥接入口由参与编译的 `viewcompose-preview` API Sample 覆盖。桥接使用 `UiThemeDefaults` 和
Compose Preview Lifecycle Semantics。需要生产主题一致性或 ViewCompose 诊断链路时，请选择
原生静态 Runner。

## 检查真机诊断

Android Studio 插件只提供一个 `Inspect Device Diagnostics` 工具栏与 Tools 菜单动作。Alpha 版本会
硬切删除原来的 Locate、Highlight、Clear 与 Timing 动作：用户只需选择一次设备和 Session，便能在
同一个 Inspector 中查看源码、最近关联帧与失败、Mounted Node 高亮和有限耗时。

1. 通过 `debugImplementation` 引入 `viewcompose-preview`，安装并让该可调试构建保持前台。
2. 在设备上进入待排查的 ViewCompose 页面。
3. 选择 `Inspect Device Diagnostics`；多台设备在线时，按设备类型、Android 版本和序列号选择。
4. 在 Parent/Child Tree 中选择 Session。摘要会区分活动、不可见、未启用和已结束生命周期；分别显示
   最近已提交帧与后续回滚尝试；并关联最近一次安全失败的 Phase、Recovery、异常类型和 Android View
   Operation。
5. 使用 `Session sources` 跳转所选 Owner，使用 `Mounted nodes` 加载、跳转、高亮或清除真实 View
   边界，使用 `Finite timing` 采集并跳转可相加的 Top-cost 记录；无需再次选择设备或 Session。

Inspector 只在显式操作时刷新。每次请求都会查找前台包、生成一次性 Nonce，并只在 Nonce、Operation、
Package 和存活 Process 全部匹配时读取一份私有响应。协议 v7 会硬拒绝旧报告。关联 Snapshot 只读取
Session 已保留的状态，不安装 Event History 或持续 Callback；也不暴露原始异常、Message、Cause、
Stack、应用 Key、View 文本、Semantics、State、Local 值、URL、Credential 或任意 `toString()`。
异常输出仅保留最长 256 字符的二进制类名。

Host、Navigation 与 Pager Session 可以携带有界 Source Candidate。Lazy Item、Overlay 与 Preview
Session 无需组合期 Source Stack Capture 也会出现在树中。源码导航只在当前项目中解析有界元数据；
缺失源码会明确失败，不会跳到另一个 Session。

节点请求最多访问 2,048 个 Mounted Node，返回 512 个、深度不超过 64，并分配新的不透明进程内 Token。
高亮会解析当前弱引用 View，报告完整与裁剪后的屏幕可见边界，并安装最长五秒、不可交互的 Overlay。
替换、Detach、Session 释放、显式清除和超时都会移除它。Stale、Recycled、Hidden、Fully Clipped、
Synthetic/Unsupported、Ended 与 Rejected 都会 Fail Closed，不改变布局、Focus、Accessibility Focus、
输入或应用 State。

耗时采集会提示触发工作负载，并在最多八个已完成 Frame Attempt 或两秒后停止。Composition 与
Reconciliation 区分 Inclusive 和 Self，Binding 使用 Direct；Studio 只把 Self/Direct 记录作为可相加
Top Cost，并报告时钟开销、Drop、Truncation、Unsupported Domain 与结束原因，还能从选中记录跳转
源码。结果限制为每帧 64 个计时节点、总计 512 条记录、深度 32、128 个有界字符串和 256 KiB。
它不测量 Android Measure/Layout/Draw、GPU、RenderThread、SurfaceFlinger、解码、网络、数据库或
外部 SDK。没有请求时，不会遍历 Mounted Tree、写报告、轮询、安装持续 Observer、逐节点读取时钟或
分配计时记录。

Receiver 要求 ADB Shell 持有的 Android `DUMP` 权限，并独立确认进程可调试。如果没有报告，请让
目标应用保持前台，并确认包含当前 `viewcompose-preview` 制品。`Diagnostics → Renderer` Demo Route
为刷新、Mounted-node 替换/高亮和可见的 `0/8` 到 `8/8` 耗时工作负载提供稳定自动化 Tag。

## 检查真机动画时间线

当包含 `viewcompose-preview` 的可调试应用位于前台时，选择 **Tools | Inspect Device Animation
Timeline**。插件会发现当前已提交的 ViewCompose Transition；如有多个则要求选择一个，随后针对
该 Identity 采集 500 ms，并打开只读报告。该操作不会控制设备动画。

报告会明确区分以下语义：

- Observation 是有界真机 Capture，不是持续 Profiler；
- Control 仅属于使用公开 `SeekableTransitionState.seekTo` 的 Static/Interactive Preview；
- 每个 Channel 保留自身 Duration，因此短 Channel 与最长 Segment 都可见；
- Spring Safety Guard Terminal、Interruption/Retarget Sample 与 Unsupported/Private Value 都会
  明确展示，不会被归一化隐藏。

请求沿用真机源码定位的最小权限 Debug Boundary：ADB Shell `DUMP` Permission Broadcast、一次性
32 字符 Nonce、前台 Package 与存活 Process 校验，以及应用私有 Cache 中的原子替换响应。
Discovery 只读取一次 Snapshot；选中后的 Capture 最长 500 ms，最多记录 64 个不同 Sample、每个
Sample 32 个 Channel，总输出不超过 256 KiB。Dialog 关闭后不会留下活动 Capture、Callback、
Thread 或 Report Publisher。

2026-08-23 的 Xiaomi MI 6 验收发现 4 个已经组合的 Timeline，并选中
`demo_seekable_transition`；报告成功捕获 `180/420/600/720 ms` 的不同 Channel Duration、
Unsupported Generic-vector 与安全数值。运行页面保持视觉不变，这是只读能力的预期结果；只有
报告随采样推进。Preview-owned Control 由调用公开 `seekTo` API 的
`animation-seekable-transition` Catalog Spec，以及确定性的 `SeekableTransitionState`
Ownership、Range、Retarget 与 Cancellation 测试单独覆盖。

## 快照回归

运行模块级快照验证：

```bash
./gradlew :viewcompose-preview:verifyPaparazziDebug
```

已提交的快照基准位于：

`viewcompose-preview/src/test/snapshots/images/`

审阅并确认视觉变更符合预期后，使用以下命令录制新基准：

```bash
./gradlew :viewcompose-preview:recordPaparazziDebug
```

提交前必须审阅每一张变更图片。原因不明的差异必须修复，不能直接录制。验证报告和差异图片输出到
`viewcompose-preview/build/reports/paparazzi/`；仓库 CI 会把 `qaPreview` 作为独立的必需门禁运行。
CI 失败时，Paparazzi 差异图片和测试报告会保存在 `qa-preview-failure-<attempt>` 产物中 7 天。

目录快照测试最多允许 `0.15%` 的整图差异，此容差仅用于吸收受支持的 macOS 与 Linux 主机之间
已知的 Layoutlib 原生可编辑文本字形栅格化差异。不得为了接受原因不明的布局、颜色或内容变化而
提高该阈值；应修复回归，或在人工审阅后为有意变更重新录制基准。

## 浮层预览策略

预览场景使用静态内容模拟浮层，不创建真正的窗口层。Dialog、Popup 和 BottomSheet 的真实行为
由 Instrumentation 测试覆盖。

## 相关文档

- [Preview Core 模块](../modules/viewcompose-preview-core/README.md)
- [Preview Gradle Plugin 模块](../modules/viewcompose-preview-gradle-plugin/README.md)
- [Preview Runner 模块](../modules/viewcompose-preview-runner/README.md)
- [Preview Worker Host 模块](../modules/viewcompose-preview-worker-host/README.md)
- [Preview Integration 模块](../modules/viewcompose-preview/README.md)
