---
translation_source: modules/viewcompose-navigation-core/README.md
translation_source_hash: ddfe98058600f726c23c98f7b70ea966b0632548d2a18eabdc1dc739f00b02a1
translation_status: current
---

# Navigation Core 模块

`viewcompose-navigation-core` 是 ViewCompose 的平台无关导航状态机。它负责不可变路由与导航图、
严格深链解析、单栈和多栈快照、可安全回滚的两阶段事务、页面生命周期规划，以及自适应 pane
场景选择。

该模块不包含 Android 或 AndroidX 类型。`Activity`、预测性返回、`LifecycleOwner`、
`SavedStateRegistryOwner`、View 挂载、转场和进程死亡适配器均位于 `viewcompose-navigation`。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。快照兼容性和路由契约在 Alpha 版本之间仍可能演进。
- 平台：面向 Java 11 的 Kotlin/JVM 库。
- 直接 ViewCompose 依赖：无。
- 平台边界：禁止 Android、View、生命周期、Saved State 或渲染类型。

## 导航图与路由

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

## 两阶段事务

```kotlin
when (val preparation = controller.prepare(NavCommand.Push(NavRoute("details")))) {
    is NavPreparation.NoChange -> Unit
    is NavPreparation.Ready -> preparation.transaction.use { transaction ->
        // 先挂载 transaction.after 并应用 owner 生命周期变化。
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

## 独立保留栈

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

```kotlin
val profileLink = NavDeepLink(
    uriPattern = "https://viewcompose.com/users/{userId}",
    argumentTypes = mapOf("userId" to NavDeepLinkArgumentType.Long),
    targetStackId = NavStackId("account"),
)
```

pattern 是严格白名单。占位符必须完整占据一个路径段或查询值。URI fragment、userinfo、非法
百分号编码、无效 UTF-8、重复查询名、未声明类型和局部占位符都会被拒绝。浮点类型必须有限，
布尔值只接受小写 `true` 或 `false`。

解析返回四种结果之一：

- `Matched` 包含胜出的声明和解码后的 `NavRoute`；
- `NoMatch` 表示合法 URI 未进入任何已注册 pattern 的匹配域；
- `Rejected` 报告非法输入、类型参数失败或最佳匹配歧义；
- `Unsupported` 表示 controller 创建时没有绑定导航图。

静态路径段的优先级高于占位符，其次比较 query 精确度。多个同分最佳匹配会被拒绝，而不是依赖
声明顺序。宿主把匹配结果转换为 `OpenDeepLink`，在同一个事务中变更并选中目标 stack。

## 生命周期规划

`NavLifecyclePlanner` 消费稳定 owner ID，而不是 Android `LifecycleOwner`。后台保留 owner 的
目标为 `Created`，可见 owner 为 `Started`，可交互 owner 为 `Resumed`。宿主生命周期会限制
所有目标状态的上限。

从保留集合移除的 owner 会转为 `Destroyed`，且不能复活。降级和销毁迁移始终先于升级迁移，
因此替换交互目的地时不会短暂出现两个 resumed owner。Android 模块负责把生成的
`NavLifecyclePlan` 应用到具体 owner。

## 自适应 pane

`NavPaneStrategy` 把活跃 stack 转换为一至三个逻辑 pane。`Single` 只显示栈顶目的地；
`BackStack` 把最新保留的目的地依次放入 primary、secondary 和 tertiary pane。

自定义策略应始终通过 `calculateValidated` 执行。验证会限制 pane 数量、拒绝活跃 stack 之外的
entry，并要求栈顶始终可见。`NavPaneScene` 默认把所有可见 pane 视为可交互；如界面只允许单一
焦点，宿主可在生命周期规划前收窄交互集合。

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

`0.1.0-alpha02` 确立了不可变快照、单一待处理的两阶段事务、独立保留栈、严格 URI 匹配、图层级
验证、生命周期规划和三个逻辑 pane 角色。只能持久化已提交快照，不要持久化 controller、事务、
策略、工厂或宿主生命周期计划。
