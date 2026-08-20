---
translation_source: modules/viewcompose-preview-gradle-plugin/README.md
translation_source_hash: 34569645f6cfba7fbe2bd8f855e067b098f3ce4b143fae71dd01b01cb619cd32
translation_status: current
---

# Preview Gradle 插件

`viewcompose-preview-gradle-plugin` 把 Android Gradle Plugin Variant 接入 ViewCompose 静态 Preview
Protocol。它发现编译后的 Preview Entry，导出确定性 Build Input，规划 Content-addressed Render，启动
隔离 Worker，并从生产 Artifact 中移除 Preview Metadata。

## Plugin 与稳定性

```kotlin
plugins {
    id("com.viewcompose.preview") version "0.1.0-alpha03"
}

dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha03")
}
```

- 稳定性：**Alpha**。Task Name 与 Protocol Version 1 构成当前工具契约。
- 构建要求：Android Gradle Plugin 8.9 线；Render 使用 JDK 17+。
- 范围：Android Application 与 Library Project。
- 生产边界：不可调试 Variant 的 Project Bytecode 不保留直接或组合的 ViewCompose Preview Annotation。

## Android Studio 配套插件

Gradle 插件负责配置 Discovery 与 Render，但不会安装 Android Studio 界面。使用交互式预览时，
还需要单独进入 `Settings | Plugins | Marketplace`，搜索并安装 `ViewCompose Preview`。IDE 插件
提供 Gutter Action、预览工具窗口与 Gallery、增量刷新、源码导航和诊断，再调用本 Gradle 插件
提供的 Task 与 Artifact。

IDE 插件与 Gradle 插件独立版本化。完整模块依赖、支持的 Android Studio 版本线和第一个预览
入口请参阅 [ViewCompose 预览](../../tooling/preview.md)。

## Variant 集成

Plugin 可以在 Android Plugin 前后应用。Android Application 或 Library Plugin 出现时，Project 只配置
一次。Debuggable Variant 获得 Discovery、Render 与 Fast-refresh Task。Non-debuggable Variant 只获得
ASM Instrumentation：移除 Root Preview Annotation 与自定义 Meta-annotation，同时保留其他 Annotation
和 Frame。

`viewComposePreviewDescriptors` 聚合所有 Debuggable Variant 的 Descriptor Export。Variant Task 遵循
Gradle 普通命名，例如 `discoverDebugViewComposePreviews`、`renderDebugViewComposePreview` 与
`refreshDebugViewComposePreview`。

## Discovery 与 Fingerprint

Discovery 扫描编译后的 Project Directory 与 JAR，不把应用 Class 加载进 Gradle Daemon。Source Root 提供
导航位置。Runtime/Boot Classpath、Manifest、Resource、Asset、Resource Package 与 Project Bytecode
会被规范化为排序后的 Input Group 和小写 SHA-256 Fingerprint。

完整 Input Fingerprint 使 Render Output 失效。更窄的 Layoutlib Compatibility Fingerprint 排除可重载
Project Class、Annotation 与 Source，使 Worker 能安全复用平台状态，同时每次 Render 都使用新的应用
ClassLoader。Manifest 与 Catalog 原子发布；不支持的 Entry 变为结构化 Discovery Diagnostic。

## 单入口与 Gallery Render

单入口使用 `--preview-id`，可选 `--variant-id`。Gallery 使用 TSV `--preview-targets-file`；两种选择互斥。
重复 Batch Target 会被拒绝，Worker Batch 受 Core Protocol 限制，每个 Response File 相互隔离。

成功 Response 按 Request Content 缓存。`--rerender=true` 跳过 Response Cache，但不丢弃规范化 Build
Input。单入口 Failure 会让 Gradle Task 失败；Batch Failure 按 Target 报告，让其他 Tile 继续完成。

## Fast Refresh 与 Worker 复用

Refresh Task 只依赖 Source Compilation，并复用上一次完整 Discovery/Resource Baseline。它重新扫描当前
Project Bytecode，写入 Fast Manifest/Catalog，并使用持久化 Render Toolchain。Baseline 缺失或不兼容时，
会明确请求完整 Discovery，而不是猜测。

Layoutlib Archive 与生成的 Resource-symbol Classpath 按内容寻址 Materialize。Worker Host 位于应用
Classpath 外。可选 `--verify-worker-reuse=true` 对比 Warm 与 Cold Render 的 Pixel/Structure，保留的平台
状态改变输出时立即失败。

## 测试与运维

- Preview Dependency 与 Theme Provider 放入 Debug Source Set。
- CI 持续执行 Release Build，验证 Preview Annotation Stripping。
- 把 Task Input Annotation 改动视为增量构建正确性改动。
- 修改 Layoutlib Compatibility Input 或 ClassLoader Policy 时执行 Worker-reuse Verification。
- 已知 Descriptor 的 Source-only Save 优先 Fast Refresh；Signature、Resource、Manifest 或 Dependency
  改动后回退到完整 Discovery。

## 相关文档

- [Preview Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-preview-core)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-preview-gradle-plugin` API 树](https://docs.viewcompose.com/api/viewcompose-preview-gradle-plugin/current/)。

## 兼容性说明

`0.1.0-alpha02` 建立 Compiled-bytecode Discovery、确定性 Grouped Fingerprint、Fast Source Refresh、
受限 Gallery Batch、Content-addressed Artifact、隔离 Worker 与 Non-debuggable Annotation Stripping。
Task 与 Protocol 兼容性在稳定版前仍可能演进。
