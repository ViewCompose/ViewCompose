---
translation_source: migration/compose-host-lifecycle-and-android-interop.md
translation_source_hash: 8fb60e576bb9dad7704f838240a005976c5c46c309ebe1b92a2e695f810bc525
translation_status: current
---

# 从 Compose 宿主、生命周期与 Android 互操作迁移到 ViewCompose

本文将 Jetpack Compose 的 Android 宿主、生命周期、状态 owner 与 Android View 互操作行为
映射到 ViewCompose。这是一份工程对比，而不是声称名称相近的 API 具有相同语义。

- **来源状态：** Jetpack Compose UI 与 Runtime 1.11.4、Activity 1.13.0、Lifecycle 2.11.0
  和 SavedState 1.5.0。
- **目标状态：** `viewcompose-android`、`viewcompose-lifecycle-androidx`、
  `viewcompose-viewmodel-androidx` 与 `viewcompose-renderer-android` 0.1.0-alpha01，以及底层
  `viewcompose-host-android` 0.1.0-alpha03 引擎。
- **最后核验：** 2026-08-06。
- **重新核验负责人：** `viewcompose-android`、`viewcompose-host-android`、
  `viewcompose-lifecycle-androidx`、`viewcompose-viewmodel-androidx` 和
  `viewcompose-renderer-android` 的维护者。

相关页面：[迁移总览](README.md) · [从 Compose Navigation 迁移](compose-navigation.md)

## 验证模型

本文的上游部分是对 AndroidX 稳定版文档和发布说明的语义复核：

- [在 View 中使用 Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views)
- [在 Compose 中使用 View](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)
- [`ComponentActivity.setContent`](https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary)
- [Composition 生命周期](https://developer.android.com/develop/ui/compose/lifecycle)
- [`Composition`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composition)
- [Lifecycle 2.11 发布说明](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [SavedState 发布说明](https://developer.android.com/jetpack/androidx/releases/savedstate)

本地可执行基线是 Compose 1.7.8、Activity 1.12.4、Lifecycle 2.8.7 和 Kotlin 2.0.21。
下文引用的仓库测试和已编译样例依据这组依赖验证 ViewCompose 行为。它们不代表实际执行了
上游 Compose 1.11.4、Activity 1.13.0 或 Lifecycle 2.11.0。因此，只要任一基线发生变化，
重新核验就必须同时重复官方语义复核和本地测试运行。

本文涉及的 ViewCompose 契约分别由
[Android 聚合层](../modules/viewcompose-android/README.md)、
[Android 宿主引擎](../modules/viewcompose-host-android/README.md)、
[生命周期](../modules/viewcompose-lifecycle-androidx/README.md)、
[ViewModel](../modules/viewcompose-viewmodel-androidx/README.md) 和
[渲染器](../modules/viewcompose-renderer-android/README.md)模块负责。

## 可编译的成对起点

下面的对照先展示最小 Activity 根宿主和原生 View 路径，不包含后续的生命周期与清理策略。
两个片段都从 `:samples:compose-migration` 提取；`qaQuick` 会编译对应源码并拒绝文档漂移。

Compose 源码：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ComposeHostSample.kt" region="compose-host" */}
```kotlin
fun ComponentActivity.installComposeInteropSample() {
    setContent {
        ComposeInteropSample()
    }
}

@Composable
private fun ComposeInteropSample() {
    AndroidView(
        factory = { context -> TextView(context) },
        update = { view -> view.text = "Native TextView" },
    )
}
```
{/* paired-sample-end */}

ViewCompose 目标：

{/* paired-sample source="samples/compose-migration/src/main/java/com/viewcompose/samples/migration/host/ViewComposeHostSample.kt" region="viewcompose-host" */}
```kotlin
fun ComponentActivity.installViewComposeInteropSample() {
    setUiContent {
        ViewComposeInteropSample()
    }
}

private fun UiTreeBuilder.ViewComposeInteropSample() {
    AndroidView(
        factory = { context -> TextView(context) },
        update = { view ->
            (view as TextView).text = "Native TextView"
        },
    )
}
```
{/* paired-sample-end */}

该示例只证明公共安装、factory 与可安全重放的 update 路径。目标代码不会继承 Compose 的释放
或复用语义；当嵌入的 View 需要这些行为时，应按下文契约选择 owner，并补充 `onReset`、
`onCommit` 与 `onRelease` 行为。

## 能力矩阵

状态值仅使用 **Supported**、**Partially supported**、**Intentionally different** 和
**Unsupported**。

| 概念 | Compose / AndroidX 行为 | ViewCompose 行为 | 状态 | 本地证据与验证说明 |
| --- | --- | --- | --- | --- |
| Activity 根宿主 | `ComponentActivity.setContent` 把 Compose 内容安装到 Activity 中，并通过宿主管理 Composition。 | `ComponentActivity.setUiContent` 替换 Activity 内容 View、同步渲染首帧、返回新的根 `ViewGroup`，并把 `RenderSession` 保存在内部注册表中，直到内容被替换或 Activity 销毁。 | Partially supported | [`AndroidHostBridge.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt)中的 Activity 路径和会话注册表；已编译的 [`AndroidEntrySamples.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/test/samples/com/viewcompose/android/samples/AndroidEntrySamples.kt)。同步首帧和内部持有的会话是 ViewCompose 特有语义。 |
| Fragment 宿主 | Fragment 中的 `ComposeView` 通常通过 `DisposeOnViewTreeLifecycleDestroyed` 随 Fragment View 树一起释放。 | `Fragment.setUiContent` 为 `onCreateView` 创建并返回根 `ViewGroup`。其内部会话随当前 `viewLifecycleOwner` 释放，但安装到 ViewCompose 内容中的生命周期 owner 目前是 Fragment 实例。 | Partially supported | [`AndroidHostBridge.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt)中的 Fragment 路径和注册表；[`LifecycleBoundDisposerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/test/java/com/viewcompose/android/LifecycleBoundDisposerTest.kt)。安装的 owner 不一致是已知验证缺口。 |
| 现有 View 层级 | `ComposeView` 提供 Composition 释放策略并发现 ViewTree owner。 | `renderInto` 渲染到指定的 `ViewGroup`；它不提供生命周期、ViewModel、保存状态、环境、主题或帧时钟 owner，并要求显式释放会话。 | Partially supported | [`RenderInto.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/RenderInto.kt)以及 [`AndroidEntrySamples.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/test/samples/com/viewcompose/android/samples/AndroidEntrySamples.kt)中已编译的 `renderIntoSample`。 |
| 生命周期 owner 传播 | Compose 宿主集成从 Activity、Fragment View 或 ViewTree 解析 AndroidX owner。 | Activity 内容接收 Activity owner。Fragment 内容目前接收 Fragment owner，而会话释放跟随 Fragment View 生命周期。自定义容器不会自动获得 owner。 | Partially supported | [`AndroidHostBridge.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt)、[`LifecycleBoundDisposer.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/LifecycleBoundDisposer.kt)和 [`LifecycleHostGuards.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/LifecycleHostGuards.kt)。 |
| ViewModel owner 传播 | Lifecycle 2.11 可用 `ViewModelStoreProvider` 为任意 UI 创建子作用域，并继承父级 factory 与 `CreationExtras`。 | 已有 Activity、Fragment、导航 entry 和导航 graph 作用域。任意 ViewCompose UI 子树没有等价的公共 provider，导航 owner 也尚无证据表明会继承所有自定义父级 factory 和 `CreationExtras`。 | Partially supported | [`NavEntryOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt)、[`NavGraphOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavGraphOwner.kt)和 [`NavEntryOwnerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavEntryOwnerTest.kt)。Lifecycle 2.11 行为仅有官方语义证据。 |
| 保存状态 | Compose 宿主集成组合使用 `SavedStateRegistryOwner`、`SavedStateHandle` 与 saveable-state 设施。 | ViewCompose 宿主安装 ViewCompose `SaveableStateRegistry`；适用的 Activity、Fragment 和导航 owner 也参与 AndroidX 保存状态。这些是相关但不可互换的 owner 层。 | Partially supported | [`AndroidHostBridge.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-android/src/main/java/com/viewcompose/android/AndroidHostBridge.kt)、[`NavEntryOwner.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/main/java/com/viewcompose/navigation/NavEntryOwner.kt)，以及 [`NavHostPublicApiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-navigation-android/src/test/java/com/viewcompose/navigation/NavHostPublicApiTest.kt)中的保存状态覆盖。 |
| 帧调度与显式渲染 | Compose 重组由 Recomposer 和帧时钟协调。 | 显式 `render` 是同步的。状态失效会合并到 Android 帧；处于 inactive 状态的会话会保留失效请求，直到再次激活。 | Intentionally different | [`AndroidFrameAlignedRenderSessionRuntime.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntime.kt)和 [`AndroidFrameAlignedRenderSessionRuntimeTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/test/java/com/viewcompose/host/android/runtime/AndroidFrameAlignedRenderSessionRuntimeTest.kt)。 |
| Effect 所有权与终结性释放 | Effect 随其 Composition 作用域退出；释放 `Composition` 是终结操作。 | 一个 `RenderSession` 拥有一个 composition 协程作用域、渲染状态、overlay、原生 View 和清理逻辑。释放会先取消 composition 作用域，再清除已挂载树，并且是幂等的。当前部分释放后操作会 no-op，而不是失败。 | Partially supported | [`RenderSession.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/runtime/session/RenderSession.kt)、运行时测试和 [`RenderSessionFailureTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RenderSessionFailureTest.kt)。释放后契约是已知验证缺口。 |
| Android View factory 与 update | `AndroidView` 为一个实例创建一次 View，并在适用的重组中运行 `update`。 | `AndroidView` 使用 factory 创建新节点，并在事务式原生树 patch 内执行可安全重放的 update 绑定。候选节点插入失败时会回滚。 | Supported | [`AndroidInteropDsl.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-host-android/src/main/java/com/viewcompose/host/android/AndroidInteropDsl.kt)、[`ViewTreePatchPipeline.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreePatchPipeline.kt)和 [`AndroidInteropRenderingUiTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/app/src/androidTest/java/com/viewcompose/AndroidInteropRenderingUiTest.kt)。 |
| Android View reset、commit 与 release | Compose 使用非空 `onReset` 选择加入可复用内容，并在内容永久离开 Composition 时调用 `onRelease`。它没有等价的事务 commit 回调。 | 同 key、同类型节点的 props 发生变化时也可能运行 `onReset`；`onCommit` 仅在整个原生树事务成功后运行；`onRelease` 为永久放弃的已创建节点执行一次性清理，其中包括回滚候选节点。 | Intentionally different | [`AndroidViewNodeProps.kt`](https://github.com/ViewCompose/ViewCompose/blob/fbe1614dd2a278f06517d775c373cb88ce5674a2/viewcompose-ui-contract/src/main/kotlin/com/viewcompose/ui/node/spec/container/AndroidViewNodeProps.kt)、[`ViewTreeDisposer.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/pipeline/ViewTreeDisposer.kt)和 [`ViewTreeRenderTransactionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt)。现有公共表述把 release 限定为已提交删除或会话释放，这是已知验证缺口。 |
| ViewBinding 与树内 Fragment 互操作 | Compose 提供 `AndroidViewBinding` 和 `AndroidFragment` 集成。 | 可以在 Android View factory 中手动 inflate XML，但没有直接 ViewBinding 集成，也没有受支持的渲染树内 Fragment 对应能力。 | Unsupported | 在已审查模块中未找到对应的公共 API 或已编译样例。 |

## 选择宿主入口 {/* #choosing-a-host-entry-point */}

当 Activity 或 Fragment 把宿主根内容交给 ViewCompose 管理时，使用 `setUiContent`。
只有在现有 Android View 层级必须继续拥有容器时，才使用 `renderInto`。后者是更底层的桥接，
不是 ViewCompose 对 `ComposeView` 的另一种写法：

| 来源模式 | 目标模式 | 所有权变化 |
| --- | --- | --- |
| `ComponentActivity.setContent` | `ComponentActivity.setUiContent` | ViewCompose 拥有内部会话；返回值是已安装的根 `ViewGroup`，不是会话句柄。 |
| Fragment `ComposeView` | 从 `onCreateView` 返回 `Fragment.setUiContent()` | ViewCompose 拥有内部会话并跟随 Fragment View 生命周期，但请参阅下文的 Fragment owner 验证缺口。 |
| 嵌入式 `ComposeView` | `renderInto(existingViewGroup)` | 调用方负责提供 owner 和执行释放。 |

所有宿主入口都必须针对仍处于 active 状态的宿主调用。渲染是 Android 主线程工作，且入口调用
返回前已经提交 ViewCompose 首帧。

## Activity 宿主

`ComponentActivity.setUiContent` 把 ViewCompose 内容安装为 Activity 根内容，并提供 Activity
生命周期与 ViewModel owner、宿主 saveable-state registry、动画上下文、帧时钟、环境和主题
局部值。再次调用时，会替换并释放之前注册的 Activity 会话。

返回值是已安装的根 `ViewGroup`，而不是内部 `RenderSession`。因此，公共 Activity 宿主不会
暴露手动渲染、rendering-active 控制或提前释放会话。替换内容或销毁 Activity 时会释放已注册
会话。

## Fragment 宿主

`Fragment.setUiContent` 创建并返回 Fragment 根 `ViewGroup`；请从 `onCreateView` 调用并返回
该根节点。当前 `viewLifecycleOwner` 可用时，内部会话注册表会绑定释放。Fragment View 重建
时会获得新会话，旧 View 会话则在 `onDestroyView` 时释放。

### 已知验证缺口：Fragment owner 身份

实现当前把 Fragment 自身作为内容生命周期 owner 传入，而会话注册表依据
`viewLifecycleOwner` 释放。这与推荐的 Fragment `ComposeView` 方案不是同一个 owner 身份。
在契约和实现达成一致之前：

- 不要把已安装的 owner 记录成 `viewLifecycleOwner`；
- 不要假设 `onDestroyView` 会把已安装的 owner 移到 `DESTROYED`；
- 把与 View 绑定的收集和清理明确限定到真实的 Fragment View 生命周期；
- 解决此缺口后，同时重新核验 owner 身份和释放行为。

## 渲染到现有 View 层级 {/* #rendering-into-an-existing-view-hierarchy */}

`renderInto` 会向指定的 `ViewGroup` 同步执行首帧渲染。它有意不发现或安装生命周期、
ViewModel、保存状态、环境、主题或帧时钟 owner。之前依赖 `ComposeView` owner 发现机制的迁移
代码，必须围绕内容提供所需的 ViewCompose 局部值，并把释放绑定到所属 Android 生命周期。

调用方必须在永久放弃容器之前释放返回的会话，也不得让会话存活时间超过其拥有的 Android
View。

### 已知验证缺口：`renderInto` 释放后的操作

`renderInto` 的公共表述称释放后操作会 fail-fast，而当前 Android 帧对齐运行时会静默忽略
部分释放后的渲染和激活调用。迁移代码不得依赖任一种行为。应把释放视为终结操作，并由调用方
保护会话引用，直到实现、文档和测试确立同一份契约。

## 生命周期、ViewModel 与保存状态 owner {/* #lifecycle-viewmodel-and-saved-state-owners */}

owner 迁移是语义迁移，不是类型名替换：

- Activity 宿主接收 Activity 作用域的 owner；
- Fragment 宿主目前把 Fragment owner 传播与 Fragment View 会话释放组合在一起；
- 导航 entry 和 graph 分别拥有独立的生命周期、ViewModel 与保存状态作用域；
- `renderInto` 不会自动提供其中任何一种作用域。

Lifecycle 2.11 为任意 Compose UI 区域增加了通用 scoped ViewModel。`ViewModelStoreProvider`
可以让子 store 跨配置变更保留、在对应 UI 作用域永久离开时清理，并继承父级 factory 和
`CreationExtras`。ViewCompose 0.1.0-alpha03 对导航 entry 和 graph owner 的永久删除提供了
可比行为，但没有为任意 UI 子树公开等价的通用 provider。若没有额外实现与测试，也不得把其
导航 owner factory 行为描述为完整传播父级 factory 或 `CreationExtras`。

ViewCompose `SaveableStateRegistry`、AndroidX `SavedStateRegistryOwner` 和
`SavedStateHandle` 服务于不同层次。迁移时应明确每个值由哪一层拥有，并把进程重建与内存中
配置变更分别验证。

## 会话、帧、Effect 与释放语义 {/* #session-frame-effect-and-disposal-semantics */}

`RenderSession` 拥有的不只是一个类似 Composition 的内容函数。它拥有 composition 协程
作用域、已挂载原生树、overlay 状态、帧调度和清理。成功的显式 render 会同步提交。状态驱动
的失效会与帧对齐并合并。禁用渲染会暂停交付这些帧，但不会丢弃待处理失效。

释放是终结且幂等的。它先取消 composition 作用域工作，再释放原生树和 overlay。导航是特殊
保留场景：隐藏目的地会话可在帧驱动渲染 inactive 时保持存活。仅仅隐藏目的地不会取消其
composition 作用域中的 Effect。生命周期感知迁移规则见
[从 Compose Navigation 迁移](compose-navigation.md#hidden-destination-retention)。

## Android View 互操作回调映射 {/* #android-view-interop-callback-mapping */}

ViewCompose Android View 回调参与渲染器的原生树事务：

| 回调 | 必需的迁移解释 |
| --- | --- |
| `factory` | 只创建新的原生节点。不要读取应放入 `update` 的变化状态。 |
| `update` | 必须可安全重放。失败帧可以恢复此前已提交的树。 |
| `onReset` | 必须可安全重放。不同于 Compose lazy-content 复用，普通的同 key、同类型节点只要 props 变化也可能调用它。 |
| `onCommit` | 仅在完整原生树事务成功后运行。需要已提交树的不可逆工作应放在这里。 |
| `onRelease` | 每当已创建节点被永久放弃时执行一次性清理，包括成功删除、会话释放和未提交候选节点的回滚。 |

### 已知验证缺口：release 表述与回滚

渲染器测试已确定，新建候选节点回滚时会调用 `onRelease`，而部分当前公共表述把 release
限制为已提交删除或会话释放。迁移代码必须把 `onRelease` 实现为：对任何永久放弃的已创建
View 执行一次性清理。修正文案时，应同时重新核验 KDoc、模块手册、回滚测试和本文。

## 不支持的直接互操作 {/* #unsupported-direct-interop */}

ViewCompose 0.1.0-alpha03 没有 Compose `AndroidViewBinding` 或 `AndroidFragment` 的直接
对应能力。factory 可以 inflate XML 布局，但 ViewBinding 生命周期管理和 Fragment 所有权
仍由应用负责。不要把 Fragment 直接放入 ViewCompose 渲染树，也不要因为能托管其根 View 就
推断已支持 Fragment。

## 迁移风险

- Fragment 内容目前接收 Fragment 生命周期 owner，而会话释放跟随 Fragment View 生命周期。
- `renderInto` 释放后的 fail-fast 表述与运行时 no-op 行为冲突。
- Android View `onRelease` 可能对回滚候选节点运行，但部分公共表述只提到已提交删除和会话
  释放。
- 隐藏导航目的地在帧渲染 inactive 时仍保留 composition 作用域和 Effect。
- Lifecycle 2.11 任意 scoped ViewModel 以及完整的父级 factory/`CreationExtras` 继承，尚无
  ViewCompose 对等证据。
- `renderInto` 不会自动发现 ViewTree owner，也没有 Composition 释放策略。
- 不支持直接 ViewBinding 与渲染树内 Fragment 互操作。

## 迁移检查表

1. 开始迁移内容前，先选择 Activity、Fragment 或现有容器宿主。
2. 记录目标根节点的生命周期、ViewModel、保存状态、主题和帧 owner。
3. 对 `renderInto` 显式安装每个必需 owner，并绑定会话释放。
4. 把可安全重放的 View 绑定放入 `update` 或 `onReset`；把依赖已提交树的不可逆工作放入
   `onCommit`。
5. 让 `onRelease` 同时安全处理回滚候选节点和已提交删除。
6. 把会话释放视为终结操作，不要依赖当前释放后 no-op 行为。
7. 分别测试 Fragment View 重建和 Fragment 销毁。
8. 把配置变更、永久移除和进程重建作为三类不同的状态事件测试。
9. 导航目的地被保留但隐藏时，生命周期感知工作仍必须遵循生命周期。
10. 移除 Compose 宿主前，记录对任何不受支持的 Compose 互操作 API 的依赖。

## 重新核验要求

以下任一项发生变化时，都要重新核验本文：

- 宿主入口、owner 局部值、会话释放规则或 Android View 回调契约；
- Compose UI/Runtime、Activity、Lifecycle 或 SavedState 稳定版基线；
- 仓库的 Compose/AndroidX 可执行对比基线；
- 上文列出的任一已知验证缺口。

最低证据包括所属模块契约、引用的 JVM 测试、Android 互操作 instrumentation、已编译宿主
样例，以及对所链接 AndroidX 官方文档的重新复核。Fragment View 重建和渲染器事务行为需要
行为测试；只有 API 签名并不足够。
