---
translation_source: tooling/preview.md
translation_source_hash: 2410cfb12bb39ace2ae73d1a4fdae99bbcd954168266c61827047d43dd6622dc
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
    id("com.viewcompose.preview") version "0.1.0-alpha02"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha02")
    add(
        "viewComposePreviewWorkerHost",
        "com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha02",
    )
    add(
        "viewComposePreviewRunner",
        "com.viewcompose:viewcompose-preview-runner:0.1.0-alpha03",
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

当前 Marketplace 版本线为 `1.0.1`，面向 Android Studio `261.*` Build Family 发布。IDE 插件与
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
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha03")
}
```

Provider 契约与生命周期规则请参阅
[Preview Integration 模块](../modules/viewcompose-preview/README.md)。

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
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha03")
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

1. 安装并打开使用当前 `viewcompose-host-android` Runtime 的可调试应用。
2. 在设备上进入目标 ViewCompose 页面。
3. 在 Android Studio 中选择 `Locate Device DSL`。

只有一台在线设备时会直接使用它。连接多台真机或模拟器时，插件会先弹出设备选择框，显示设备
类型、Android 版本和序列号。当同一窗口存在多个同样可见且嵌套最深的 ViewCompose 会话（例如
双栏布局）时，还会显示第二个选择框列出候选源码位置。

该动作通过 Android Studio 的 ADB 连接读取前台应用及其私有 Debug 报告，确认报告属于仍在运行
的进程，再把有界 JVM 源码候选解析到当前项目。当共享 Scaffold 先于 Content 发出工具栏或容器
节点时，插件会移除在其他候选中重复出现的外层调用方，优先进入 Content DSL；仍有多个独立
Content 来源时会显示源码选择框。它不依赖预览面板、外部存储、网络服务，也不会传输源码文本。
非调试构建不会暴露报告。如果没有可用报告，请让目标应用保持在前台，并确认其 Debug 构建使用
当前 Host 产物。

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
