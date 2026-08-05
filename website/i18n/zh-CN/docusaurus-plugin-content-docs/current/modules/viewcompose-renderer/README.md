---
translation_source: modules/viewcompose-renderer/README.md
translation_source_hash: 8b48346fa3b33822b4b326c479438301e141f2d823e739edfb4c71b09738cca3
translation_status: current
---

# Renderer 渲染器模块

`viewcompose-renderer` 是 ViewCompose 的 Android View 渲染引擎。它把不可变 VNode 快照与已
挂载树进行差分，创建并绑定原生 View，应用定向 patch，驱动 Lazy 容器和 Pager 状态，桥接
shape 与绘图命令，并提供渲染工作量、树结构、布局过程和源码工具链诊断。

应用通常通过 `viewcompose-host-android` 间接获得本模块。实现自定义 Android host、渲染器
诊断、平台装饰后端，或在脱离组件 DSL 的情况下测试差分逻辑时，可以直接依赖它。

本模块不负责 composition、应用生命周期、状态保存、导航、浮层窗口、图片解码或高级阴影的
具体光栅化。这些职责分别属于 `viewcompose-runtime`、`viewcompose-widget-core`、
`viewcompose-host-android` 和可选功能模块。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-renderer:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。渲染扩展契约和诊断模型在 alpha 版本间可能变化。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- UI Contract 会被传递暴露，因为 Renderer 入口会接收并返回其 Node 与 Modifier 类型。
  Runtime、Text Core、Graphics Core 和 Gesture Core 保持为实现依赖。
- Android 运行时依赖：AndroidX Core、AppCompat、RecyclerView、ViewPager2、
  ConstraintLayout、SwipeRefreshLayout 和 Material Components。
- 当前版本构建基线：Kotlin 2.0.21、Android Gradle Plugin 8.7.3。

## 渲染模型

```kotlin
var mounted = ViewTreeRenderer.renderInto(
    container = container,
    previous = emptyList(),
    nodes = firstFrame,
).mountedNodes

mounted = ViewTreeRenderer.renderInto(
    container = container,
    previous = mounted,
    nodes = nextFrame,
).mountedNodes

ViewTreeRenderer.disposeMounted(container, mounted)
```

已挂载节点列表是所有权令牌，不是可有可无的缓存。host 必须把上一次成功帧返回的原始根节点
传回同一个容器和渲染器。具有稳定 key 的同级节点在重排后仍可保留原生 View；无 key 节点
只会在相同 index 和类型下复用，防止平台状态在外观相似的 item 之间静默转移。

渲染在结构变更阶段具有事务性。差分、View 创建或绑定失败时，流水线恢复上一棵 View 树并
重新抛出错误。Android View 生命周期回调和延迟释放在结构提交后执行；由于新的可见树此时
不能安全回滚，其失败会被隔离在 `RenderTreeResult.commitFailures` 中。

## 主要 API

- [`ViewTreeRenderer`](https://docs.viewcompose.com/api/viewcompose-renderer/0.1.0-alpha01/viewcompose-renderer/com.viewcompose.renderer.view.tree/-view-tree-renderer/)
  管理 VNode 到 View 的事务渲染与释放边界。
- [`ChildReconciler`](https://docs.viewcompose.com/api/viewcompose-renderer/0.1.0-alpha01/viewcompose-renderer/com.viewcompose.renderer.reconcile/-child-reconciler/)
  在不修改平台状态的前提下生成插入、复用和移除计划。
- [`LazyListDiff`](https://docs.viewcompose.com/api/viewcompose-renderer/0.1.0-alpha01/viewcompose-renderer/com.viewcompose.renderer.reconcile/-lazy-list-diff/)
  把稳定 Lazy item key 转换成有序 RecyclerView 更新；身份缺失或有歧义时会主动退化为全量刷新。
- `RenderTreeResult`、`RenderStats`、`RenderStructureStats`、patch 记录和布局过程采样提供不可变
  诊断数据，供 demo、预览工具和性能测试使用。
- [`AndroidViewDecorationBackend`](https://docs.viewcompose.com/api/viewcompose-renderer/0.1.0-alpha01/viewcompose-renderer/com.viewcompose.renderer.decoration/-android-view-decoration-backend/)
  是高级阴影等普通 View 状态无法表达的效果的可选 SPI。没有后端时，装饰请求走空操作路径，
  也不会加载阴影实现。
- `ViewDecorationHostLayout` 和 `DecorationChildDrawingOrder` 支持自定义绘制平面与声明式
  `zIndex`，无需为每个 child 再包一层 View。
- `ViewNodeToolingRegistry` 仅在工具元数据存在时，以弱引用方式关联 View 与源码信息；普通
  渲染不会额外持有源码对象。
- 图片节点在存在 loader 时，会把 `UiImageRequest` 绑定到注入的 `UiImageLoader`。渲染器把可
  释放句柄存放在挂载的 `ImageView` 上；等价 request 会保留已有句柄和已加载 drawable。Request
  变化时，渲染器先释放旧工作，再应用 placeholder 并启动替换工作；移除、回滚和 Session 释放
  时也会清理句柄。Request 会携带节点捕获的密度，使适配器解析固定 `UiDp` 解码边界时与布局
  保持一致。没有适配器时 Resource source 仍可直接渲染；空 source 直接绑定 fallback。即使支持
  装饰效果的布局 host 会允许子 View 越界绘制阴影等效果，图片内容仍始终裁剪在 `ImageView`
  的 padding 边界内。

完整生成参考位于
[`viewcompose-renderer` API 树](https://docs.viewcompose.com/api/viewcompose-renderer/current/)。
当前版本仍为 alpha，因此文档站不会提供稳定的 `latest` 别名。

## 身份与 patch 规则

- 有 key 的 child 只有在 key 与 `NodeType` 同时一致时才能复用上一轮 payload。同级 key 必须
  稳定且唯一。
- 无 key 的 child 只复用相同 index 和类型上的 payload。因此，无 key 的有状态内容重排属于
  语义替换，而不是 move。
- Lazy 列表精确差分还要求每个 item 都有唯一且非空的 key。key 缺失或重复时使用
  `ReloadAll`，保护 RecyclerView holder 状态。
- 只要影响输出的捕获值发生变化，Lazy item 的 `contentToken` 就必须变化。即使 item 语义未变，
  session 回调也会从 next 列表中的原始 item 实例刷新。
- 定向 patch 和子树跳过只是优化。自定义 host 不得从 patch 记录或诊断计数推断业务状态。

## Android host 与线程规则

- 渲染、释放、View 绑定、Pager 更新和装饰回调都限制在 UI 线程。
- 一个容器只有一个已挂载树所有者。不得在容器或 render session 之间共享 mounted node。
- `collectDiagnostics = false` 会省略结构、patch、warning 和详细绑定快照；性能敏感且不消费
  诊断的路径应关闭它。
- `LayoutPassTracker` 是进程级可选能力。它会为受监控过程增加单调时钟读取和同步聚合开销，
  应用于有限时间的诊断，而不是持续生产遥测。
- `AndroidViewDecorationRuntime.install` 是进程级操作。应在应用初始化时安装后端；现有 View
  只有在下一次绑定装饰请求时才会切换。
- 装饰 host 不增加 per-child wrapper。无装饰的常见路径只经过一次分支就委托给普通 View
  绘制；有装饰的 child 仅为实际申请的绘制平面执行索引后的后端分发。
- `Row` 与 `Column` 会把直接子级的 Animated Visibility Host 视为渐进式间距参与者。主轴 Item
  间距随 Host 的测量尺寸 Channel 一起展开和收起；中间 Host 完全折叠时，稳定同级元素之间原有
  的间距仍会保留。
- 可见性进入稳定隐藏态后，空 Host 仍会作为零尺寸的调和身份锚点挂载。其内容子树已经移除，
  但稳定的 Host 会让后续无 Key 同级元素在可见性切换间保留原生 View 身份与交互状态。

## 相关文档

- [当前架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [VNode 与渲染器注册](https://docs.viewcompose.com/zh-CN/architecture/node-spec)
- [渲染失败与提交语义](https://docs.viewcompose.com/zh-CN/architecture/render-failures)
- [Lazy 容器指南](https://docs.viewcompose.com/zh-CN/guides/lazy-collections)
- [阴影与装饰指南](https://docs.viewcompose.com/zh-CN/guides/shadows)
- [图片加载指南](https://docs.viewcompose.com/zh-CN/guides/image-loading)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

## 兼容性说明

`0.1.0-alpha01` 建立了第一版公开的差分、原生绑定、诊断、工具关联和装饰后端契约。不要把
mounted node、patch 记录、诊断树对象、不透明 Lazy content token 或 View tag 作为外部长久
数据持久化。即使应用 DSL 源码仍能编译，自定义 host 和装饰后端也必须随渲染器契约变化升级。
