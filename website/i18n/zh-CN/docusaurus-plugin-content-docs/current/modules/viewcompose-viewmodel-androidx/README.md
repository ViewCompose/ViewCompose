---
translation_source: modules/viewcompose-viewmodel-androidx/README.md
translation_source_hash: 7b07281b909e87e88fc79b1c75b8ad302f54919dcf4adbb940b5ead1051a4eb9
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

`LocalViewModelStoreOwner.current` 是 nullable，便于可选基础设施查询。`viewModel()` 和
`savedStateHandle()` 要求存在 Owner，缺失时会报告明确配置错误。自定义宿主可使用
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

## SavedStateHandle 便捷入口

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-saved-state" sample_id="module.viewmodel-saved-state" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
/** Resolves an independent saved-state namespace under a stable key. */
fun UiTreeBuilder.savedStateHandleSample(): SavedStateHandle {
    return savedStateHandle(key = "profile-filters")
}
```

`savedStateHandle()` 把一个 Handle 存放在 `SavedStateHandleHolderViewModel` 中。同一 Owner/key
重复调用返回同一个 Handle，Holder 会随 Store 跨配置变化存活。独立 Handle 命名空间应使用
不同稳定 key；默认 key 表示每个 Owner 的一个通用 Handle。

进程死亡恢复还要求感知 Saved State 的 Owner、默认 Factory 与 CreationExtras。Activity、
Fragment、导航目的地和导航图 Owner 已提供该集成。只有 `NewInstanceFactory` 的裸
`ViewModelStoreOwner` 无法自动构造或持久化 Handle。

Holder 类保持 public 只是为了让 AndroidX Factory 能构造它。应用代码应使用
`savedStateHandle()`，不要直接请求 Holder。

## 导航 Ownership

- 目的地级 ViewModel 会跨重组、转场、暂时不可见和保留 Tab 切换存活，在 Entry 永久离开所有
  Stack 时清理。
- 图级 ViewModel 会跨该图实例内的目的地变化存活，在最后一个后代离开保留导航状态后清理。
- 两次 push 同一路由会创建不同目的地 Owner。
- 稍后再次进入同一图路由会创建新的图 Owner 与 Model Store。
- 目的地内容已覆盖当前 Local；需要 Activity 级模型时必须显式传入 Activity Owner。

这些规则无需为每个目的地创建 Activity 或 Fragment，也能保持页面状态独立。

## 测试

Phase 1 的同一测试 Owner 从此前 7 项增加到 20 项 Focused Resolution Test，20 项全部通过。
覆盖 Store Clear 后查询、Null/Empty/Blank/Ordinary Key、Key 精确保留、Owner Replacement、
显式/默认 Factory 与 Extras、Reified 与 `KClass` Initializer、创建失败重试、Model Class
Replacement 与 Composition Boundary。结论为 **improved**。这关闭 Lookup/Creation 缺陷，但未
关闭 Scoped Owner 或 Process Restoration；后两者分别由 Phase 2 与 Phase 4 负责。

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
Dependency 编写的一次性单 Class Factory。应让 Owner，而不是 Composition Call Position，成为
权威生命周期边界。
