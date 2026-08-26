---
translation_source: migration/compose-state-recomposition-and-restoration.md
translation_source_hash: ebb0a0f215f9403826eec443be0765fc2aaf5bbb2dc5f8122973082e8890a119
translation_status: current
---

# 迁移 Compose 状态、重组与恢复

本文比较 Jetpack Compose 与 ViewCompose 的状态和组合语义，并给出从 Compose 所有的 UI
迁移到 ViewCompose 所有的 Android `View` 树的路径。这是一份工程对比，而不是源码兼容承诺：
API 名称相似，并不表示编译器、失效、Identity 或恢复行为完全相同。

最后验证日期：**2026-08-16**

重新验证负责人：**`viewcompose-runtime`、`viewcompose-ui-foundation`、
`viewcompose-android` 与 AndroidX lifecycle 集成的维护者**

## 基线与对比规则

本页支持的对比目标是以下采用独立版本的 ViewCompose 产物集合：

| 产物 | 版本 | 在本页中的职责 |
| --- | --- | --- |
| `viewcompose-runtime` | `0.1.0-alpha02` | 可变状态、派生状态、快照、观察及 `ComposerLite` |
| `viewcompose-ui-foundation` | `0.1.0-alpha01` | `remember`、`key`、Effect、`Saver` 及 `rememberSaveable` |
| `viewcompose-android` | `0.1.0-alpha01` | Activity/Fragment 入口与默认 Android Owner 安装 |
| `viewcompose-host-android` | `0.1.0-alpha04` | 底层自定义容器宿主与 Android SavedState 桥接 |
| `viewcompose-lifecycle-androidx` | `0.1.0-alpha01` | 组合 Scope 与生命周期 Scope 的状态收集 |
| `viewcompose-viewmodel-androidx` | `0.1.0-alpha01` | AndroidX ViewModel 与 `SavedStateHandle` Ownership |

上游稳定语义基线为：

- Compose Runtime、UI 和 Foundation `1.11.4`；
- Activity `1.13.0`；
- Lifecycle `2.11.0`；
- SavedState `1.5.0`。

上述上游版本与发布状态已根据官方的
[Compose Runtime](https://developer.android.com/jetpack/androidx/releases/compose-runtime)、
[Compose Foundation](https://developer.android.com/jetpack/androidx/releases/compose-foundation)、
[Activity](https://developer.android.com/jetpack/androidx/releases/activity)、
[Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) 和
[SavedState](https://developer.android.com/jetpack/androidx/releases/savedstate) 发布说明核对。
Compose `1.11.3` 与 `1.11.4` 的发布说明没有列出会改变本矩阵的状态、快照、remember、Effect
或可保存状态变更。Lifecycle `2.11.0` 新增 Compose Scope 的 ViewModel Ownership API；该新增
能力会影响 Ownership 选择，但不会使两个组合 Runtime 等价。

仓库中的可执行对比 Fixture 有意保留在一组较旧版本上：

| 依赖 | 仓库版本 |
| --- | --- |
| Compose Runtime、UI 和 Foundation | `1.7.8` |
| Activity | `1.12.4` |
| Lifecycle | `2.8.7` |
| Kotlin 与 Compose compiler plugin | `2.0.21` |

这些版本记录在固定版本的
[`gradle/libs.versions.toml`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/gradle/libs.versions.toml)
中。因此，本页使用两类不同证据：

1. **官方语义证据**描述 Compose `1.11.4` 与 AndroidX 基线，并且只链接到 Android 官方文档
   或 API 参考。
2. **仓库执行证据**描述当前 ViewCompose 源码、测试与已编译示例。因为本地对比依赖版本为
   `1.7.8`，这些证据不能证明 Compose `1.11.4` 的行为。

能力状态具有固定含义：

- **支持（Supported）**——ViewCompose 提供迁移所需的行为，并有仓库证据支持。
- **部分支持（Partially supported）**——主要用例已经存在，但仍有重要的语义或 API 边界差异。
- **刻意不同（Intentionally different）**——ViewCompose 有意采用不同的 Ownership 或执行模型，
  调用方必须随之调整。
- **不支持（Unsupported）**——此版本没有对应的 ViewCompose 公共能力。

本页不主张任何量化性能等价性。下文的性能指导只讨论执行边界，并非基准测试结果。

## 可编译的成对起点

下面是仓库中最小的状态迁移执行基准。两个片段都来自 `:samples:compose-migration`；
`qaQuick` 会编译该模块，并在任一片段不再匹配其标记的源码区域时失败。

Compose 源码：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ComposeStateSample.kt" region="compose-state" */}
```kotlin
@Composable
fun ComposeStateCounter() {
    var count by remember { mutableIntStateOf(0) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        BasicText("Count: $count")
        BasicText(
            text = "Increment",
            modifier = Modifier.clickable { count += 1 },
        )
    }
}
```
{/* paired-sample-end */}

ViewCompose 目标：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/state/ViewComposeStateSample.kt" region="viewcompose-state" */}
```kotlin
fun UiTreeBuilder.ViewComposeStateCounter() {
    val count = remember { mutableStateOf(0) }

    Column(
        spacing = 16.dp,
        modifier = Modifier.padding(24.dp),
    ) {
        Text("Count: ${count.value}")
        Button(
            text = "Increment",
            onClick = { count.value += 1 },
        )
    }
}
```
{/* paired-sample-end */}

目标代码显式保留状态对象，并在构建 ViewCompose 树时读取 `value`。这里验证的是可编译的语法
路径，不代表快照事务、编译器重启 Scope、keyed Identity、Effect 或恢复语义等价；这些决策
仍应遵循下文契约。

## 迁移前先选择状态 Owner

不要从替换 API 名称开始。首先确定每个值应由哪种生命周期持有：

| 所需生命周期 | ViewCompose Owner | 迁移指导 |
| --- | --- | --- |
| 一个已提交的组合位置 | `remember` | 用于可替换的内存对象与状态持有者 |
| 组合以及 Activity/Fragment 重建 | 安装了 `SaveableStateRegistry` 的 `rememberSaveable` | 只保存重建页面所需的最小 UI 状态 |
| 页面或导航目的地的业务状态 | 通过 `viewcompose-viewmodel-androidx` 使用 AndroidX `ViewModel` | 生命周期由 `ViewModelStoreOwner` 而非调用位置定义 |
| 已挂载 Eager Scroll 位置 | Q3 `ScrollState` | 保留调用方持有的 State；它只连接一个已挂载的 ScrollableColumn/Row Backend |
| 已挂载 Pager 观察与命令 | Q3 `PagerState` 加受控 `currentPage` | 保持 `currentPage` 权威性；把 `onPageChanged` 视为 Idle 停稳事件 |
| 系统发起的进程重建期间保留 ViewModel 状态 | `SavedStateHandle` | 保存少量重建输入，不要保存派生页面模型 |
| 持久应用数据 | 组合之外的 Repository 或数据库 | `rememberSaveable` 和 `SavedStateHandle` 都不是持久存储 |

Lifecycle `2.11.0` 也提供 Compose 专用的 Scope ViewModel API。它们与源应用的 Ownership
设计相关，但 ViewCompose 的目的地、导航图、Activity 和 Fragment Owner 通过自身的宿主与导航
集成安装。迁移 Compose Scope 的 ViewModel 边界之前，请参阅
[ViewModel 集成手册](../modules/viewcompose-viewmodel-androidx/README.md)。

## 可变状态与 Mutation Policy {/* #mutable-state-and-mutation-policies */}

Compose `mutableStateOf` 创建可观察的快照状态。Composable 读取该状态时会订阅当前重组 Scope，
非等价写入则会调度受影响的工作。上游契约由官方
[状态指南](https://developer.android.com/develop/ui/compose/state)与
[`SnapshotMutableState` 参考](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/SnapshotMutableState)
定义。

ViewCompose 提供相同的迁移层模型：

- 读取 `MutableState.value` 会参与当前快照和 `RuntimeObservation`；
- 在显式可变快照之外写入时，会使用自动可变事务；
- `SnapshotMutationPolicy.equivalent` 会抑制等价写入、全局版本推进和观察失效；
- 当 `SnapshotMutationPolicy.merge` 能产生非 null 的合并值时，会用它解决并发写入；
- 观察回调在应用成功写入的线程上执行，宿主则负责串行化组合与 Android 工作。

仓库证据：

- [`State.kt` 第 25–80 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/State.kt#L25-L80)；
- [`MutableStateImpl.kt` 第 16–114 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/MutableStateImpl.kt#L16-L114)；
- [`SnapshotMutationPolicy.kt` 第 3–119 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/SnapshotMutationPolicy.kt#L3-L119)；
- [`RuntimeObservation.kt` 第 9–94 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/observation/RuntimeObservation.kt#L9-L94)；
- [`SnapshotStateTest.kt` 第 15–177 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotStateTest.kt#L15-L177)；
- [`RuntimeObservationTest.kt` 第 18–122 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/observation/RuntimeObservationTest.kt#L18-L122)；
- [`RuntimeSamples.kt` 中的已编译示例](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt)。

一次成功的全局 Apply 最多在 Apply 线程调用受影响的 `RuntimeObservation` 一次，即使多个被观察
State 同时变化。不同 Apply 仍是不同的 Callback 机会；ViewCompose 不会跨 Transaction、Frame
或时间合并 Observation。这是 ViewCompose 自身的 Callback 契约，不代表与 Compose 的调用次数等价。

## 派生状态与失效差异 {/* #derived-state-and-invalidation-differences */}

Compose `derivedStateOf` 会缓存计算，并追踪计算期间读取的每个快照状态。接收
`SnapshotMutationPolicy` 的重载控制变化后的计算结果何时更新观察者。Android 官方的
[`derivedStateOf` 参考](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary#derivedStateOf(androidx.compose.runtime.SnapshotMutationPolicy,kotlin.Function0))
与 [Effect 指南](https://developer.android.com/develop/ui/compose/side-effects#derivedstateof)
描述了常见用例：当输入变化频率高于派生结果时，减少下游重组。

ViewCompose `derivedStateOf` 为**部分支持（Partially supported）**。它采用惰性计算、缓存最近
结果、观察计算依赖，并在依赖变化时使自己的观察者失效。它没有暴露结果 Mutation Policy
重载，而且公共契约明确说明不会抑制相等派生结果引发的失效。它还会根据当前快照读取 Token
重新验证缓存的计算。

仓库证据：

- [`State.kt` 第 67–80 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/State.kt#L67-L80)；
- [`DerivedStateImpl.kt` 第 9–68 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/DerivedStateImpl.kt#L9-L68)；
- [`DerivedStateTest.kt` 第 11–40 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/DerivedStateTest.kt#L11-L40)；
- [`RuntimeSamples.kt` 中的 `derivedStateSample`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt)。

迁移后果：不要在转换 Compose `derivedStateOf` 优化后，承诺相等结果会抑制 ViewCompose
失效。如果正确性或成本控制依赖这种抑制，应发布一个经过单独比较的 `MutableState` 值，或把
计算放到显式稳定输入边界之后。在文档声称更强的对等性之前，还需要扩大对嵌套派生状态、依赖
切换、计算失败与相等结果失效的回归覆盖。

## 快照、原子更新与冲突 {/* #snapshots-atomic-updates-and-conflicts */}

Compose 可变快照会隔离写入，并在 `apply` 时原子发布；释放尚未应用的快照不会发布其中任何
变更。活跃快照会保留状态历史，因此必须释放。请参阅官方
[`Snapshot` 参考](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/Snapshot)与
[`MutableSnapshot` 参考](https://developer.android.com/reference/kotlin/androidx/compose/runtime/snapshots/MutableSnapshot)。

ViewCompose 支持一致性读取快照、自动可变事务、显式可变快照、把嵌套可变变更应用到父快照、
原子应用到全局、冲突报告、基于 Policy 的冲突合并，以及在旧 Reader 释放后裁剪历史。
`withMutableSnapshot` 会释放其临时事务，并在无法合并冲突时抛出
`SnapshotApplyConflictException`。

这个领域仍为**部分支持（Partially supported）**，而非完全等价：

- ViewCompose 不保证并发进入或修改同一快照实例的安全性；
- ViewCompose Policy 协议使用 `null` 表示不可合并冲突，因此不能表示“成功合并且结果为
  `null`”；
- Compose 不允许在当前只读快照中创建可变快照，而当前 ViewCompose 实现会从当前 Read ID
  派生根可变快照，并未执行这项拒绝检查；
- ViewCompose 不提供 Compose `SnapshotStateList`、`SnapshotStateMap` 或 `SnapshotStateSet`。
  它提供 Cold `snapshotFlow`，支持每个 Collector 独立读取观察、失效合并、条件依赖替换和结构
  Distinct Emission。计算必须无副作用，并运行在 Collector Coroutine 中。

仓库证据：

- [`Snapshot.kt` 第 5–197 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/snapshot/Snapshot.kt#L5-L197)；
- [`SnapshotRuntime.kt` 第 49–296 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/snapshot/SnapshotRuntime.kt#L49-L296)；
- [`MutableStateImpl.kt` 第 98–142 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/MutableStateImpl.kt#L98-L142)；
- [`SnapshotStateTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotStateTest.kt)；
- [`SnapshotApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotApiTest.kt)。
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/SnapshotFlow.kt`；
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotFlowTest.kt`。

只读快照到可变快照的嵌套差异是根据当前实现推断的，并且没有专门的回归测试。应把它视为迁移
风险，而不是可以依赖的功能。请用存放在 `MutableState` 中的不可变集合替换 Compose 快照集合，
或者让外部可观察 Owner 持有集合，再通过
[`viewcompose-lifecycle-androidx`](../modules/viewcompose-lifecycle-androidx/README.md) 收集。

## 不使用 Compose 编译器的重组 {/* #recomposition-without-the-compose-compiler */}

Compose 编译器转换是上游重组机制的核心。它创建 Restart Group、记录变化的参数、推断稳定性，
并决定哪些 Composable 可以跳过。Strong skipping 还会改变比较规则与 Lambda Memoization
规则。官方的[稳定性指南](https://developer.android.com/develop/ui/compose/performance/stability)、
[Strong skipping 指南](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)
和 [Composable 生命周期指南](https://developer.android.com/develop/ui/compose/lifecycle)描述了这些规则。

ViewCompose 在这里是**刻意不同（Intentionally different）**。`ComposerLite` 在没有编译器生成
Change Flag 的情况下协调位置 Group。Widget 与 Renderer 集成通过
`runGroup(signature, inputs)` 建立显式 Group。每个执行的 Group 使用 `RuntimeObservation`
收集状态读取；失效会把该 Scope 及其祖先标记为脏、把 Scope 加入队列，并允许干净的兄弟节点
复用缓存结果。显式输入使用 Kotlin 相等性比较。ViewCompose 没有稳定/不稳定类型推断、
`@Stable` 效果、编译器生成的 Lambda Memoization 或自动 Composable 函数 Restart Boundary。

仓库证据：

- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`；
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/RecomposeScope.kt`；
- [`InvalidationQueue.kt` 第 3–76 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/InvalidationQueue.kt#L3-L76)；
- [`ComposerLiteTest.kt` 第 16–169 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/composition/ComposerLiteTest.kt#L16-L169)；
- [`SubtreeRecompositionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SubtreeRecompositionTest.kt)；
- [`ComposerDiagnosticsTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/kotlin/com/viewcompose/runtime/composition/ComposerDiagnosticsTest.kt)。

迁移后果：把频繁变化的读取移动到应该更新的最小 ViewCompose 组件或 Node Group 中。不要把
`@Stable`、`@Immutable` 或 Compose 编译器报告移植为 ViewCompose 优化控制。请使用
ViewCompose 诊断信息验证实际重组与跳过的 Group。普通 Kotlin 函数边界不会自动成为 Restart
Scope。

### 显式属性事务

ViewCompose Q3 Observed Property 是针对更窄场景的 **Intentionally different** 显式能力。
`observedValue(inputs) { ... }` 与 `observedNodeSpec(inputs) { ... }` 会把 State Read 从外层
Composition Scope 中移出。`RenderSession` 从同一个 Snapshot 读取全部 Dirty Property
Declaration，再要求 Renderer 原子 Patch 对应的精确已提交节点。第一项 Typed Integration 是
`Text(observedValue { state.value })`；低层 Observed `emit` Overload 接受一份具体类型保持不变的
完整 `NodeSpec`。

这不是 Compose Compiler Skipping。Node Type、Key、Modifier、Child 与捕获的 Environment 仍然
属于结构，并通过普通 Composition 更新。每个变化的非 State Kotlin Capture 都必须进入 `inputs`；
ViewCompose 无法推断变化参数，因此遗漏输入属于不支持用法。违反属性契约时会失败并回滚，不会
静默退化成整树渲染。高频叶子属性且 Renderer Patch Contract 完整时使用该边界；条件 Child 或
Node Replacement 仍应放入 `RecomposeBoundary`。

仓库证据包括 `observedTextValueSample`、`observedNodeSpecSample`、Runtime Dependency
Replacement 测试、RenderSession Batch/Failure 测试、Android Multi-target Rollback 测试，以及
[Observed-property ADR](../architecture/decisions/0015-observed-property-transactions.md)。

## Remembered Identity、Key 与重排 {/* #remembered-identity-keys-and-reordering */}

两个 Runtime 都使用组合位置和 Key 保留内存值，但结构匹配并不等价。

ViewCompose `remember` 使用当前 `RecomposeScope` 的下一个位置 Slot。它的 Key 使用结构相等性，
Key 变化时会创建候选替换值。Prepared Composition 使替换具有事务性：提交时调用 Remember
生命周期回调；中止时恢复旧 Slot，并放弃新值。

ViewCompose `key` 会扩展 Group Signature、Remember Slot、Effect、Observation、Child Scope 与
自动 Saveable Key 使用的当前 Key Namespace。显式 Keyed Sibling Scope 可以作为一个完整逻辑
Identity 移动到其他兄弟位置。同一 Parent 下重复的有效 Key/Signature 会让组合尝试失败，避免条目
串用状态。Prepared Abort 会恢复此前顺序、Observation 与 Invalidation 所有权。

仓库证据：

- [`Remember.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/Remember.kt)；
- [`Key.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/Key.kt)；
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`；
- `viewcompose-runtime/src/test/java/com/viewcompose/runtime/composition/ComposerLiteTest.kt`；
- [`RememberTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberTest.kt)。

普通 Sibling 发生插入、删除或重排时，应使用稳定 `key` Scope 保留 Identity，并保证该结构边界
内 Key 唯一。Lazy 容器仍使用独立的 Item Key、Revision、Session 与原生树复用契约；普通 Scope
移动不能取代该契约。

## Effect 与已提交帧边界 {/* #effects-and-committed-frame-boundaries */}

Compose Effect 只在组合成功后运行。每次重组成功后，`SideEffect` 会发布当前状态；Key 变化
或调用离开 Composition 时，`DisposableEffect` 会清理；`LaunchedEffect` 会随 Key 取消并重启
协程。请参阅 Android 官方的
[Effect 指南](https://developer.android.com/develop/ui/compose/side-effects)。

ViewCompose 支持这些迁移层生命周期，但提交边界由宿主定义：

- 候选 Effect 在组合期间记录；Prepared Composition 中止时会被丢弃；
- Renderer 建立新的原生树之后，`RenderSession` 提交 Prepared Runtime Composition；
- Committed `rememberUpdatedState` 值先于生命周期回调发布；
- 全部退出的 Remembered、Disposable 与 Launched 生命周期先于任何进入生命周期执行；
- `LaunchedEffect` 与 `DisposableEffect` 在 Runtime Commit 期间从 Remembered Observer 启动；
- 随后由 `commitSideEffects` 按声明顺序运行 `SideEffect`；
- 原生提交回调在 Composition Side Effect 之后运行；
- 离开组合或释放 Session 会取消协程，并执行已经提交的清理。

仓库证据：

- [`SideEffect.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/SideEffect.kt)；
- [`DisposableEffect.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/DisposableEffect.kt)；
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/CoroutineEffects.kt`；
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/ProduceState.kt`；
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`；
- `viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/RenderSession.kt`；
- [`SideEffectTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SideEffectTest.kt)；
- [`DisposableEffectTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/DisposableEffectTest.kt)；
- [`CoroutineEffectsTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/CoroutineEffectsTest.kt)；
- `viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RenderSessionFailureTest.kt`。

Renderer 建立新原生树之后发生的失败会报告为 Committed-frame Failure，不会回滚该原生树。
因此 Effect 必须只包含提交后工作，并自行处理失败清理。Remembered 激活抛错后保持 Pending，
并在后续成功 Commit 中重试；`DisposableEffect` Setup 在返回 Cleanup 前必须能安全重试。此版本的
`DisposableEffect` 与 `LaunchedEffect` 都要求至少一个 Key。Disposable Setup 必须以
`onDispose { ... }` 结束；旧的
Lambda-return Cleanup 写法不再接受。ViewCompose 还提供带 Key 的 `SideEffect` 重载用于只在变化
时发布，而无 Key 重载仍是每次调用都执行的形式。Launched Effect 的 Dispatcher 与 Parent Job
来自安装的 ViewCompose Host Context，而不是 Compose `Recomposer`。

## 可保存状态与 Saver 迁移 {/* #saveable-state-and-saver-migration */}

Compose `rememberSaveable` 会在重组期间保留值，并利用 Saved Instance State 机制跨 Activity
或系统发起的进程重建恢复。Input 会重置保留值；`Saver`、`listSaver` 或 `mapSaver` 会把领域
状态转换为可保存表示。上游行为由 Android 官方的
[状态指南](https://developer.android.com/develop/ui/compose/state#store-state-with-keys-beyond-recomposition)、
[状态保存指南](https://developer.android.com/develop/ui/compose/state-saving)与
[`rememberSaveable` API](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/rememberSaveable.composable)
定义。

ViewCompose 为**部分支持（Partially supported）**：

- 未安装 Registry 时，`rememberSaveable` 会回退到普通 `remember`；
- Input 会重置 Holder，但不会存入其 Saved Representation；
- 自动 Key 由结构 Group Path、位置 Saveable Slot 及活跃显式 Key Hash 组合；
- 提供 `Saver`、`autoSaver`、`listSaver`、`mapSaver` 和 `mutableStateSaver`；
- ViewCompose 仍接受用户提供的字符串 Key，而 Compose `1.11.4` 已把自定义
  `rememberSaveable` Key 标为不支持，改用位置 Scope；
- 显式 Key 不得为空白，并且在同一 Registry 的活跃 Provider 中必须唯一。

仓库证据：

- [`RememberSaveable.kt` 第 5–160 行](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/RememberSaveable.kt#L5-L160)；
- [`Saver.kt` 第 8–90 行](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/Saver.kt#L8-L90)；
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`；
- [`RememberSaveableTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt)；
- [`WidgetCoreSamples.kt` 中已编译的 Saveable Registry 示例](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/samples/com/viewcompose/ui/foundation/samples/WidgetCoreSamples.kt)。

优先使用自动位置 Key。只有 Ownership 设计确有需要时才使用 ViewCompose 显式 Key，并保持其
稳定、唯一；不要把这一模式反向带回当前 Compose。绝不能在 Registry 之外持久化自动 Saveable
Key。Android Saved State Bundle 的大小有限，因此应保存少量重建输入，而非大型列表或完整
页面模型。

## 恢复事务：Claim、Commit 与 Release

Compose 公共 `SaveableStateRegistry.consumeRestored` 契约会在消费恢复值时将它移除，使同一个
Key 无法恢复同一值两次。请参阅官方
[`SaveableStateRegistry` 参考](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/SaveableStateRegistry)。

ViewCompose 在这里**刻意不同（Intentionally different）**，因为渲染帧会在原生树提交前完成
Prepare。其 Registry 使用 Claim Transaction：

1. `rememberSaveable` 在准备组合时调用 `claimRestored`。
2. Claimed Value 仍包含在 `performSave` 中，从而保护与进行中帧并发的 Host Save。
3. 组合提交后，Holder 注册 Provider 并提交恢复 Claim。
4. Provider 注册失败时 Claim 仍可保存，并在后续 Composition Commit 重试。组合中止、恢复失败、
   Abandon 或 Forget 会释放未提交 Claim，使后续 Owner 仍可恢复该值。

仓库证据：

- [`SaveableStateRegistry.kt` 第 3–253 行](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/SaveableStateRegistry.kt#L3-L253)；
- [`RememberSaveable.kt` 第 76–160 行](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/RememberSaveable.kt#L76-L160)；
- `viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt`；
- [`RememberSaveableTest.kt` 第 39–163 行](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt#L39-L163)。

自定义 ViewCompose Host Registry 必须实现这套 Claim 协议；Compose 风格的立即消费不能作为
兼容替代。Provider 注册与恢复 Claim Commit 共同组成可重试的 Remembered 激活：注册失败不会
消费 Claim，`performSave` 会继续包含该值，后续 Commit 无需重建 Holder 即可完成所有权交接。

## Activity、Fragment、自定义宿主与进程死亡行为

标准 `ComponentActivity.setUiContent` 与 `Fragment.setUiContent` 会安装 Android 支持的
ViewCompose Saveable State Registry。每个 `SavedStateRegistryOwner` Identity 绑定一个 Registry。
Bridge 首次访问时消费 Owner 的 Restored Bundle，注册一个在 Android 保存时拉取最新已提交
ViewCompose Snapshot 的 Provider，并在 Owner 销毁时移除绑定。

Android Codec 接受 `null`、可递归保存的 List、String Key Map、Object Array，以及 Bundle 支持的
`Parcelable`、`Serializable`、`IBinder`、`Size` 和 `SizeF` 值。未知外层格式会被忽略；单个损坏
条目会隔离处理，使其他条目仍可恢复。自定义 `renderInto` Session 不会安装 Lifecycle、ViewModel、
Saved State、Environment、Theme 或 Frame Clock 服务。其 Owner 必须显式提供并释放这些服务。

仓库证据：

- [`AndroidHostBridge.kt` 第 60–108、131–180、194–224 行](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt#L60-L224)；
- [`AndroidSaveableStateRegistry.kt` 第 18–254 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidSaveableStateRegistry.kt#L18-L254)；
- [`RenderInto.kt` 第 52–93 行](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/RenderInto.kt#L52-L93)；
- [`AndroidSaveableStateRegistryTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/test/java/com/viewcompose/host/android/AndroidSaveableStateRegistryTest.kt)；
- [`SaveableStateRestorationUiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/app/src/androidTest/java/com/viewcompose/SaveableStateRestorationUiTest.kt)；
- `app/src/debug/java/com/viewcompose/SaveableStateTestActivity.kt`；以及
- `tools/state/validate_android_activity_root_process_death.sh`。

`SaveableStateRestorationUiTest` 继续作为快速 Activity 重建回归路径。Activity 根节点认证 Runner
`tools/state/validate_android_activity_root_process_death.sh` 会写入自动 Key 的
`rememberSaveable` 状态、把保留的 Task 移至后台、只终止应用进程、在新 PID 下恢复 Task，并
验证恢复值。导航认证 Runner
[`validate_android_process_death.sh`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/tools/navigation/validate_android_process_death.sh)
同样把 Task 移至后台，只终止应用进程而不 Force Stop Package，再恢复既有 Task，要求产生新
PID，并比较完整的导航与状态恢复报告。准确覆盖范围请参阅公开英文站点的
[导航恢复指南](https://docs.viewcompose.com/guides/navigation)。

因此，当前证据支持普通 Activity 根节点和导航宿主的标准 Android 宿主恢复。自定义
`renderInto` 恢复仍为**部分支持（Partially supported）**，因为调用方必须显式安装并拥有
SavedState 服务。与 Compose 相同，ViewCompose 不承诺在用户 Force Stop 或显式从 Recents
移除应用后恢复 Saved Instance State。

## 能力矩阵

| 概念 | 状态 | 迁移边界 | 主要仓库证据 |
| --- | --- | --- | --- |
| `mutableStateOf`、Mutation Policy 与读取观察 | **支持（Supported）** | 回调次数与线程遵循 ViewCompose 契约 | [`State.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/State.kt)；[`SnapshotMutationPolicy.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/SnapshotMutationPolicy.kt)；[`RuntimeObservationTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/observation/RuntimeObservationTest.kt) |
| 惰性依赖派生状态 | **部分支持（Partially supported）** | 没有结果 Policy；不会抑制相等派生结果的失效 | [`DerivedStateImpl.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/state/DerivedStateImpl.kt)；[`DerivedStateTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/DerivedStateTest.kt) |
| 读取与可变快照事务 | **部分支持（Partially supported）** | 嵌套/线程规则不同，并有 Nullable Merge 限制 | [`Snapshot.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/snapshot/Snapshot.kt)；[`SnapshotRuntime.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/snapshot/SnapshotRuntime.kt)；[`SnapshotStateTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/SnapshotStateTest.kt) |
| 快照集合与 `snapshotFlow` | **部分支持（Partially supported）** | 支持 `snapshotFlow`，不提供 Snapshot Collection 类型 | `SnapshotFlow.kt`；`SnapshotFlowTest.kt` |
| 编译器生成的 Restart/Skipping/Stability | **刻意不同（Intentionally different）** | 显式 `runGroup` 与观察到的读取取代 Compose 编译器 Group | [`ComposerLite.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/main/java/com/viewcompose/runtime/composition/ComposerLite.kt)；[`ComposerDiagnosticsTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/kotlin/com/viewcompose/runtime/composition/ComposerDiagnosticsTest.kt) |
| 细粒度失效与干净兄弟节点复用 | **部分支持（Partially supported）** | 依赖显式 Group 边界；没有 Stability 推断 | [`ComposerLiteTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/composition/ComposerLiteTest.kt)；[`SubtreeRecompositionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SubtreeRecompositionTest.kt) |
| 位置 `remember` | **支持（Supported）** | 结构 Key 及事务化 Commit/Abort | [`Remember.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/Remember.kt)；[`ComposerLiteTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-runtime/src/test/java/com/viewcompose/runtime/composition/ComposerLiteTest.kt) |
| 普通兄弟节点重排时的 `key` Identity | **支持（Supported）** | 显式 Key 移动完整 Scope；重复有效 Identity 会失败 | [`Key.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/composition/Key.kt)；`ComposerLiteTest.kt` 的 Keyed Movement、Ownership 与 Abort 测试 |
| `SideEffect`、`DisposableEffect`、`LaunchedEffect` 与 `produceState` | **支持（Supported）** | 在 ViewCompose Committed-frame Boundary 执行 | [Effect 源码](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/effects/SideEffect.kt)；[`RenderSession.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/RenderSession.kt)；[Effect 测试](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/SideEffectTest.kt) |
| `rememberSaveable`、Input 与 `Saver` | **部分支持（Partially supported）** | 显式 Key API 和 Registry Fallback 与当前 Compose 不同 | [`RememberSaveable.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/RememberSaveable.kt)；[`Saver.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/Saver.kt)；[`RememberSaveableTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt) |
| Restored Claim/Commit/Release | **刻意不同（Intentionally different）** | 用于安全放弃 Render Preparation | [`SaveableStateRegistry.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/saveable/SaveableStateRegistry.kt)；[Abort 与 In-flight-save 测试](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt) |
| 标准 Android 宿主恢复 | **支持（Supported）** | Activity/Fragment 宿主自动安装 Registry | [`AndroidHostBridge.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt)；[`AndroidSaveableStateRegistry.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidSaveableStateRegistry.kt) |
| 自定义宿主恢复 | **部分支持（Partially supported）** | `renderInto` 不安装任何 SavedState 服务 | [`RenderInto.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/RenderInto.kt) |
| 通用进程死亡认证 | **支持（Supported）** | 真实 Process-kill Runner 已认证普通 Activity 根节点和导航宿主状态；自定义 `renderInto` 所有权仍由调用方管理 | Activity 根节点与导航进程死亡 Runner |
| Eager Scroll 与 Pager Snapshot | **支持（Supported）** | Connector 所有权受挂载生命周期约束；横向偏移与页面索引在 RTL 中保持逻辑顺序 | `ScrollState.kt`；`PagerState.kt`；Connector 与 Renderer 生命周期测试 |

## 迁移检查清单与已知风险

替换 Compose 有状态子树之前：

1. 记录源状态 Owner 与所需生命周期：组合、宿主重建、导航 Entry、ViewModel 或持久存储。
2. 找出每个 Compose Compiler Restart Boundary，并选择拥有对应状态读取的 ViewCompose 组件
   或 Node Group。
3. 移除基于 Compose Stability 推断、Strong skipping 或自动 Lambda Memoization 的假设。
4. 审查每个 `derivedStateOf`；相等结果抑制很重要时，添加显式结果比较。
5. 用 `MutableState` 中的不可变值替换 Snapshot Collection；`snapshotFlow` 只用于明确拥有
   Collection Lifetime 的无副作用 State 计算。
6. 保持 Unkeyed `remember` 调用顺序稳定。普通 Sibling 可能插入、删除或重排时，使用唯一且
   稳定的 `key` 值。
7. 在所需组合位置保留 `ScrollState` 或 `PagerState`。不要持有原生 Connector，也不要把声明绑定
   期间的 Pager Callback 当作业务事件。
8. 把全部外部工作移入已提交 Effect。Effect 失败属于无法恢复上一棵原生树的 Committed-frame
   Failure。
9. 优先使用自动 `rememberSaveable` Key，保持 Saver 输出精简且与 Bundle 兼容，并验证由 Input
   驱动的重置行为。
10. 实现自定义 Registry 时，保留 Claim/Commit/Release 协议。
11. 尽量使用 Activity/Fragment 宿主。使用 `renderInto` 时，显式安装 Lifecycle、ViewModel、
    Saveable State、Environment 和 Frame Clock 服务。
12. 分别测试配置重建与系统风格进程死亡；仅测试 Activity 重建不足以作为证据。

在形成更强文档声明之前，以下已知风险需要新的可执行证据：

- 相等结果及嵌套 `derivedStateOf` 的失效行为；
- 在只读快照中创建可变快照；
- 直接针对官方 Compose `1.11.4` 基线的语义对比测试，而不是仓库中较旧的 `1.7.8` Fixture。

## 验证基线与重新验证负责人

本页通过阅读当前实现、公共源码契约、既有测试与已编译示例源码完成验证。生成初始语义矩阵时
没有执行测试；上面列出的路径用于标识既有证据，并不表示本次审查运行了每项测试。

以下任一项变化时，应重新验证本页：

- 列出的 ViewCompose 模块版本或源码 Revision；
- Compose Runtime/UI/Foundation、Activity、Lifecycle 或 SavedState 稳定基线；
- `State`、`Snapshot`、`ComposerLite`、Group 匹配、Remember、Effect、`Saver`、Registry 或
  Host 的公共行为；
- Renderer Prepare/Commit/Rollback 顺序；
- Android 进程死亡认证覆盖范围。

Runtime 维护者负责状态、快照、观察、Remember 与重组结论。UI Foundation 维护者负责 Effect、
Saver 与 Claim Transaction 结论。Android 聚合层与 Engine 维护者共同负责 Activity、Fragment、Bundle 与进程
重建结论。只有这些负责人就能力状态达成一致，并且引用的源码/测试证据仍能保护文档声明时，
对比更新才算完成。
