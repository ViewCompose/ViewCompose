---
translation_source: modules/viewcompose-renderer-android/README.md
translation_source_hash: 1ecd99609d7b684b6b7fd5d01bf29953e2fcf187e4c84a37754d07c423e175b4
translation_status: current
---

# Android Renderer Engine 模块

`viewcompose-renderer-android` 是 ViewCompose 的 Android View 渲染引擎。它把不可变 VNode 快照与已
挂载树进行差分，创建并绑定原生 View，应用定向 patch，驱动 Lazy 容器和 Pager 状态，桥接
shape 与绘图命令，并提供渲染工作量、树结构、布局过程和源码工具链诊断。

应用通常通过 `viewcompose-android` 间接获得本模块。实现自定义 Android host、渲染器
诊断、平台装饰后端，或在脱离组件 DSL 的情况下测试差分逻辑时，可以直接依赖它。

本模块不负责 composition、应用生命周期、状态保存、导航、浮层窗口、图片解码或高级阴影的
具体光栅化。这些职责分别属于 `viewcompose-runtime`、`viewcompose-ui-foundation`、
`viewcompose-host-android` 和可选功能模块。

## 构件与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-renderer-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。渲染扩展契约和诊断模型在 alpha 版本间可能变化。
- 平台：Android library，`minSdk 24`、`compileSdk 36`，Java 11 字节码。
- UI Contract 会被传递暴露，因为 Renderer 入口会接收并返回其 Node 与 Modifier 类型。
  Runtime、Text Core、Graphics Core 和 Gesture Core 保持为实现依赖。
- Android 运行时依赖：AndroidX Core、AppCompat、RecyclerView、ViewPager2、
  ConstraintLayout 与 SwipeRefreshLayout；不依赖 Material Components。
- 通用 Surface、圆角/切角/连续圆角和进度指示器使用引擎自有 Android 绘制实现，并只消费节点解析值。
- `SurfaceNodeProps` 使用同一份缓存的 `UiShapeDrawable` 几何来完成纯色或渐变 Fill、Border、
  Ripple Mask、Outline 与可选裁剪。连续圆角使用凸三次曲线路径；稳定绘制不会逐帧分配 Path、
  Shader、Drawable 或集合。
- 引擎自有圆角使用圆弧绘制。Shape 边框会沿向内偏移半个线宽的路径居中绘制，保证轮廓完整落在
  逻辑 Drawable 边界内，包括组件在较大触控目标中居中较短可见 Surface 的情况。
- Button 可以请求比有效 View 触控目标更短的可见 Surface。引擎会在 View 内居中其背景、边框、
  涟漪和轮廓，同时不改变测量、命中测试或无障碍边界。显式 Background、Border、Corner Radius
  或 Shape Modifier 会关闭组件提供的内缩，保证应用样式优先。
- Button、IconButton、交互式 Box/Row 组合控件与 SegmentedControl 状态层使用 NodeSpec 中
  已解析的 `UiStateLayerColors`。引擎在现有 Shape 遮罩和可见 Surface 内缩中应用启用态的
  按下、聚焦和悬停选择器，不选择语义角色或 Material 透明度值。
- 通用集合语义会映射为 AndroidX 无障碍集合元数据。父节点负责行列数量和选择基数，子节点负责
  逻辑位置和跨度；已有的 `selected` 与 `heading` 语义仍是 item 状态的唯一事实来源。
- 当前版本构建基线：Kotlin 2.0.21、Android Gradle Plugin 8.13.2。

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

- [`ViewTreeRenderer`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.view.tree/-view-tree-renderer/)
  管理 VNode 到 View 的事务渲染与释放边界。
- [`ChildReconciler`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-child-reconciler/)
  在不修改平台状态的前提下生成插入、复用和移除计划。
- [`LazyListDiff`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.reconcile/-lazy-list-diff/)
  把稳定 Lazy item key 转换成有序 RecyclerView 更新；身份缺失或有歧义时会主动退化为全量刷新。
- `RenderTreeResult`、`RenderStats`、`RenderStructureStats`、patch 记录和布局过程采样提供不可变
  诊断数据，供 demo、预览工具和性能测试使用。
- [`AndroidViewDecorationBackend`](https://docs.viewcompose.com/api/viewcompose-renderer-android/0.1.0-alpha01/viewcompose-renderer-android/com.viewcompose.renderer.decoration/-android-view-decoration-backend/)
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
[`viewcompose-renderer-android` API 树](https://docs.viewcompose.com/api/viewcompose-renderer-android/current/)。
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
- Gesture 分发会保留尚未判定的 Pointer Stream，直到识别出 Drag。若 Stream 结束时没有被 Gesture
  消费，保留目标会收到一次普通 Click；已识别的 Drag 会消费 Stream 并抑制该 Click。
- Button Surface 内缩变化会参与定向样式 Patch，不得因此重建原生 View 或改变其有效测量目标。
- Basic Surface 使用相同的有效/可见边界模型。Surface 快照变化会对保留的
  `DeclarativeBoxLayout` 执行中立重绑定；调用方 Background、Border 或 Shape Modifier 会移除
  组件提供的可见内缩，并占满有效边界。
- 引擎创建的 Box 与 Surface 容器不执行 XML 属性解析。没有显式 `BoxScope.align` 的子项会在
  LayoutParams 中保留继承内容对齐标记，因此内容对齐 Patch 只更新这些子项，不再在每次布局时
  扫描全部子项；显式对齐的子项保持不变。
- Button 与 IconButton 状态层变化参与定向样式 Patch，只重建 Surface Drawable。交互式
  Box/Row 变化会重新执行现有样式绑定；SegmentedControl 只重建选中角色发生变化的分段背景。
  按下优先于聚焦和悬停，聚焦优先于悬停；多状态路径的非活动态或禁用态保持透明。多状态契约
  为空时，原有单值 Ripple 选择器保持不变。
- Slider 绑定使用渲染器中性的 `AppCompatSeekBar` 子类，因为平台控件可能在 `AT_MOST` 测量
  规格下忽略 `minimumHeight`。它会遵守已声明的最小值，同时让应用或父容器的精确高度保持
  最终权限；Android Renderer 不解释任何 Material 策略或 Token。
- 原生 Switch 与 Slider 绑定通过 `SRC_IN` 应用每个已解析 Tint，从而保留平台或 OEM Drawable
  遮罩。Slider 分别持有激活轨道、非激活轨道和 Thumb Tint，定向 Patch 可在不重建 View 的
  情况下更新非激活轨道。当受控 Callback 接受原生 Switch 已提交的值时，定向 Patch 不会再次
  写入相同值，因此平台或 OEM 的 Thumb Transition 可以继续执行。在独立且经过测试的自定义控件
  契约被接受前，平台 Drawable 几何及其内建覆盖率仍具有最终权限。
- 集合行列索引是从零开始的逻辑位置。Android 在 RTL 中反向排列后代时，Renderer 不得反转这些
  索引。选中态和标题态读取 item 已有的语义字段，防止组件通过重复契约暴露相互矛盾的无障碍状态。

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

`0.1.0-alpha01` 建立重命名后不依赖 Material 的 Android Renderer 坐标，并继续承载差分、
原生绑定、诊断、工具关联和装饰后端契约。不要把
mounted node、patch 记录、诊断树对象、不透明 Lazy content token 或 View tag 作为外部长久
数据持久化。即使应用 DSL 源码仍能编译，自定义 host 和装饰后端也必须随渲染器契约变化升级。

Renderer 的多状态路径实现通用 UI Contract，并非 Material 功能。采用 `UiStateLayerColors` 的
自定义 Renderer 必须保留启用态优先级与透明非活动态；收到空值的 Renderer 可以继续使用其已有
单色兼容路径。

消费集合语义的自定义 Renderer 必须保留逻辑行列顺序，并把 item 跨度、选中态和标题态映射为
等价的平台无障碍元数据。alpha 阶段尚未识别这些可空集合字段的 Renderer 可以忽略它们，但其
无障碍输出将无法播报集合位置。
