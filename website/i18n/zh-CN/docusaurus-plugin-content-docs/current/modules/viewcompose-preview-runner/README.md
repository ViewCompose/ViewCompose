---
translation_source: modules/viewcompose-preview-runner/README.md
translation_source_hash: c04280d24375571827a1c5b242a65c42f41a2dcd95ce507a13baa8b2cd1a0127
translation_status: current
---

# Preview Runner 模块

`viewcompose-preview-runner` 是确定性 Static Preview 的 Android 执行层。它通常由 Gradle Plugin/
Worker Host 解析，不应放入 Application Runtime Classpath。它支持 API 24 及以上的 Layoutlib、
Paparazzi 或其他受控 Host。

{/* compiled-region source="viewcompose-preview-runner/src/test/samples/com/viewcompose/preview/runner/samples/PreviewRunnerSamples.kt" region="preview-runner-render" sample_id="module.preview-runner-render" build_target=":viewcompose-preview-runner:compileDebugUnitTestKotlin" */}
```kotlin
/** Resolves application bytecode and exports one static preview response. */
fun renderCompiledPreviewSample(
    context: Context,
    request: PreviewRenderRequest,
    applicationClassLoader: ClassLoader,
): PreviewRenderResponse {
    return StaticPreviewWorker().render(context, request, applicationClassLoader)
}
```

## 执行与所有权

`PreviewJvmEntryPointResolver` 只接受唯一的公开静态 JVM Method：一个 `UiTreeBuilder`
Receiver/Parameter，返回 `Unit`。Application Theme Provider 通过 Kotlin `INSTANCE` 或公开无参构造
创建。`StaticPreviewRenderer.mount` 校验 Descriptor/API Identity，解析 Android Configuration 和
Theme，安装 Lifecycle、ViewModel、Saved State、Resource Environment 与 Theme Owner，并布局一棵
Native View Hierarchy。

每个成功的 `StaticPreviewFrame` 都必须关闭。关闭会销毁全部 Frame-scoped Owner/Provider；独立
Mount 不共享 SDK State。借入的 Application ClassLoader 不会成为 Thread Context Loader，也不会由
Runner 关闭。

Worker 通过 Atomic Replacement 导出 `preview.png` 与 `render-tree.json`。Response 记录 Entry
Resolution、Mount/Layout、Image Export 与 Snapshot Export Timing。预期内的 Discovery、Theme、
Render、Layout、Capture、Export 失败成为结构化 Response；Thread Death 与 OOM 向外抛出以便 Host
退出。

## 配置、尺寸与诊断

`PreviewAndroidContextFactory` 把 Density、Font Scale、Viewport、Locale、Direction 与 Light/Dark
Mode 同步到 Android Resource 和关闭 Observation 的 `AndroidResourceEnvironment`。存在 Application
`PreviewThemeProvider` 时它是权威来源；否则确定性 Android Theme Bridge 会关闭 Dynamic Color。
请求 API Level 必须与 Worker 匹配。

Fixed Height 使用配置 Viewport。Auto Height 先布局真实 Viewport，只扩展会随 Root 增长的 Scroll
Descendant，并受最大 dp 高度和 1600 万 Pixel Capture Budget 限制。PNG 为 Lossless。不可变诊断
包含 Structure、Native Bounds、Clipping、Patch、Composition 与源码位置，不持有 Runtime Object。

- 稳定性为 **Alpha** Tooling Infrastructure；Protocol Compatibility 归 Preview Core 所有。
- 每个 Mounted Frame 都必须关闭，包括只读取 Snapshot 的测试。
- 用 `:viewcompose-preview-runner:testDebugUnitTest` 验证 Fixed/Auto Height、Nested Scroller、
  Capture Limit、RTL、Locale、Font Scale、API 匹配、Application Theme 与各失败阶段。

另见 [Preview Core](../viewcompose-preview-core/README.md)、
[Worker Host](../viewcompose-preview-worker-host/README.md) 与
[生成式 API Reference](https://docs.viewcompose.com/api/viewcompose-preview-runner/current/)。
