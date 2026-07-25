# ViewCompose Unified Roadmap

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

性能专项仍保留独立深度文档见 [PERFORMANCE.md](/Users/gzq/AndroidStudioProjects/UIFramework/PERFORMANCE.md)。

## 2. 当前基线（2026-03）

### 2.1 框架层

1. 节点语义已完成 `NodeSpec-only` 收口（`VNode.spec` 非空必填，无 `Props` 双轨）
2. `Modifier` 边界已收口到“通用修饰 + scoped parent-data”
3. `Overlay` 已分层为：
   - session-bound surface：`Dialog`、`Popup`、`ModalBottomSheet`
   - host-driven feedback：`Snackbar`、`Toast`
4. Android 宿主入口已统一到 `:viewcompose-host-android`（`setUiContent/renderInto/RenderSession`），并内部管理 session 生命周期
5. `system bars insets` 已转为组件侧 `Modifier.systemBarsInsetsPadding(...)`
6. 生命周期与 ViewModel 协作 API 已拆分为 `:viewcompose-lifecycle` 与 `:viewcompose-viewmodel`，并统一到新包 `com.viewcompose.lifecycle` / `com.viewcompose.viewmodel`
7. 重组模型已硬切到 `SlotTable Lite` 节点组级脏区重组（无旧全量重建开关）
8. 依赖边界已收口为 `runtime + ui-contract + widget-core + renderer(android) + host-android`，`widget-core` 不再直依赖 `renderer`
9. `viewcompose-runtime` 已硬切为纯 Kotlin/JVM，并补齐 `policy/snapshot/observation/invalidation/composer` 核心测试分支
10. 宿主公开诊断回调已收口到 core 自有类型（`RenderStats/RenderTreeResult`），host API 不再泄漏 renderer 实现类型
11. overlay 默认装配已改为 `OverlayHostFactoryProvider + ServiceLoader`，无实现时稳定回退 no-op（移除反射路径）
12. 开发预览模块已落地：`viewcompose-preview` 提供 Compose Preview bridge + `PreviewCatalog` + Paparazzi 快照回归（`qaPreview`）
13. 动画与手势模块已落地：`viewcompose-animation-core` + `viewcompose-animation` + `viewcompose-gesture-core` + `viewcompose-gesture`（Compose-like API + 手势策略内核 + renderer 事件适配 + lazy/pager motion 策略 + Android interop）
14. 约束布局能力已落地：`viewcompose-widget-constraintlayout` + renderer `DeclarativeConstraintLayout`，支持 anchors/dimension/bias/baseline/baselineToTop/baselineToBottom/circle/guideline/barrier/chain(+weights)/Flow/Group/Layer/Placeholder/decoupled `ConstraintSet`，并补齐 match-constraint `min/max/percent/constrained`
15. graphics 能力已落地：`viewcompose-graphics-core` + `viewcompose-graphics` + renderer draw pipeline + `host-android` interop；`Graphics` demo 与 preview/Paparazzi 覆盖已接入，并完成 v2 P0 语义收口（RoundRect 四角半径 / Drawable DrawPaint / ImageFilter Chain）
16. 组合事务已落地：`ComposerLite.prepareRoot/commit/abort` 覆盖 slot、观察订阅、RememberObserver 与 Effect，失败组合保留旧依赖并可继续失效重组。
17. 结构化协程已落地：`RenderSession` 统一持有组合父 Job，提供 `LaunchedEffect/rememberCoroutineScope`，`produceState` 已硬切 suspend + `awaitDispose`，Flow 与动画已移除独立根 Job。
18. renderer apply transaction 已落地：递归 patch 共享事务、延迟释放 removal，并对绑定/插入失败执行旧树 best-effort 恢复。

### 2.2 Demo 与验证层

1. demo 已稳定在多 `Activity` 结构
2. 已实现章节具备统一 scenario 模板
3. instrumentation 已覆盖关键 smoke 回归路径，延迟 session 容器专项已覆盖 `LazyVerticalGrid`、`HorizontalPager`、`VerticalPager` 与 `ModalBottomSheet`
4. 基线更新（2026-03-08）：tag-first UI 测试迁移与关键组件族 smoke 已完成；当前 `qaQuick` 可通过，`qaFull` 存在 1 条已知失败（`DemoVisualUiTest.inputSearch_focusSearchBar_doesNotAutoScrollList`，详见 `app/build/reports/androidTests/connected/debug/index.html`）。

## 2.3 里程碑进度快照（2026-03-09）

| Milestone | 状态 | 完成态字段（C/U/D/UI） | 说明 |
| --- | --- | --- | --- |
| A：Overlay 稳定性收口 | Completed | C:✅ U:✅ D:✅ UI:✅ | Overlay host 已统一 reconcile 模板，Dialog/Popup/ModalBottomSheet/反馈流均已回归 |
| B：Collections 与容器扩展 | In Progress | C:✅ U:✅ D:✅ UI:✅ | 已补 `LazyVerticalGrid/HorizontalPager/VerticalPager` 专项回归，并新增 rotate-order 可见刷新断言（`qaFull` 21/21）；下一步聚焦 sticky headers 与 list state 抽象 |
| C：Input 与表单态增强 | Next | C:✅ U:✅ D:✅ UI:⚠ | 已补 Input/Navigation smoke 基线；focus/IME/表单组合专项仍待系统化补齐 |
| D：Diagnostics + Performance 联动 | In Progress | C:✅ U:✅ D:✅ UI:✅ | 已补 `DiffUtil + payload`、`SlotTable Lite` 子树重组与 `SkipSubtree/skippedSubtrees` 主路径，下一步聚焦可视化与发布态优化 |
| E：开发预览与截图回归 | In Progress | C:✅ U:✅ D:✅ UI:✅ | `viewcompose-preview` + Compose Preview + Paparazzi + `qaPreview` 已落地；下一步补全新增组件自动缺口提示与深色快照集 |
| F：动画与手势首轮覆盖 | Completed | C:✅ U:✅ D:✅ UI:✅ | 已完成 `viewcompose-animation-core` + `viewcompose-animation` 分层、`Transition` 共享时钟重构、`AnimatedVisibility` Compose 语义对齐、`animateContentSize` 布局级动画落地、`Animatable` 易用性重构、`InfiniteTransition` typed API、Android interop（MotionLayout/TransitionManager/ObjectAnimator/ViewPropertyAnimator/DynamicAnimation）与 demo+preview+回归测试收口 |
| G：Graphics 2D 主链能力 | Completed | C:✅ U:✅ D:✅ UI:⚠ | 已完成 `viewcompose-graphics-core` + `viewcompose-graphics` 分层、Canvas/draw modifiers/drawWithCache、renderer 渲染管线与 `AndroidGraphicsInterop`，并完成 v2 P0 语义收口（RoundRect/Drawable/ImageFilter Chain）；`qaFull` 当前受 ActivityScenario 生命周期不稳定影响待单独收口 |

## 3. 统一设计原则

1. 组件参数负责语义，`Modifier` 负责通用修饰，`Theme/Defaults` 负责默认值。
2. 平台实现不回流到 DSL 模块：Android 宿主实现进入 `viewcompose-overlay-android` 或 bridge 层。
3. 新能力以“最小可验证步”推进：文档、实现、测试、demo 逐步落地并小步提交。
4. 路线图文档必须和实现同步更新，禁止“代码已变、roadmap 未收口”。

## 4. 能力状态矩阵

| 方向 | 当前状态 | 下一阶段重点 |
| --- | --- | --- |
| Foundations / Input / Layout / State | 已形成 v1 主能力 | 聚焦边界态、表单/焦点态与复杂组合场景 |
| Runtime Effects / Transactions | 组合 prepare/commit/abort、结构化协程、suspend `produceState` 与 renderer 失败恢复已落地 | 增强异常诊断，并继续约束 `AndroidView` 外部副作用边界 |
| Lifecycle / ViewModel Integration | 模块拆分与 API 硬切已完成（`viewcompose-lifecycle` / `viewcompose-viewmodel`） | 继续补强生命周期边界态与 SavedState 复杂场景回归 |
| Collections | `LazyColumn/LazyRow/LazyVerticalGrid` + 基础分页容器可用 | sticky headers、list state 抽象 |
| Overlay | Dialog/Popup/ModalBottomSheet/Snackbar/Toast 主链路已打通 | Popup 锚点定位增强、反馈队列策略收口 |
| Theming | 已完成 token 收口升级（语义色、tier typography、interactive shape、defaults 语义守卫），并新增 `Diagnostics -> 主题诊断` 作为当前主题语义的权威人工验证入口 | Android 动态色/shape 桥接与 token 生命周期治理 |
| Interop | `AndroidView` 支持 update、复用 reset、释放 release 与 nativeView patch 更新 | 强化复杂原生 View 场景与主题协同 |
| Diagnostics | 基础 render/layout 诊断已落地，并补齐 `主题诊断` 页用于 token/defaults/关键组件视觉映射验证 | locals/render tree/patch 可视化与告警可读性 |
| UI Testing | 核心 instrumentation 路径已建立 | 扩展容器专项、overlay 宿主专项、主题断言覆盖 |
| Developer Preview | Compose Preview bridge + Paparazzi 快照链路已建立（`qaPreview` 可执行） | 继续扩展预览覆盖域与快照矩阵（Dark/Tablet） |
| ConstraintLayout | 已新增 `viewcompose-widget-constraintlayout` 与 renderer 映射，核心能力覆盖 anchors/helpers/constraintSet + advanced dimensions/weights/circle/baseline extensions + Virtual Helpers（Flow/Group/Layer/Placeholder） | 下一步推进 MotionLayout interop 专题（保持 host-android 边界） |
| Animation | `viewcompose-animation-core` + `viewcompose-animation` 已完成内核/DSL 分层；`Transition` 为共享时间线语义，`AnimatedVisibility` 已完成 Compose 默认语义与 Row/Column 轴向特化，`animateContentSize` 已落地布局级尺寸动画；Animation demo 已扩展为 6 标签页并覆盖全部业务公开动画 API，UI 回归链路已补齐 7 条 | retarget/cancel 压测、性能画像、更多复杂场景样例 |
| Gesture | `viewcompose-gesture-core` + `viewcompose-gesture` + renderer dispatcher 已支持 tap/drag/anchoredDraggable/transform 与消费回落策略（含多锚点 settle 与 no-op combinedClickable） | 深化嵌套滚动冲突策略、复杂手势并发场景回归 |
| Graphics | `viewcompose-graphics-core` + `viewcompose-graphics` + renderer Canvas draw pipeline 已落地，支持 Canvas 节点与 draw modifiers（drawBehind/drawWithContent/drawWithCache） | 扩展 dark/tablet 预览快照与更复杂图形场景（图表/自定义控件） |
| Performance | 已有 viewcompose-benchmark 基线，且 `DiffUtil + payload + SlotTable Lite + subtree skip` 主路径已落地 | 继续扩大 skip 覆盖、增强诊断指标、推进发布态优化 |

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
2. 新增容器纳入 [SESSION_CONTAINER_CHECKLIST.md](/Users/gzq/AndroidStudioProjects/UIFramework/SESSION_CONTAINER_CHECKLIST.md)
3. 结构稳定 + 闭包变化刷新路径专项测试

完成标准：

1. 容器级空 diff 刷新能力可验证
2. keyed reorder 与本地状态保持稳定
3. demo 提供 stress 场景可人工验证

### Milestone C：Input 与表单态增强

交付：

1. focus/IME action 回调链增强
2. 表单校验与只读/错误态组合场景
3. 主题和状态切换下输入控件视觉一致性回归

完成标准：

1. 输入交互路径可预测且无跨控件串扰
2. 文案裁剪与控件高度问题可通过 UI 测试稳定复现和防回归

### Milestone D：Diagnostics + Performance 联动

交付：

1. render/patch/layout 指标可视化增强
2. viewcompose-benchmark 路线固定化并持续更新基线
3. 发布态优化项（baseline profile 等）推进

完成标准：

1. 性能回归具备可量化证据
2. 诊断面板能直观定位高频问题

### Milestone F：动画与手势首轮覆盖

交付：

1. `viewcompose-animation-core` + `viewcompose-animation`：`AnimationSpec`、`Animatable`、`animate*AsState`、`Transition`、`AnimatedVisibility/AnimatedContent/Crossfade/animateContentSize`
2. `viewcompose-gesture-core` + `viewcompose-gesture`：策略内核（axis/slop/anchored settle）+ `pointerInput`、`combinedClickable`、`draggable/anchoredDraggable/transformable` 状态与 DSL 入口
3. `graphicsLayer` + renderer patch 语义接入，Android 高阶动画 interop（`TransitionManager/MotionLayout/Animator`）
4. demo 与 preview 覆盖：Animation 页已升级为 6 标签 API 索引（typed/generic/spec/transition/visibility-state/infinite/animatable），并形成 7 条 animation instrumentation 回归；PreviewCatalog 与 Paparazzi 快照已接入

完成标准：

1. 新能力默认 opt-in，不破坏现有组件/容器行为
2. 手势消费回落策略稳定（`gesture consumed -> no clickable fallback`）
3. `qaQuick` 与 `qaPreview` 通过，设备可用时 `qaFull` 通过

## 6. 测试与 Demo 的统一门禁

每个“进入已实现状态”的能力必须补齐：

1. 单元测试
2. demo 场景（含验证点）
3. 必要的 demo UI 测试

新增下列能力时，必须补延迟 session 容器专项：

1. 基于 `RecyclerView/ViewPager2` 的复用型容器
2. 结构 diff 与可见内容刷新可能解耦的容器
3. overlay surface 的独立 session 容器

里程碑标记为 `Completed` 之前，必须满足：

1. `:viewcompose-renderer:compileDebugKotlin` 与 `:app:compileDebugKotlin` 通过
2. `:app:connectedDebugAndroidTest` 全绿（或在 roadmap 中登记明确豁免范围与截止时间）

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

归档目录见 [docs/archive/README.md](/Users/gzq/AndroidStudioProjects/UIFramework/docs/archive/README.md)。
