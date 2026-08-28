---
translation_source: modules/viewcompose-preview/README.md
translation_source_hash: 24a6dc9c77d525dec1509b601ce461ed55b9513eabf47e683dd7b2f35b681d9c
translation_status: current
---

# Preview Integration 模块

`viewcompose-preview` 是 Application Theme、Compose Preview Bridge、Paparazzi Catalog Test 与按请求
运行的真机检查工具所使用的可选 Android API。把
`com.viewcompose:viewcompose-preview:0.1.0-alpha05` 放在 `debugImplementation`、Test 或专用
Tooling Source Set。Runtime 从 API 24 开始支持。

## 应用主题提供方

Native Runner 接收带配置限定符的 Context。一个带 `@ViewComposePreviewThemeProvider` 的实现返回
Themed Context 与匹配的 `UiThemeTokens`，让 Native View 与 ViewCompose Component 使用同一 Theme。
Provider 必须无状态、保留输入配置、避免机器相关输入且不得持有 Context。

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

## Compose 预览桥接

`ViewComposePreview` 承载不依赖 Root 的 DSL；`ViewComposePreviewWithRoot` 暴露 Bridge-owned Root
供 Interop 使用；`ViewComposePreviewHost` 是带 Overlay/Diagnostic 配置的底层 Host。仅 Content
Recomposition 会复用 Android Root/Session；Theme、Debug、Overlay、Diagnostics、Container 变化
会重建 Session，退出 Compose Composition 会销毁它。Content 不得持有或移除 Root。

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

Bridge 从创建 Native View 的同一 Context 安装 `AndroidResourceEnvironment` 并使用
`UiThemeDefaults`。它适合复用已有 Compose Tooling，但不是 Application-theme Screenshot Truth，
也不生成 Static Runner Artifact。

## 真机开发工具

本 Artifact 是 Studio 显式 **Inspect Device Diagnostics** 与 **Inspect Device Animation Timeline**
Action 的 Application-process 实现。它遵守 ADR-0009：激活必须同时满足 Artifact 存在、进程可调试、
请求合法；Non-debuggable 或 Idle 路径不拥有 Report Polling、Recurring View Traversal、Frame
Observer、Per-node Timer 或 Report Write。

Diagnostics Protocol v7 返回受隐私限制的 Session/Frame/Failure Summary、Mounted-node
Snapshot/Highlight 与有限 Timing Capture。除了对已选 Session 最多采集八帧/两秒，
**Capture next LazyItem** 还会对一个精确 Parent Session 进入最多十秒单调时间的 Armed 状态，并
采集下一个 `LazyItem` 子 Session 的首个受支持帧。匹配只使用 Parent ID、Child Role 和 Arm 后的
Session-ID 下限。报告包含不透明的进程内 Physical-container Token，可以在不公开应用 Key 或 Native
Object 的前提下关联不同 Logical Session 与 Holder 复用。请求要求 Android `DUMP`、One-use Nonce、
Foreground Package 与 Live-process 验证。Node Traversal、Depth、String、Timing Record、Response
Byte 与 Highlight Lifetime 都有上限；Stale、Hidden、Recycled、Clipped、Ended、Unsupported Node
Fail Closed。Timing 只覆盖 ViewCompose Composition、Reconciliation 与 Direct Binding，不覆盖
Android Measure/Layout/Draw、GPU、RenderThread、Decode、Network、Database 或 SDK Work。

Animation Inspection 只读。它发现 Committed Transition，并对一个选中 Timeline 做最长 500 ms
的有界 Sample/Channel/Byte Capture。它不写入 Private Transition State；交互控制仍只属于 Preview
Content 中公开的 `SeekableTransitionState.seekTo`。

## 验证与兼容性

- 稳定性为 **Alpha**；Wire Compatibility 归 Preview Core 所有。
- 运行 `:viewcompose-preview:testDebugUnitTest` 与 `:viewcompose-preview:verifyPaparazziDebug`。
- Catalog ID 是不可变 Snapshot Identity；只在审核 Visual Diff 后记录新 Golden。
- Device Tooling 必须证明 Release Classpath 排除、Idle Zero-work、合法请求单 Response、Stale Nonce
  拒绝、Privacy Bound 与 Fail-closed Disposal。

另见 [Preview 工具](../../tooling/preview.md)、
[ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md) 与
[生成式 API Reference](https://docs.viewcompose.com/api/viewcompose-preview/current/)。
