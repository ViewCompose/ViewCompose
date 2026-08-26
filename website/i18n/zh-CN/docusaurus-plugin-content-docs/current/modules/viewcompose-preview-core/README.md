---
translation_source: modules/viewcompose-preview-core/README.md
translation_source_hash: 68ca254fa3d6628a9aeb49672d8d785995f1600a8a82fb32086782a7885bb8e1
translation_status: current
---

# Preview Core 模块

`viewcompose-preview-core` 是预览注解、Gradle 发现、Layoutlib Runner/Worker、Android Studio、
测试与 CI 共享的纯 JVM 契约。把 `com.viewcompose:viewcompose-preview-core:0.1.0-alpha03`
加入可调试或专用 Preview Source Set；Gradle 插件会从不可调试产物中移除根注解和组合注解。

## 入口与配置契约

`@ViewComposePreview` 标记只接收一个 `UiTreeBuilder` Receiver/Parameter 并返回 `Unit` 的公开静态
JVM Method。它可重复使用，也支持源码可见的自定义 Multi-preview Annotation。内置组合覆盖
Light/Dark、Phone/Tablet、LTR/RTL 与常用 Font Scale。

{/* compiled-region source="samples/counter/src/debug/java/com/viewcompose/samples/counter/CounterPreview.kt" region="preview-entry" sample_id="tooling.preview-entry" build_target=":samples:counter:compileDebugKotlin" */}
```kotlin
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

`PreviewConfiguration` 确定性地拥有 Viewport、Density、Font Scale、Locale、Direction、Theme 与
可选 API Level。Matrix Axis 保持声明顺序；多个 Axis 覆盖同一字段时后者生效。ID 会先按小写
Cache/Artifact Identity 规则校验。

{/* compiled-region source="viewcompose-preview-core/src/test/samples/com/viewcompose/preview/tooling/samples/PreviewCoreSamples.kt" region="preview-configuration-matrix" sample_id="module.preview-core-matrix" build_target=":viewcompose-preview-core:compileTestKotlin" */}
```kotlin
fun previewConfigurationMatrixSample(): List<PreviewVariant> {
    return PreviewConfigurationMatrix(
        axes = listOf(
            PreviewConfigurationPresets.Theme,
            PreviewConfigurationPresets.LayoutDirection,
        ),
    ).variants()
}
```

## 协议与 Snapshot 契约

Core Model 分离 Project Bytecode 与 Layoutlib-compatible Input，在计算 SHA-256 前排序输入，把
Worker Batch 限制为八条顺序 Command，要求 Response Path 唯一，并要求 Protocol Version 完全
相等。文件路径在负责访问的进程解析和约束前始终是不透明字符串。

{/* compiled-region source="viewcompose-preview-core/src/test/samples/com/viewcompose/preview/tooling/samples/PreviewCoreSamples.kt" region="preview-protocol-round-trip" sample_id="module.preview-core-protocol" build_target=":viewcompose-preview-core:compileTestKotlin" */}
```kotlin
fun previewProtocolRoundTripSample(): PreviewRenderRequest {
    val variant = PreviewVariant(
        id = "phone-light",
        displayName = "Phone / Light",
        configuration = PreviewConfiguration(),
    )
    val request = PreviewRenderRequest(
        requestId = "render-1",
        descriptor = PreviewDescriptor(
            id = "account-preview",
            displayName = "Account preview",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName = "com.example.AccountPreviewsKt",
                methodName = "accountPreview",
                methodDescriptor = "(Lcom/viewcompose/ui/foundation/UiTreeBuilder;)V",
            ),
            variants = listOf(variant),
        ),
        variantId = variant.id,
        modulePath = ":app",
        buildVariant = "debug",
        buildFingerprint = "0".repeat(64),
        outputDirectory = "build/viewcompose-preview/account-preview/phone-light",
    )
    return PreviewProtocolJson.decodeRequest(PreviewProtocolJson.encodeRequest(request))
}
```

失败以结构化诊断跨进程传递。成功必须包含 Image Artifact；Timing 名称必须唯一且 Duration 非负。
不可变 Snapshot 只包含有界且可序列化的 Structure、Native Bounds/Property、Clipping、Layout
Diagnostic、Patch、Composition 与源码位置，不携带存活的 View、VNode、ClassLoader 或 Exception。
JSON 写入默认值、忽略显式 Null、接受新增未知字段，同时仍校验 Identity 与 Protocol Compatibility。

## 兼容性与验证

- 稳定性为 **Alpha**；注解形态已建立，但 Alpha 之间 Wire Protocol 仍可演进。
- Runtime 为 JVM 11，不依赖 Android、Gradle、Layoutlib 或 IDE。
- Serialization JSON 是 API 依赖，因为 Protocol Model 和 `PreviewProtocolJson` 是公开契约。
- 用 `:viewcompose-preview-core:test` 验证配置顺序、协议 Round Trip、非法输入、确定性 Fingerprint、
  Snapshot Bound 与新旧 Worker 不匹配路径。

安装方式见 [ViewCompose Preview 工具](../../tooling/preview.md)，完整符号清单见
[生成式 API Reference](https://docs.viewcompose.com/api/viewcompose-preview-core/current/)。
