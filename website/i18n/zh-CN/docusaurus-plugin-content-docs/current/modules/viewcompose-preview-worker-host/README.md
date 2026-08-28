---
translation_source: modules/viewcompose-preview-worker-host/README.md
translation_source_hash: af9cb5053f9b935e34b1d81001b9c87ff16adbbbcae7fc57d7576d3814447c34
translation_status: current
---

# Preview Worker Host 模块

`viewcompose-preview-worker-host` 是在 Gradle 和 Android Studio 之外拥有 Mutable Layoutlib State
的独立 JDK 21+ Process Boundary。Gradle Plugin 在专用 Worker Configuration 解析它；Application
不能把它放入 Android Runtime Classpath。

{/* compiled-region source="viewcompose-preview-worker-host/src/test/samples/com/viewcompose/preview/worker/samples/PreviewWorkerHostSamples.kt" region="preview-worker-execute" sample_id="module.preview-worker-execute" build_target=":viewcompose-preview-worker-host:compileTestKotlin" */}
```kotlin
fun executeWorkerCommandSample(commandJsonFile: File): PreviewRenderResponse {
    return PreviewWorkerHost.execute(commandJsonFile)
}
```

## 进程与隔离契约

One-shot Mode 接收一个 Command JSON；顶层 Command Collection 表示有界顺序 Batch。每条 Command
校验 Protocol、Module/Variant/Fingerprint、Layoutlib Root 与导出 Build Input，准备一个
Paparazzi/Layoutlib SDK，反射调用 Runner，并在 `finally` 中销毁。

可重载 Project Code 每条 Command 使用全新 Child `URLClassLoader`。它只在本次渲染中成为 Thread
Context Loader，之后恢复旧 Loader，并在成功或失败时关闭 Child。Permanent Host/Layoutlib
Classpath 不包含 Application Code。解码后的非致命错误成为结构化 Response；Malformed JSON、
Filesystem Publication Failure、Thread Death 与 OOM 可以结束进程。

Server Mode 使用 Ephemeral Loopback Socket、Random Token、完全相等的 Protocol Version 与
Layoutlib Compatibility Fingerprint。空闲 120 秒、24 条 Command、768 MiB 已用 Heap、任一失败
Render/Invalid Request 或显式 Shutdown 都会使它退出；Endpoint Replacement 受 Token 保护。
Batch 中每次 Render 仍有独立 Application ClassLoader 与 Response File。

- 稳定性为 **Alpha** Executable Tooling Infrastructure。
- Endpoint File 按 Credential 处理，同时要求 Loopback 与 Token。
- 用 `:viewcompose-preview-worker-host:test` 验证 ClassLoader 恢复/关闭、Atomic Response、
  Compatibility Fingerprint、Idle/Count/Heap/Failure Retirement 与 Explicit Shutdown。

另见 [Preview Core](../viewcompose-preview-core/README.md)、
[Gradle Plugin](../viewcompose-preview-gradle-plugin/README.md) 与
[生成式 API Reference](https://docs.viewcompose.com/api/viewcompose-preview-worker-host/current/)。
