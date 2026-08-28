---
translation_source: modules/viewcompose-viewmodel-androidx/README.md
translation_source_hash: fd32a9b284878d493fae2ed145687eaef4d86e20fb59d74f995d47d910628803
translation_status: current
---

# AndroidX ViewModel 集成

`viewcompose-viewmodel-androidx` 把 ViewCompose 组合 Scope 连接到 AndroidX `ViewModelStoreOwner`、
`ViewModelProvider`、CreationExtras 和 `SavedStateHandle`。Android 宿主提供 Owner Local；导航
目的地和图 Scope 会覆盖它，使模型生命周期跟随声明式页面 Ownership，而不是一律扩大到 Activity。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="viewmodel-androidx-module-dependency" sample_id="module.viewmodel-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-viewmodel-androidx:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。Owner、key、factory 与 Saved State 契约已经过审查和测试，命名在 Alpha
  版本间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- UI Foundation 与 AndroidX Lifecycle 2.11 ViewModel/SavedState 支持会被传递暴露，因为它们的
  Builder、Owner、Factory、Creation Extra、ViewModel 与 `SavedStateHandle` 类型出现在公开 API 中。
- 它不创建或清理宿主 Owner；Ownership 仍属于 Activity、Fragment、导航或自定义容器。

## Owner 传播

标准 Android 宿主把最近的 Owner 安装为 `LocalViewModelStoreOwner`。导航渲染默认安装目的地
Owner；`ProvideNavGraphOwner` 会在子树中替换为选定图 Owner。因此页面 ViewModel 在 pop 时
清理，而图级模型可以跨同一个图实例内的多个目的地存活。

`LocalViewModelStoreOwner.current` 是 nullable，便于可选基础设施查询。`viewModel()` 要求存在
Owner，缺失时会报告明确配置错误。自定义宿主可使用
`ProvideViewModelStoreOwner(owner) { ... }`。提供 Owner 不会清空 Store；创建它的组件必须在
预期终态生命周期边界清理。

延迟子会话会和声明上下文一起捕获该 Local，避免 Overlay 或保留导航内容稍后渲染时意外退回
另一个 Activity Owner。

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-owner-boundary" sample_id="module.viewmodel-owner-boundary" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
/** Installs a custom store owner for a nested subtree. */
fun UiTreeBuilder.provideViewModelStoreOwnerSample(
    owner: ViewModelStoreOwner,
): ProfileViewModel {
    lateinit var model: ProfileViewModel
    ProvideViewModelStoreOwner(owner) {
        model = viewModel()
    }
    return model
}
```

## 保留型子 Scope

当 Pager 页面、Tab、Lazy Item、Overlay 或自定义容器需要短于 Activity/Fragment、但长于一次
可见渲染的生命周期时，使用一个 `ViewModelScopeProvider`。Provider 把子 Store 分配和引用计数
委托给 Lifecycle 2.11 `ViewModelStoreProvider`；ViewCompose 只补充预备组合的提交/回滚、稳定 Key
命名空间、终止后禁止复活以及幂等 Lease 关闭。

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-scoped-owners" sample_id="module.viewmodel-scoped-owners" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
/** Retains one profile subtree below a stable parent and child identity. */
fun UiTreeBuilder.retainedViewModelScopeSample(
    parentOwner: ViewModelStoreOwner,
    parentLifecycleOwner: LifecycleOwner,
): ProfileViewModel {
    val provider = rememberViewModelScopeProvider(
        key = "profile-pane-provider",
        parentOwner = parentOwner,
        lifecycleOwner = parentLifecycleOwner,
    )
    val profileOwner = rememberViewModelStoreOwner(
        key = "primary-profile-pane",
        provider = provider,
    )
    lateinit var model: ProfileViewModel
    ProvideViewModelStoreOwner(profileOwner) {
        model = viewModel()
    }
    return model
}

/** Sends the terminal signal only when the logical profile pane is permanently removed. */
fun removeRetainedProfileScope(provider: ViewModelScopeProvider) {
    provider.clear("primary-profile-pane")
}
```

三个使用角色共用一套实现核心，但适配 API 分层明确：

1. `rememberViewModelScopeProvider` 把稳定 Provider Key 绑定到父 Store 和 Lifecycle。最后一个已
   提交 Binding 正常移除时会清理全部子项；父 Lifecycle 已销毁时则为配置重建保留它们，父 Store
   仍是宿主真正结束时的最终清理边界。
2. `rememberViewModelStoreOwner` 以事务方式获取一个子 Lease。首次候选失败时会清理新 Scope；
   已提交子项的候选被中止时会保留原 Store。组合忘记该调用只释放临时引用。
3. 保留型容器引擎直接调用 `acquireOwner`，并关闭返回的 `ViewModelStoreOwnerLease`。逻辑对象永久
   移除时调用一次 `clear(key)`，整个 Provider 永久销毁时调用 `clearAll()`。

Provider 和子 Key 必须是由应用或容器持有的非空稳定值。同一父 Store 中相等的 Provider Key
共享状态；相等的子 Key 只在同一个 Provider 内共享。位置、可变对象和递增计数器都不是合法的
保留身份。存在活动 Lease 时调用 `clear` 会把子项标记为终态并延迟物理清理；旧 Lease 全部关闭前
新获取会失败，之后同一 Key 会创建全新 Scope。`close`、`clear` 与 `clearAll` 都是幂等清理操作。

默认子 Factory 和 `CreationExtras` 来自父 Owner，并在 Provider 创建时捕获。Scoped Model 需要
`SavedStateHandle` 时，应向 `acquireOwner` 传入 `SavedStateRegistryOwner`，或让
`rememberViewModelStoreOwner` 使用当前组合型 Owner。相等的活动 Scope 若使用不一致的 Saved State
或 Lifecycle 边界会直接失败，不会退回 Activity 或进程级全局 Store。

## 解析 ViewModel

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-resolution" sample_id="module.viewmodel-resolution" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
class ProfileViewModel : ViewModel()

class SavedProfileViewModel(
    val handle: SavedStateHandle,
    val profileId: String,
) : ViewModel()

/** Resolves one instance from the owner installed by the current Android host. */
fun UiTreeBuilder.viewModelSample(): ProfileViewModel {
    return viewModel()
}

/** Keeps two instances of the same class in one store under stable application keys. */
fun UiTreeBuilder.keyedViewModelSample(
    owner: ViewModelStoreOwner,
): Pair<ProfileViewModel, ProfileViewModel> {
    val primary = viewModel(
        modelClass = ProfileViewModel::class,
        key = "primary-profile",
        owner = owner,
    )
    val comparison = viewModel(
        modelClass = ProfileViewModel::class,
        key = "comparison-profile",
        owner = owner,
    )
    return primary to comparison
}

/** Creates a ViewModel with constructor dependencies and the owner's restored state handle. */
fun UiTreeBuilder.initializerViewModelSample(
    owner: ViewModelStoreOwner,
): SavedProfileViewModel {
    return viewModel(owner = owner) {
        SavedProfileViewModel(
            handle = createSavedStateHandle(),
            profileId = "primary-profile",
        )
    }
}

/** Uses the initializer contract when the model class is selected at runtime. */
fun UiTreeBuilder.kClassInitializerViewModelSample(
    owner: ViewModelStoreOwner,
): SavedProfileViewModel {
    return viewModel(
        modelClass = SavedProfileViewModel::class,
        owner = owner,
    ) {
        SavedProfileViewModel(
            handle = createSavedStateHandle(),
            profileId = "runtime-selected-profile",
        )
    }
}
```

解析遵循 AndroidX `ViewModelProvider`：

1. 优先使用显式 Owner，否则使用 `LocalViewModelStoreOwner.current`；
2. 优先使用显式 Factory，否则使用 Owner 默认 Factory，再否则使用 `NewInstanceFactory`；
3. 优先使用显式 CreationExtras，否则复制 Owner 默认 Extras，再否则使用空 Extras；
4. 按显式 key 或 AndroidX 类名派生的默认 key 查询 Owner Store。

Initializer Overload 同时支持 Reified Type 与运行时 `KClass`。其
`CreationExtras.() -> VM` Callback 接收 Owner 默认 Extras，因此 Constructor Dependency 与
`createSavedStateHandle()` 保持为一次创建操作。已有 Entry 会忽略后续 Initializer Callback；
失败 Callback 不发布 Entry，可以稍后重试。

调用必须在组合期间的 Android 主线程执行。Owner 的 `ViewModelStore` 是唯一 ViewModel 实例缓存。
每次实际执行都会做一次有界 Provider 查询，因此 Store 清理会在下一次 Composition 被观察到，
不会返回陈旧的 Remembered Model。

## Key 与查询 Identity

null key 会选择从 ViewModel 类派生的默认 Identity。每个非 null 字符串都是显式 AndroidX Key，
包括空字符串和仅空白字符串，并逐字节保留。在同一个 Owner 中保留多个同类型实例时，应提供稳定的
应用 Key，如上方已编译的 `keyedViewModelSample` 所示。

每次实际执行都会重新查询 Provider。Owner 或 Key 变化可以指向不同 Entry；如果目标 Entry 已存在，
Factory、Extras 或 Initializer 变化不会强制重建。一个显式 Key 下请求不同 Model Class 时遵循
AndroidX Replacement 语义，并清理旧 Model。

不要使用变化对象或调用顺序计数器作为 key。导航已为不同目的地和图实例提供独立 Owner；只有
多个模型有意共享同一个 Store 时才添加应用 key。

## Factory 与 CreationExtras

显式 Factory 优先于 Owner 默认值，显式 Extras 同样优先。使用默认 Extras 时，ViewCompose
会复制到 `MutableCreationExtras`，不会暴露或修改可能被共享的 Owner 对象。

Factory 和 Extras 影响首次创建，不影响 Store 中已有条目。模型需要 `SavedStateHandle` 时，
应使用实现 AndroidX Saved State Factory/Extras 契约的 Owner，或使用 Initializer Overload 与
`createSavedStateHandle()`。Constructor、Initializer 与 Factory 失败会向调用方传播且不发布
不完整 Model；可恢复创建失败应在 Host 边界显式建模。

## SavedStateHandle 所有权

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-saved-state" sample_id="module.viewmodel-saved-state" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
class ProfileFiltersViewModel(
    handle: SavedStateHandle,
) : ViewModel() {
    val selectedFilter = handle.getMutableStateFlow("selected-filter", "all")
}

/** Gives one ViewModel sole write ownership of restored business state. */
fun UiTreeBuilder.savedStateViewModelSample(): ProfileFiltersViewModel {
    return viewModel(key = "profile-filters") {
        ProfileFiltersViewModel(createSavedStateHandle())
    }
}
```

ViewModel 是恢复型业务状态唯一的可写 Owner。可以使用 Owner 默认 Saved State Factory 注入其
构造器，也可以在 `viewModel` Initializer 内调用 `createSavedStateHandle()`。ViewModel 对外提供
`getMutableStateFlow()` 或只读领域操作，并由 Lifecycle 集成负责观察。不要为同一个值再创建第二套
Snapshot State Adapter 或仅持有 Handle 的 Model。

进程死亡恢复还要求感知 Saved State 的 Owner、默认 Factory 与 CreationExtras。Activity、
Fragment、导航目的地和导航图 Owner 已提供该集成。只有 `NewInstanceFactory` 的裸
`ViewModelStoreOwner` 无法自动构造或持久化 Handle。

仅属于 UI 的状态继续由 ViewCompose `rememberSaveable` 持有，不得同时写入 `SavedStateHandle`。
已删除的 `savedStateHandle()` 与 `SavedStateHandleHolderViewModel` 不提供兼容别名。升级前，应把
原有稳定 Key 迁移为真实业务 ViewModel Key，并把每个值移入该 Model 的 Handle。

## 导航 Ownership

- Navigation Entry 与 Graph 从任意保留子树共用的 `ViewModelScopeProvider` 租用 Store；导航
  只持有 Identity、Lifecycle 和终态信号，不再实现第二套 Store Allocator。
- 目的地级 ViewModel 会跨重组、转场、暂时不可见、保留 Tab 切换以及同一父 Store 下的配置重建
  存活，在 Entry 永久离开所有 Stack 时清理。
- 图级 ViewModel 会跨该图实例内的目的地变化存活，在最后一个后代离开保留导航状态后清理。
- 两次 push 同一路由会创建不同目的地 Owner。
- 稍后再次进入同一图路由会创建新的图 Owner 与 Model Store。
- 目的地内容已覆盖当前 Local；需要 Activity 级模型时必须显式传入 Activity Owner。

这些规则无需为每个目的地创建 Activity 或 Fragment，也能保持页面状态独立。

## 测试

Phase 1 的同一测试 Owner 从此前 7 项增加到 21 项 Focused Resolution Test，即新增 14 项契约，
标准化后的 Suite 规模为此前 3 倍（`+200%`）。Phase 2 再增加 20 项 Scoped Owner 契约测试，使
所属模块达到 44/44 全部通过，且没有 Skip、Failure 或 Error。新增用例覆盖 Provider 共享与隔离、
多 Lease、幂等关闭、临时缺席、终态清理、禁止复活、父 Store 清理、Factory/Extras/默认参数、
不一致 Saved State 边界、组合提交与中止、配置重建、延迟 Local 捕获、Pager/Lazy/Overlay 重排以及
`INITIALIZED`/`DESTROYED` Lifecycle 边界诊断。
Phase 3 还通过 151/151 项 Navigation Android 测试和 21/21 项 Aggregate Host Case。导航 Suite
从 148 项增至 151 项，三项聚焦契约分别覆盖缺失 Owner、配置重建时保留 Entry ViewModel，以及
旧格式状态迁移。Aggregate Host 源码覆盖由 10 增至 11 个测试方法，并区分 Activity ViewTree
发现与 Fragment 显式 Owner 优先级。

Phase 4 用两项 `SavedStateViewModelIntegrationTest` 契约替换已删除的 Helper Guard：默认 Factory
会把默认参数注入 Constructor Handle；Initializer 创建的 Handle 与 Mutable State Flow 能跨进程式
新 Owner/新 Store 恢复。所属模块 45/45 项测试全部通过，无 Skip、Failure 或 Error；Navigation
仍为 151/151，Preview Runner 仍为 12/12，迁移后的 Demo 也可编译。相较 Phase 3，模块 Suite 净增
一项测试，因为两项恢复契约替换了一项仅针对 Helper 的 Guard。

结论为 **improved**。解析、创建、通用 Scoped Ownership、导航集成与 Host Owner Selection 均有
直接证据，恢复型业务状态也改由一个 ViewModel 持有，不再依赖框架 Holder。本次 JVM 聚焦运行不
证明真机进程终止、内存保留或性能；这些维度在 Phase 5 前仍为 **inconclusive**。

单元测试中使用真实 `ViewModelStore`，重复渲染同一调用，并在 Teardown 清理 Store。Process-death
`SavedStateHandle` 测试仍应使用感知 Saved State 的 Robolectric 或真机 Owner。

## 相关文档

- [Android host 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-host-android)
- [Navigation Android 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-android)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成式参考位于
[`viewcompose-viewmodel-androidx` API 目录](https://docs.viewcompose.com/api/viewcompose-viewmodel-androidx/current/)。

## 兼容性说明

Lifecycle 2.11 基线硬切两项 Alpha 行为。只有 `null` 选择默认 Key；此前把 `""` 或空白字符串
当作默认 Sentinel 的调用方必须改传 `null`，Blank Key 现在标识显式 Entry。Composition 不再
Remember 已解析 ViewModel，因此 Store Clear 立即可见。Initializer Overload 可替代为 Constructor
Dependency 编写的一次性单 Class Factory。对于宿主以下的生命周期，应把自定义子 Store Map
迁移到一个稳定 Key 的 `ViewModelScopeProvider`，并把逻辑移除与临时渲染缺席分开。Owner 与稳定
Scope Key，而不是 Composition Call Position，仍是权威生命周期边界。
