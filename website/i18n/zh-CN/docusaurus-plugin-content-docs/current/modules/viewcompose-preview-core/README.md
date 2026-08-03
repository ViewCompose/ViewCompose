---
translation_source: modules/viewcompose-preview-core/README.md
translation_source_hash: 5fe089dcbc7ad925ffd2918404354fd987a92fdd6c45c53e987f4688ed2bf305
translation_status: current
---

# Preview Core 模块

`viewcompose-preview-core` 定义 ViewCompose Gradle Plugin、Layoutlib Worker、Android Studio Plugin、
测试与 CI 共享的平台无关注解、配置、发现、渲染、Worker 和诊断协议。它不依赖 Android 或 IDE Runtime。

## 产物与稳定性

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。注解源码形态已经建立；工具 Wire Protocol 要求版本完全相等，稳定版前仍可能演进。
- 平台：JVM 11；协议模型与平台无关。
- 打包：Preview Metadata 应放在可调试 Source Set。ViewCompose Preview Gradle Plugin 会从不可调试
  Android Output 中移除它。
- 边界：该模块不加载 Android View、Layoutlib、Gradle 或 IDE Class。

## Preview 入口注解

`@ViewComposePreview` 标记顶层或 Static DSL Function。编译后的 Method 必须是 public static，只接收一个
`UiTreeBuilder` Receiver/Parameter，并返回 `Unit`。即使 Kotlin 给额外参数提供默认值也不支持，因为
Worker 调用精确 JVM Method，而不是 Kotlin 合成的 Default Bridge。

注解可以重复，也可以组合成自定义 Multi-preview Annotation。内置项覆盖 Light/Dark、Phone/Tablet、
LTR/RTL 与常见 Font Scale。Auto Height（`-1`）从参考 Viewport 开始并在安全限制内增长；验证 Clip 或
Scroll 行为时应使用正数固定高度。

## 确定性配置

`PreviewConfiguration` 在不读取 Host System 的情况下解析 Width、Height、Density、Font Scale、Locale、
Direction、应用 Theme Mode 与可选 API Level。Configuration Matrix 生成确定性的 Cartesian Product。
Axis 与 Option 保持声明顺序；多个 Axis 覆盖同一字段时，后者获胜。

Stable ID 使用 `-` 连接小写 ASCII Word，`__` 保留给 Matrix 组合。ID 成为 Cache Key 或 Artifact Path
之前会被验证。

## Build 与 Worker 协议

Gradle Bridge 导出规范化 `PreviewBuildManifest`、排序后的 Build Input 与小写 SHA-256 Fingerprint。
Project Bytecode 与 Layoutlib-compatible Input 分离，使 Warm Host 保留昂贵的平台 Runtime，同时每次
Render 都创建新的 Child ClassLoader。

Worker Batch 最多包含八条 Command，并在一个短生命周期 Host 中顺序执行。Response Path 必须唯一。
这样既能摊薄 JVM 启动成本，也不会让可变 Layoutlib 或应用状态无限泄露。

协议版本必须完全相等。Path 跨边界时保持 Opaque String；拥有文件系统操作的 Process 负责解析与约束。

## Response 与诊断

Worker Failure 以结构化 Response Data 跨边界。成功 Response 必须包含 Image Artifact；失败必须至少有
一条 Diagnostic。可选 Phase Timing 的名称唯一，Duration 非负。

Render Snapshot 有意只包含 Primitive、String、Collection 与可序列化 Protocol Value。它暴露 VNode
Structure、Native View Bounds、Clipping、常见 View Property、Layout Problem、Patch、Composition
Scope 与 Source Call Site，不保留 Runtime 所有的 View、VNode、ClassLoader 或 Exception。

## JSON 兼容性

`PreviewProtocolJson` 编码默认值以获得确定性 Artifact，省略显式 Null，并输出可读格式。Reader 忽略
未知 Key 以允许增加字段，但仍执行 Model Validation，并拒绝不支持的版本或无效 Identity。

## 测试与运维

- 把协议模型改动视为跨进程兼容性改动，同时更新所有 Producer 与 Consumer。
- Hash 前规范化有序 Input，不对不确定 Collection Order 生成 Fingerprint。
- 按编译后 JVM Signature 验证 Annotation Discovery，而不是 Kotlin 源码外观。
- Consumer Process 必须限制外部 Snapshot 的大小和递归结构。
- 覆盖 Protocol Round-trip、无效输入拒绝、Variant Ordering 与 Worker 版本不匹配。

## 相关文档

- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-preview-core` API 树](https://docs.viewcompose.com/api/viewcompose-preview-core/current/)。

## 兼容性说明

`0.1.0-alpha01` 使用 Protocol Version 1、严格版本协商、确定性 JSON 与 Fingerprint、受限 Worker Batch、
Auto-height Configuration、应用所有的 Theme Provider 与感知源码的 Render Diagnostic。Alpha 线之间尚不
承诺 Wire Format 稳定。
