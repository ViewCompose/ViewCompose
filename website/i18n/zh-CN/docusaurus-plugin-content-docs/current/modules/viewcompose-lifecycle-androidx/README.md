---
translation_source: modules/viewcompose-lifecycle-androidx/README.md
translation_source_hash: 4bb707a0e4c3691914e517d3a350f8689258bfae664002afc8f690c4e1798968
translation_status: current
---

# AndroidX Lifecycle 集成

`viewcompose-lifecycle-androidx` 把 Kotlin `Flow` 收集、ViewCompose 组合工作和已提交的原生 View
连接到 AndroidX Lifecycle 与 SavedState Owner。它提供 Owner Local、感知 Lifecycle 和仅感知
组合生命周期的 `collectAsState` 适配器、成对 Start/Resume Effect、可观察 Lifecycle State，
以及 SDK View 集成使用的类型安全边界。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="lifecycle-androidx-module-dependency" sample_id="module.lifecycle-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-lifecycle-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。收集与 Owner 传播契约已经过审查和测试，命名在 Alpha 版本间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- Runtime、UI Foundation、Android Host、Kotlin Coroutines、AndroidX Lifecycle Runtime 与
  AndroidX SavedState 会被传递暴露，因为其类型出现在公开 API 中。
- 它传播由应用拥有的 Lifecycle/SavedState Owner，并可注册 View 级 Provider；它不拥有
  Activity、Fragment、ViewModel、Owner Registry、SDK State Format 或应用业务状态。

## LifecycleOwner 传播

Activity、Fragment、自定义容器和导航目的地宿主会把最近的 Android `LifecycleOwner` 安装为
`LocalLifecycleOwner`。延迟子会话会和其他声明环境一起捕获该 Local，因此 Overlay 与导航内容
观察的是预期 Owner，而不是稍后真正渲染时碰巧处于当前状态的 Activity。

Owner 可选时读取 `LocalLifecycleOwner.current`。感知生命周期的收集重载会自动解析它；不存在
Owner 时会抛出明确的配置错误。自定义 Host 或有意建立的嵌套边界可使用
`ProvideLifecycleOwner(owner) { ... }` 安装 Owner。子树正常返回或抛出异常后都会恢复先前值。

Android Host 还会安装 `LocalSavedStateRegistryOwner`。两个 Local 有意保持独立：Fragment 内容
使用 Fragment View Owner 作为 Lifecycle Owner，而 SavedState Owner 是 Fragment 本身；导航
Destination 或 Graph 则会为两个 Local 安装自己的 Owner。自定义 Host 只有在 Registry 已完成
Attach 和 Restore 后才能使用 `ProvideSavedStateRegistryOwner(owner) { ... }`。

{/* compiled-region source="viewcompose-lifecycle-androidx/src/test/samples/com/viewcompose/lifecycle/samples/LifecycleSamples.kt" region="lifecycle-owner-boundary" sample_id="module.lifecycle-owner-boundary" build_target=":viewcompose-lifecycle-androidx:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.provideLifecycleOwnerSample(
    source: StateFlow<String>,
    owner: LifecycleOwner,
): State<String> {
    lateinit var state: State<String>
    ProvideLifecycleOwner(owner) {
        state = source.collectAsStateWithLifecycle()
    }
    return state
}
```

## 绑定 Lifecycle 的 Android View

可复用 SDK 集成应继承 `LifecycleAndroidViewAdapter<V, S>`，并在不可变 Adapter State 中捕获
最近的 `LifecycleOwner`。Create 与 `update` 仍须可重放。受保护的 `onViewCommit` 只在 Renderer
事务提交后运行；随后基类才安装 Observer，并按 Android 顺序根据需要补齐 `ON_CREATE`、
`ON_START` 和 `ON_RESUME`。

Owner 替换会先让旧 View 侧依次下降到 `ON_PAUSE`、`ON_STOP`、`ON_DESTROY` 并分离，然后执行
新 Commit 与 Catch-up，两个 Owner 永不重叠。因此 Retained Navigation Destination 隐藏时会
自动限制 Player、Map 或 Camera View，即使物理 View 仍保留在树中也不会继续活跃。

`onLifecycleEvent` 接收最近一次成功提交的 State。Lifecycle-event Callback 失败会终止当前
Binding：错误重新抛出前会尝试完成有界的下降清理与 Observer 移除。`onViewCommit` 必须让 SDK
特定工作具有 Failure Atomicity；若它抛出，基类仍会清除自己的 Lifecycle 与 SavedState Binding，
而不会让此前 State 继续活跃。Reset 与 Release 一定先移除两个 Binding，再运行受保护的 Adapter
Cleanup Hook。所有回调都是主线程同步工作，不能发出由应用拥有的 Lifecycle 命令，也不能阻塞
分发。

{/* compiled-region source="viewcompose-lifecycle-androidx/src/test/samples/com/viewcompose/lifecycle/samples/LifecycleSamples.kt" region="lifecycle-android-view" sample_id="module.lifecycle-android-view" build_target=":viewcompose-lifecycle-androidx:compileDebugUnitTestKotlin" */}
```kotlin
private data class LifecycleLabelState(
    val owner: LifecycleOwner,
    val text: String,
)

private object LifecycleLabelAdapter : LifecycleAndroidViewAdapter<TextView, LifecycleLabelState>() {
    override fun lifecycleOwner(state: LifecycleLabelState): LifecycleOwner = state.owner

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: LifecycleLabelState) {
        scope.view.text = state.text
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<TextView>,
        state: LifecycleLabelState,
        event: Lifecycle.Event,
    ) {
        when (event) {
            Lifecycle.Event.ON_START -> scope.view.isActivated = true
            Lifecycle.Event.ON_STOP,
            Lifecycle.Event.ON_DESTROY,
            -> scope.view.isActivated = false

            else -> Unit
        }
    }
}

/** Mounts a View whose AndroidX owner is caught up only after the View transaction commits. */
fun UiTreeBuilder.lifecycleAndroidViewAdapterSample(
    owner: LifecycleOwner,
    text: String,
) {
    AndroidView(
        adapter = LifecycleLabelAdapter,
        state = LifecycleLabelState(owner = owner, text = text),
        key = "lifecycle-label",
    )
}
```

## 已提交 Android View 的 SavedState

当 SDK View 拥有 Bundle Payload（例如 `MapView` State）时，在 `onViewCommit` 中调用
`AndroidViewCommitScope.bindAndroidViewSavedState(...)`。稳定 Key 是捕获的
`SavedStateRegistryOwner` 内的持久化身份，不是 AndroidView Reconciliation Key。集成层自行
定义并版本化 SDK Payload；框架只负责隔离 Provider 注册、替换、恢复与清理。

首次 Bind 返回 `AndroidViewSavedStateBindResult.Initial`，其中包含一次性的防御性 Restored
Bundle 或 `null`。相同 Owner、Key 与 Version 的后续 Commit 返回 `Retained`，只替换 Saver，
保证 Android 保存最近已提交的 View。Format 不匹配或嵌套 Payload 损坏会被隔离为无 State，
且不阻止新 Provider。Lifecycle-aware Adapter 会在 Reset、Release 或 Owner Destroy 时自动清除
Provider；原始 `AndroidViewAdapter` 必须在自己的最终清理中调用
`clearAndroidViewSavedStateBinding()`。

{/* compiled-region source="viewcompose-lifecycle-androidx/src/test/samples/com/viewcompose/lifecycle/samples/LifecycleSamples.kt" region="lifecycle-android-view-saved-state" sample_id="module.lifecycle-android-view-saved-state" build_target=":viewcompose-lifecycle-androidx:compileDebugUnitTestKotlin" */}
```kotlin
private data class SavedLabelState(
    val lifecycleOwner: LifecycleOwner,
    val savedStateOwner: SavedStateRegistryOwner,
    val text: String,
)

private object SavedLabelAdapter : LifecycleAndroidViewAdapter<TextView, SavedLabelState>() {
    override fun lifecycleOwner(state: SavedLabelState): LifecycleOwner = state.lifecycleOwner

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: SavedLabelState) {
        scope.view.text = state.text
    }

    override fun onViewCommit(scope: AndroidViewCommitScope<TextView>, state: SavedLabelState) {
        val result = scope.bindAndroidViewSavedState(
            owner = state.savedStateOwner,
            key = "saved-label",
            formatVersion = 1,
        ) {
            Bundle().apply { putString("text", view.text.toString()) }
        }
        if (result is AndroidViewSavedStateBindResult.Initial) {
            result.restoredState?.getString("text")?.let(scope.view::setText)
        }
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<TextView>,
        state: SavedLabelState,
        event: Lifecycle.Event,
    ) = Unit
}

/** Registers SDK Bundle state from commit and restores it before lifecycle catch-up. */
fun UiTreeBuilder.androidViewSavedStateBindingSample(
    lifecycleOwner: LifecycleOwner,
    savedStateOwner: SavedStateRegistryOwner,
    text: String,
) {
    AndroidView(
        adapter = SavedLabelAdapter,
        state = SavedLabelState(
            lifecycleOwner = lifecycleOwner,
            savedStateOwner = savedStateOwner,
            text = text,
        ),
        key = "saved-label",
    )
}
```

## 组合生命周期收集

{/* compiled-region source="viewcompose-lifecycle-androidx/src/test/samples/com/viewcompose/lifecycle/samples/LifecycleSamples.kt" region="lifecycle-flow-composition" sample_id="module.lifecycle-flow-composition" build_target=":viewcompose-lifecycle-androidx:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.collectStateFlowSample(source: StateFlow<String>): State<String> {
    return source.collectAsState()
}

/** Supplies the first-frame value required by a general flow. */
fun UiTreeBuilder.collectFlowSample(source: Flow<String>): State<String> {
    return source.collectAsState(initial = "Loading")
}
```

`StateFlow.collectAsState()` 会同步暴露当前 `StateFlow.value`，并在组合提交后启动 collector。
普通 `Flow.collectAsState(initial)` 会在第一次 emission 前暴露调用方提供的初始值。

这些重载不受 Android 生命周期状态限制，适用于唯一边界就是组合的工作，包括自定义宿主和
非 UI 测试。离开组合会取消收集；Flow 或收集 Context 改变会重启 producer，同时保留同一个
remembered State holder 及其最新值。

## 感知 Lifecycle 的收集

{/* compiled-region source="viewcompose-lifecycle-androidx/src/test/samples/com/viewcompose/lifecycle/samples/LifecycleSamples.kt" region="lifecycle-flow-aware" sample_id="module.lifecycle-flow-aware" build_target=":viewcompose-lifecycle-androidx:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.collectWithLifecycleSample(
    source: StateFlow<String>,
    owner: LifecycleOwner,
): State<String> {
    return source.collectAsStateWithLifecycle(
        lifecycleOwner = owner,
        minActiveState = Lifecycle.State.STARTED,
    )
}

/** Uses a Lifecycle directly when no owner object is available. */
fun UiTreeBuilder.collectWithExplicitLifecycleSample(
    source: Flow<String>,
    lifecycle: Lifecycle,
): State<String> {
    return source.collectAsStateWithLifecycle(
        initial = "Loading",
        lifecycle = lifecycle,
        minActiveState = Lifecycle.State.RESUMED,
    )
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

{/* compiled-region source="viewcompose-lifecycle-androidx/src/test/samples/com/viewcompose/lifecycle/samples/LifecycleSamples.kt" region="lifecycle-effects" sample_id="module.lifecycle-effects" build_target=":viewcompose-lifecycle-androidx:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.lifecycleStartEffectSample(
    owner: LifecycleOwner,
    trackerId: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    LifecycleStartEffect(trackerId, lifecycleOwner = owner) {
        onStart()
        onStopOrDispose(onStop)
    }
}

/** Acquires and releases foreground-only work with resumed lifecycle state. */
fun UiTreeBuilder.lifecycleResumeEffectSample(
    owner: LifecycleOwner,
    requestId: String,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    LifecycleResumeEffect(requestId, lifecycleOwner = owner) {
        onResume()
        onPauseOrDispose(onPause)
    }
}

/** Exposes the latest lifecycle state as ViewCompose observable state. */
fun UiTreeBuilder.lifecycleCurrentStateSample(
    owner: LifecycleOwner,
): State<Lifecycle.State> = owner.lifecycle.currentStateAsState()
```

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
| 让一个已提交原生 View 协同可替换 Owner | 继承 `LifecycleAndroidViewAdapter<V, S>` |
| 读取或安装最近的 SavedState Owner | `LocalSavedStateRegistryOwner` / `ProvideSavedStateRegistryOwner` |
| 恢复和保存一个 SDK View Bundle | 在 Commit 中调用 `bindAndroidViewSavedState(...)` |

不要把返回值再镜像到第二个 `MutableState`；读取返回 State 已经会参与 runtime observation，
并使所属组合 Scope 失效。

## 测试

使用 `LifecycleRegistry` 显式驱动 `ON_CREATE`、`ON_START`、`ON_STOP` 和销毁。Collection 测试
应覆盖初始值、不活跃期间保留、重启后的 Emission、Dispose 取消、Owner 缺失失败、非法阈值，
以及快速重启时 Collector 不重叠。原生 View Adapter 测试还应覆盖 Commit 后 Catch-up、Owner
替换顺序、Hidden Destination 限制、回调失败清理、进程重建、Format 不匹配隔离和 Provider
一次性移除。

## Phase 2 验证证据

2026-08-24 相对基线 `eb02abc5` 的验收通过 Lifecycle 模块全部 35 个 JVM 与 Robolectric 测试，
其中包括 6 个 Lifecycle Adapter Case 和 3 个 SDK SavedState Case。受影响的 Host、Renderer、
Android Aggregate、Navigation 与 Preview 测试也通过。选定 Q3 API 审计、文档/依赖/发布/工具
隔离门禁、完整 `qaQuick`（1,954 个任务，6 分 35 秒）与 `qaPreview`（1,115 个任务，22 秒）均通过。

基线只有 Composition-scoped Lifecycle Effect，没有受事务约束的原生 View Owner 或 SDK Bundle
Provider Boundary。验收实现覆盖 Commit 后 Catch-up、串行 Owner 替换、Retained Destination 限制、
失败清理、进程重建与 Provider 移除，因此行为结论为 **improved**。门禁耗时未按缓存状态归一化，
不能支持性能结论。本阶段没有新增 SDK 或可视 Surface，真机 UI 证据无法检验新行为；真实 Surface
与前后台验证从 Media3 集成开始。

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

`LifecycleAndroidViewAdapter`、SavedState Owner Local 与已提交 Android View SavedState Binding
是 Q3 集成 API，并把 Android Host 与 AndroidX SavedState 加入本产物的传递 API Surface。SDK
集成应把手写 Lifecycle Observer 与 Provider Bookkeeping 硬切到这些边界；应用 State 及播放、
权限或凭据策略仍不属于它们。
