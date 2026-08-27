---
translation_source: architecture/decisions/0022-in-memory-development-tooling-installation.md
translation_source_hash: 632c6a9de86acea61ea90530f80284940d3700d6c32561d3331bd19b9ba66903
translation_status: current
---

# ADR-0022：开发工具内存安装

- 状态：已接受
- 日期：2026-08-24
- 替代：[ADR-0009](0009-development-tooling-isolation.md) 中的 `ServiceLoader` 发现机制，以及
  [ADR-0019](0019-animation-physics-transition-and-inspection-ownership.md) 中的 Animation
  Provider 发现机制

## 背景

ADR-0009 把具体真机 Tooling 下移并限制非激活 Runtime 成本，但 Host 集成仍会在首个 Render
Session 创建时惰性执行 `ServiceLoader` 扫描。Animation Timeline 随后也在首个 Transition
Attach 时采用了同样模式。

2026-08-24，在 Pixel 4 XL API 33 上执行带凭据的 Google Maps 验收时，首轮 Composition 外层
开启了线程与 VM `StrictMode`。首个 Host Session 产生四次归属于集成代码的主线程磁盘读取违规，
全部来自 `ServiceLoader` 配置查找。这与 ADR-0009 对非激活 Tooling 路径禁止文件 I/O 的要求
冲突。延迟、缓存或过滤这些违规都会保留错误的所有权边界。

## 决策

1. Runtime 与 Animation 制品不再从 Classpath Resource 发现开发工具。两者各自持有一个受同步
   保护、进程级、可空的中立 Tooling Port Slot。
2. 可选 `viewcompose-preview` 制品合并一个不导出的 Android 初始化 Provider。Android 会在
   `Application.onCreate` 与 Activity 之前创建它。Provider 先验证 `FLAG_DEBUGGABLE`，再把两个
   中立 Port 实现直接安装到内存。
3. 安装必须在首个 Host Session 或 Transition 读取端口前完成。首次读取会在进程生命周期内冻结
   Slot。未安装会冻结为 `null`；后续安装会被忽略。
4. 首次读取前重复安装同一实例是幂等的；不同实例会令 Slot 产生歧义并永久禁用。Provider 顺序
   永远不能决定胜者。
5. 安装与首次读取都是受同步保护、非阻塞的内存操作，不执行 Classpath 扫描、文件 I/O、
   Serialization、View 遍历、Listener 注册、Report 工作或线程创建。
6. 三道激活门仍相互独立：可选制品存在只会被动安装端口；可调试进程授予 Tooling 权限；有效的
   显式 IDE 请求才会启用有界检查工作。
7. Host 与 Animation 公开带编译 Sample 的 Q3 下游集成 Hook。应用与普通自定义 Host 不调用它们。

## 考虑过的替代方案

### 保留 `ServiceLoader` 并允许一次启动磁盘读取

拒绝。实测操作发生在首个 Render Session 的主线程路径，直接违反已接受的非激活 Tooling 契约。
缓存只能改变频率，不能修正所有权。

### 把发现移到 Worker

拒绝。首批 Session 会不确定地观察 Provider；等待 Worker 仍会阻塞渲染。此外，Provider 制品
已经控制 Android Manifest Merge，没有理由继续保留 Classpath 发现。

### 增加 AndroidX Startup 依赖

拒绝。单个不导出的 Provider 已能提供所需的 `Application.onCreate` 前顺序，无需增加另一项
Runtime 依赖或初始化图。Provider 只执行两次有界内存安装。

## 后果

- 首次 Host Composition 与首个 Transition Attach 不再执行开发工具发现 I/O。无 Tooling 路径
  只读取一次冻结的可空引用。
- Preview 是否存在可从合并 Manifest 机械验证；现有 Classpath Guard 仍会把它排除在普通 Release
  配置之外。
- Tooling 实现保持在下游，且不能由 Classpath 顺序选中。
- 应用渲染开始后才初始化的 Tooling 制品会在当前进程保持禁用。这是刻意的 Fail-closed 规则，
  不得通过晚重试补丁绕过。
- Manifest Initializer 运行在应用默认进程。未来多进程检查必须建立显式进程契约，不能恢复隐式发现。

## 验证与落地

1. Host 与 Animation 单测覆盖缺失、单 Provider、同实例幂等安装、歧义、冻结选择与拒绝晚安装。
2. Preview 单测证明两个中立实现共同安装，且不可调试进程不安装任何端口。
3. `verifyDevelopmentToolingIsolation` 继续强制依赖方向、非激活路径限制与 Release Classpath 排除。
4. Pixel 带凭据设备测试会在首次 Maps Composition、生命周期与状态变化外层启用 `StrictMode`；
   不得保留任何归属于集成代码的违规。Google 第三方 SDK 违规单独报告，不能归因于 ViewCompose。

## 验收证据

2026-08-24，同一 Pixel 4 XL / API 33 有凭据 Maps 路径在硬切前报告 4 次归属于 Host 的
`DiskReadViolation`，硬切后归属于集成代码的违规为零，已检测缺陷下降 100%。最终 19.422 秒的方法
覆盖首次 Composition、状态更新、后台/恢复、重建与释放。另有 18 次
`IncorrectContextUseViolation` 与 5 次 `UntaggedSocketViolation` 的首个所属 Frame 位于 Google
Maps 内部；同时验证了适配器 `MapView` 的 Context 是 UI Context。

Host、Animation 与 Preview 单测全部通过，覆盖 Slot 缺失、选择、幂等、歧义、拒绝晚安装、成对
安装与不可调试进程门。`verifyDevelopmentToolingIsolation`、文档/翻译校验以及三个受影响模块的
Dokka 发布均通过。Demo 合并 Manifest 在 Debug 中包含 Initializer，在 Release 中不包含。
结论为 **improved**。限制：依赖网络的方法耗时不作为性能测量；真机路径覆盖 Host 启动，没有执行 Animation Timeline Request；由于
移除的是确定性启动 I/O，未运行帧性能或功耗对比。下一步是在 CameraX 验收中保留严格启动门。
