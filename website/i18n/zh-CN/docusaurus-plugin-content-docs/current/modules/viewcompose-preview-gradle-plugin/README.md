---
translation_source: modules/viewcompose-preview-gradle-plugin/README.md
translation_source_hash: ab763793768a01361465e5498e90f7209645638f7cc34f073e1e6cac84b02001
translation_status: current
---

# Preview Gradle Plugin 模块

`viewcompose-preview-gradle-plugin` 把可调试 Android Gradle Plugin Variant 接到 Static Preview
Protocol。Plugin ID 为 `com.viewcompose.preview`；`0.1.0-alpha03` 面向 AGP 9.1。插件字节码仍兼容
Java 11，而隔离的 Paparazzi Worker 需要 JDK 21 及以上版本才能渲染。
可解析的 Worker Host Configuration 会显式请求 JVM 21 字节码，因此依赖选择与该进程边界一致，
而不会错误沿用插件自身的 Java 11 字节码目标。Native Layoutlib Runtime 与 Resource 固定到
Paparazzi 2.0.0-alpha05 所加载的同一个 16.2.1 版本；不支持混用 Native 与 Java Layoutlib 版本。

{/* compiled-region source="viewcompose-preview-gradle-plugin/src/test/samples/com/viewcompose/preview/gradle/samples/PreviewGradlePluginSamples.kt" region="preview-gradle-apply" sample_id="module.preview-gradle-apply" build_target=":viewcompose-preview-gradle-plugin:compileTestKotlin" */}
```kotlin
fun applyPreviewPluginSample(project: Project) {
    project.pluginManager.apply("com.viewcompose.preview")
    project.tasks.named("viewComposePreviewDescriptors")
}
```

## Variant、发现与任务契约

插件可在 Android Application/Library Plugin 前后应用，并只配置项目一次。可调试 Variant 获得
Discovery、Render 与 Refresh Task；不可调试 Variant 只执行 Bytecode Instrumentation，移除根
Preview Annotation 与组合 Annotation，同时保留无关 Annotation 与 Stack Frame。

Discovery 扫描已编译 Project Directory/JAR，不把 Application Class 加载进 Gradle Daemon。它把
Source Location 与 Runtime/Boot Classpath、Manifest、Resource、Asset、Resource Package 和
Project Bytecode 规范化。完整 Fingerprint 使 Render Output 失效；较窄的 Layoutlib Compatibility
Fingerprint 排除可重载 Project Code，使 Warm Worker 可保留 Platform State，而每次渲染仍使用全新
Application ClassLoader。

`viewComposePreviewDescriptors` 聚合 Descriptor Export。Variant Task 包括
`discoverDebugViewComposePreviews`、`renderDebugViewComposePreview` 与
`refreshDebugViewComposePreview`。Single Render 选择一个 Preview/Variant；Gallery 使用 Target
File。两种模式互斥，Batch 受协议限制，Response Path 彼此隔离。`--rerender=true` 只绕过
Response Cache。

Fast Refresh 在仅源码变化时复用最后一个完整 Discovery/Resource Baseline。Signature、Resource、
Manifest 或 Dependency 变化需要 Full Discovery；缺失或不兼容 Baseline 会明确请求完整路径。
Content-addressed Layoutlib 与 Resource-symbol Input 不进入 Application Classpath；可选 Worker Reuse
验证会比较 Warm/Cold Pixel 与 Structure。

## IDE 与运维边界

Gradle 插件不会安装 Android Studio UI。Gutter、Gallery、源码导航、刷新与诊断需要单独从
Marketplace 安装 `ViewCompose Preview`。IDE 与 Gradle 插件独立版本化。

- Preview Artifact 只放入 Debug/Tooling Configuration，并持续执行 Release Build 验证剥离。
- Task Input Annotation 与 Fingerprint 属于 Incremental Correctness 契约。
- Fast Refresh 只用于已知 Descriptor 的纯源码变化。
- Discovery、Classpath 或 Layoutlib Compatibility Input 变化时运行 Plugin Unit/Functional Test 与
  Worker Reuse 验证。

2026-08-29 的依赖解析验收先用本地发布的 Worker Artifact 复现了 JVM 17 Consumer/JVM 21 Worker
不匹配，随后又暴露了 Paparazzi 16.2.1 Java Engine 与过期 15.2.3 Native Layoutlib Pin 的冲突。
在 Worker Host Configuration 对齐 JVM 21、Layoutlib 两侧统一为 16.2.1 后，Descriptor Discovery
与真实 `CounterPreview` Render Path 均通过。结论为功能兼容性**改善**，且 Application Runtime
**无实质变化**：这些 Configuration 仅属于工具链，只会在 Preview Render 或 Refresh Task
运行时解析。本地 macOS/JBR 21 结果不构成跨 Host 兼容性矩阵；CI 与现有 Preview Suite 仍是
Release Gate。验收 Render 生成一张 1,079 x 2,339、25,755 bytes 的 PNG 和一份 121,271-byte
Render Tree，Protocol Diagnostic 为零；Worker 报告在 2,315 ms Layoutlib Setup 后的 Render
Duration 为 220 ms。Plugin Suite 通过 23/23 Test，Documentation、Translation、Release Intent
与 Development Tooling Isolation Gate 也全部通过。

另见 [Preview 工具](../../tooling/preview.md)、[Preview Core](../viewcompose-preview-core/README.md)
与[生成式 API Reference](https://docs.viewcompose.com/api/viewcompose-preview-gradle-plugin/current/)。
