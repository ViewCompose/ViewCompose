# Archive Docs

## 1. 目录定位

本目录存放“历史阶段文档”与“一次性审计快照”。

这些文档用于追溯背景，不作为当前开发规范。

当前规范请从[文档索引](../README.md)进入。

## 2. 判断规则

文档进入归档目录通常满足任一条件：

1. 阶段性任务已完成，仅保留历史记录价值
2. 内容已被上层主文档合并
3. 存在明显时间戳属性（如某次审计报告）

## 3. 使用规则

1. 归档文档原则上不再增量维护状态。
2. 如果历史结论仍有效，应回填到当前有效文档后，再引用归档文档作为证据。
3. AI 上下文恢复默认不读取本目录，除非需要追溯历史决策。

## 4. 已归档文档清单

| 文档 | 归档原因 |
| --- | --- |
| `documentation-system-governance-v2.md` | Governance V2 已从 733 条历史 finding 和 311 条精确 Exception 收敛为 531 个公开入口、123 个双语公共页面、201 个可执行代码块全部结构化归属；生成 Reference、公开 API impact、信息架构、样例与语言门禁均已闭环，临时基线及兼容路径已硬切删除，永久门禁以零 issue、零 Exception 的 blocking strict mode 运行 |
| `system-navigation-delivery-history.md` | Navigation Stage 1–10 的临时交付顺序已完成；当前事务、生命周期与所有权不变量已迁至 Architecture，应用验收迁至聚焦 Guide |
| `paging3-integration.md` | AndroidX `PagingDataPresenter` 可选前端、紧凑占位表、生命周期与 mediator 契约、受控 Demo、百万位置真机证明、Release 固定频率绝对基线、Maven 消费和完整仓库门禁均已闭环；首个基线不作相对性能结论，Row/Grid、真实 I/O 或替代引擎需重新立项 |
| `third-party-android-view-integrations.md` | The typed Android View adapter, lifecycle and saved-state coordination, Media3, legacy ExoPlayer 2, Google Maps, and CameraX integrations are complete; exact ownership, physical-device evidence, bilingual manuals, consumer gates, and release changesets are closed, and future integrations require a new attributed plan |
| `diagnostics-correlation-inspection-observability.md` | 跨 Session 关联、有界生产故障聚合、请求驱动的真实 View 高亮、有限逐节点耗时与统一 Studio Inspector 已全部闭环；同机固定频率 Debug 对照为 `no material change`，显式刷新成本有界，弱生命周期、Release APK 排除与 Maven 消费门禁通过，后续扩展必须重新立项 |
| `animation-compose-capability-expansion.md` | 动画 Phase 0～7 已全部合并：物理 spring/decay/result、完整 AnimatedContent、丰富显隐变换、可 seek Transition、真实 Bounds、导航共享运动，以及请求驱动的只读时间线工具均完成；固定频率结果保持 `no material change` 的精确边界，后续能力必须重新立项，MotionLayout、持续 profiler 与真机远程修改仍未排期 |
| `focus-visibility-scroll-ownership-hard-cut.md` | 不稳定的 `focusFollowKeyboard` 策略与四套合成滚动实现已硬切删除；真实滚动所有者恢复 Android 原生子矩形协议，Pager 改为保持焦点的框架自有 RecyclerView 后端，延迟 Item/Page 收紧为单根契约；双语文档、Q3 样例、全仓 CI 以及小米 Android 9 双配置人工真机矩阵均已闭环 |
| `constraintlayout-parity-performance-expansion.md` | ConstraintLayout 发版后 Phase 0～4 已闭环：分类协调快速路径、类型化 Chain/Wrap/物理方向/Grid/CircularFlow、完整 Demo/视觉/配置/生命周期矩阵，以及 Revision 6 Released/Candidate/Direct 受控性能矩阵均已完成；发版安全结论为 `no material change`，不宣称全帧性能领先，后续工作必须重新立项并独立归因 |
| `demo-benchmark-verification-harness-rearchitecture.md` | Demo 的场景化路由、稳定 Selector、基准宿主、双语内容、人工评审修复以及发布相关 Renderer/Overlay 修复均已闭环；collection-stress revision 3、扩大配置矩阵、Popup Golden 与遗留清理由独立的发版后计划继续承接 |
| `observed-property-transactions.md` | Session 级属性事务、完整批次回滚、Renderer 精确 Target、固定频率三引擎 Timing 和 API 33 非 Debuggable 阶段归因均已闭环，Xiaomi 与 Samsung 证据各自保持明确适用边界 |
| `lazy-collection-memory-efficiency.md` | Lazy 声明共享策略、紧凑 Adapter 元数据和延迟绘制资源已完成；固定频率 P99 未回退，最大 heap 方向与减少 129,518 字节、6,276 个对象的独立归因一致，完整门禁闭环 |
| `cross-session-theme-propagation.md` | 跨 Activity 根与保留 NavHost 目的地的主题刷新已闭环，自动化、人工验证、完整 `qaFull` 和发布 Changeset 均已完成，进入本轮 Maven 发布计划 |
| `multi-design-system-high-fidelity.md` | 多设计系统 Token/Recipe/Backend 边界、中立 Host 与 Overlay、Material 3/One UI 7 压力切片、跨版本模拟器及 Pixel/Samsung 真机验收全部闭环，进入本轮 Maven 发布计划 |
| `material3-design-convergence.md` | Material 3 Phase 0–2 的 Token、Theme Bridge、默认值、触控目标和状态层已验收；TextField 与 Switch/Slider 结构级候选已明确移交路线图，保留范围进入本轮 Maven 发布计划 |
| `five-layer-module-architecture-hard-cut.md` | Kernel、UI Foundation、Android Engine、Design System 与 Integration 五层坐标和职责硬切、Maven/样例/文档/消费门禁均已闭环，进入本轮 Maven 发布计划 |
| `constraintlayout-native-engine-hardening.md` | ConstraintLayout 首发硬切与加固已完成：事务图、完整 Helper 所有权、类型安全 DSL、AndroidX `2.2.2`、真机矩阵、固定频率性能安全对照、双语文档和仓库发版门禁均已闭环；后续能力对齐与性能领先工作由有效的发版后计划承接 |
| `android-views-performance-control.md` | 非阴影列表与复杂布局已建立 ViewCompose、Compose、Android Views 三引擎对照；修正后的 revision-4 固定频率 15 方法矩阵、报告工具、双语性能结论、`qaQuick` 与 119 项真机回归均已闭环，阴影原生对照需先定义等价渲染契约后再单独立项 |
| `dsl-contract-convergence.md` | 布局与主题交互反馈已硬切到渲染器中立 Indication，冗余组件别名、输入 Wrapper 与误导性 AnimatedContent 入口已移除，保留 DSL 的 Q3 文档、结构门禁、全库 `qaQuick` 和针对性真机反馈测试均已闭环 |
| `native-widget-contract-convergence.md` | 36 个第一方 Android View Backend 已完成契约审计；状态、输入、身份、无障碍、RTL、Pager、Adaptive Grid 与可移植测量契约已硬切，真实嵌套滚动手势缺陷同步闭环，编译样例、迁移文档、发布登记以及 122 项真机测试全部通过 |
| `remaining-component-appearance-convergence.md` | FAB、AppBar、Badge 与 AlertDialog 已完成稀疏 Overrides 硬切，ModalBottomSheet 已以统一解析快照贯穿 Material 3 与 One UI presenter 的展示及同 key 更新；Scaffold 与原始 Dialog 明确保留布局和 Overlay 协议职责，测试、样例、Demo、文档与 `qaQuick` 已闭环 |
| `component-appearance-override-convergence.md` | 高层组件已硬切到可按字段合并的稀疏 Overrides，`BasicTextField` 已改用完整 Style，输入控件状态模型、下游设计系统、重构后 Demo、测试与文档门禁均已闭环；未触发的其他组件审计进入统一路线图 |
| `compose-migration-capability-convergence.md` | Runtime 事务与 Key 身份、Fragment/Session 所有权、AndroidView 释放、Navigation owner/deep link、逻辑 RTL 边以及普通 Activity 根节点真实进程死亡认证均已闭环；未触发的便利能力继续保留为有条件的独立候选项 |
| `runtime-data-propagation-and-view-patch-optimization.md` | 可空 Local、框架状态原子发布与每次 Apply 唯一通知已修正；Modifier-only View Patch 和不可变 LocalSnapshot 身份复用通过确定性测试、相邻版本性能归因及完整仓库门禁 |
| `lazy-collection-three-layer-hard-cut.md` | Lazy 集合的逻辑快照、Key Session 与物理树三层所有权已经硬切完成；AndroidView Reset/Release、Pager 驻留、TabRow eager children、失败重试与真机长 Fling 门禁已闭环 |
| `android-resource-environment.md` | Android 资源 API、宿主配置失效、设计系统中立的修订传播、保留 Session 与 Preview 收敛已经闭环；事务式子 Session 修复及 Demo 96 项真机回归、仓库 `qaFull` 全部通过 |
| `effect-runtime-convergence.md` | 事务式 Remember/Effect 生命周期、候选回滚、协程所有权、Lifecycle Effect、公共 API 硬切与完整测试矩阵已经闭环；Demo 96 项设备回归及仓库 `qaFull` 全部通过 |
| `maven-dependency-contract-convergence.md` | AndroidX 风格依赖暴露契约、Host-only 消费验证、Maven 元数据门禁和模块治理标准已经完成；Central 发布与发布后文档切换移交正式发布流程，BOM 在具备独立版本兼容性证据前继续延期 |
| `image-loading-pipeline-generalization.md` | 通用图片源协议、Renderer 请求生命周期、Coil/Glide 适配、迁移与发布登记已经完成；已记录并接受最终真机重跑受锁屏设备限制的历史证据 |
| `DOCUMENT_LANGUAGE_CONSISTENCY_2026-08.md` | 67 篇有效公共手写文档已实现完整中英文镜像，12 篇错位权威源、33 个英文中文页标题和 14 个缺失镜像已收口，并加入语言、覆盖与指纹硬门禁 |
| `LAYERED_TASK_LIST_TUTORIALS_2026-08.md` | 同一个可运行任务清单应用已经形成六章分层教程，包含可编译双语片段、真机行为测试与 `qaQuick`/`qaFull` 门禁 |
| `COMPOSE_MIGRATION_PAIRED_SAMPLES_2026-08.md` | 四个 Compose/ViewCompose 迁移域已有可编译成对源码、双语片段与 `qaQuick` 漂移门禁 |
| `VERSIONED_DOCUMENTATION_RETENTION_2026-08.md` | 25 个发布模块的不可变 API 与手册快照、完整历史重建、版本别名及发版门禁已经闭环 |
| `HOSTED_DOCUMENTATION_SYSTEM_2026-08.md` | 托管站点、25 模块 API、双语模块与 Compose 迁移文档、搜索、重定向及质量门禁已经闭环 |
| `API_DOCUMENTATION_COMPLETENESS_PLAN_2026-08.md` | 25 个发布模块的严格 API 文档、双语手册、不可变源码链接与完整发布门禁已经闭环 |
| `ADVANCED_SHADOW_EXECUTION_PLAN_2026-07.md` | 高级阴影实现与验证已经完成，长期契约已并入当前文档 |
| `STATIC_PREVIEW_TOOLING_PLAN_2026-07.md` | 静态预览插件 1.0 已完成，长期工具说明已并入当前文档 |
| `COMPOSE_COMPONENT_GAP.md` | 阶段性对照快照，数据口径易过期 |
| `ARCHITECTURE_FULL_2026-03-06.md` | 根文档已收敛为规范版，此文件保留完整历史分析 |
| `PERFORMANCE_FULL_2026-03-06.md` | 根文档已收敛为规范版，此文件保留完整历史分析 |
| `THEMING_FULL_2026-03-06.md` | 根文档已收敛为规范版，此文件保留完整历史分析 |
| `NODE_PROPS_FULL_2026-03-06.md` | 根文档已收敛为规范版，此文件保留完整历史分析 |
| `WIDGET_ROADMAP.md` | 已合并到 `ROADMAP.md` |
| `DEMO_ROADMAP.md` | 已合并到 `ROADMAP.md` |
| `OVERLAY_COMPONENTS_ROADMAP.md` | 已合并到 `ROADMAP.md` |
| `UI_TESTING.md` | 已合并到 `ROADMAP.md` |
| `THEME_OVERRIDES.md` | 规则已并入 `THEMING.md` |
| `THEME_AUDIT.md` | 一次性审计快照 |
| `WIDGET_PROPERTY_AUDIT.md` | 一次性属性审计快照 |
| `REVIEW.md` | 一次性架构审查快照 |
| `PROJECT_AUDIT_2026-03-05.md` | 一次性项目审计快照 |
| `PROJECT_REAUDIT_2026-03-06.md` | 二次审计快照（文档去旧 + 架构偏差复核） |
| `REFACTOR_PLAN.md` | 已完成的阶段性执行计划 |
| `AUDIT_REMEDIATION_PLAN_2026-03.md` | 已完成的审计整改执行计划 |
| `REAUDIT_EXECUTION_PLAN_2026-03.md` | 已完成的 re-audit 闭环执行计划（F1/F2/F3/F5） |
| `PERF_OPT_EXECUTION_PLAN_2026-03.md` | 已完成的性能优化执行计划（Diff/RecyclerView/subtree skip） |
| `ENVIRONMENT_UNIFICATION_EXECUTION_PLAN_2026-03.md` | 已完成的 Environment 一致性收口执行计划 |
| `CONTAINER_CORE_AUDIT_2026-03-07.md` | 已完成的容器核心审计闭环记录（Flow/Navigation/Segmented/TabRow） |
| `FOCUS_FOLLOW_REAUDIT_2026-03.md` | 已完成的输入框焦点随键盘跟随能力复审闭环记录 |
| `PACKAGE_RENAME_EXECUTION_PLAN_2026-03.md` | 已完成的包名/模块名迁移执行计划（`com.gzq.uiframework` -> `com.viewcompose`） |
| `THEME_TOKEN_REFINEMENT_EXECUTION_PLAN_2026-03.md` | 已完成的 Theme Token 收口与升级执行计划（P0/P1） |
| `BUSINESS_LOCAL_EXT_EXEC_PLAN_2026-03.md` | 已完成的业务侧自定义 Local 扩展执行计划（统一 Local API、去除旧调用方式） |
| `STATE_SNAPSHOT_EXEC_PLAN_2026-03.md` | 已完成的状态系统升级执行计划（SnapshotMutationPolicy + MVCC + Snapshot 事务） |
| `NODE_SPEC_ONLY_EXECUTION_PLAN_2026-03.md` | 已完成的 `VNode.props` 全量移除与 NodeSpec-only 迁移执行计划 |
| `NODE_SPEC_ONLY_BLOCKER_CONTEXT_2026-03.md` | NodeSpec-only 收口阶段 `qaFull` 设备离线阻塞与解除记录 |
| `RECOMPOSITION_LITE_EXEC_PLAN_2026-03.md` | 已完成的 SlotTable Lite + 子树级重组执行计划 |
| `RECOMPOSITION_LITE_BLOCKER_CONTEXT_2026-03.md` | Recomposition Lite 收口阶段 instrumentation 阻塞与解除记录 |
| `VIEWMODEL_LIFECYCLE_MODULE_SPLIT_EXEC_PLAN_2026-03.md` | 已完成的 ViewModel/Lifecycle 模块化拆分执行计划（双模块 + 新包名硬切） |
| `FRAME_ALIGNED_SCHEDULER_EXEC_PLAN_2026-03.md` | 已完成的 RenderSession 帧对齐调度执行计划（Choreographer/frame-aligned scheduling） |
| `RENDERER_COMPLEXITY_CONVERGENCE_EXEC_PLAN_2026-03.md` | 已完成的 renderer 复杂度收敛执行计划（ViewModifierApplier 分层 + binder/differ descriptor 单源化） |
| `RESPONSIBILITY_BOUNDARY_CONVERGENCE_EXEC_PLAN_2026-03.md` | 已完成的职责边界收口执行计划（P1+P2 硬切：host 会话执行归位、诊断类型解耦、overlay SPI 装配） |
| `RUNTIME_PURITY_TEST_COVERAGE_EXEC_PLAN_2026-03.md` | 已完成的 runtime 纯度与测试覆盖收口执行计划（Kotlin/JVM 硬切 + 核心分支测试矩阵补齐） |
| `P3_BOUNDARY_REFINEMENT_EXEC_PLAN_2026-03.md` | 已完成的 P3 边界收口执行计划（descriptor 分域收敛 + Modifier 策略提取下沉） |
| `WIDGET_CORE_RENDERER_DECOUPLE_EXEC_PLAN_2026-03.md` | 已完成的 widget-core 与 renderer 依赖解耦执行计划（ui-contract + host-android 分层硬切） |
| `WIDGET_CORE_RENDERER_DECOUPLE_BLOCKER_CONTEXT_2026-03.md` | widget-core 解耦阶段 `qaFull` 设备离线阻塞与解除记录 |
| `PACKAGE_ROOT_STRICT_UNIFICATION_EXEC_PLAN_2026-03.md` | 已完成的严格单包根迁移执行计划（每模块单一包根 + package/namespace 守卫） |
| `MIGRATION_UI_CONTRACT_HOST_ANDROID_2026-03.md` | `ui-contract + host-android` 分层迁移说明，内容已成为历史迁移记录 |
| `PREVIEW_EXECUTION_PLAN_2026-03.md` | 已完成的开发预览执行计划（Compose Preview bridge + PreviewCatalog + Paparazzi + `qaPreview`） |
| `DRAWABLE_BACKGROUND_EXEC_PLAN_2026-03.md` | 已完成的 drawable 背景 API 执行计划（`Modifier.backgroundDrawableRes` + renderer/demo/test/documentation 收口） |
| `DRAWABLE_BACKGROUND_CLIP_POLICY_EXEC_PLAN_2026-03.md` | 已完成的 drawable 背景圆角自动裁剪策略调整执行计划（`backgroundDrawableRes + cornerRadius` 自动裁剪） |
| `ANIMATION_GESTURE_EXEC_PLAN_2026-03.md` | 已完成的动画与手势执行计划（animation/gesture 模块、graphicsLayer、手势分发、demo+preview+snapshot 收口） |
| `ANIMATED_VISIBILITY_COMPOSE_PARITY_EXEC_PLAN_2026-03.md` | 已完成的 AnimatedVisibility Compose 语义对齐执行计划（可组合过渡 + host 节点尺寸动画 + 状态机退出后移除） |
| `ANIMATION_REAUDIT_EXEC_PLAN_2026-03.md` | 已完成的动画复扫问题收口执行计划（repeat/infinite 语义修复、animateContentSize spec 语义透传、transformOrigin 同步） |
| `MODIFIER_GESTURE_OPT_EXEC_PLAN_2026-03.md` | 已完成的 Modifier 手势优化执行计划（pointerInput 强短路、transform slop 门槛、swipe 距离+速度联合判定与回归补强） |
| `GESTURE_ARCH_CONVERGENCE_EXEC_PLAN_2026-03.md` | 已完成的 Gesture 跨平台分层重构执行计划（`gesture-core` 策略内核 + `gesture` DSL 入口 + renderer 事件适配收口） |
| `GESTURE_REAUDIT_HARDCUT_EXEC_PLAN_2026-03.md` | 已完成的 Gesture re-audit 硬切执行计划（anchoredDraggable API 替换、多锚点 settle、pointerId transform 稳定性与回归补齐） |
| `GESTURE_ARCH_BLOCKER_CONTEXT_2026-03.md` | Gesture 架构重构阶段设备门禁阻塞记录（Huawei 并行噪声）及 Pixel 4 XL 单设备复跑解除记录 |
| `CONSTRAINT_LAYOUT_EXEC_PLAN_2026-03.md` | 已完成的 ConstraintLayout 组件模块化执行计划（`viewcompose-widget-constraintlayout` + renderer 映射 + demo/preview/test/documentation 收口） |
| `CONSTRAINT_LAYOUT_API_PARITY_EXEC_PLAN_2026-03.md` | 已完成的 ConstraintLayout API 补齐执行计划（P0+P1：advanced dimension/weights/circle/baseline extensions/barrier 行为） |
| `CONSTRAINT_LAYOUT_VIRTUAL_HELPERS_EXEC_PLAN_2026-03.md` | 已完成的 ConstraintLayout Virtual Helpers 补齐执行计划（Flow/Group/Layer/Placeholder） |
| `CONSTRAINT_LAYOUT_BLOCKER_CONTEXT_2026-03.md` | ConstraintLayout Virtual Helpers 收口阶段 instrumentation 阻塞与 Pixel 4 XL 复跑解除记录 |
| `CONSTRAINT_LAYOUT_DEMO_API_COVERAGE_2026-03.md` | ConstraintLayout 业务 API 函数级 demo 覆盖矩阵与人工验证锚点说明（阶段性覆盖快照） |
| `ANIMATION_ARCH_CONVERGENCE_EXEC_PLAN_2026-03.md` | 已完成的 Animation 架构级重构收口执行计划（animation-core 分层、transition 语义收口、animateContentSize 落地） |
| `GRAPHICS_EXEC_PLAN_2026-03.md` | 已完成的 Graphics 跨平台分层执行计划（graphics-core/graphics 模块、renderer draw pipeline、demo+preview+Paparazzi 收口） |
| `GRAPHICS_V2_CONVERGENCE_EXEC_PLAN_2026-03.md` | 已完成的 Graphics v2 收口执行计划（RoundRect 四角半径、Drawable DrawPaint、ImageFilter Chain 语义修复与文档同步） |
| `GRAPHICS_BLOCKER_CONTEXT_2026-03.md` | Graphics 收口阶段 instrumentation 生命周期阻塞复核与解除记录（`RESUMED` 阻塞未复现，转历史归档） |
| `CONTAINER_POLICY_SCOPE_REFACTOR_EXEC_PLAN_2026-03.md` | 已完成的 P0 容器策略 API 作用域收口执行计划（移除 modifier 策略 API，改为容器 `reusePolicy/motionPolicy/focusFollowKeyboard` 参数） |
| `ANDROID_THEME_BRIDGE_CONVERGENCE_EXEC_PLAN_2026-03.md` | 已完成的 Android Theme Bridge 收口执行计划（扩展颜色/tiered typography/shapeAppearance/scrim/ripple 桥接与文档同步） |
| `THEME_TOKEN_CONSUMPTION_EXEC_PLAN_2026-03.md` | 已完成的 Theme Token 实际消费收口执行计划（surface/onSurface 语义落地、controls/shapes 默认值消费、复合文本样式全量下发、token usage audit） |
| `THEME_DIAGNOSTICS_DEMO_EXEC_PLAN_2026-03.md` | 已完成的 Demo 主题诊断页执行计划（Diagnostics 主题诊断页、关键组件视觉样本、人工回归说明与权威入口文档收口） |
| `THEME_TOKENS_ALIAS_CLEANUP_EXEC_PLAN_2026-03.md` | 已完成的 ThemeTokens 冗余收口执行计划（移除 compatibility aliases，保留 reserved semantic palette，并将主题模型硬切到纯语义主入口） |
