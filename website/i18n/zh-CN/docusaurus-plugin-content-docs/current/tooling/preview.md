---
translation_source: tooling/preview.md
translation_source_hash: aab8d49ffb567df4182d481277b266e96e7d27955f53c9bdbf4699018092d467
translation_status: current
---

# ViewCompose 预览

第一方 Static Preview Pipeline 会通过 Layoutlib 把已编译 ViewCompose DSL 渲染为 Native Android
View，业务模块不需要 Compose Compiler/Runtime。已经使用 Compose Tooling 的项目可按需使用
Compose Preview Bridge。

## 安装 Static Preview Pipeline

Gradle Plugin 发现已编译入口并准备 Android Input；Preview Core 定义 Annotation/Protocol；Worker
Host 拥有 Layoutlib；Runner Mount 并导出 Frame。Gutter Action、Gallery/Tool Window、Refresh、
Source Navigation 与 Diagnostics 需要另行安装 Android Studio `ViewCompose Preview` 插件。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="preview-native-install" sample_id="tooling.preview-native-install" build_target=":samples:tutorials:compileDebugKotlin" */}
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

各 Artifact 独立版本化。它们只应进入 Debug/Tooling Configuration；混用版本前检查当前
[模块目录](../modules/README.md)。

## 声明并渲染入口

为只接收一个 `UiTreeBuilder` Receiver/Parameter 且返回 `Unit` 的 Public Top-level/Static Function
添加注解。Repeated Annotation 和源码可见 Meta-annotation 会产生 Variant。

{/* compiled-region source="samples/counter/src/debug/java/com/viewcompose/samples/counter/CounterPreview.kt" region="preview-entry" sample_id="tooling.preview-entry" build_target=":samples:counter:compileDebugKotlin" */}
```kotlin title="CounterPreview.kt"
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

同步项目、打开源码，通过 Gutter Icon 或 **View | Tool Windows | ViewCompose Preview** 渲染。
只改源码可用 Incremental Refresh；Signature、Resource、Manifest、Dependency 变化必须 Full Update。
Inspector 展示 Native/VNode Structure、Bounds、Composition/Patch、Phase Timing 与 Source-aware
Diagnostic，而 Application/Layoutlib Code 始终在 Studio 进程之外执行。

## 匹配 Application Theme

默认 Android Theme Bridge 不足时，把 `com.viewcompose:viewcompose-preview:0.1.0-alpha04` 加到
`debugImplementation`。一个 Provider 返回带 Configuration Qualifier 的 Context 与匹配的
ViewCompose Token。

{/* compiled-region source="viewcompose-preview/src/test/samples/com/viewcompose/preview/samples/PreviewSamples.kt" region="preview-theme-provider" sample_id="module.preview-theme-provider" build_target=":viewcompose-preview:compileDebugUnitTestKotlin" */}
```kotlin
@ViewComposePreviewThemeProvider
object ApplicationPreviewThemeProvider : PreviewThemeProvider {
    override fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution {
        val tokens = when (theme) {
            PreviewTheme.Light -> UiThemeDefaults.light()
            PreviewTheme.Dark -> UiThemeDefaults.dark()
        }
        return PreviewThemeResolution(context = context, tokens = tokens)
    }
}
```

Native View 与 `stringResource`/`colorResource`/`dimensionResource` 会共享 Locale、Density、
Direction、Night Qualifier 与同一个 Resource Environment。

## 使用可选 Compose Bridge

只有在需要复用现有 Compose Preview Surface 时才启用 Compose 并添加同一个可选
`viewcompose-preview` Artifact。Bridge 不等同于 Native Gallery、Application Theme Provider、
Static Artifact 或 Structured Diagnostic Pipeline。

{/* compiled-region source="viewcompose-preview/src/test/samples/com/viewcompose/preview/samples/PreviewSamples.kt" region="preview-compose-bridge" sample_id="module.preview-compose-bridge" build_target=":viewcompose-preview:compileDebugUnitTestKotlin" */}
```kotlin
@Preview
@Composable
fun composePreviewBridgeSample() {
    val diagnostics = remember {
        RenderDiagnostics(
            collection = RenderDiagnosticCollection(),
            sink = { event -> println(event) },
        )
    }
    ViewComposePreview(
        options = ViewComposePreviewOptions(diagnostics = diagnostics),
    ) {
        Text("ViewCompose")
    }
}
```

## 检查运行中的 Debug Build

当前台可调试应用包含 `viewcompose-preview` 时，Studio 提供两个显式、按请求工具：

- **Inspect Device Diagnostics**：选择 Session、展示关联的 Committed Frame/Failure、导航有界源码
  候选、Snapshot/Highlight Mounted View，并记录最多八帧/两秒的有限 ViewCompose Timing Workload。
  **Capture next LazyItem** 则对已选精确 Parent 进入十秒 Armed 状态，记录下一个 Logical
  `LazyItem` 的首个受支持帧，并返回用于关联 Holder 复用的不透明 Physical-container Token。
- **Inspect Device Animation Timeline**：发现 Committed Transition，对一个选中 Timeline 做最长
  500 ms 的只读 Capture；不能 Seek 或修改真机状态。

两者都使用 Android `DUMP`、One-use Nonce、Foreground Package/Process 校验、Private Atomic
Response File、有界 Payload 与 Fail-closed Stale/Disposed Path。没有合法请求时，不进行 Report
Polling/Write、Recurring Tree Traversal、Frame Observer、Timing Allocation 或 Active Capture。
Future-item Arm 只匹配 Parent Session ID、`LazyItem` Role 和请求后的 Session-ID 下限；它从不接收
或返回应用 Key 与 Native Object。
真机 Timing 不覆盖 Android Measure/Layout/Draw、GPU、RenderThread、SurfaceFlinger、Decode、
Network、Database 与 External SDK Work。精确所有权和上限见
[Preview Integration 模块](../modules/viewcompose-preview/README.md)。

## Snapshot 验证

运行 `./gradlew :viewcompose-preview:verifyPaparazziDebug`；已审核 Golden 位于
`viewcompose-preview/src/test/snapshots/images/`。仅在有意视觉变化并审核 Diff 后运行
`./gradlew :viewcompose-preview:recordPaparazziDebug`。不得记录无法解释的 Mismatch。
`qaPreview` 是独立 Required CI Gate，失败时会上传 Diff/Test Artifact。当前 `0.15%` Catalog
Tolerance 只覆盖已记录的 Layoutlib Editable-text Glyph 差异，不接受 Layout、Color 或 Content
Regression。Static Catalog 用静态 Scene 表示 Overlay；真实 Dialog/Popup/Sheet 行为归 Instrumentation。

## 所有权映射

- [Preview Core](../modules/viewcompose-preview-core/README.md)：负责注解、配置、进程协议与不可变
  Snapshot。
- [Gradle Plugin](../modules/viewcompose-preview-gradle-plugin/README.md)：负责 Variant 接入、编译产物
  发现、Fingerprint、Task 与不可调试产物剥离。
- [Runner](../modules/viewcompose-preview-runner/README.md)：负责入口解析、Android Frame、图像捕获与
  结构化诊断。
- [Worker Host](../modules/viewcompose-preview-worker-host/README.md)：负责 Layoutlib 进程与
  ClassLoader 隔离。
- [Preview Integration](../modules/viewcompose-preview/README.md)：负责应用主题、Compose Bridge 与
  可选真机工具。
