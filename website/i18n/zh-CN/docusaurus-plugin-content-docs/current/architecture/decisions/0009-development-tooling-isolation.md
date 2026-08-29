---
translation_source: architecture/decisions/0009-development-tooling-isolation.md
translation_source_hash: a60a1246f3782428efc20432619c2d79fde9aeaa4552f677c6c4b2f2f6716130
translation_status: current
---

# ADR-0009：开发工具隔离与按请求检查

- 状态：已接受
- 日期：2026-08-13

## 背景

ViewCompose 使用 Android View 作为渲染引擎，因此开发工具可能意外与应用渲染共享相同的回调和
主线程预算。首版真机 DSL 定位器暴露了这种失败模式：实现位于 `viewcompose-host-android`，为每个
符合条件的 Render Session 注册全局布局与滚动 Listener，并在每次回调后重建进程报告。虽然文件
替换在 Worker 上完成，View 检查、Session Snapshot 与 JSON 序列化仍同步发生在 UI 线程。

在同一台 Samsung SM-G991B / Android 13、SurfaceFlinger 活动模式为 60 Hz 的设备上，Demo 首页
列表 Frame CPU P50 从早期
预发布版约 5--7 ms 上升到该工具进入 Host 后的 11--12 ms。仅移除滚动回调即可恢复到约 7 ms。
功能行为虽只针对 Debug，但 `debuggable` 本身没有隔离开销：没有 IDE 请求时，普通 Debug Session
仍要持续为该工具付费。

预览、源码导航、检查器、诊断与未来开发辅助仍然重要。架构必须保留它们，同时阻止可选工具隐式
成为渲染引擎或热路径的一部分。

## 决策

1. 开发工具始终位于所有 Runtime Layer 的下游。Runtime 制品可以暴露平台无关的可选检查端口，
   但不能包含具体 IDE 协议、Transport、Report Writer 或开发工具生命周期实现。
2. Tooling 激活有三道相互独立的门：可选 Tooling 制品存在、应用可调试、收到有效的显式 IDE
   请求。`debuggable` 只授予权限，不代表应持续执行工作。
3. 非活动 Runtime 成本仅允许中立的可空端口判断，以及端口明确记录的有界元数据捕获。禁止
   Tooling 所有的 Thread、文件 I/O、序列化、Stack Capture、View Tree Traversal，或注册在滚动、
   布局、绘制、触摸、Animation Frame 或重组热路径上的 Listener。
4. 真机 DSL 定位器由可选 `viewcompose-preview` 制品持有，应用通过 `debugImplementation` 引入。
   按 [ADR-0022](0022-in-memory-development-tooling-installation.md) 的硬切方案，该制品会在应用启动前
   直接在内存中安装中立 `RenderSessionInspectionTooling` 端口；`viewcompose-host-android` 只读取冻结的
   可空端口。端口缺失、歧义或失败均为诊断 no-op，不能导致应用渲染失败。
5. `RenderSessionInspectionPolicy` 将被动 Session Registration 与 Source Capture 分离。可选制品
   存在于可调试进程时，可以在符合条件的 Host、Navigation 或 Pager Page Session 首次提交时有界
   捕获一次源码身份；这是唯一不依赖请求的例外。Lazy Item、Overlay 与 Preview Session 可以登记
   弱 Mounted-node Inspection，但不捕获 Source Stack。两种 Policy 都不保留 Node Tree、不安装
   View Listener、不启动 Worker、也不执行报告 I/O。
6. 实时 View 状态只在显式 IDE 请求后采样。定位器使用带 Nonce 的 Request/Response 协议：IDE
   发出显式 Debug Android 请求，进程只对当前弱引用 Session 做一次 Snapshot，响应包含相同
   Nonce。陈旧响应永远不能满足后续请求。
7. 请求验证与 View 检查在 Android 主线程执行。有界序列化与应用私有缓存原子替换可以转移到按需
   创建的 Worker。请求路径因开发者主动调用而允许临时较重，但不能留下周期性工作。
8. Tooling Transport 遵循最小权限与 Debug Scope。设备定位 Receiver 只在可选制品打包时存在，
   要求 ADB Shell 持有的平台 `DUMP` 权限，确认应用可调试，只接受有界 Nonce，并且只写应用私有
   Cache。
9. 架构必须机械执行。Runtime 模块不能依赖 Tooling 模块或包含具体定位器协议标记。没有显式审阅
   Allowlist 时，Tooling 生产代码不能注册高频 View Callback。Demo Release Runtime Classpath 不得
   包含 Tooling 制品。
10. 可在应用进程执行的 Tooling 变更必须添加确定性的非活动、请求基数、陈旧响应、生命周期与失败
    隔离测试。如果它观察或可能影响热路径，还必须增加同设备 Debug Benchmark。在设备、Build、
    Workload、温度和刷新率相同时，Frame CPU P50 回归上限必须同时不超过 5% 与 0.3 ms，P95 必须
    同时不超过 10% 与 0.8 ms；空闲滚动期间报告写入次数必须为零。

## 公开 API 与模块影响

- `viewcompose-ui-foundation` 持有 Q3 中立 `RenderSessionInspectionTooling`、
  `RenderSessionInspectionPolicy` 与 `RenderSessionInspectionRegistration` 契约；缺失时继续 no-op。
  这次 Alpha 版本线硬切替换旧的 Source-only Port。
- `viewcompose-host-android` 只持有 Android Platform 安装与中立的进程内 Tooling Slot；不再持有
  设备定位实现、协议或 Classpath Discovery。
- `viewcompose-preview` 持有可调试进程定位服务、显式 Request Receiver、实时 Session Snapshot、
  Response 序列化与私有报告生命周期。
- Android Studio 插件持有请求创建、Nonce 验证、响应轮询、源码解析与面向用户的失败处理。

没有面向应用的 DSL API 变化。需要真机源码导航的使用方必须把 `viewcompose-preview` 保留在
Debug、Test 或专用 Tooling 配置。Release 构建不需要 Preview 制品。

## 后果

- 普通 Runtime 制品不能静默获得具体开发工具循环。
- 包含 Preview 的 Debug 构建仍保留定位器，但滚动与布局不再触发 Snapshot 或报告写入。
- 点击源码定位会执行一次有界检查往返，因此可能比读取持续刷新的文件稍慢。
- Host 对进程内 Tooling Slot 执行一次同步、非阻塞读取。冻结结果不可变且可空，因此既不产生
  Classpath I/O，也不产生逐帧发现。
- 为了准确定位页面且不在 IDE 请求时重新执行应用组合，首次有界源码候选捕获仍是显式取舍。
- 确实需要持续观察的工具必须获得新 ADR、狭窄激活生命周期、显式 Allowlist 与 Benchmark 证据；
  便利性不是例外。

## 被否决的替代方案

### 把实现保留在 Host，并用 `FLAG_DEBUGGABLE` 保护

否决原因：多数 Debug Session 没有活跃的 IDE 检查请求。该方案保留原有耦合，并可能再次让持续
工作回归应用行为。

### 把实现保留在 Host，并增加可变全局开关

否决原因：实现、Transport、Callback 与失败模式仍会随 Runtime 制品交付。遗漏或错误初始化开关会
重新激活缺陷，Release Classpath 隔离也无法证明。

### 只修补滚动 Listener

否决原因：全局布局与焦点发布仍保留持续主线程工作；若移除全部 Listener 却不增加请求协议，Pager
与多栏报告会过期。它只修一个症状，没有定义所有权边界。

### 持续在后台线程发布

否决原因：Android View 状态必须在主线程检查，跨线程 Snapshot 要么不安全，要么仍需要主线程采集。
只移动 JSON 或文件 I/O 不能消除 Callback 压力。

### IDE 请求源码时重新组合页面

否决原因：诊断导航不能重新执行应用组合、提交 Effect 或创建候选 Remember Resource。首次成功提交
时捕获有界源码身份侵入更小，也让后续请求保持只读。

## 验证与发布

只有以下条件持续通过时才保留此变更：

1. `verifyDevelopmentToolingIsolation` 进入 `qaQuick`，验证所有权、禁用高频 Callback 与 Release
   Classpath 排除。
2. 单元测试证明注册、Rendering Active 变化、布局和滚动都不写报告；一个有效请求生成一份 Nonce
   匹配响应；无效请求不会写入，陈旧响应不能满足后续 Nonce；释放会清理弱 Session 状态。
3. Android Studio 插件测试覆盖请求命令构建、响应轮询、超时、进程/包名验证、协议边界与多栏/最深
   Session 选择。
4. Demo Debug APK 保留按请求工作的 `Inspect Device Diagnostics` 能力，Release APK 同时不包含
   Request Receiver 和 Inspector 实现。
5. 同设备 Debug 数据按上述阈值比较受影响的首页列表 Workload，且空闲滚动期间 Inspector 写入为零。
