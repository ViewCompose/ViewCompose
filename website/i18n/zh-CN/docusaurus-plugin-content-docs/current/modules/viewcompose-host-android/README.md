---
translation_source: modules/viewcompose-host-android/README.md
translation_source_hash: c51b28974180314b446ea0e439d3a05e76cc2a557a9488a55acb30129c3ce19c
translation_status: current
---

# Android 宿主模块

`viewcompose-host-android` 是 ViewCompose composition 与 Android View 系统之间的标准边界。
它负责创建 Activity/Fragment 根节点、管理保留式渲染会话、提供 Android 生命周期和状态服务、
解析主题与环境、把失效请求调度到 Choreographer 帧，并提供显式的原生 View、动画与图形互操作。

大多数 Android 应用只需要这一个 ViewCompose 依赖。它会传递暴露 Runtime、UI Contract 与
Widget Core，而 Renderer、Lifecycle 和 ViewModel 集成保持为 Host 私有实现。只有应用需要
脱离 Host 直接使用某个底层高级 API 时，才显式依赖相应产物。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-host-android:0.1.0-alpha03")
}
```

- 稳定性：**Alpha**。host 扩展与原生互操作契约在 alpha 版本间可能变化。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- 依赖暴露：Runtime、UI Contract 与 Widget Core 是 API 依赖；Lifecycle、ViewModel 和 Renderer
  是实现依赖。
- Android 依赖：AndroidX Activity/Fragment/AppCompat、Lifecycle、SavedState、
  ConstraintLayout、DynamicAnimation、Material Components 和 Android coroutines。
- Activity/Fragment 类层次、Material 主题，以及可选原生动画或 ConstraintLayout Interop 属于
  caller-owned 平台集成。应用声明自己直接使用的 AndroidX/Material 产物；Host 不充当其版本目录。

## 推荐 host 入口

当 ViewCompose 管理 Activity 内容时使用 `ComponentActivity.setUiContent`：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUiContent {
            Text("Hello from ViewCompose")
        }
    }
}
```

Fragment 管理内容时，在 `onCreateView` 中使用 `Fragment.setUiContent`：

```kotlin
override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
): View = setUiContent {
    ProfilePage()
}
```

两个入口都会创建全尺寸根节点、同步提交第一帧，并向内容提供：

- 当前 `LifecycleOwner` 和 `ViewModelStoreOwner`；
- 用于 `rememberSaveable` 的 Android `SaveableStateRegistry`；
- Android density、locale、layout direction 和 context 环境；
- Android 主题解析及所选动态色策略；
- 主线程动画协程上下文和基于 Choreographer 的单调帧时钟；
- 默认 Android overlay host，以及可供自定义 host 和测试替换的工厂。

重复调用 `setUiContent` 会先释放旧会话。Fragment 会话跟随当前 View lifecycle，在 View
重建时正确释放；Activity 会话在 Activity 销毁时结束。

## 自定义容器 host

`renderInto(container)` 是底层保留式会话入口。它会安装 Android renderer，并在返回前提交
第一帧：

```kotlin
val session = renderInto(container) {
    CustomSurface()
}

session.setRenderingActive(false)
session.render() // inactive 时显式渲染仍然同步执行
session.dispose()
```

该入口不会自动提供 lifecycle、ViewModel、saved state、environment、theme 或 frame clock。
自定义 host 必须自行提供这些上下文，并在容器或生命周期结束前释放会话。一个容器只能有一个
活动的 mounted-tree 所有者。

## 原生 View 事务契约

`AndroidView` 在挂载平台 View 的同时保留 renderer 的回滚语义：

```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> configurePlayer(view as PlayerView, state) },
    key = playerId,
    onCommit = { view -> (view as PlayerView).play() },
    onRelease = { view -> (view as PlayerView).release() },
)
```

- `factory` 只在需要新原生节点时运行。
- `update`、`onReset` 和 `Modifier.nativeView` 都是可重放配置。失败帧恢复上一棵已提交树时，
  它们可能再次执行，因此不能承载外部一次性副作用。
- `onCommit` 只在整棵 View tree 的事务提交后运行。
- `onRelease` 在正式移除或 session 释放后只运行一次。
- 稳定 sibling key 能在重排时保留正确的原生 View。

## 状态保存

`viewComposeSaveableStateRegistry(owner)` 按 Android `SavedStateRegistryOwner` 身份绑定一个
ViewCompose registry。第一次访问会消费恢复值；Android 每次保存时读取最新的已提交
ViewCompose 快照。owner 销毁时 provider 和进程内绑定都会被移除。

值可以是 null、递归可保存的 list、字符串 key 的 map，或 Bundle 支持的 Android 类型，
例如 `Parcelable`、`Serializable`、`IBinder`、`Size` 和 `SizeF`。函数及不支持的对象会被
saveable-state 契约拒绝。单个损坏的恢复项会被隔离，不会阻止其他 key 恢复。

## 帧调度与线程

- View 创建、绑定、差分、显式渲染和释放都属于主线程工作。
- 状态失效请求会合并到下一次 Choreographer 帧。
- `RenderSession.render()` 会取消待执行帧，并立即同步渲染。
- inactive session 会保留一次待处理失效，在恢复 active 后重新排帧。
- 等待 `AndroidMonotonicFrameClock` 的协程被取消时，其待执行帧回调也会移除。
- `lastFrameReport` 描述最近一次尝试帧；`lastRenderFailure` 会在后续成功帧后保留历史失败。

## 动画与图形互操作

`AndroidAnimationInterop` 可启动平台 `ObjectAnimator`、`ValueAnimator`、
`ViewPropertyAnimator`、spring、fling、transition 和 MotionLayout 操作。返回的动画对象归
调用方所有，必须随所属生命周期取消。这些操作不是 ViewCompose 状态动画，也不参与渲染回滚。

`AndroidGraphicsInterop` 提供带 API 级别判断的 RenderEffect/RuntimeShader 工厂、bitmap
渲染辅助方法和 View layer paint 配置。不支持的版本返回 `null` 或 `false`。
`Modifier.androidAnimation` 与 `Modifier.androidGraphics` 是可重放的原生 View 配置，不能
在其回调中启动一次性工作。

## 相关文档

- [架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [渲染失败与提交语义](https://docs.viewcompose.com/zh-CN/architecture/render-failures)
- [生命周期与状态保存架构](https://docs.viewcompose.com/zh-CN/architecture/lifecycle-and-saved-state)
- [主题集成指南](https://docs.viewcompose.com/zh-CN/guides/theming)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-host-android` API 树](https://docs.viewcompose.com/api/viewcompose-host-android/current/)。

## 兼容性说明

`0.1.0-alpha03` 建立了 Activity、Fragment、自定义容器、状态保存、帧调度和原生 View 事务
契约。不要持久化 `RenderSession`、Android 根 View、saved-state registry 实例或 renderer
诊断对象。即使 DSL 源码仍能编译，host、widget-core 或 renderer 契约变化时，自定义 host
也必须重新审查。
