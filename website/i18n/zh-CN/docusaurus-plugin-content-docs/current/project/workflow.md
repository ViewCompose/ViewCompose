---
translation_source: project/workflow.md
translation_source_hash: 86d1c0d8f745c491906cca2746e11144672313f8b476de660eb8519316756330
translation_status: current
---

# 开发流程

## 1. 文档定位

本文档定义 `ViewCompose` 当前开发协作流程。

目的不是增加流程负担，而是解决两个真实问题：

1. 功能开发跨多个阶段，容易把不同改动混在一起
2. 线程中断、附件损坏、上下文丢失后，需要快速恢复工作

因此，后续开发默认遵守本文档，除非任务本身明确要求不同流程。

## 2. 小步提交原则

每完成一个可独立验证的小步骤，立即提交一次。

小步骤的判断标准：

1. 能单独描述目标
2. 能单独验证
3. 不依赖把多个无关修改捆在一起才能成立

例如：

1. 新增一份专项规划文档
2. 落地一个最小宿主抽象
3. 修掉一条独立 bug
4. 补一组单元测试
5. 补一个 demo 页面
6. 补一条 instrumentation 回归

禁止：

1. 把多个无关 bug 修复混成一个提交
2. 把“文档规划 + 大段实现 + 多组测试”长期堆在工作区不提交

## 3. 文档同步原则

开始实现前先按[文档治理规范](./documentation-governance.md#变更影响矩阵)判断影响类型；
PR 必须列出同步更新的 KDoc/Javadoc、模块文档或跨模块文档。若判断无文档影响，也必须说明
不改变公开 API、行为、架构、兼容性或维护流程的具体理由。

新增或修改公开/受保护 API 时，必须在实现前确定 Q 等级，并在同一 PR 内按照
[源码文档与 API 注释规范](./api-documentation-quality.md)补齐所有参数、返回值、状态、生命周期、
线程、失败和平台契约；Q3 API 同步提供可编译 `@sample`。既有注释欠账不能作为新增欠账或
“后续再补文档”的理由。

涉及下面任一情况时，必须先更新文档，或和实现同步提交：

1. 新能力方向
2. 架构边界变化
3. 新测试策略
4. 新 demo 模块规划
5. 新宿主/容器语义
6. 文档里已经登记过的技术债、架构点、roadmap 项被修复或优化

优先更新对应分类中的当前有效文档，例如：

1. [架构总览](../architecture/overview.md)
2. [统一路线图](./roadmap.md)
3. [主题系统](../guides/theming.md)
4. [Modifier 模型](../architecture/modifier.md)
5. [NodeSpec 模型](../architecture/node-spec.md)

补充要求：

1. 如果这次代码改动直接修掉了文档中提过的一个问题点，不能只改代码不改文档
2. 这种场景下，文档更新和代码更新必须在同一步内完成，或紧邻提交完成
3. 文档中的“当前问题 / 剩余问题 / 后续计划”要随实现状态一起收口，不能长期滞后于代码

### 3.1 独立发布模块的发布意图

每个 PR 在合并前都必须判断 Maven 发布影响。自动归属发现会影响发布的源码、模块构建元数据或
编译 API sample 时，必须为该 PR 新增一份不可变 `release/changes/<unique>.json`。直接制品改动
填写 `breaking`、`feature` 或 `fix`；只有检测路径不改变公开契约或制品时，才能用 `ignored` 并
写明具体理由。禁止手写 `dependency`，它由发布规划器根据当前 Gradle project graph 推导反向依赖。

纯测试、Demo、benchmark 和手写文档默认不影响发布。根目录共享构建输入无法只凭路径确定影响，
必须声明受影响制品，或说明不发布的具体理由。Changeset 合并后只读并永久保留为审计记录。即使
使用 squash 或 rebase，发布意图也属于 PR，而不是每个中间 commit。

本地运行 `./gradlew verifyViewComposeReleaseIntent`；它已进入 `qaQuick`，CI 会与精确 PR base SHA
比较。release owner 按[发布流程](./publishing.md#确定性的独立发布规划)使用
`planViewComposeRelease` 和 `prepareViewComposeRelease`。

## 4. 测试与 demo 补齐原则

只要功能进入“已实现”状态，就应补齐对应验证资产。

默认顺序：

1. 单元测试
2. demo 场景
3. 必要的 demo UI 测试

如果某一步暂时做不到，提交说明里必须明确缺什么、为什么缺。

## 4.1 完成态门禁命令

统一命令入口：

1. 快速门禁：`./gradlew qaQuick`
2. 预览快照门禁：`./gradlew qaPreview`
3. 全量门禁：`./gradlew qaFull`

`qaQuick` 负责编译核心模块并运行单元测试。`qaPreview` 运行
`:viewcompose-preview:verifyPaparazziDebug`，并作为独立的必需 CI 检查。只有审阅生成图片与差异后，
视觉变更才能更新已提交基准；禁止仅为通过门禁而录制原因不明的差异。

`qaFull` 在 `qaQuick` 基础上增加应用、Counter sample 和教程的连接设备测试。仓库内每个
`connectedDebugAndroidTest` 入口会先运行 `verifyConnectedAndroidDeviceReady`。前置检查要求：未通过
`ANDROID_SERIAL` 指定设备时只能有一台在线设备、系统已完成启动、屏幕处于唤醒状态且 keyguard
没有显示。该检查不会绕过安全锁屏；重试前必须唤醒并解锁所选设备。能力标记为“完成”前，默认要求
`qaFull` 通过；若当前缺设备或存在临时豁免，必须在 roadmap 写明豁免范围和补齐时间。

## 5. 新增代码归类原则

新增代码必须先判断“属于哪个模块、哪个目录层级”，再开始落文件。

要求：

1. 不接受为了赶进度，把新代码平铺进当前目录
2. 不接受把平台实现、DSL、runtime、demo 代码混放
3. 如果现有目录没有合适落点，先更新文档说明，再新增目录

默认判断顺序：

1. 先判断模块职责边界，例如 `viewcompose-runtime`、`viewcompose-ui-contract`、`viewcompose-animation-core`、`viewcompose-animation`、`viewcompose-gesture-core`、`viewcompose-gesture`、`viewcompose-graphics-core`、`viewcompose-graphics`、`viewcompose-ui-foundation`、`viewcompose-constraintlayout-androidx`、`viewcompose-renderer-android`、`viewcompose-host-android`、`viewcompose-lifecycle-androidx`、`viewcompose-viewmodel-androidx`、`app`
2. 再判断目录职责边界，例如 `context/`、`dsl/`、`runtime/`、`view/`、`defaults/`
3. 最后才决定具体文件名

执行要求：

1. 新功能实现前，先阅读相关架构文档和同模块已有代码
2. 如果发现“当前改动能跑，但文件落点明显不合理”，应优先纠正结构，而不是把技术债留到后面集中处理
3. review 时，模块归属和目录归属属于必查项，不是可选项

## 5.1 反平铺约束

为避免目录再次退化为平铺，新增约束：

1. 同一目录源码文件建议上限：`12`，超过后必须按职责拆分子目录。
2. 命中上限时优先按“领域/控件族群”拆分，不按人名或临时阶段拆分。
3. 目录重排必须与文档更新同一步完成（至少更新[架构总览](../architecture/overview.md)的目录基线）。
4. 目录重排默认不改公开 API；若必须改包名或 API，需单独提交并给出迁移说明。

## 5.2 环境来源一致性

新增映射或扩展框架能力时，环境来源必须遵守单一入口，不允许另起一套：

1. 宿主侧环境语义统一来自 `viewcompose-ui-foundation/context/Environment` 与 `UiEnvironment`。
2. Android 环境提取统一通过 `AndroidEnvironmentBridge` 进入 `UiEnvironmentValues`。
3. renderer 不新增环境语义通道；只允许使用 renderer 内部尺寸工具（`viewcompose-renderer-android/view/DimensionUtils.kt`）做平台换算。
4. 禁止在 renderer 容器类新增私有 `density` 缓存或 `dpToPx`/`spToPx` 辅助方法。
5. 发现现存代码偏离以上约束时，必须在同一步改动里完成“代码修正 + 文档更新”。

### 5.2.1 Lifecycle / ViewModel API 落点

生命周期与 ViewModel 协作能力的新增/修改必须遵守：

1. `collectAsState`/`collectAsStateWithLifecycle` 放在 `:viewcompose-lifecycle-androidx`（`com.viewcompose.lifecycle`）。
2. `viewModel`/`savedStateHandle` 放在 `:viewcompose-viewmodel-androidx`（`com.viewcompose.viewmodel`）。
3. 宿主默认 Local 注入由 `viewcompose-host-android` 的 host bridge 负责，不在上述模块重复实现注入逻辑。

## 5.3 服务提供者优先约束（Overlay/Host/Decoration）

扩展装配默认走服务契约（SPI），反射仅作为最后兜底且需单独评审：

1. overlay 默认装配必须通过 `AndroidOverlayHostFactoryProvider + ServiceLoader`，禁止新增 `Class.forName` 字符串反射主路径。
2. `viewcompose-overlay-material3-android` 的默认实现必须通过 `META-INF/services` 注册 provider；缺失时行为必须稳定回退 no-op 并可观测日志提示。
3. 可选 View 装饰后端必须通过 `AndroidViewDecorationBackend + ServiceLoader` 接入；renderer/host 禁止反向依赖具体阴影实现，缺失后端时必须稳定 no-op。
4. 若确实需要反射（临时兼容场景），必须在同一步补充架构文档与契约测试，并登记移除计划，不得长期保留。

## 5.4 Local API 一致性

新增 Local/主题作用域能力时，必须遵守统一范式：

1. 对外只使用 `uiLocalOf`、`UiLocals.current`、`ProvideLocal`、`ProvideLocals`。
2. 禁止新增专用 `ProvideXxx` 风格包装方法，避免语义分叉与维护成本膨胀。
3. 变更 Local 机制时，必须同步补齐 snapshot/lazy/overlay 传播回归测试。
4. 发现旧实现仍使用专用包装时，优先在同一轮改造中收口到统一 API，并同步更新文档。

## 5.5 NodeSpec-Only 语义边界

节点语义扩展必须遵守单轨模型：

1. 新增语义字段只允许进入 `NodeSpec` 或 `Modifier`，禁止引入动态 `Props`。
2. 禁止新增或回引 `Props/TypedPropKeys/PropKeys/node.props`。
3. renderer binder 读取节点语义时，必须使用显式 spec 读取（不可静默 fallback 到默认 spec）。
4. 若确需新增元数据（如锚点），必须通过 modifier 元素或明确的 spec 字段传递，不得用隐式 map 透传。
5. 相关变更必须同步更新 [node-spec.md](../architecture/node-spec.md) 与对应守卫测试。

## 5.6 节点组重组稳定性约束

涉及 `SlotTable Lite` 组级重组能力的变更，必须遵守：

1. `emit` 同层 group 的 key/顺序必须保持稳定；新增循环或条件分支时优先显式 key。
2. 若设计上无法保持稳定，必须接受“最近稳定祖先回退重组”语义，并补充对应测试。
3. 禁止通过关闭告警或吞异常掩盖结构漂移；结构漂移必须可观测（日志/诊断可见）。
4. `emit` 参数变化（`spec/modifier`）必须可触发组级重组，禁止出现“参数变化但组被错误复用”。
5. 相关改动至少补一条 Runtime/UI Foundation 单测验证组复用与回退行为。

## 5.7 状态快照一致性约束

涉及 `MutableState`、`RuntimeObservation`、`ComposerLite` 的改动，必须遵守：

1. `MutableState` 写入必须走 snapshot 事务（显式 `MutableSnapshot` 或 autocommit），禁止新增绕过事务的写路径。
2. mutation 去抖与并发冲突语义统一通过 `SnapshotMutationPolicy` 实现，不允许在调用侧散落自定义判等逻辑。
3. 并发冲突场景必须覆盖三类测试：无冲突、merge 成功、merge 失败。
4. compose 一轮内的读取一致性必须有单测约束，防止“同一轮读值漂移”回归。
5. 调整 snapshot 语义时，必须同步更新 [state-snapshots.md](../architecture/state-snapshots.md)。
6. 在组合阶段发生“先写 mirror state 再读回”时，禁止把该回读值用于控制流（协程启动、任务调度、版本选择）；这类判定必须读取实时内核字段，并补对应回归用例。

## 5.8 组合事务与结构化协程约束

涉及 `ComposerLite`、Effect、Flow、动画或协程 API 的改动，必须遵守：

1. 组合结果必须经过 prepare/commit/abort；renderer 失败不得提交 slot、观察订阅或 Effect。
2. `DisposableEffect`、`SideEffect`、`LaunchedEffect` 只能在成功提交后启动。
3. 失败候选中的 `RememberObserver` 必须走 `onAbandoned`，不能走 `onForgotten`。
4. 业务可见异步任务必须属于 `RenderSession` 组合 Job；禁止新增 `CoroutineScope(SupervisorJob())` 独立根。
5. 自定义 dispatcher/context 只能覆盖非 Job 元素；携带 `Job` 必须 fail-fast。
6. 协程相关改动至少覆盖：Key 重启、条件移除、失败组合不启动、Session 销毁、子任务异常隔离。
7. renderer 事务回归至少覆盖：同层中途失败、递归子树失败、新节点释放、旧 View 顺序与绑定恢复。
8. `AndroidView.update/onReset/nativeView` 必须可重放且只修改传入 View；外部不可重放副作用必须使用成功事务后执行的 `onCommit`。
9. 组合和 renderer 的事务日志必须与本轮 touched scope/mutated node 数量相关；禁止重新引入每帧全树 checkpoint。
10. renderer 快速路径调整必须验证：稳定 VNode/List 引用保持、`SkipSubtree` 不进入 children、诊断关闭时不做深度结构统计。
11. 重复失效优化必须保留“组合进行中再次失效”的下一帧语义，并覆盖同帧多次写只调度一次。
12. `RecomposeBoundary` 内普通 Kotlin 捕获值必须通过 `inputs` 声明；snapshot state 不需要重复声明。
13. 新增 render/session 失败路径必须映射到结构化 `RenderFailure` 阶段与恢复状态，并验证单个失败不会阻断后续提交期回调或清理。

## 5.9 帧对齐调度约束

涉及 `RenderSession`、失效调度与测试等待机制的改动，必须遵守：

1. 状态失效重绘统一走 `FrameAlignedRenderDispatcher` + `Choreographer`，禁止新增 `container.post` 主调度路径。
2. `RenderSession.render()` 必须保持立即执行语义；若调整语义，必须先更新架构文档并补全回归用例。
3. 调度器改动必须覆盖 4 类单测：同帧合并、取消、重入下一帧、跨线程请求去重。
4. instrumentation 若依赖“UI 空闲后断言”，必须保证等待至少一个 frame，避免调度升级后误报。
5. session `dispose()` 路径改动必须验证“销毁后无延迟渲染”。

## 5.10 Renderer 单源注册约束

涉及 renderer binder/differ 的新增或重构，必须遵守：

1. `NodeType -> binder`、`NodeViewPatch -> patch applier`、`NodeSpec -> patch factory` 只允许在 `NodeBinderDescriptors` 维护。
2. 禁止在 `NodeViewBinderRegistry` 或 `NodeBindingDiffer` 新增并行手工映射表。
3. 新增节点能力时必须先补 descriptor，再补对应 binder/patch 逻辑。
4. 变更完成后必须跑 descriptor guard tests，确保覆盖与一致性无缺口。
5. `NodeBinder*.kt` 源码必须放在 `view/tree/binder/core/descriptor/`，禁止平铺回 `core/` 根目录。
6. 若目录结构回退，必须在同一提交恢复目录收敛并补结构守卫测试。

## 5.10 模块依赖边界约束

1. 每个运行时模块必须且只能登记为 Kernel、UI Foundation、Android Engine、Design System、
   Integration 或显式 Consumer Aggregate；Tooling 单独登记。
2. 依赖只能指向同层或门禁允许的低层。Tooling 禁止进入公开运行时依赖，任意
   `viewcompose-*` 模块禁止依赖 `app`。
3. UI Foundation 主源码禁止导入 Renderer、AndroidX 或 Material API；UI Contract 主源码禁止
   导入 `android.*` 或 `androidx.*`。
4. `ComponentActivity/Fragment.setUiContent` 只位于 `viewcompose-android`；`renderInto` 与
   `AndroidView/nativeView` 保留在底层 `viewcompose-host-android` Engine。
5. Material Theme Policy 只位于 `viewcompose-material3`，Material-backed Presentation 只能位于
   名称明确的 Integration。UI Foundation、Renderer Android 与 Host Android 禁止导入或依赖
   Material Components。
6. `qaQuick` 中的 `verifyModuleDependencyBoundaries` 与 `verifyDesignSystemIsolation` 是不可豁免
   硬门禁；禁止只靠 Code Review 口头维持边界。
7. 公开依赖按 Consumer 暴露而不是实现便利性分类：public/protected 签名类型与明确的入口聚合使用
   `api`；完全属于私有实现的依赖使用 `implementation`。caller-owned 平台集成是唯一例外，且
   必须在模块手册与外部 Consumer 测试中写明，不能根据已有 `implementation` 声明反推。
8. 普通应用只声明实际使用的 Aggregate 与可选 Feature。禁止把下层坐标写成修补不完整 Maven
   元数据的必需依赖。
9. 所有直接 ViewCompose 发布边必须在同一变更中登记到
    [`gradle/viewcompose-dependency-contracts.properties`](https://github.com/ViewCompose/ViewCompose/blob/main/gradle/viewcompose-dependency-contracts.properties)。
    `verifyViewComposeDependencyContracts` 会阻断契约与 Gradle 声明漂移。
10. 新增或修改入口必须增加最小外部 Consumer 编译测试。发版前，本地仓库检查必须验证 `api`
    保持为 Maven compile scope、`implementation` 保持为 runtime scope。
11. 依赖暴露变更属于发布输入变更；同一 PR 必须更新所属模块手册并添加不可变 Release Intent。
12. 首次 Central 发布前，仓库 Maven Sample 只有在门禁先将当前 Checkout 发布到
    `build/maven-repository`，再消费生成 POM 时，才能使用新坐标。发布后必须在没有生成仓库的
    干净 Checkout 中再次验证安装路径。

## 5.11 模块单包根约束

涉及新增模块、包路径重构或文件迁移时，必须遵守：

1. 每个模块仅允许一个包根前缀，覆盖 `src/main`、`src/test`、`src/androidTest`。
2. Android 模块 `namespace` 必须与该模块包根一致（`viewcompose-ui-contract` 例外）。
3. lifecycle/viewmodel 对外包名固定为 `com.viewcompose.lifecycle` 与 `com.viewcompose.viewmodel`，并且源码必须放在各自模块。
4. `qaQuick` 中的 `verifyModulePackageRoots` 与 `verifyAndroidModuleNamespaces` 为硬门禁，任何违规不得豁免合并。

## 5.12 Runtime 纯度与测试覆盖约束

涉及 `viewcompose-runtime` 的改动，必须遵守：

1. `viewcompose-runtime` 固定为 Kotlin/JVM 模块，禁止回退 Android library 形态。
2. runtime 主源码禁止 `android.*` / `androidx.*` import，且 runtime 构建禁止引入 `androidx.core.ktx`。
3. `qaQuick` 中的 `verifyRuntimePurity` 为硬门禁，违规必须阻断合并。
4. runtime 关键分支（policy/snapshot/observation/invalidation/composer）变更必须同步补单测，禁止只改实现不补回归。

## 5.13 Host 会话与诊断边界约束

涉及 `RenderSession`、host 诊断回调或会话创建路径改动时，必须遵守：

1. Android 会话执行细节（frame clock/dispatcher）只放 `viewcompose-host-android`，UI Foundation 仅保留 `RenderSessionRuntime` 契约与 provider。
2. 聚合层的 `setUiContent` 与 Engine 的 `renderInto` 禁止暴露 renderer 实现诊断类型；统一使用 core 诊断类型 `RenderStats`/`RenderTreeResult`。
3. lazy item 子会话与 overlay surface 子会话必须通过会话契约创建，禁止直接 new 平台具体实现类。
4. 相关重构必须补边界守卫测试，至少覆盖“禁止 renderer 类型泄漏到 host public API”与“provider 缺失回退 no-op”两条路径。

## 5.14 Modifier 与容器策略边界

涉及 `Modifier` 或容器策略（reuse/motion/focus follow）相关改动时，必须遵守：

1. `viewcompose-ui-contract` 的 `Modifier` 仅维护“全局稳定语义”的元素与 builder API，禁止新增“仅特定容器生效”的策略型 modifier。
2. 容器策略必须进入容器 DSL 参数与 `NodeSpec`（`reusePolicy/motionPolicy/focusFollowKeyboard`），renderer 直接读取 spec 应用，不再走 modifier 提取链路。
3. 若新增策略类型，必须同轮补齐 DSL->NodeSpec 映射测试与 renderer bind/patch 生效测试。

## 5.15 开发预览约束

涉及组件新增、组件行为调整或视觉语义调整时，必须同步维护开发预览资产：

1. `:viewcompose-preview-core` 只承载 Preview 注解、确定性配置和版本协议，主源码禁止 `android.*` / `androidx.*` import。
2. `:viewcompose-preview-runner` 只负责原生 View 静态挂载、截图和诊断导出，禁止 Compose 与 IDE SDK 依赖。
3. `:viewcompose-preview` 的 `PreviewCatalog` 是组件预览单源，新增组件时必须补 `PreviewSpec`。
4. Paparazzi 快照测试必须消费同一份 `PreviewCatalog`，禁止单独维护第二套截图样例。
5. `qaPreview` 为硬门禁；修改组件视觉语义后必须更新快照基线并通过完整协议/运行器/快照测试。
6. preview 模块禁止依赖 `:app`，禁止 import demo 包路径。
7. preview worker 和 IDE 插件只允许通过带 `protocolVersion/requestId` 的结构化数据协议通信。
8. overlay 在 preview 场景只允许静态模拟，真实弹窗行为回归必须落在 instrumentation。

## 5.16 动画与手势约束

涉及动画/手势能力新增或改造时，必须遵守：

1. 动画能力分层固定为 `:viewcompose-animation-core`（内核）+ `:viewcompose-animation`（DSL 集成）+ `:viewcompose-host-android`（interop）；手势能力分层固定为 `:viewcompose-gesture-core`（策略内核）+ `:viewcompose-gesture`（DSL 入口）+ renderer（Android 事件适配）。
2. Android 高阶动画能力（`TransitionManager/MotionLayout/Animator`）仅允许通过 `:viewcompose-host-android` interop API 暴露。
3. `graphicsLayer` 语义变更必须同步补 renderer patch/rebind 稳定性测试，禁止通过全量 rebind 兜底。
4. 手势事件消费规则固定为“手势优先，未消费再 clickable 回落”；涉及冲突策略修改时必须补“子手势 vs 父滚动容器”回归。
5. 列表/分页动画能力默认 opt-in；改动容器 `motionPolicy/reusePolicy` 语义时必须补容器回归与文档说明。
6. `AnimatedVisibility` 必须走 `NodeType.AnimatedVisibilityHost` 承载尺寸动画；隐藏语义固定为“exit 动画结束后再移除 subtree”。
7. `pointerInput` 仲裁语义变更必须补“Consumed 强短路”回归：`pointerInput` 消费后，`transform/drag/anchoredDraggable/combinedClickable` 均不可再触发。
8. transform 阈值语义变更必须补单测覆盖 slop 三路径（pan/zoom/rotation）与 instrumentation 覆盖双指平移/旋转变化。
9. anchored settle 语义变更必须补单测覆盖“速度触发/距离触发/最近锚点”三路径，禁止仅凭人工回归上线。
10. `updateTransition` 语义必须保持“单 transition 多 channel 共享时间线”；`AnimatedVisibility` 必须复用该时间线，不允许回退到多自动画时钟拼装。
11. `Modifier.animateContentSize(...)` 必须保持布局级尺寸动画语义（父布局可观察到连续尺寸变化），禁止回退到 `graphicsLayer` 缩放假象。
12. `AnimatedSizeHost` 实现改动必须覆盖“展开 + 收起”双向视觉连续性，禁止出现只展开平滑、收起瞬跳的回归。
13. 手势策略算法（axis lock / transform slop / swipe settle）变更必须改在 `:viewcompose-gesture-core`，renderer 仅允许阈值采集与事件分发适配。
14. `combinedClickable` 在 `enabled=true` 但无回调时必须保持 no-op，不得消费触摸流；语义变更必须补回归测试。

## 5.17 ConstraintLayout 约束

涉及 `ConstraintLayout` 能力新增或改造时，必须遵守：

1. 组件 DSL 与 scope 只放 `:viewcompose-constraintlayout-androidx`；renderer 只做 Android `ConstraintLayout` 映射与约束应用。
2. `layoutId/constrainAs/constrain` 属于 parent-data，错误宿主必须触发 `ModifierParentDataValidator` 警告，禁止静默忽略。
3. 同一 child 同时存在 inline 约束与 decoupled `ConstraintSet` 时，必须保持 inline 优先并输出一次 warning。
4. `ConstraintDimension` 与 `Modifier.width/height/size` 冲突时，必须保持约束 dimension 优先。
5. 新增 guideline/barrier/chain/Flow/Group/Layer/Placeholder/constraintSet 语义时，必须同轮补 DSL 单测 + renderer 单测 + demo UI 回归锚点。
6. `Barrier(allowsGoneWidgets = ...)` 参数必须真实生效，禁止仅保留参数但在 renderer 侧静默降级。
7. chain `weights` 与 `referencedIds` 数量不一致时必须 fail-fast（DSL）并在 renderer 输出一次可定位 warning。
8. 约束新增 `min/max/percent/constrained`、`baselineToTop/baselineToBottom`、`circle` 语义时，必须同轮补齐 DSL 发射断言与 renderer 应用断言。

## 5.18 Graphics 分层与绘制语义约束

涉及 graphics 能力新增或改造时，必须遵守：

1. `viewcompose-graphics-core` 仅承载平台无关图形模型与 draw command，禁止引入 `android.*` / `androidx.*`。
2. `viewcompose-graphics` 仅承载 DSL 与业务 API（`Canvas`、`drawBehind/drawWithContent/drawWithCache`），禁止直接落 Android Canvas 执行细节。
3. renderer 仅做 `DrawCommand -> Android Canvas/Paint/Path` 执行映射与 patch 接入，不允许在业务层重复实现绘制命令。
4. `drawWithCache` 变更必须补 cache 命中与失效断言，禁止通过每帧重建缓存绕过回归。
5. Android 专属图形扩展（`RenderEffect`、`RuntimeShader`、`Drawable` bridge）必须落在 `viewcompose-host-android` interop，禁止回流 `graphics-core` 或 `graphics`。
6. graphics 视觉语义变更必须同轮更新 `viewcompose-preview` 的 `PreviewCatalog` 与 Paparazzi 快照基线（`qaPreview` 硬门禁）。

## 6. 线程中断恢复原则

如果聊天线程丢失、附件损坏或上下文中断，恢复顺序固定为：

1. `git log`
2. 当前工作区 `git diff`
3. 根目录 roadmap / architecture 文档
4. 最近失败日志或测试报告
5. 最后才依赖聊天记录回忆

也就是说：

项目真实上下文以仓库状态为准，不以聊天线程为准。

## 7. 提交信息原则

提交标题必须直接描述当前这一个最小步骤。

推荐格式：

1. `docs: ...`
2. `feat: ...`
3. `fix: ...`
4. `test: ...`
5. `refactor: ...`

示例：

1. `docs: add overlay components roadmap`
2. `feat: add overlay host contract`
3. `fix: refresh dialog content on state updates`
4. `test: add snackbar presenter coverage`
5. `test: add overlay demo instrumentation`

## 8. 当前执行约定

当前项目默认采用下面这条执行顺序：

1. 先规划
2. 再做最小实现
3. 每完成一小步立即提交
4. 再进入下一小步

这条约定的目标不是追求提交数量，而是保证：

1. 每一步都可回退、可审阅、可恢复
2. 任何线程丢失后，都能从仓库状态继续工作

## 9. 文档分层约定

文档的完整目录、命名、链接和生命周期规则统一由
[文档治理规范](./documentation-governance.md)定义。

基本分层：

1. 当前入口：[`docs/README.md`](../README.md)
2. 长期规范：`docs/architecture/`、`docs/guides/`、`docs/tooling/`、`docs/project/`
3. 跨会话执行计划：`docs/project/plans/`
4. 历史审计/快照：`docs/archive/`

根目录只保留项目入口和社区治理文件，不再承载功能、架构或计划文档。

## 10. 执行计划防丢失约定

对于“跨多步、跨多天”的任务，必须先创建执行计划文档，并持续回写状态。

执行规则：

1. 计划文档放在 `docs/project/plans/`，使用小写 kebab-case 名称
2. 每完成一小步（且完成一次提交）后，立即更新计划文档中的 checklist 与执行日志
3. 计划文档必须记录：当前基线、完成标准、未完成项、下一步
4. 全部完成后，将长期结论回填到对应有效文档，再把计划迁入 `docs/archive/`
5. 同步检查路线图和相关有效文档中的“进行中/未完成/Next/待推进”标记，避免状态漂移
