---
translation_source: modules/viewcompose-runtime/README.md
translation_source_hash: 735987155f91893b9a40f6f5bb33528fa95ae69c3dac2a88bbfc207fdf3e71f1
translation_status: current
schema_version: 2
document_id: module.viewcompose-runtime
doc_type: module
owner:
  kind: module
  id: viewcompose-runtime
version_lane: released
capability_ids:
  - runtime.reusable-content
  - runtime.state
artifact_ids:
  - viewcompose-runtime
sample_ids:
  - module.runtime-dependency
  - module.runtime-reusable-content
  - module.runtime-state
  - module.runtime-snapshot
coordinate: com.viewcompose:viewcompose-runtime:0.1.0-alpha04
minimal_usage_sample_id: module.runtime-state
---

# Runtime 运行时模块

`viewcompose-runtime` 是 ViewCompose 其他模块共同使用的平台无关状态、快照、观察与轻量组合
引擎。当自定义集成只需要 ViewCompose 的状态或组合语义，而不需要 Android `View` 宿主时，
可以直接使用该模块。

本模块不负责 UI 渲染、Android 生命周期集成、可视帧调度，也不负责跨进程重建的状态持久化。
这些职责属于更上层的模块及其宿主。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="runtime-module-dependency" sample_id="module.runtime-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-runtime:0.1.0-alpha04")
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

{/* compiled-region source="viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt" region="runtime-module-state" sample_id="module.runtime-state" build_target=":viewcompose-runtime:compileTestKotlin" */}
```kotlin
val count = mutableStateOf(0)
val label = derivedStateOf { "Count: ${count.value}" }

count.value += 1
check(label.value == "Count: 1")
```

显式可变快照之外的状态写入会立即提交。需要让多个值原子可见时，应使用事务：

{/* compiled-region source="viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt" region="runtime-module-snapshot" sample_id="module.runtime-snapshot" build_target=":viewcompose-runtime:compileTestKotlin" */}
```kotlin
val count = mutableStateOf(0)
val enabled = mutableStateOf(false)

Snapshot.withMutableSnapshot {
    count.value = 1
    enabled.value = true
}

check(count.value == 1 && enabled.value)
```

## 主要 API

- [`State`、`MutableState` 与 `derivedStateOf`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/)
  提供支持快照的值，以及基于读取依赖惰性计算的派生状态。
- [`Snapshot` 与 `MutableSnapshot`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/-snapshot/)
  提供一致性读取、带冲突报告的原子缓冲写入。
- [`RuntimeObservation`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.observation/-runtime-observation/)
  是把状态读取转化为显式失效订阅的 Q3 API。一次成功的全局 Apply 最多在 Apply 线程调用每个
  受影响 Observation 一次，即使多个依赖同时变化；不同 Apply 仍是不同的通知机会。Q3
  `prepareReplacement` 会通过同一个 Observation Identity 读取候选依赖集合；提交时保留共有订阅
  并原子切换，中止时不会扰动已提交依赖集合。
- [`snapshotFlow`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime/snapshot-flow.html)
  创建 Cold Flow；它会为每个 Collector 跟踪 Snapshot 读取、合并失效、替换条件依赖，并只发出
  结构不相等的计算结果。
- [`ComposerLite`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.composition/-composer-lite/)
  在不依赖编译器生成变更标记的前提下，提供事务式位置组合、remember 值、effect 与诊断。
- Q3 `ComposerLite.withReusableContent` 可以改变可复用结构的逻辑 State Owner，同时保留相等的
  纯结构结果。显式 Owner Transfer 只重新执行持有 Remember 值、Saveable Path、Effect 或
  Observation 的后代 Group；Prepare 失败会恢复先前已提交的 Owner。
- `CompositionTimingCollector`、`CompositionTimingScope` 与
  `ComposerLite.prepareRootWithTiming` 组成 Q3、仅请求期有效的组合计时边界。只有实际执行的 Scope
  会被提交给 Collector；跳过的 Scope 不调用接口，也不读取时钟。Collector 负责单一 Monotonic
  Clock、嵌套核算、上限与开销测量；Runtime 提供惰性分配的进程内 Identity 和已经保留的有界
  Source Hint。
- [`MonotonicFrameClock`](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha02/viewcompose-runtime/com.viewcompose.runtime.frame/-monotonic-frame-clock/)
  是动画集成所消费的平台无关计时契约。

可复用内容的 Owner Transfer 是显式操作，因此物理容器可以保留纯结构，而不会继承另一个逻辑
Item 的 Remembered State：

{/* compiled-region source="viewcompose-runtime/src/test/samples/com/viewcompose/runtime/samples/RuntimeSamples.kt" region="runtime-module-reusable-content" sample_id="module.runtime-reusable-content" build_target=":viewcompose-runtime:compileTestKotlin" */}
```kotlin
val composer = ComposerLite()
var owner = "account-A"
var revision = 0L

fun compose(replaceOwner: Boolean): Any {
    composer.requestRootRecompose()
    return composer.composeRoot {
        composer.runGroup(
            signature = "reusable-host",
            inputs = revision,
        ) {
            composer.withReusableContent(owner, replaceOwner) {
                composer.runGroup(signature = "content") {
                    composer.remember(emptyList()) { Any() }
                }
            }
        }
    }
}

val firstOwnerState = compose(replaceOwner = false)
owner = "account-B"
revision += 1L
val secondOwnerState = compose(replaceOwner = true)

check(firstOwnerState !== secondOwnerState)
composer.dispose()
```

完整生成参考位于
[`viewcompose-runtime` API 树](https://docs.viewcompose.com/api/viewcompose-runtime/current/)。
由于当前版本仍为 Alpha，文档站不会提供稳定的 `latest` 别名。

## 状态与生命周期契约

- `MutableState` 的等价判断和快照冲突行为由其 `SnapshotMutationPolicy` 决定。等价写入不会推进
  全局快照，也不会通知读取者。
- `Snapshot` 在释放前会固定保留历史记录。读取快照不再使用时，必须调用 `close`、`dispose`
  或 Kotlin `use`。
- `MutableSnapshot` 应在应用或放弃后释放。冲突失败不会改变目标，可以重试；成功应用是终态。
- `Observation` 拥有收集期间读取的全部状态订阅。一次成功的全局 Apply 最多使其失效一次，
  多个受影响 Observation 按首次观察的稳定顺序交付。不再需要通知时应将其释放，避免状态继续
  持有订阅；已经与释放形成竞态并开始的 Callback 可以执行完成。
- `PreparedObservationReplacement` 是终态对象：外部候选工作成功或失败后，必须且只能调用一次
  `commit` 或 `abort`。Prepare 会保留已提交订阅，并临时订阅候选独有依赖，避免读取和发布之间
  丢失更新或重复 Callback。同一个 Observation 同时只允许一个 Prepared Replacement。
- 每个 `snapshotFlow` Collector 拥有独立的读取观察。取消或计算失败会将其释放；计算必须无
  副作用，并且运行次数可能多于发出值的次数。
- `ComposerLite` 与派生状态实例按线程封闭设计。宿主负责串行化组合、prepared
  commit/abort、effect 投递和释放。
- Reusable Content Owner 只能在正在执行的 Group 中变化，并且该次 Transfer 必须设置
  `replaceOwner`。Owner 会参与 Remember 与 Saveable Identity，因此必须在一个逻辑生命周期内
  保持稳定，不能与物理容器 Identity 混淆。
- Composition Timing Collector 只在一次同步 `prepareRootWithTiming` 调用期间有效。它不得保留
  Scope、调用应用代码、阻塞、执行 I/O 或重入 Composer；Collector 失败与组合隔离。普通
  `prepareRoot` 路径不分配 Timing Identity、不执行逐 Scope 时钟读取，也不保留 Timing History。
- Remembered 生命周期对象会保持 Pending，直到 `onRemembered` 成功返回。激活抛错后，后续
  成功的 Composition Commit 会重试它，但不会再次激活已成功的兄弟对象。激活前移除会调用
  `onAbandoned`；Active 值则通过恰好一次 `onForgotten` 终止。Abort 不会终止已提交对象，也
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
- 显式 Keyed Sibling Group 可以移动，同时完整保留 Scope Identity，包括 Remember Slot、
  Observation、Child 与 Saveable Path。同一 Parent 下重复的有效 Key/Signature 会让组合尝试
  失败，防止两个逻辑条目共享状态。
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
Composition 现在强制执行 Owner Thread、终态释放与 Callback Re-entry 边界。Remember 激活失败
可重试；显式 Keyed Sibling 会作为完整 Scope 移动，重复有效身份则快速失败。
