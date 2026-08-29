---
translation_source: project/roadmap.md
translation_source_hash: 1188a9ea20b6aab7bd162096bc47cc3d7f29026dd5d6dcf9cefdcbcbb7155214
translation_status: current
---

# ViewCompose 统一路线图

## 1. 文档定位

本文档是统一路线图，合并以下历史分散文档的“仍有效”部分：

1. `WIDGET_ROADMAP.md`
2. `DEMO_ROADMAP.md`
3. `OVERLAY_COMPONENTS_ROADMAP.md`
4. `UI_TESTING.md`

目标：

1. 让路线图只有一个主入口
2. 避免多份 roadmap 状态漂移
3. 让 AI 上下文聚焦在当前有效计划，而不是历史阶段文档

性能专项仍保留独立深度文档见 [performance.md](../tooling/performance.md)。

## 2. 当前基线（2026-08）

### 2.1 框架层

1. 节点语义已完成 `NodeSpec-only` 收口（`VNode.spec` 非空必填，无 `Props` 双轨）
2. `Modifier` 边界已收口到“通用修饰 + scoped parent-data”
3. `Overlay` 已分层为：
   - session-bound surface：`Dialog`、`Popup`、`ModalBottomSheet`
   - host-driven feedback：`Snackbar`、`Toast`
4. `:viewcompose-android` 负责中立 Activity/Fragment `setUiContent`，`:viewcompose-material3-android`
   负责具名 `setMaterial3UiContent` Context 适配；底层 `:viewcompose-host-android` 引擎负责
   `renderInto`、`RenderSession` 与挂载树生命周期，但不选择设计系统
5. `system bars insets` 已转为组件侧 `Modifier.systemBarsInsetsPadding(...)`
6. 生命周期与 ViewModel 协作 API 已拆分为 `:viewcompose-lifecycle-androidx` 与
   `:viewcompose-viewmodel-androidx`，并统一到 `com.viewcompose.lifecycle` /
   `com.viewcompose.viewmodel`；逻辑导航页面与 Graph 现在拥有受限 Lifecycle、SavedState 与
   Retained ViewModel Scope，不再借用 Activity 生命周期
7. 重组模型已硬切到 `SlotTable Lite` 节点组级脏区重组（无旧全量重建开关）
8. 运行时职责按 Kernel、UI Foundation、Android Engine、Design System 与 Integrations 五层划分；
   `viewcompose-android` 与 `viewcompose-material3-android` 是经审查的应用聚合层，不是第六层
9. `viewcompose-runtime` 已硬切为纯 Kotlin/JVM，并补齐 `policy/snapshot/observation/invalidation/composer` 核心测试分支
10. 宿主公开诊断回调已收口到 core 自有类型（`RenderStats/RenderTreeResult`），host API 不再泄漏 renderer 实现类型
11. overlay 默认装配已改为 `AndroidOverlayHostFactoryProvider + ServiceLoader`，无实现时稳定回退 no-op（移除反射路径）
12. 开发预览模块已落地：`viewcompose-preview` 提供 Compose Preview bridge + `PreviewCatalog` + Paparazzi 快照回归（`qaPreview`）
13. 动画与手势模块已落地：`viewcompose-animation-core` + `viewcompose-animation` + `viewcompose-gesture-core` + `viewcompose-gesture`（Compose-like API + 手势策略内核 + renderer 事件适配 + lazy/pager motion 策略 + Android interop）
14. `viewcompose-constraintlayout-androidx` 与 Renderer `DeclarativeConstraintLayout` 现已包含 Alpha
   基线以及类型化 Chain Endpoint、Parent Wrap Contribution、物理 Anchor/Guideline、类型化 Grid、
   声明式 CircularFlow、分类协调、专用 Marker Scope、按轴类型化 Target、不可变 Helper Snapshot
   与事务化 Graph/Helper 所有权。
   [已归档的发版后扩展计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md)
   已完成 Phase 0～4：精确 JVM/真机/Lifecycle 覆盖、人工复核的 Pairwise 视觉矩阵、最低/最新 API
   验收，以及 Revision 6 Released/Candidate/Direct 矩阵均已闭环。7 个稳定 Longitudinal Pair 通过
   全部 Timing 与 Peak-heap Regression 行；另有 5 个保持 `inconclusive`，所以发版安全结论为
   **no material change**，不宣称全帧优化胜利。MotionLayout 继续明确保持范围外；后续性能工作
   必须新建归因计划，而不是无限复测。
15. graphics 能力已落地：`viewcompose-graphics-core` + `viewcompose-graphics` + renderer draw pipeline + `host-android` interop；`Graphics` demo 与 preview/Paparazzi 覆盖已接入，并完成 v2 P0 语义收口（RoundRect 四角半径 / Drawable DrawPaint / ImageFilter Chain）
16. 组合事务已落地：`ComposerLite.prepareRoot/commit/abort` 覆盖 slot、观察订阅、RememberObserver 与 Effect，失败组合保留旧依赖并可继续失效重组。
17. 结构化协程已落地：`RenderSession` 统一持有组合父 Job，提供 `LaunchedEffect/rememberCoroutineScope`，`produceState` 已硬切 suspend + `awaitDispose`，Flow 与动画已移除独立根 Job。
18. renderer apply transaction 已落地：递归 patch 共享事务、延迟释放 removal，并对绑定/插入失败执行旧树 best-effort 恢复。
19. 无编译器重组性能收口已落地：VNode 引用保持、等价结果规范化、同帧失效合并、显式 `RecomposeBoundary`、组合/View mutation journal、renderer 快速跳过与按需诊断。
20. 完整文档编辑模型已硬切落地：`viewcompose-text-core` 提供 `TextDocument`、text/selection/composition、原子 EditingBuffer、输入变换和撤销/重做；Android renderer 通过专用 `AppCompatEditText/InputConnection` 控制器同步，`rememberTextFieldState` 保存完整文档 + selection。
21. Lazy 容器 P1 已落地：`LazyListState` 提供完整可观察 layout snapshot、边界与滚动控制；结构化 DSL 支持稳定 key、sticky header、contentType、Grid span、非对称 padding、reverse layout、用户滚动开关和预取策略。
22. 组合 API 已硬切到 `ComposerLite` 单一路径：移除备用 remember/effect/key 上下文，所有组合外调用立即失败，避免无编译器约束下的静默状态丢失与副作用遗漏。
23. 宿主平台能力已收口为原子安装：渲染引擎、帧调度 runtime 与组合协程上下文作为同一不可变快照注册，移除空渲染、即时调度和空协程上下文的半安装降级路径。
24. 结构化无障碍语义已落地：`Modifier.semantics` 覆盖描述、状态、role、heading、live region、选择/勾选/启用、错误、进度与子树策略；renderer 映射原生 Accessibility，并在 View 复用时恢复原始语义。
25. 结构化渲染失败与原生副作用边界已落地：`RenderFailure/RenderFrameReport` 覆盖阶段、恢复结果、帧号与 AndroidView 操作；不可重放动作通过事务成功后发布的 `AndroidView.onCommit` 执行。
26. Overlay P2 完整度已增强：Popup 使用平台无关 positioner 支持四向精确锚定、RTL、翻转/夹取和滚动跟随；Snackbar/Toast 使用统一队列策略与结构化结束原因。
27. Theming P2 位于 `viewcompose-material3`：Android 动态色策略、配置变化驱动的 token 生命周期、来源/修订元数据，以及四角独立的 rounded/cut + dimension/fraction shape 桥接已落地。
28. Diagnostics P2 可视化已落地：公开 `RenderTreeResult` 提供 render tree、逐节点 patch 时间线、CompositionLocal 快照与结构化重组原因，demo 检查器可直接浏览。
29. Lifecycle/SavedState P2 已收口：恢复值使用 composition claim/commit/release 事务，快速生命周期重启串行取消 collector，损坏 Bundle entry 隔离，destroyed host 明确拒绝。
30. 发布态性能基准已固定：release/benchmark target 启用 R8 与 resource shrink，新增无 ART 预编译的冷启动/state patch 基线及 `qaRelease`/`benchmarkRelease` 入口。
31. 动画取消/重定向已收口：`Animatable` 采用最后一次 mutation 生效，`animateTo/snapTo/stop` 统一仲裁，旧帧不能覆盖新目标，并公开 `targetValue/isRunning`。
32. 手势并发已收口：拖动、锚点拖动、双指变换和 pointer input 具备结构化取消原因；变换接管后不恢复旧拖动，系统取消不再触发 fling/settle。
33. 复杂图形场景已增强：`DrawScene` 支持不可变复用、嵌套 transform/clip 与 Canvas 状态隔离，并拒绝不平衡 save/restore。
34. 富文本文档与 Receive Content 已落地：span、段落、链接、行内附件共享 `TextDocument`；clipboard、drag/drop、IME content 统一转换、变换、插入、撤销和保存恢复。
35. 高级阴影装饰层已落地：`viewcompose-shadow-android` 提供有序多层外阴影、前景内阴影、有界栅格缓存、RenderNode 实验后端和结构化诊断；默认 `Auto` 依据首轮发布态基准保持 ExactBitmap。
36. AI 可验证工具 Phase 0--1 冻结版本、安全、评测与证据契约，并发布受新鲜度门禁保护、覆盖
    30 个制品与 537 个符号的 Knowledge Bundle 和紧凑 `llms.txt`。
    [Phase 2](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/ai-verifiable-development-tooling.md)
    负责静态验证、隔离编译/Preview 证据与有界项目分析。

### 2.2 Demo 与验证层

1. demo 已稳定在多 `Activity` 结构
2. 已实现章节具备统一 scenario 模板
3. instrumentation 已覆盖关键 smoke 回归路径，延迟 session 容器专项已覆盖 `LazyVerticalGrid`、`HorizontalPager`、`VerticalPager` 与 `ModalBottomSheet`
4. 验证状态更新（2026-08-03）：当前主干门禁确认 `qaQuick` 可通过；本轮尚未在统一设备环境重跑完整 `qaFull`，因此不再把 2026-03-08 的单条本地失败当作当前事实，也不声明聚合设备门禁已全绿。需要 UI 证据的里程碑必须以当前定向设备结果为依据，或保持 `In Progress`。
5. `Graphics` Demo 已新增外阴影、内阴影与 Lazy/诊断 3 个子页，覆盖多层/彩色/offset/spread/shape、输入互操作、1000 项稳定 key、缓存命中和实际后端选择。
6. 新增独立 `:samples:counter` 最小应用，不依赖大型 Demo 内部脚手架；`qaQuick` 编译应用、测试源码和 debug Preview 入口，`qaPreview` 验证编译后 Preview 发现，`qaFull` 在设备上验证计数点击路径。

## 2.3 里程碑进度快照（2026-08-03）

| Milestone | 状态 | 完成态字段（C/U/D/UI） | 说明 |
| --- | --- | --- | --- |
| A：Overlay 稳定性收口 | Completed | C:✅ U:✅ D:✅ UI:✅ | Overlay host 已统一 reconcile 模板，Dialog/Popup/ModalBottomSheet/反馈流均已回归 |
| B：Collections 与容器扩展 | Completed | C:✅ U:✅ D:✅ UI:✅ | Lazy/Pager 基线、结构化条目、完整 list state、sticky headers、contentType/span、预取与保存恢复均已落地 |
| C：Input 与表单态增强 | In Progress | C:✅ U:✅ D:✅ UI:⚠ | `TextFieldState` 硬切、selection/composition、IME batch、撤销历史、输入变换、键盘动作、autofill 与保存恢复已落地；仍需真实设备 IME/无障碍矩阵 |
| D：Diagnostics + Performance 联动 | In Progress | C:✅ U:✅ D:✅ UI:✅ | 诊断关联、聚合、Inspector、空闲/请求预算、Release 隔离与 Maven 收尾已在[归档诊断计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md)中完成；baseline profile 收益仍待量化 |
| E：开发预览与截图回归 | In Progress | C:✅ U:✅ D:✅ UI:✅ | Compose Preview/Paparazzi 与独立 Android Studio 预览插件 1.0 已落地，覆盖源码联动、全部预览、缓存、增量刷新、缩放平移和诊断；下一步扩展 Dark/Tablet 快照矩阵 |
| F：动画与手势 | Completed | C:✅ U:✅ D:✅ UI:✅ | 首轮 Core/DSL 基线与后续七项增强均已完成；[已归档的动画能力计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/animation-compose-capability-expansion.md)记录了物理运动、内容与显隐、seek、bounds、导航共享运动、时间线工具及验收证据 |
| G：Graphics 2D 主链能力 | In Progress | C:✅ U:✅ D:✅ UI:⚠ | 已完成 `viewcompose-graphics-core` + `viewcompose-graphics` 分层、Canvas/draw modifiers/drawWithCache、renderer 渲染管线与 `AndroidGraphicsInterop`，并完成 v2 P0 语义收口（RoundRect/Drawable/ImageFilter Chain）；在当前设备矩阵重新取得稳定 UI 证据前不标记 Completed |
| H：高级阴影装饰层 | Completed | C:✅ U:✅ D:✅ UI:✅ | 多层外阴影、内阴影、shape/spread/offset、Lazy 缓存、后端诊断与 Compose 成对基准已闭环；Samsung SM-G991B 定向设备回归通过，Auto 保持 ExactBitmap |

## 3. 统一设计原则

1. 组件参数负责语义，`Modifier` 负责通用修饰，`Theme/Defaults` 负责默认值。
2. 平台实现不回流到 DSL 模块：Android 宿主实现进入 `viewcompose-overlay-material3-android` 或 bridge 层。
3. 新能力以“最小可验证步”推进：文档、实现、测试、demo 逐步落地并小步提交。
4. 路线图文档必须和实现同步更新，禁止“代码已变、roadmap 未收口”。

## 4. 能力状态矩阵

| 方向 | 当前状态 | 下一阶段重点 |
| --- | --- | --- |
| Foundations / Input / Layout / State | 已形成 v1 主能力；声明式焦点、方向导航、焦点组和硬件 KeyEvent 分发已落地 | 聚焦真实设备键盘/焦点边界态与复杂组合场景 |
| Accessibility / Semantics | 结构化 semantics 契约与 Android 原生 Accessibility 映射已落地，支持状态、role、heading、live region、错误和进度等核心语义 | 扩展真实设备 TalkBack、Switch Access 与字体放大回归矩阵 |
| Text Editing | `TextDocument + TextFieldState + EditingBuffer + InputTransformation + AppCompatEditText/InputConnection bridge` 已落地，支持富文本、段落、行内附件、selection/composition/undo/save 与统一 Receive Content | 真实设备覆盖主流中文/日文 IME、TalkBack、硬件键盘、拖放和第三方内容提供方 |
| Runtime Effects / Transactions | 组合 prepare/commit/abort、结构化协程、renderer 恢复、`RenderFailure/RenderFrameReport`、`AndroidView.onCommit` 副作用边界与可选有界故障聚合已落地 | 维护已交付的隐私与非激活路径契约；调度、同意、持久化与上传仍由应用 Exporter 持有 |
| Runtime Recomposition Performance | VNode 子树缓存、mutation journal、失效合并、显式边界和 renderer O(1) identity skip 已落地 | 维护叶子更新规模基准，避免固定成本随整树节点数增长 |
| Lifecycle / ViewModel Integration | 模块拆分、串行 Lifecycle Collection、事务化 SavedState Claim、Retained Subtree/Destination/Graph ViewModel Scope、ViewTree Host Discovery、单 Owner `SavedStateHandle`、Terminal Clear，以及 Android 9 Activity Root 与多 Stack 进程恢复证据均已完成 | 扩展 Release 构建、多窗口、API Level 与后台进程恢复矩阵；内存与运行时性能结论继续要求独立证据 |
| Collections | `LazyColumn/LazyRow/LazyVerticalGrid` + Pager；完整 List State、Sticky Header、Content Type/Span、预取、中立紧凑 `LazyItemTable` 边界，以及支持显式 Placeholder、Page Drop、Load State 投影、Lifecycle/Mediator Fixture、受控状态 Demo、百万位置真机证明与稳定首份 Release 基线的可选 AndroidX Paging `PagingLazyColumn` 已落地 | [已归档 Paging 3 集成计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/paging3-integration.md)已完成 Phase 0--7。Paging 仍位于核心契约之外；Row/Grid 支持、真实 I/O 或更广纵向性能需要新建可归因计划 |
| Overlay | Popup 精确锚点、滚动跟随、RTL、翻转/夹取，以及 Snackbar/Toast 统一队列与结构化结束原因已落地 | 扩展多窗口、IME 与自由窗真实设备矩阵 |
| Theming | 已完成 token 收口、Android 动态色策略、完整 shape 桥接与配置变化 token 生命周期，并提供 `Diagnostics -> 主题诊断` 权威人工验证入口 | 扩展多窗口、厂商主题和动态色设备矩阵 |
| Interop | `AndroidView` 支持 replay-safe update/reset/nativeView、提交期 onCommit 与一次性 release | 强化复杂原生 View、第三方控件与主题协同 |
| Diagnostics | 已关联的渲染 Session 与 Studio Inspector、受限的生产故障聚合、Render/Layout Tree、逐节点 Patch、CompositionLocal、重组原因、源码跳转、按请求触发的真实 View 高亮、有限采样的 Composition/Reconciliation/Binding 逐节点耗时，以及已验收的空闲/请求/Release 隔离预算 | 当前没有扩展计划；持续观察器、新耗时域或更广设备契约必须重新建立可归因计划，并提供 ADR 支持的非激活路径证据 |
| UI Testing | 核心 instrumentation 路径与 P1 焦点/键盘、nested scroll、失败回滚真机用例已建立 | 扩展多 API/TV/ChromeOS、overlay 宿主与主题断言矩阵 |
| Developer Preview | Compose Preview、Paparazzi 与独立 Studio 插件链路已建立；插件支持静态渲染、源码双向联动、布局/View/组合诊断、全部预览、有界缓存和增量刷新 | 继续扩展预览覆盖域与快照矩阵（Dark/Tablet） |
| AI 可验证开发工具 | Phase 0--1：冻结契约/评测与受新鲜度门禁保护的 Knowledge Bundle/`llms.txt`；后续服务与转换器尚不存在 | [Phase 2](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/plans/ai-verifiable-development-tooling.md)：验证、隔离编译/Preview 证据与有界分析 |
| ConstraintLayout | Alpha DSL 加分类协调、类型化 Chain/Wrap/物理方向 API、类型化 Grid、声明式 CircularFlow、精确 Helper/回滚/Lifecycle 覆盖、Pairwise 视觉验收、API 24/33/36 设备覆盖，以及稳定行发版安全的 Released/Candidate/Direct 矩阵 | [已归档的能力与性能扩展计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md)完成了 Phase 0～4，发版安全为 **no material change**，没有全帧优化胜利。保持 MotionScene/MotionLayout 范围外；多 OEM 性能或新增能力必须新建归因计划 |
| Animation | 已具备动画 Core/DSL 分层、物理 spring/decay/result、共享的通用与可 seek Transition、完整 AnimatedContent 与丰富显隐变换、animateContentSize/animateBounds、导航共享运动、原始 Android interop，以及请求驱动的只读时间线工具 | [已归档的动画能力扩展计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/animation-compose-capability-expansion.md)完成了 Phase 0～7，保持有界所有权与 `no material change` 的发版安全结论。MotionLayout、持续 profiler 和真机远程修改继续不排期，直到新的归因需求获批 |
| Gesture | `viewcompose-gesture-core` + `viewcompose-gesture` + renderer dispatcher 已支持 tap/drag/anchoredDraggable/transform、统一 nested scroll 和结构化并发取消；双指接管与系统 CANCEL 不会触发旧拖动 settle | 扩展原生三方滚动控件与真实设备多指回归 |
| Graphics | 2D draw 主链与独立 `viewcompose-shadow-android` 装饰层已落地，支持 Canvas、draw modifiers、不可变 `DrawScene`、有序多层外/内阴影、静态栅格缓存和后端诊断 | 扩展 dark/tablet 快照；在明确预算下研究动态 RenderEffect/转场阴影 |
| Performance | 已有 R8 release Macrobenchmark 基线，且 `DiffUtil + payload + SlotTable Lite + subtree skip` 主路径已落地；列表/复杂布局已建立同 target Compose 对照、内存指标、自动报告和归一化回归门禁 | 在真实设备持续积累配对基线并量化 baseline profile 收益 |

### 4.1 完成态字段定义（C/U/D/UI）

统一字段：

1. `C`（Compile）：编译门禁
2. `U`（Unit）：单元测试门禁
3. `D`（Demo）：demo 场景与验证说明
4. `UI`（Instrumentation）：设备 UI 回归门禁

状态值：

1. `✅` 已通过
2. `⚠` 部分通过或存在阻塞
3. `❌` 未通过

默认判定口径：

1. `C`：`qaQuick` 中编译任务通过
2. `U`：`qaQuick` 中 unit test 通过
3. `D`：对应能力已有 demo 页面和验证点说明
4. `UI`：`qaFull` 中 instrumentation 通过，或在 roadmap 登记豁免范围与补齐时间

### 4.2 延期的设计系统增强候选

这些候选项不是当前进行中的工作、缺陷定级或发布阻塞项。只有满足启动条件并评审通过一份独立、
边界明确的执行计划后，才能开始实施。不得把已归档的上级计划重新当作可变待办列表。

| 候选项 | 当前决定 | 启动条件 | 排期契约 |
| --- | --- | --- | --- |
| Material 3 TextField 结构保真 | 保留当前受支持的原生 TextField 结构和现有主题桥接 | 已排定优先级的产品需求，或经过评审的视觉基线证明当前实现与锁定版本的标准 Material 3 行为存在明显且实质性的差异 | 新建 `material3-textfield-structural-fidelity` 计划，完整负责 IME、选区、无障碍、RTL、字体缩放、测量、保存恢复、视觉、性能和回滚证据；不得向 UI Foundation 或 Android Renderer 引入 Material 依赖 |
| Material 3 Switch 与 Slider 的精确几何和动效 | 保留已验收的颜色、触控目标、语义、原生行为和当前几何实现 | 产品评审确认普通密度下存在明显的几何或动效差异，或确认存在无障碍影响 | 新建 `material3-switch-slider-geometry` 计划，覆盖截图与几何、触控、键盘、无障碍、RTL、密度、帧耗时与内存分配证据，并保证每个控件都可独立回滚 |

原“其他组件外观”候选已由 2026-08-15 的逐字段审计正式启动，并在已归档的
[剩余组件外观收敛计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/remaining-component-appearance-convergence.md)
中完成。FAB、应用栏、Badge、AlertDialog 和底部弹层外观现已遵循 ADR-0013；同一次审计
已否决为 Scaffold 和原始 Dialog 增加 Overrides，它们继续保持布局与 Overlay 协议职责。

## 5. 里程碑计划

### Milestone A：Overlay 稳定性收口

交付：

1. `Dialog`：位置、蒙层、dismiss 语义全量回归
2. `Popup`：对齐策略、锚点刷新、窗口切换稳定性
3. `Snackbar/Toast`：队列和重复触发策略文档化并测试覆盖
4. `ModalBottomSheet`：show/update/dismiss 行为与宿主生命周期回归

完成标准：

1. 单测覆盖 show/hide/update/dismiss 一致性
2. instrumentation 覆盖真实宿主中的可见性与交互（含 bottom sheet 路径）
3. `Activity` finish / 配置变化无泄漏

### Milestone B：Collections 与容器扩展

交付：

1. `LazyRow`、`LazyVerticalGrid`、`HorizontalPager/VerticalPager` 最小可用实现
2. 新增容器纳入 [session-containers.md](../architecture/session-containers.md)
3. 结构稳定 + 闭包变化刷新路径专项测试
4. `LazyListState` 完整布局快照、边界状态和滚动控制
5. 结构化 item DSL、sticky header、contentType、Grid span 与预取策略

完成标准：

1. 容器级空 diff 刷新能力可验证
2. keyed reorder 与本地状态保持稳定
3. demo 提供 stress 场景可人工验证
4. sticky header 推挤、稳定 ID、view type 分区与状态恢复有自动化回归

### Milestone C：Input 与表单态增强

交付：

1. focus/IME action 回调链增强
2. `TextFieldState` 完整值模型（text/selection/composition）与输入事务
3. 输入变换、撤销/重做、autofill 与保存恢复
4. 富文本、段落、链接、行内附件与统一 Receive Content
5. 表单校验与只读/错误态组合场景
6. 主题和状态切换下输入控件视觉一致性回归

完成标准：

1. 输入交互路径可预测且无跨控件串扰
2. 文案裁剪与控件高度问题可通过 UI 测试稳定复现和防回归
3. 中文/日文组合输入、方向选区、外部状态更新和进程恢复均不跳光标、不丢文本

### Milestone D：Diagnostics + Performance 联动

交付：

1. 诊断可观测性已经完成；[归档诊断关联、检查与生产可观测性计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md)记录了关联 Inspector、生产聚合、真实 View 高亮、有限耗时、同机空闲/请求预算、生命周期所有权以及 Release/Maven 收尾
2. 本里程碑继续负责固定 viewcompose-benchmark 路线并持续更新基线
3. 当前未完成项只剩发布态优化量化，例如 baseline profile

完成标准：

1. 两条工作流的性能回归均具备可量化证据
2. 诊断计划交付的面板能直观定位高频问题

### Milestone E：开发预览与截图回归

交付：

1. Compose Preview bridge、原生静态 runner 与 Android Studio 插件链路
2. `PreviewCatalog` 与 Paparazzi 共用场景和稳定快照 ID
3. 应用模块可编译、可发现的 Preview 入口；首个应用教程复用真实 `CounterScreen`
4. Light/Dark、Phone/Tablet 等代表性配置矩阵

完成标准：

1. `qaPreview` 同时覆盖静态 runner、Counter Preview 发现和共享 Paparazzi 快照
2. Studio 插件的源码联动、增量刷新、诊断与渲染路径有自动化回归
3. Dark/Tablet 快照矩阵达到公开组件和教程示例的约定覆盖范围

### Milestone F：动画与手势首轮覆盖

交付：

1. `viewcompose-animation-core` + `viewcompose-animation`：`AnimationSpec`、`Animatable`、`animate*AsState`、`Transition`、`AnimatedVisibility/Crossfade/animateContentSize`
2. `viewcompose-gesture-core` + `viewcompose-gesture`：策略内核（axis/slop/anchored settle）+ `pointerInput`、`combinedClickable`、`draggable/anchoredDraggable/transformable` 状态与 DSL 入口
3. `graphicsLayer` + renderer patch 语义接入，Android 高阶动画 interop（`TransitionManager/MotionLayout/Animator`）
4. demo 与 preview 覆盖：Animation 页已升级为 6 标签 API 索引（typed/generic/spec/transition/visibility-state/infinite/animatable），并形成 7 条 animation instrumentation 回归；PreviewCatalog 与 Paparazzi 快照已接入

完成标准：

1. 新能力默认 opt-in，不破坏现有组件/容器行为
2. 手势消费回落策略稳定（`gesture consumed -> no clickable fallback`）
3. `qaQuick` 与 `qaPreview` 通过，设备可用时 `qaFull` 通过

上述首轮里程碑与后续七阶段扩展均已完成。物理 spring/decay/results、完整 AnimatedContent、
丰富显隐变换、可 seek Transition、bounds 动画、导航感知的共享运动与请求驱动时间线工具记录在
[已归档的动画 Compose 能力扩展计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/animation-compose-capability-expansion.md)
中。MotionLayout 扩展、持续 profiler 与真机远程修改仍未排期，必须重新建立归因计划。

### Milestone G：Graphics 2D 主链能力

交付：

1. `viewcompose-graphics-core` 与 `viewcompose-graphics` 分层，以及 renderer draw pipeline
2. Canvas、draw modifiers、`drawWithCache`、不可变 `DrawScene` 与 Android interop
3. Preview/Paparazzi、demo、单测与定向 instrumentation 覆盖

完成标准：

1. 图形纯度、编译、单测和快照门禁通过
2. RoundRect、Drawable 与 ImageFilter Chain 等 v2 P0 语义有回归证据
3. 当前设备环境中的 Graphics instrumentation 稳定通过；满足前保持 `In Progress`

### Milestone H：高级阴影装饰层

交付：

1. 有序多层外阴影、前景内阴影和 shape/spread/offset 契约
2. 有界静态栅格缓存、实验 RenderNode 后端和结构化诊断
3. Lazy 场景、输入互操作、Compose 成对基准和定向设备回归

完成标准：

1. 默认 `Auto` 后端由发布态基准决定，不以实验实现替代已验证路径
2. `qaQuick`、相关 preview/benchmark 门禁和 Samsung SM-G991B 定向回归通过
3. 缓存命中、后端选择与失败回退可在 demo 诊断中验证

## 6. 测试与 Demo 的统一门禁

每个“进入已实现状态”的能力必须补齐：

1. 单元测试
2. demo 场景（含验证点）
3. 必要的 demo UI 测试

新增下列能力时，必须补延迟 session 容器专项：

1. 基于 RecyclerView 的 Lazy 与 Pager 复用型容器
2. 结构 diff 与可见内容刷新可能解耦的容器
3. overlay surface 的独立 session 容器

里程碑标记为 `Completed` 之前，必须满足：

1. `:viewcompose-renderer-android:compileDebugKotlin` 与 `:app:compileDebugKotlin` 通过
2. `:app:connectedDebugAndroidTest` 与教程示例的 connected test 全绿（或在 roadmap 中登记明确豁免范围与截止时间）

## 7. 非目标（当前阶段）

1. 不追求完整复刻 Compose Runtime/Compiler 模型
2. 不在 v1 阶段引入复杂全局 overlay 路由系统
3. 不为了文档“完整性”继续维护重复 roadmap 文件

## 8. 历史文档迁移映射

| 旧文档 | 当前去向 |
| --- | --- |
| `WIDGET_ROADMAP.md` | 本文档 + 归档保留 |
| `DEMO_ROADMAP.md` | 本文档 + 归档保留 |
| `OVERLAY_COMPONENTS_ROADMAP.md` | 本文档 + 归档保留 |
| `UI_TESTING.md` | 本文档 + 归档保留 |

归档目录见 [docs/archive/README.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)。
