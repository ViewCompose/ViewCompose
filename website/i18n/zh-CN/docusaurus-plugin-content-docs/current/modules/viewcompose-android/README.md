---
translation_source: modules/viewcompose-android/README.md
translation_source_hash: 5630162f65e88e2b976a8621f33723f4a7bb3f158dc80a065ffc370ffbb67390
translation_status: current
---

# 中立 Android 应用聚合模块

`viewcompose-android` 是希望显式选择设计系统的 Android 应用推荐使用的单一依赖。它组合 UI
Foundation、中立 Android Host Engine、Lifecycle 集成与 ViewModel 集成，并提供 Activity 和
Fragment 的 `setUiContent` 入口。

该聚合模块不包含 Material 依赖或设计系统策略，并以运行时实现依赖包含中立
`viewcompose-overlay-android` 传输；它负责依赖编排与稳定的应用宿主边界。Material
应用应改用具名的
[`viewcompose-material3-android`](../viewcompose-material3-android/README.md) 聚合模块。

## 构件与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="android-module-dependency" sample_id="module.android-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-android:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- 传递 API：Host Engine、UI Foundation、Lifecycle、ViewModel、AndroidX Activity 与
  AndroidX Fragment。
- Material 依赖：无。

## 中立入口

{/* compiled-region source="viewcompose-android/src/test/samples/com/viewcompose/android/samples/AndroidEntrySamples.kt" region="android-entry" sample_id="module.android-entry" build_target=":viewcompose-android:compileDebugUnitTestKotlin" */}
```kotlin
fun activityHostSample(activity: ComponentActivity) {
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event -> println(event) },
    )
    activity.setUiContent(diagnostics = diagnostics) {
        Text("Hello from ViewCompose")
    }
}
```

`ComponentActivity.setUiContent` 和 `Fragment.setUiContent` 会创建全尺寸根节点，并提供：

- Lifecycle 与 ViewModel Owner；
- Android 状态保存；
- 密度、字体比例、Locale、布局方向、Android 资源访问与资源版本；
- 动画协程上下文与 Choreographer 帧时钟；
- 中立 Android Overlay 传输，以及可替换的 Factory。

Activity Host 会从已安装 Root 的 `ViewTreeViewModelStoreOwner` 解析
`LocalViewModelStoreOwner`。Fragment Receiver 则保持为显式 ViewModel Owner，并压过 Root
ViewTree 中生命周期更短的 `FragmentViewLifecycleOwner`，所以 Fragment Scope 模型能够跨 View
重建存活。嵌套的 `ProvideViewModelStoreOwner` 仍会在对应子树中覆盖两种 Host。底层
`viewcompose-host-android` 的 `renderInto` 不执行 Owner 发现或安装。

它们不会解析 Material XML、动态色或设计 Token。没有显式 Provider 时，内容读取确定性的
`UiThemeDefaults.light()` 框架基线。可选的 `rootContext` 默认使用 Activity 或 Fragment
Context，并由根节点、原生子 View 与默认 Overlay 共同使用。

如果根设计系统需要不同的 Android Context，应先解析该 Context，再传给 `rootContext`。切换根设计
系统时必须使用新的 Context 与 Token Provider 再次调用 `setUiContent`，使 View 在同一份平台/主题
快照下重建。重复调用会释放旧 Session。Fragment 在 `onCreateView` 中调用时会先返回 Root，等
Android 发布该 Root 的 `viewLifecycleOwner` 后立即开始渲染；内容接收这个 View Owner，Session
在 `onDestroyView` 时结束，而 Fragment Scope 的 ViewModel 与 Saved State Owner 会跨 View 重建
保持稳定。Activity Session 同步渲染，并在 Activity 销毁时结束。

标准 Root 还会为已提交 SDK View State 安装 `LocalSavedStateRegistryOwner`。Activity 内容的两个
Owner Local 都接收 Activity。Fragment 内容通过 `LocalLifecycleOwner` 接收
`viewLifecycleOwner`，通过 `LocalSavedStateRegistryOwner` 接收 Fragment；因此绑定 Lifecycle
的原生工作会随 View 结束，而兼容 SDK Bundle 可恢复到下一个 View 实例。

两个入口都接受 `diagnostics = RenderDiagnostics(...)`。该配置会启动一棵 Host 诊断树，并由
Navigation、Lazy、Pager 和 Overlay 子 Session 继承。Alpha 硬切移除了 `onRenderStats`、
`onRenderResult` 与 `onRenderFailure`；请改用 `RenderFrameCompleted` 和
`RenderFailureObserved`。`debug` 仍只控制日志与慢操作策略。

标准 Root 会自动安装 `AndroidResourceEnvironment`，因此内容可以直接使用
`com.viewcompose.host.android.resources` 中的查询函数，不需要页面自有的失效状态。Configuration
Callback 会刷新普通资源与环境值。应用 Locale/主题 Wrapper 修改没有产生 Callback 时，把同一个
`AndroidResourceRefreshController` 传给 `setUiContent`，替换稳定 `rootContext` 的资源后调用
`refresh()`。构造期敏感的 Context 或设计系统变化仍需再次调用 `setUiContent` 并重建 Root。

Phase 3 Owner 边界 Run 通过全部 21 项 Aggregate Host 执行 Case。新增 Activity 契约验证 ViewTree
发现与嵌套显式 Owner 优先级；Fragment 重建契约验证 ViewTree View Owner 会变化，而内容中的显式
Fragment ViewModel Owner 保持稳定。结论为 **improved** 的 Owner Selection。该结果属于
Robolectric 证据，不证明真机进程终止、内存或性能行为。

## 依赖规则

静态或应用自有设计系统（例如 `viewcompose-oneui7`）配合 `viewcompose-android` 使用。Android
Material XML 与动态色集成使用 `viewcompose-material3-android`。Navigation、图片适配器、具名 Overlay Presenter
或高级阴影等能力按需添加；除非业务要约束版本或直接使用独立 API，否则不要重复声明传递基础模块。

## 相关文档

- [多设计系统架构](../../architecture/design-systems.md)
- [快速开始](../../tutorials/getting-started.md)
- [Android 宿主引擎](../viewcompose-host-android/README.md)
- [Material 3 Android 集成](../viewcompose-material3-android/README.md)
- [One UI 7 设计系统](../viewcompose-oneui7/README.md)

完整生成参考位于
[`viewcompose-android` API 树](https://docs.viewcompose.com/api/viewcompose-android/current/)。

## 兼容性说明

当前 Alpha API 进行源码级硬切：通用 `setUiContent` 不再接收
`Material3DynamicColorPolicy` 或 `Material3ThemeRefreshController`。Material 调用方把依赖换为
`viewcompose-material3-android`，导入 `setMaterial3UiContent`，内容主体保持不变。由于全默认值的
弃用转发重载会与中立的零参数入口产生歧义，因此不保留该重载。

关联诊断硬切从 Activity 与 Fragment 入口同时移除了三个独立渲染回调。不保留弃用转发重载或
仅传播 Result 的 Local。
