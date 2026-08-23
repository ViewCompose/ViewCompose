---
translation_source: tooling/preview.md
translation_source_hash: c03c1737e6e7a55ecac967d13213ef6025764a75d5795715e2487656d216071e
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

## 定位真机当前 DSL

`ViewCompose Preview` Android Studio 插件还提供独立的 `Locate Device DSL` 工具栏动作与
Tools 菜单入口，不与预览工具窗口共用图标。要打开设备当前可见页面的 DSL：

1. 通过 `debugImplementation` 引入 `viewcompose-preview`，然后安装并打开该可调试应用。
2. 在设备上进入目标 ViewCompose 页面。
3. 在 Android Studio 中选择 `Locate Device DSL`。

只有一台在线设备时会直接使用它。连接多台真机或模拟器时，插件会先弹出设备选择框，显示设备
类型、Android 版本和序列号。当同一窗口存在多个同样可见且嵌套最深的 ViewCompose 会话（例如
双栏布局）时，还会显示第二个选择框列出候选源码位置。

该动作先查找前台包名、生成一次性 Nonce，再通过 ADB 向 `viewcompose-preview` Debug Receiver
发送显式请求。进程只采样一次当前 Session 可见性并写入一份私有响应；IDE 仅在 Nonce、包名和
存活进程都匹配时才接受它。滚动与布局不会刷新响应。随后插件把有界 JVM 源码候选解析到当前项目。
当共享 Scaffold 先于 Content 发出工具栏或容器节点时，插件会移除在其他候选中重复出现的外层
调用方，优先进入 Content DSL；仍有多个独立 Content 来源时会显示源码选择框。

Receiver 要求 ADB Shell 持有的 Android `DUMP` 权限，进程还会独立确认应用可调试。该动作不依赖
预览面板、外部存储、网络服务、持续 View Listener，也不会传输源码文本。非调试构建会拒绝请求。
如果没有可用响应，请让目标应用保持在前台，并确认 Debug 构建包含当前 `viewcompose-preview`
制品。

## 高亮真机节点

让同一个可调试应用保持前台，然后选择 **Tools → Highlight Device DSL Node**。选择设备与可见 Session
后，Studio 会请求一份当前 Mounted Tree 快照，并按深度优先顺序列出声明式节点类型。
`Diagnostics → Renderer` Demo Fixture 提供唯一的 `AndroidView` 目标，便于确定性验收。
**Tools → Clear Device DSL Highlight** 可以立即移除当前 Overlay。

Host、Navigation 与 Pager Session 可以携带有界 Source Candidate。Lazy Item、Overlay 与 Preview
Session 同样可选，但其被动登记不捕获 Source Stack；因此虚拟化 Child Session 中的 Target 仍然
可达，又不会增加高频组合工作。

节点请求最多访问 2,048 个 Mounted Node，返回 512 个、深度不超过 64，并为每个条目分配新的不透明
进程内 Token。它不会公开应用 Key、View 文本、Semantics、State、Local 值或任意 `toString()` 输出。
选择 Token 后会解析当前弱引用 View，返回其屏幕边界和全局可见裁剪边界，并安装一个最长五秒、进程内
唯一且不可交互的 Overlay。目标替换、View Detach、Session 释放、显式清除和超时都会移除它。

Studio 会明确报告 Stale、Recycled、Hidden、Fully Clipped、Synthetic/Unsupported、Ended Session
与 Rejected，不会猜测其他 View。Overlay 不能触发重组或应用代码，不能改变 Focus 或 Accessibility
Focus，不拦截输入，也不修改布局。协议 v6 会分别校验 Source、Nodes、Select、Clear 与 Timing
Operation 的 Nonce、前台 Package 与存活 Process；旧版报告会被明确拒绝。

## 检查真机逐节点耗时

让可调试应用保持前台，然后选择 **Tools → Inspect Device Node Timing**。完成设备与可见 Session
选择后，Studio 会提示在两秒内触发工作负载。在 Demo 的 `Diagnostics → Renderer` 页面点击
**Run 8-frame timing workload**；可见计数器会到达 `8/8`，请求的 Capture 则在最多八个已完成
Frame Attempt 或两秒后自动停止。

报告包含实际执行的组合 Scope、Reconciliation 与 Direct Binding 聚合。组合和 Reconciliation
区分 Inclusive 与 Self，Binding 采用 Direct 语义。Studio 按 Self/Direct 记录排序，避免嵌套的
Inclusive 总量被重复累加。Node Token 不透明且只在当前 Capture 内有效；Source Hint 复用已有的
有界组合元数据，不会在计时时抓取 Stack Trace。

同一进程一次只接受一个活动 Capture。结果会报告时钟读取次数、空计时对开销估计、Drop、
Truncation、Unsupported Domain 与结束原因；每帧最多 64 个计时节点，总计 512 条记录、深度 32、
128 个有界字符串，响应最多 256 KiB。该能力不测量 Android Measure/Layout/Draw、GPU、
RenderThread、SurfaceFlinger、解码、网络、数据库或外部 SDK。没有请求时不会安装持续 Observer，
逐节点时钟读取和计时记录分配都为零。

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
