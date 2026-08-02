---
translation_source: modules/viewcompose-renderer/README.md
translation_source_hash: 9e633b984be513996df9e8719dfb9fcf11b149f1cde9eb1eda9474bb941015eb
translation_status: current
---

# Renderer

`viewcompose-renderer` 是 ViewCompose 的 Android View 渲染引擎。它把不可变 VNode 快照与已
挂载树进行差分，创建并绑定原生 View，应用定向 patch，驱动 Lazy 容器和 Pager 状态，桥接
shape 与绘图命令，并提供渲染工作量、树结构、布局过程和源码工具链诊断。

应用通常通过 `viewcompose-host-android` 间接获得本模块。实现自定义 Android host、渲染器
诊断、平台装饰后端，或在脱离组件 DSL 的情况下测试差分逻辑时，可以直接依赖它。

本模块不负责 composition、应用生命周期、状态保存、导航、浮层窗口、图片加载或高级阴影的
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
- 直接 ViewCompose 依赖：runtime、text core、UI contract、graphics core 和 gesture core。
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

## 相关文档

- [当前架构与模块边界](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [VNode 与渲染器注册](https://docs.viewcompose.com/zh-CN/architecture/node-spec)
- [渲染失败与提交语义](https://docs.viewcompose.com/zh-CN/architecture/render-failures)
- [Lazy 容器指南](https://docs.viewcompose.com/zh-CN/guides/lazy-collections)
- [阴影与装饰指南](https://docs.viewcompose.com/zh-CN/guides/shadows)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

## 兼容性说明

`0.1.0-alpha01` 建立了第一版公开的差分、原生绑定、诊断、工具关联和装饰后端契约。不要把
mounted node、patch 记录、诊断树对象、不透明 Lazy content token 或 View tag 作为外部长久
数据持久化。即使应用 DSL 源码仍能编译，自定义 host 和装饰后端也必须随渲染器契约变化升级。
