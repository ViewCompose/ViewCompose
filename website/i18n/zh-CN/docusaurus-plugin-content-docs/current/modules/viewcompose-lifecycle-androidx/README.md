---
translation_source: modules/viewcompose-lifecycle-androidx/README.md
translation_source_hash: d6a1ae599780bf345b91a69bef68b16c614db75e3ab0942b000295ef4e0717d9
translation_status: current
---

# AndroidX Lifecycle 集成

`viewcompose-lifecycle-androidx` 把 Kotlin `Flow` 收集及 ViewCompose 组合工作连接到 AndroidX
Lifecycle。它提供 Android 宿主安装的 LifecycleOwner Local、感知生命周期和仅感知组合
生命周期的 `collectAsState` 适配器、成对 Start/Resume Effect，以及可观察 Lifecycle State。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-lifecycle-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。收集与 Owner 传播契约已经过审查和测试，命名在 Alpha 版本间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- Runtime、UI Foundation、Kotlin Coroutines 与 AndroidX Lifecycle Runtime 会被传递暴露，因为
  `State`、`UiTreeBuilder`、`Flow` 与 Lifecycle 类型出现在公开 API 中。
- 它不拥有 Activity、Fragment、ViewModel 或 SavedStateRegistry。

## LifecycleOwner 传播

Activity、Fragment、自定义容器和导航目的地宿主会把最近的 Android `LifecycleOwner` 安装为
`LocalLifecycleOwner`。延迟子会话会和其他声明环境一起捕获该 Local，因此 Overlay 与导航内容
观察的是预期 Owner，而不是稍后真正渲染时碰巧处于当前状态的 Activity。

Owner 可选时读取 `LocalLifecycleOwner.current`。感知生命周期的收集重载会自动解析它；不存在
Owner 时会抛出明确的配置错误。自定义 Host 或有意建立的嵌套边界可使用
`ProvideLifecycleOwner(owner) { ... }` 安装 Owner。子树正常返回或抛出异常后都会恢复先前值。

## 组合生命周期收集

```kotlin
fun UiTreeBuilder.Profile(model: ProfileModel) {
    val profile = model.profile.collectAsState().value
    Text(profile.displayName)
}
```

`StateFlow.collectAsState()` 会同步暴露当前 `StateFlow.value`，并在组合提交后启动 collector。
普通 `Flow.collectAsState(initial)` 会在第一次 emission 前暴露调用方提供的初始值。

这些重载不受 Android 生命周期状态限制，适用于唯一边界就是组合的工作，包括自定义宿主和
非 UI 测试。离开组合会取消收集；Flow 或收集 Context 改变会重启 producer，同时保留同一个
remembered State holder 及其最新值。

## 感知 Lifecycle 的收集

```kotlin
fun UiTreeBuilder.Profile(model: ProfileModel) {
    val profile = model.profile.collectAsStateWithLifecycle().value
    Text(profile.displayName)
}
```

感知生命周期的重载使用 AndroidX `repeatOnLifecycle`：

- 默认阈值为 `STARTED`；
- 支持 `CREATED`、`STARTED` 和 `RESUMED`；
- 低于阈值时停止收集，但不清空最近值；
- 重新进入活跃状态时重启上游收集；
- 快速重启前会先完成上一 collector 的取消清理，因此不会重叠；
- 到达 `DESTROYED` 或离开组合会永久取消 producer。

即使 Lifecycle 不活跃，`StateFlow` 仍会同步提供当前值。普通 Flow 则在首次活跃 emission 前
显示调用方提供的 `initial`。

普通 UI 使用 Owner 重载；基础设施组件有意不持有 `LifecycleOwner` 时，使用显式 `Lifecycle`
重载。

## 成对 Lifecycle Effect

`LifecycleStartEffect(key)` 与 `LifecycleResumeEffect(key)` 只在最近或指定 Owner 至少处于
`STARTED` 或 `RESUMED` 时运行成对同步工作。Setup 在组合成功提交后开始；Cleanup 在对应下降
转换、Destroy、Key 或 Owner 替换、离开组合或释放 Session 时执行。

每个 Setup 必须以 `onStopOrDispose { ... }` 或 `onPauseOrDispose { ... }` 结束。Key 是强制的，
并使用结构相等性。替换 Cleanup 会先于替换 Setup 完成；Abort 候选不会分离已提交 Observer，
也不会启动替换对象。如果已处于活跃状态的 Owner 在 Composition Commit 安装期间执行初始
Setup 并抛出，Effect 会保持 Pending 并在后续 Commit 重试，因此 Setup 必须可安全重试。若 Setup
在之后的 Lifecycle 转换中抛出，则会分离该 Observer，并在身份变化前不再重试；Cleanup 抛出后
进入终态。这些回调同步运行在 Lifecycle Dispatch Thread，不得阻塞。Composition Local 必须在
声明 Effect 时解析；后续 Lifecycle Callback 读取 Local 时，会用 Local Diagnostic Name 失败，
即使该线程上恰好有另一个无关 Provider 处于活跃状态也不会读取它。

`Lifecycle.currentStateAsState()` 返回稳定、归组合所有的 Holder；它在 Commit 后观察每次状态
转换，协调首次安装期间竞态的转换，并在离开组合时移除 Observer。

## Coroutine Context 与结构化 Ownership

可选 `context` 可选择 Dispatcher、CoroutineName 或其他非 Job 元素。传入 `Job`（包括
`SupervisorJob`）会被拒绝。组合的 Launched Effect 和 `repeatOnLifecycle` 必须拥有取消权；替换
Job 会让 collector 脱离结构化生命周期并可能活得比 UI 子树更久。

Flow identity、Lifecycle identity、活跃阈值和 Context 共同构成 producer 的重启 key。成功的
组合提交会取消旧 producer 并启动新 producer，但不会替换可观察 State holder。被放弃的组合
尝试不会启动或重启收集。

上游异常遵循结构化协程语义并终止 producer。可恢复错误应在 Flow 中使用 `catch` 等操作处理，
并暴露明确 UI 状态，不要依赖游离的异常处理器。

## API 选择

| 数据源与目标边界 | API |
| --- | --- |
| `StateFlow`，仅组合生命周期 | `collectAsState()` |
| 普通 `Flow`，仅组合生命周期 | `collectAsState(initial)` |
| `StateFlow`，最近或显式 Owner | `collectAsStateWithLifecycle(...)` |
| 普通 `Flow`，最近或显式 Owner | `collectAsStateWithLifecycle(initial, ...)` |
| 没有当前 Owner，但存在显式 Lifecycle | 接收 `Lifecycle` 的重载 |
| 在 Started 期间执行成对 Setup/Cleanup | `LifecycleStartEffect(key) { ... }` |
| 在 Resumed 期间执行成对 Setup/Cleanup | `LifecycleResumeEffect(key) { ... }` |
| 在声明式内容中观察 Lifecycle State | `lifecycle.currentStateAsState()` |

不要把返回值再镜像到第二个 `MutableState`；读取返回 State 已经会参与 runtime observation，
并使所属组合 Scope 失效。

## 测试

使用 `LifecycleRegistry` 显式驱动 `ON_CREATE`、`ON_START`、`ON_STOP` 和销毁。测试应覆盖
初始值、不活跃期间保留、重启后的 emission、dispose 取消、Owner 缺失失败、非法阈值，以及
快速重启时 collector 不重叠。

## 相关文档

- [Android host 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-host-android)
- [UI Foundation 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-foundation)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [事务式 Effect 与结构化工作](https://docs.viewcompose.com/zh-CN/architecture/effects)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成式参考位于
[`viewcompose-lifecycle-androidx` API 目录](https://docs.viewcompose.com/api/viewcompose-lifecycle-androidx/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立了 nullable Owner 查询、Scoped Owner 提供、感知 commit 的 collector 启动、
结构化取消、`repeatOnLifecycle` 重启行为，以及不活跃期间保留 State。Flow 错误应显式建模，
收集 Context 中绝不能传入独立 Job。

`LifecycleStartEffect`、`LifecycleResumeEffect` 与 `Lifecycle.currentStateAsState()` 是本版本新增的
Q3 Lifecycle API。成对 Effect 至少要求一个显式 Key，且不会替代现有 Flow Collection API；
当被拥有的工作本身（而不只是数据收集）必须随 Android Lifecycle Threshold 进入和退出时，
应选择这些 API。
