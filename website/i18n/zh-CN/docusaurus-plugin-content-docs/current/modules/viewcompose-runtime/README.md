---
translation_source: modules/viewcompose-runtime/README.md
translation_source_hash: 83f6a48bd434a63bcec951b3e3f3222e4d1e6eaa1f6295f44fbefb4f726fa79d
translation_status: current
---

# Runtime 运行时模块

`viewcompose-runtime` 是 ViewCompose 其他模块共同使用的平台无关状态、快照、观察与轻量组合
引擎。当自定义集成只需要 ViewCompose 的状态或组合语义，而不需要 Android `View` 宿主时，
可以直接使用该模块。

本模块不负责 UI 渲染、Android 生命周期集成、可视帧调度，也不负责跨进程重建的状态持久化。
这些职责属于更上层的模块及其宿主。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。Alpha 版本之间可能发生源码和二进制不兼容变更。
- 平台：Kotlin/JVM，使用 Java 11 工具链编译；不依赖 Android SDK 或 AndroidX。
- 直接依赖的 ViewCompose 模块：无。
- 传递提供的 ViewCompose 模块：无。
- 公共 `snapshotFlow` API 返回 `Flow`，因此会暴露 Kotlin Coroutines。
- 本版本构建基线：Kotlin 2.0.21。除非选择的其他产物有要求，否则使用者不需要 Android
  Gradle Plugin。

## 最小状态示例

```kotlin
val count = mutableStateOf(0)
val label = derivedStateOf { "Count: ${count.value}" }

count.value += 1
check(label.value == "Count: 1")
```

显式可变快照之外的状态写入会立即提交。需要让多个值原子可见时，应使用事务：

```kotlin
Snapshot.withMutableSnapshot {
    count.value = 2
    enabled.value = true
}
```

## 主要 API

- [`State`、`MutableState` 与 `derivedStateOf`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/)
  提供支持快照的值，以及基于读取依赖惰性计算的派生状态。
- [`Snapshot` 与 `MutableSnapshot`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/-snapshot/)
  提供一致性读取、带冲突报告的原子缓冲写入。
- [`RuntimeObservation`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.observation/-runtime-observation/)
  将状态读取转化为显式失效订阅。
- [`snapshotFlow`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/snapshot-flow.html)
  创建 Cold Flow；它会为每个 Collector 跟踪 Snapshot 读取、合并失效、替换条件依赖，并只发出
  结构不相等的计算结果。
- [`ComposerLite`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.composition/-composer-lite/)
  在不依赖编译器生成变更标记的前提下，提供事务式位置组合、remember 值、effect 与诊断。
- [`MonotonicFrameClock`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.frame/-monotonic-frame-clock/)
  是动画集成所消费的平台无关计时契约。

完整生成参考位于
[`viewcompose-runtime` API 树](https://docs.viewcompose.com/api/viewcompose-runtime/current/)。
由于当前版本仍为 Alpha，文档站不会提供稳定的 `latest` 别名。

## 状态与生命周期契约

- `MutableState` 的等价判断和快照冲突行为由其 `SnapshotMutationPolicy` 决定。等价写入不会推进
  全局快照，也不会通知读取者。
- `Snapshot` 在释放前会固定保留历史记录。读取快照不再使用时，必须调用 `close`、`dispose`
  或 Kotlin `use`。
- `MutableSnapshot` 应在应用或放弃后释放。冲突失败不会改变目标，可以重试；成功应用是终态。
- `Observation` 拥有收集期间读取的全部状态订阅。不再需要失效通知时应将其释放，避免状态继续
  持有该订阅。
- 每个 `snapshotFlow` Collector 拥有独立的读取观察。取消或计算失败会将其释放；计算必须无
  副作用，并且运行次数可能多于发出值的次数。
- `ComposerLite` 与派生状态实例按线程封闭设计。宿主负责串行化组合、prepared
  commit/abort、effect 投递和释放。
- Remembered 生命周期对象在 Prepared Commit 前处于 Pending，`onRemembered` 后处于 Active，
  并在恰好一次 `onForgotten` 或 `onAbandoned` 后进入 Terminal。Abort 不会终止已提交对象，也
  不会激活候选替换对象。
- `ComposerLite.composeRoot` 会提交 Runtime State，但不会执行一次性 Side Effect。宿主只有在
  对应渲染树与 Remember 生命周期事务都提交成功后，才调用 `commitSideEffects`。
- `ComposerLite.rememberUpdatedState` 仅向活跃组合线程暴露候选值，在已提交生命周期回调前
  发布该值，并在 Abort 时丢弃它。
- `ComposerLite.scopedExplicitSaveableKey` 根据当前结构 Key 路径派生显式
  `rememberSaveable` Registry Key。Lazy List、Pager 等子 Session 所有者通过此边界隔离恢复
  状态，因此不同逻辑条目中相同的应用 Key 不会共享状态，物理 Holder 变化也不会改变逻辑所有者。
  若两个不相等的活跃 Keyed Group 产生相同结构路径 Hash，Runtime 会在注册 Saveable Provider
  前失败，而不会共享恢复状态；因此自定义 Saveable Key 必须提供稳定且无碰撞的 Hash。
- Callback 失败会保留原始 Throwable，并附加有界 Effect Kind、Operation、结构 Scope、Slot
  与不持有 Key 对象的 Metadata。Host 可以通过 `ComposerLite` 构造参数选择非负的同步 Callback
  警告阈值。

长期持有旧快照会保留额外的值记录，频繁改变结构组顺序会阻止组合复用。这些操作也不会阻止
任意用户计算；调用方应把昂贵工作移出状态访问器与组合块，或显式缓存结果。

## 相关文档

- [状态与快照架构](https://docs.viewcompose.com/architecture/state-snapshots)
- [事务式 Effect 与结构化工作](https://docs.viewcompose.com/zh-CN/architecture/effects)
- [当前架构与模块边界](https://docs.viewcompose.com/architecture/overview)
- [已发布模块目录](../README.md)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/project/api-documentation-quality)

Android 应用通常通过 `viewcompose-ui-foundation` 或 `viewcompose-host-android` 传递使用本产物。
只有在自身公共 API 暴露其类型，或者开发自定义宿主/运行时集成时，才需要显式依赖。

## 兼容性说明

`0.1.0-alpha02` 首次建立快照和轻量组合契约，没有更早的稳定版本迁移路径。不要把内部快照
标识符、组合 saveable key、诊断结构或实现类名持久化为长期外部数据；只有公共 API 参考明确
描述的行为属于受支持契约。

本版本新增 `snapshotFlow`，并因此把 Kotlin Coroutines 暴露为 API 依赖；同时移除 Alpha 阶段的
`ComposerLite.disposableEffect` Slot API。自定义组合集成应把所有权工作迁移到 Remembered
`RememberObserver`，应用 UI 则使用 `viewcompose-ui-foundation` 的 Effect API。Prepared
Composition 现在强制执行 Owner Thread、终态释放与 Callback Re-entry 边界。
