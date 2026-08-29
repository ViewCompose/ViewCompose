---
translation_source: modules/viewcompose-navigation-core/README.md
translation_source_hash: 721c0307d3f6e7476c94883134f86747bf0d30a0c02317004de35b6854193bcf
translation_status: current
---

# Navigation Core 模块

`viewcompose-navigation-core` 是 ViewCompose 的平台无关导航状态机。它负责不可变路由与导航图、
严格深链解析、单栈和多栈快照、可安全回滚的两阶段事务、页面生命周期规划，以及经过验证的内容、
自适应 Pane 与 Overlay Scene 选择。

该模块不包含 Android 或 AndroidX 类型。`Activity`、预测性返回、`LifecycleOwner`、
`SavedStateRegistryOwner`、View 挂载、转场和进程死亡适配器均位于 `viewcompose-navigation-android`。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="navigation-core-module-dependency" sample_id="module.navigation-core-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha03")
}
```

- 稳定性：**Alpha**。快照兼容性和路由契约在 Alpha 版本之间仍可能演进。
- 平台：面向 Java 11 的 Kotlin/JVM 库。
- 直接 ViewCompose 依赖：无。
- 平台边界：禁止 Android、View、生命周期、Saved State 或渲染类型。

## 导航图与路由

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-graph" sample_id="module.navigation-core-graph" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val graph = navGraph(
    route = "root",
    startDestination = NavRoute("home"),
) {
    destination("home")
    navigation(
        route = "account",
        startDestination = NavRoute("profile"),
    ) {
        destination("profile")
        destination("settings")
    }
}
```

完整根导航图内的 route 名称必须唯一。嵌套图的起始目的地必须是其直接子节点。请求图 route
会递归进入其起始链，最终得到叶子 `NavGraphResolution` 和从根到叶的图 owner 路径。

`NavRoute` 参数使用封闭的 `NavValue` 模型。所有集合在构造时都会复制。route 相等性包含参数，
因此 `SingleTop` 会把同名但参数不同的 route 视为不同请求。

`NavEntryId` 标识具体的目的地或图 owner，而不是 route。只要 owner 仍被保留，ID 就必须稳定，
并随导航快照持久化。图 owner 允许 Android 宿主在同一个图实例内的目的地之间共享生命周期、
Saved State 和 ViewModel，同时不把 Android 概念引入本模块。

### 类型化 route 契约

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-typed-route" sample_id="module.navigation-core-typed-route" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
data class ProfileRoute(val userId: Long)

val ProfileDestination = NavRouteSpec(
    name = "profile",
    encodeArguments = { profile: ProfileRoute ->
        mapOf("userId" to NavValue.LongValue(profile.userId))
    },
    decodeArguments = { arguments ->
        ProfileRoute((arguments.getValue("userId") as NavValue.LongValue).value)
    },
)

fun typedRouteSample() {
    val graph = navGraph(
        route = "root",
        startDestination = NavRoute("home"),
    ) {
        destination("home")
        destination(ProfileDestination)
    }
    val route = ProfileDestination.encode(ProfileRoute(userId = 42L))
    val entry = NavEntry(NavEntryId("profile-42"), graph.resolve(route).destination)

    check(entry.toRoute(ProfileDestination).userId == 42L)
}
```

`NavRouteSpec<T>` 是 Graph Identity、类型化编码和 Entry 解码共用的唯一应用声明。它始终生成
既有不可变 `NavRoute`；Graph 只保留稳定名称，Snapshot 与恢复仍只持久化封闭的 `NavValue`
参数。进程重建前后必须保持名称和 Schema 兼容。解码失败会显式抛出，且 Route Name 不匹配时
不会调用应用 Decoder。

## 两阶段事务

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-transaction" sample_id="module.navigation-core-transaction" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
when (val preparation = controller.prepare(NavCommand.Push(NavRoute("details")))) {
    is NavPreparation.NoChange -> Unit
    is NavPreparation.Ready -> preparation.transaction.use { transaction ->
        // First mount transaction.after and apply owner lifecycle changes.
        transaction.commit()
    }
}
```

`prepare` 计算候选不可变状态和 entry 差异，但不会发布它们。宿主先完成渲染和生命周期 owner
变更，成功后再调用 `commit`。如果挂载失败，`rollback` 会释放待处理槽位，并保持已提交状态
不变。关闭仍处于 prepared 状态的事务会自动回滚。

每个 controller 同一时间只允许一个待处理事务。事务完成操作只能执行一次且经过同步。
`NavStackMutation` 覆盖所有保留栈，因此切换 Tab 或打开深链时，平台 owner 不会失去同步。

无变化结果具有明确语义：

- 根 entry 不能继续 pop；
- `SingleTop`、replace 或 reset 已经指向当前有效目的地；
- 目标 stack 已按请求策略处于选中状态。

## 原子返回结果

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-results" sample_id="module.navigation-core-results" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val selectedItem = NavResultKey.text("catalog.selection")
val preparedResultPop = controller.prepare(
    NavCommand.PopWithResult(selectedItem.encode("item-42")),
)
check(preparedResultPop is NavPreparation.Ready)
```

`PopWithResult` 在移除活动栈顶时携带一个类型化 `NavValue`。只有已提交事务才向 `after.top`
交付；Core 不持有 Callback 或 Lifecycle。

## 独立保留栈

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-stacks" sample_id="module.navigation-core-stacks" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val configuration = NavStackConfiguration(
    initialStackId = NavStackId("home"),
    stacks = listOf(
        NavStackSpec(NavStackId("home"), NavRoute("home")),
        NavStackSpec(NavStackId("account"), NavRoute("profile")),
    ),
    rootBackBehavior = NavRootBackBehavior.PreviousStack,
)
val controller = NavBackStackController.create(configuration, graph)
```

每个声明的 stack 都拥有独立且非空的返回栈，以及独立的目的地和图 ID。`Preserve` 会精确恢复
用户离开 stack 时的状态；`PopToRoot` 会在选中前移除根节点以上的 entry。选择历史按照从旧到新
记录未激活 stack。

`systemBackCommand()` 是纯查询。非根 stack 返回 `Pop`；到达根时可返回 `PopStackHistory`；
否则返回 `null`，让 Android 宿主向外委托返回事件。

`NavStackSetSnapshot` 会验证目的地或图 owner 身份不会跨 stack 复用。该约束阻止生命周期、
Saved State 和 ViewModel owner 在 Tab 之间泄漏。

## 深链

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-deep-link" sample_id="module.navigation-core-deep-link" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val graph = navGraph(
    route = "root",
    startDestination = NavRoute("home"),
) {
    destination("home")
    destination(
        route = "shared-image",
        deepLinks = listOf(
            NavDeepLink(
                action = "android.intent.action.SEND",
                mimeType = "image/*",
            ),
        ),
    )
}
val result = graph.resolveDeepLink(
    NavDeepLinkRequest(
        action = "android.intent.action.SEND",
        mimeType = "image/png",
    ),
)
check((result as NavDeepLinkResolution.Matched).match.route.name == "shared-image")
```

声明是 URI、action、MIME 或三者组合的严格白名单。每个已声明维度都必须匹配，请求中的额外
维度不参与策略。MIME 值不区分大小写，并支持精确 `type/subtype`、`type/*`、`*/subtype` 与
`*/*` 约束；action 精确比较。组合声明优先于命中的单维声明，然后才比较 URI 与 MIME 具体度。
多个同分最佳匹配会被拒绝，而不是依赖声明顺序。

URI 占位符必须完整占据一个路径段或查询值。URI fragment、userinfo、非法百分号编码、无效
UTF-8、重复查询名、未声明类型和局部占位符都会被拒绝。浮点类型必须有限，布尔值只接受小写
`true` 或 `false`。

输入中额外的 query 参数会被容忍，但完全不参与导航。未声明值不会进入
`NavRoute.arguments`、改变匹配具体度、消除歧义、选择保留栈或决定 launch mode。若应用要求
精确或已签名 URL，应在交给 resolver 前验证完整输入。

解析返回四种结果之一：

- `Matched` 包含胜出的声明和解码后的 `NavRoute`；
- `NoMatch` 表示合法请求未完整满足任何已注册声明；
- `Rejected` 报告非法输入、类型参数失败或最佳匹配歧义；
- `Unsupported` 表示 controller 创建时没有绑定导航图。

宿主把匹配结果转换为 `OpenDeepLink`，在同一个事务中变更并选中目标 stack。String URI 解析
只是 `NavDeepLinkRequest` 的便捷重载，并非第二套匹配实现。

本次 Alpha 切片刻意以 `NavDeepLinkRejection.candidates` 替代
`NavDeepLinkRejection.matchingPatterns`。渲染诊断信息时必须读取不可变声明，因为仅包含 action
或 MIME 的候选项没有 URI pattern。这里不保留 Deprecated Bridge 或并行 String Projection。

## 生命周期规划

`NavScene` 用一份经过校验、从底到顶的语义投影替代并行的 Visible 与 Interactive ID Set。
每个 `NavSceneEntry` 记录 Presence、Visibility、Interaction、粗粒度 Transition Phase、Content
Pane Role 与 Content/Overlay Layer，并在不依赖 Android 类型或逐帧 Progress 的前提下分别推导
Scene 与 Entry Lifecycle Cap。

`NavLifecyclePlanner` 接收 Destination Record 与该 Scene，并只应用一条规则：

```text
effective destination lifecycle = min(host cap, scene cap, entry cap)
```

Prepared 与 Hidden Entry 上限为 `Created`；Covered 与 Active-transition Entry 上限为 `Started`；
只有 Retained、Visible、Interactive 且 Settled 的 Entry 才能到达 `Resumed`。仍在退出动画中的
Popped Entry 上限为 `Created`，终态删除目标为 `Destroyed`。Active-transition Scene 会拒绝任何
Interactive Entry，从构造阶段阻止过早进入 `Resumed`。

从保留集合移除的 owner 会转为 `Destroyed`，且不能复活。降级和销毁迁移始终先于升级迁移，
因此替换交互目的地时不会短暂出现两个 Resumed Owner。Graph Owner 的目标取其后代中的最高
有效状态，保证 Parent 不低于 Active Child。Android 模块负责把生成的不可变
`NavLifecyclePlan` 应用到具体 Owner。

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-scene-projection" sample_id="module.navigation-core-scene-projection" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val list = NavEntry(NavEntryId("list"), NavRoute("list"))
val detail = NavEntry(NavEntryId("detail"), NavRoute("detail"))
val scene = NavScene(
    listOf(
        NavSceneEntry(
            entryId = list.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Hidden,
            interaction = NavSceneInteraction.NonInteractive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = null,
        ),
        NavSceneEntry(
            entryId = detail.id,
            presence = NavEntryPresence.Retained,
            visibility = NavSceneVisibility.Visible,
            interaction = NavSceneInteraction.Interactive,
            transitionPhase = NavSceneTransitionPhase.Settled,
            paneRole = NavPaneRole.Primary,
        ),
    ),
)
val plan = NavLifecyclePlanner.plan(
    currentStates = mapOf(
        list.id to NavEntryLifecycleState.Resumed,
        detail.id to NavEntryLifecycleState.Created,
    ),
    entries = listOf(list, detail),
    scene = scene,
    hostState = NavHostLifecycleState.Resumed,
)

check(plan.targetStates[list.id] == NavEntryLifecycleState.Created)
check(plan.targetStates[detail.id] == NavEntryLifecycleState.Resumed)
check(plan.transitions.first().entryId == list.id)
```

## 统一执行 Reducer

`NavExecutionReducer` 是 Stack Transaction、Scene Layout 与 Lifecycle Projection 之上的唯一策略
边界。`settled`、`transition` 和 `predictivePreview` 分别表达三类事件的前置条件，但统一委托给
同一实现并返回相同的不可变 `NavExecutionPlan`。`reconcile` 保留原计划的 Stack 和 Scene 决策，
只重新计算外层 Host Lifecycle、Presentation Inventory、Retention 或 Back Ownership。

计划同时包含 Candidate 或 Committed Stack Delta、精确 Semantic Scene、有序 Lifecycle Target、
Presentation 的 prepare/refresh/retain/evict/dispose 列表、Input/Focus/Accessibility 与 Back
Ownership、Rendering Suspension、提交前 Rollback 和终态 Cleanup。它只包含 ID 与 Core Value，
绝不持有 View、`LifecycleOwner`、Callback 或动画进度。平台 Adapter 必须先准备计划要求的全部
Presentation，再提交 Transaction；提交后按计划顺序发布 Effect，并直接使用计划记录的
Rollback 或 Cleanup 列表，不能从 Controller State 重建第二套策略。

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-execution-plan" sample_id="module.navigation-core-execution-plan" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val plan = NavExecutionReducer.transition(
    currentLifecycleStates = mapOf(
        before.top.id to NavEntryLifecycleState.Resumed,
    ),
    transaction = transaction,
    beforeSceneLayout = NavSceneLayout(
        NavPaneScene(listOf(NavPane(NavPaneRole.Primary, before.top.id))),
    ),
    afterSceneLayout = NavSceneLayout(
        NavPaneScene(listOf(NavPane(NavPaneRole.Primary, transaction.after.top.id))),
    ),
    hostState = NavHostLifecycleState.Resumed,
    presentedEntryIds = listOf(before.top.id),
    maxRetainedHiddenPresentations = 0,
)

// A platform adapter prepares these identities before committing the stack.
check(plan.preparePresentationEntryIds == listOf(transaction.after.top.id))
check(plan.inputEntryIds.isEmpty())
check(plan.rollbackOwnerEntryIds == listOf(transaction.after.top.id))
check(plan.lifecycle.targetStates.values.none(NavEntryLifecycleState.Resumed::equals))
```

所有 Reducer 调用都无副作用，复杂度与 Retained Entry、Graph Depth、Current Owner 和
Presentation 数量线性相关。`null` 明确表示 Hidden Presentation 无上限，非负值表示确定性的
Oldest-first 上限。该 API 仍为 Alpha，刻意不提供旧新双计划兼容桥。

## Scene Strategy 与自适应 Pane

`NavSceneLayout` 组合非空 Content Pane 与自底向上的 Overlay 后缀，使 Z-order、Back、Result
与 Restore 服从同一 Stack 顺序。

{/* compiled-region source="viewcompose-navigation-core/src/test/samples/com/viewcompose/navigation/core/samples/NavigationCoreSamples.kt" region="navigation-core-scene-strategy" sample_id="module.navigation-core-scene-strategy" build_target=":viewcompose-navigation-core:compileTestKotlin" */}
```kotlin
val overlayStrategy = NavSceneStrategies.trailingOverlays { entry ->
    entry.route.name.endsWith("-dialog")
}
val layout = resolveNavSceneLayout(
    snapshot = snapshot,
    maxPaneCount = 1,
    sceneStrategies = listOf(overlayStrategy),
)

check(layout.contentPaneScene.visibleEntryIds == setOf(home.id))
check(layout.overlayEntryIds == listOf(dialog.id))
check(layout.interactiveEntryIds == setOf(dialog.id))
```

`resolveNavSceneLayout` 选择首个非空 Strategy。受限 `projectContent` 对 Stack Prefix 应用经验证的
Pane Policy；`trailingOverlays` 分类连续栈顶 Entry。Strategy 必须确定且无副作用。

`NavPaneStrategy` 把活跃 stack 转换为一至三个逻辑 pane。`Single` 只显示栈顶目的地；
`BackStack` 把最新保留的目的地依次放入 primary、secondary 和 tertiary pane。

自定义策略应始终通过 `calculateValidated` 执行。验证会限制 pane 数量、拒绝活跃 stack 之外的
entry，并要求栈顶始终可见。`NavPaneScene` 默认把所有可见 Pane 视为可交互；Host 可在构造
`NavScene` 时收窄 Focus Policy。

## 保存与恢复契约

应持久化完整的不可变 `NavStackSetSnapshot`，包括 route 参数、目的地 ID、图 owner entry、
活跃 stack 和选择历史。Android 集成会把该模型编码到 `SavedStateRegistry` 值中。

带图状态必须使用当前 `NavGraph` 恢复。如果 route 被删除、解析到不同叶子，或移动到了不同图
层级，恢复会直接失败，因为复用旧平台 owner 已不安全。多 stack 恢复还要求当前配置拥有完全
相同的 stack ID。待处理事务从不属于持久化状态。

## 相关文档

- [完整导航指南](https://docs.viewcompose.com/zh-CN/guides/navigation)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [Session 容器架构](https://docs.viewcompose.com/zh-CN/architecture/session-containers)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-navigation-core` API 树](https://docs.viewcompose.com/api/viewcompose-navigation-core/current/)。

## 兼容性说明

Scene Projection API 是 Alpha 硬切。`NavExecutionReducer` 现改为接收
`beforeSceneLayout`/`afterSceneLayout`，不再接收仅含 Pane 的字段。原先接收
`retainedEntryIds`、`visibleEntryIds` 与
`interactiveEntryId(s)` 的两个 `NavLifecyclePlanner.plan` Overload，必须迁移到唯一的
`entries` 加 `scene` Overload；不存在 Deprecated Bridge 或双 Planner。

`0.1.0-alpha03` 确立了不可变快照、单一待处理的两阶段事务、独立保留栈、严格 URI 匹配、图层级
验证、生命周期规划、Scene Strategy、Overlay 后缀和三个逻辑 Pane 角色。只能持久化已提交快照；
恢复后必须用当前 Strategy 重新计算 Layout。
