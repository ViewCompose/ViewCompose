---
translation_source: modules/viewcompose-preview-worker-host/README.md
translation_source_hash: 2e22753d733c3d17948f5ab0b749e71fbe053dc1a46e04a920f1a36fe4038b7f
translation_status: current
---

# Preview Worker Host

`viewcompose-preview-worker-host` 是拥有 ViewCompose 静态 Preview Layoutlib 的独立 JVM Process
Boundary。它让可变平台渲染状态远离 Gradle 与 Android Studio，验证 Protocol File，隔离可重载应用
Class，并原子发布结构化 Response。

## 产物与稳定性

```kotlin
dependencies {
    runtimeOnly("com.viewcompose:viewcompose-preview-worker-host:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。Executable Protocol 属于内部工具基础设施。
- Runtime：JDK 17 及以上。
- 普通安装：ViewCompose Preview Gradle Plugin 解析该产物；应用代码不应把它加入 Android Runtime
  Classpath。
- 边界：Host 依赖 Preview Core 与平台 Layoutlib Bridge，编译时不依赖应用 UI 模块。

## One-shot 执行

Main Entry 接收一个 Worker-command JSON Path。顶层 `commands` 字段选择受限 Batch，否则文件代表单条
Command。Render 前验证 Protocol Version、Module Path、Variant、Build Fingerprint、Layoutlib Root 与
每个导出的 Build Input。

每条 Command 从规范 Manifest 配置 Paparazzi/Layoutlib SDK，Prepare 后反射调用 Android Runner，并在
`finally` 中 Teardown。Setup、Runner、Export 与 Teardown Timing 都保留在 Response 中。

## ClassLoader 与 Failure 隔离

可重载 Project Bytecode 每条 Command 进入新的 Child `URLClassLoader`。Host 只在本次 Render 期间把它
设为 Thread Context Loader，随后即使失败也恢复旧 Loader 并关闭 Child。Layoutlib 与 Host Class 保留在
Parent Process Classpath。

Request 解码完成后，非致命 Validation、Setup、Runner 与 Export Failure 会变为感知源码的
`RenderFailure` Response。Thread Death 与 Out-of-memory Error 会继续抛出。Malformed Command/Request
JSON 和 Filesystem Publication Failure 可能在产生 Response 前终止 Process。Response File 使用临时
文件替换，Client 不会看到半份 JSON。

## Warm Server 生命周期

Server Mode 绑定 Ephemeral Loopback-only Socket，并原子发布包含 Protocol Version、Process ID、随机
Token、Port 与 Compatibility Fingerprint 的 Endpoint。每个 Client 必须提供相同 Token 与 Protocol
Version。

默认 Server 在空闲 120 秒、处理 24 条 Command、已用 Heap 达 768 MiB、任意 Render 失败、无效 Client
Request 或显式 Shutdown 后退出。只有 Endpoint 仍带当前 Server Token 时才删除，避免旧 Process 删除
替代 Server 的 Endpoint。

## Batch 行为

一个 Protocol Batch 中的 Command 顺序执行。每次 Render 仍有自己的可重载 ClassLoader 与 Response
File；共享 Process 只摊薄 JVM 与保留 Layoutlib 的启动成本。Batch Size 在执行前受 Preview Core 限制。
结构化 Render Failure 会返回给当前 Command，并让 Persistent Server 在接受更多工作前退出。

## 测试与运维

- 永远不要把应用 Bytecode 放进永久 Worker Process Classpath。
- 保留的 Layoutlib Input 变化时验证 Compatibility Fingerprint。
- 覆盖成功与每个失败阶段的 Context-class-loader 恢复和关闭。
- Endpoint File 按 Credential 对待：同时要求 Loopback Transport 与随机 Token。
- 覆盖 Idle、Command-count、Heap-pressure、Render-failure 与显式 Shutdown Retirement。
- Worker stdout/stderr 只用于诊断；Protocol Result 写入 Response File。

## 相关文档

- [Preview Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-preview-core)
- [Preview Gradle Plugin 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-preview-gradle-plugin)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-preview-worker-host` API 树](https://docs.viewcompose.com/api/viewcompose-preview-worker-host/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立 One-shot 与 Loopback Server Mode、严格 Protocol/Token Check、新的可重载 Child
ClassLoader、确定性 Layoutlib Teardown、原子 Response 与受限 Retirement。这些 Process-level Limit
可能在 Alpha Release 间调整。
