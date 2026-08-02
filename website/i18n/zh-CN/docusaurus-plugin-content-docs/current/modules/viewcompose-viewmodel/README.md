---
translation_source: modules/viewcompose-viewmodel/README.md
translation_source_hash: 1d941267cd132a82bd23d5438074056ffac963cf9fa2e90e836ffa1ce46aab13
translation_status: current
---

# ViewModel 集成

`viewcompose-viewmodel` 把 ViewCompose 组合 Scope 连接到 AndroidX `ViewModelStoreOwner`、
`ViewModelProvider`、CreationExtras 和 `SavedStateHandle`。Android 宿主提供 Owner Local；导航
目的地和图 Scope 会覆盖它，使模型生命周期跟随声明式页面 Ownership，而不是一律扩大到 Activity。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-viewmodel:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。Owner、key、factory 与 Saved State 契约已经过审查和测试，命名在 Alpha
  版本间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- 本产物依赖 widget core 与 AndroidX ViewModel/SavedState 支持。
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

## 解析 ViewModel

```kotlin
class ProfileViewModel : ViewModel()

fun UiTreeBuilder.ProfilePage() {
    val model = viewModel<ProfileViewModel>()
    // 渲染可观察模型状态。
}
```

解析遵循 AndroidX `ViewModelProvider`：

1. 优先使用显式 Owner，否则使用 `LocalViewModelStoreOwner.current`；
2. 优先使用显式 Factory，否则使用 Owner 默认 Factory，再否则使用 `NewInstanceFactory`；
3. 优先使用显式 CreationExtras，否则复制 Owner 默认 Extras，再否则使用空 Extras；
4. 按显式 key 或 AndroidX 类名派生的默认 key 查询 Owner Store。

调用必须在组合期间的 Android 主线程执行。Owner 的 `ViewModelStore` 是权威缓存：重组和重复
调用会返回同一实例，直到 Store 被清理。

## Key 与查询 Identity

null 或空白 key 会选择从 ViewModel 类派生的默认 Identity。在同一个 Owner 中保留多个同类型
实例时，应提供稳定、非空白 key：

```kotlin
val primary = viewModel<ProfileViewModel>(key = "primary")
val comparison = viewModel<ProfileViewModel>(key = "comparison")
```

Owner、key、factory、extras 和 model class 构成组合的 Provider 查询 Identity。任一项改变时，
ViewCompose 会重新查询 Provider，但这不会强制重建实例：如果新查询仍指向已有 Owner/key 条目，
AndroidX 会返回 Store 中的实例，并忽略仅用于首次创建的新 Factory 或 Extras。

不要使用变化对象或调用顺序计数器作为 key。导航已为不同目的地和图实例提供独立 Owner；只有
多个模型有意共享同一个 Store 时才添加应用 key。

## Factory 与 CreationExtras

显式 Factory 优先于 Owner 默认值，显式 Extras 同样优先。使用默认 Extras 时，ViewCompose
会复制到 `MutableCreationExtras`，不会暴露或修改可能被共享的 Owner 对象。

Factory 和 Extras 影响首次创建，不影响 Store 中已有条目。模型需要 `SavedStateHandle` 时，
应使用实现 AndroidX Saved State Factory/Extras 契约的 Owner，或提供兼容覆盖。构造或 Factory
失败会向调用方传播；可恢复创建失败应在宿主边界显式建模，不要返回不完整模型。

## SavedStateHandle 便捷入口

```kotlin
fun UiTreeBuilder.Filters() {
    val handle = savedStateHandle(key = "filters")
    // 读写 AndroidX SavedStateHandle 支持的值。
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

单元测试中使用真实 `ViewModelStore`，重复渲染同一调用，并在 teardown 清理 Store。应验证稳定
复用、不同 key、显式 Owner 替换、Factory 优先级、Extras、Owner 缺失失败，以及 Ownership
边界的 `onCleared`。进程死亡 `SavedStateHandle` 测试应使用感知 Saved State 的 Robolectric
或真机 Owner。

## 相关文档

- [Android host 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-host-android)
- [Navigation Android 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation)
- [生命周期与 Saved State 架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成式参考位于
[`viewcompose-viewmodel` API 目录](https://docs.viewcompose.com/api/viewcompose-viewmodel/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立了 nullable Owner 查询、嵌套 Owner 提供、AndroidX Store Identity、显式与
默认 Factory/Extras 优先级、Keyed 实例和 SavedStateHandle Holder。应让 Owner，而不是组合
调用位置，成为权威生命周期边界。
