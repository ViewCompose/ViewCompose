---
translation_source: guides/lazy-collections.md
translation_source_hash: cc4d7429f7c0805eb4803178290bb56a151476dd109212077096e8ee2583c428
translation_status: current
---

# ViewCompose 延迟集合

## 1. 范围

延迟集合继续使用 Android `RecyclerView` 和布局管理器承载滚动、回收、焦点、无障碍、嵌套滚动、
fling 与边缘效果。ViewCompose 拥有平台无关的 item 模型、可观察状态、保存恢复锚点和渲染映射。

支持 `LazyColumn`、`LazyRow` 和 `LazyVerticalGrid`；Pager 状态仍是独立的页面模型，Eager
`ScrollableColumn`/`ScrollableRow` 则使用 `ScrollState`。

## 2. 可观察状态

`LazyListState` 基于 snapshot state。在组合期间读取属性会自动登记重组依赖。

```kotlin
val state = rememberLazyListState()

Text(
    "visible=${state.layoutInfo.visibleItemsInfo.map { it.index }} " +
        "scrolling=${state.isScrollInProgress}",
)

Button(
    text = "Go to 20",
    onClick = { state.animateScrollToItem(20) },
)
```

状态提供：

- 首个可见 item 的 index、key 和像素偏移，以及最后可见 item index；
- 可见 item 的 key、content type、偏移、尺寸和网格 span；
- viewport、content padding、间距、方向和 reverse layout；
- item 总数、滚动状态和最近滚动方向；
- 前后滚动能力、起止边界；
- 立即滚动、动画滚动和停止滚动命令。

宿主重建时只保存持久的 index 与像素偏移。可见几何、正在滚动状态和方向标记属于当前布局 Session。

## 3. 结构化 item DSL

所有集合 item helper 都要求稳定 key。重复 key 在构树时直接失败，不会静默关闭 keyed diff。

```kotlin
LazyColumn(
    state = state,
    contentPadding = LazyContentPadding.symmetric(
        horizontal = 16.dp,
        vertical = 8.dp,
    ),
    prefetchPolicy = LazyLayoutPrefetchPolicy(
        nestedInitialPrefetchItemCount = 4,
        itemViewCacheSize = 4,
    ),
    reusePolicy = CollectionReusePolicy(mountedTreeCacheSize = 2),
) {
    stickyHeader(
        key = "contacts-header",
        contentRevision = StaticContentRevision,
        contentType = "header",
    ) {
        Text("Contacts")
    }

    items(
        items = contacts,
        key = { contact -> contact.id },
        contentType = { "contact-row" },
        contentRevision = { contact -> contact.version },
    ) { contact ->
        ContactRow(contact)
    }
}
```

列表 Scope 支持 `item`、`items` 和 `stickyHeader`。网格 Scope 还支持逐 Item Span；网格 Sticky
Header 占满整行。`contentRevision` 是正确性契约而不只是性能提示。Item Content 捕获的变化值必须
是可观察 State，或进入该 Revision；Key 和 Revision 相等时完全跳过 Item Render。
因此，单条 `item`、`stickyHeader`、Pager `Page` 与 `Tab` Declaration 必须显式提供非空 Revision。
其签名顺序是 `key`、`contentRevision`，然后才是 `contentType`、网格 `span` 等可选物理复用或布局
策略。`null` 不是静态捷径；`StaticContentRevision` 是普通输入真正静态时使用的具名承诺。批量
`contentRevision: (T) -> Any? = { it }` Selector 仍可空，其默认值仅适用于 Equality 覆盖 Item
Content 所读取全部普通非 State 值的不可变值模型。

每个普通均质或 Scoped `List` Declaration 都会在父 Composition 的每一轮执行中遍历有序元素，并调用
`key`、`contentType`、`contentRevision` 和网格 Span Selector。ViewCompose 不会把 List 引用身份、
List Equality 或调用方提供的聚合 Token 当作整份 Declaration 未变化的证明：Kotlin `List` 可能存在
可变别名，而且没有编译器转换时，框架无法推断普通 Lambda Capture。

执行 Selector 并不等于每个 Item 都会再次 Render。求值完成后，如果 Key、Content Revision、捕获的
框架 Environment、Content Type、Kind 与 Span 都相等，框架会复用已提交的逻辑 Item 与 Session
Binding。因此 List 变化时仍可保留全部未受影响的 Item Session，而 `contentRevision` 变化只会定向
刷新受影响 Item。主题、资源、Locale、布局方向、密度、字体缩放与其他框架 Local 会自动进入
Environment Revision；Item Session 内读取的可观察 State 仍会独立跟踪。框架无法自动推断 Item
Content 读取的普通非 State 值，它仍必须进入该 Item 的 `contentRevision`。

批量 Typed 与强 Snapshot Declaration 使用一个 Declaration 共享的 Item Session Strategy。每个已
提交 Item 直接把源 Model 保存为不透明 Payload，不再各自分配 Factory、Updater 和捕获 Model 的
Content Closure。RecyclerView Holder Bind 会把选中 Item 直接交给该 Strategy，因此减少 Retained
Set 不会把 Callback 构造转移到滚动路径。这项存储优化不改变 Key、Revision、State 或 Effect
所有权。

### 显式整表快照快路

当应用已经持有不可变 List Snapshot，并且需要在稳定的父级重组中避免 Selector 与 Key 扫描时，
可以在该 State 边界创建 `LazyItemsSnapshot`：

```kotlin
val contactsSnapshot = remember(contacts) {
    contacts.toLazyItemsSnapshot()
}

LazyColumn(
    items = contactsSnapshot,
    key = { contact -> contact.id },
    contentType = { "contact-row" },
    contentRevision = { contact -> contact.version },
) { contact ->
    ContactRow(contact)
}
```

`toLazyItemsSnapshot()` 会按迭代顺序浅拷贝 Item 引用，并为结果分配不透明的框架 Identity；它不会
执行 Selector，也不会深拷贝 Item 模型。每个消费容器第一次在当前框架 Environment 中看到该
Identity 时才执行 Selector。容器保留当前和上一个成功提交的 Snapshot/Environment Pair。精确命中
时会以常量时间返回缓存的有序逻辑 Item List，不调用 Selector 或计算 Item Key Hash；因此在两个已
提交 Snapshot 间来回切换也保持快路。Environment 变化会有意 Cache Miss 并重新执行 Selector，
从而保证主题、资源、Locale、方向、Density 与其他 Local 正确。
只有 Item Content 在 Active Session 中执行时读取的 State 会独立观察。Selector 读取的 State 或其他
变化输入会固化在已求值 Snapshot 中，因此必须替换 Snapshot。如果 Selector 抛出异常或 Key 重复，
失败的 Declaration 不会发布已求值 Snapshot；Retry 会重新执行全部 Selector。

顺序、成员、保留的 Item 数据、Selector Capture 或 Item Content 捕获的普通非 State 值变化时，
应用必须替换 `LazyItemsSnapshot`。Item Content Capture 还必须进入受影响 Item 的
`contentRevision`；没有编译器转换时，框架无法推断这些值。每轮 Composition 都新建 Snapshot 在
正确性上没有问题，但会失去精确 Identity 快路。内容相等的新 Identity 仍会执行 Selector Pass，
随后可以规范复用未变化的 Keyed Item。该 Overload 只存在于顶层及 `ScrollableScope` 的均质
`LazyColumn`、`LazyRow` 与 `LazyVerticalGrid`；Scoped `LazyColumn { items(...) }` 和
`LazyVerticalGrid { items(...) }` 有意保留安全的逐轮契约，不提供 Snapshot Overload。

网格列使用 Sealed Policy，而不是 Android Span Count：

```kotlin
LazyVerticalGrid(cells = GridCells.Adaptive(minSize = 120.dp)) {
    item(
        key = "heading",
        contentRevision = StaticContentRevision,
        span = GridItemSpan.FullLine,
    ) {
        Text("Gallery")
    }
    items(items = cards, key = { card -> card.id }) { card ->
        CardRow(card)
    }
}
```

`GridCells.Fixed` 保留精确正列数。`GridCells.Adaptive` 根据可用内部宽度、最小 Cell 尺寸、间距与
密度推导至少一列。`GridItemSpan.Fixed` 会限制到当前列数，`FullLine` 会在尺寸变化后跟随当前
列数，`Fixed(1)` 会规范化为 `Single`。物理列数变化不会改变 Key、Revision、Session、Remember
状态或 Effect。

## 4. 渲染器映射

| 契约 | Android 映射 |
| --- | --- |
| 稳定 key | adapter 内无冲突的 stable ID |
| content type | 空 Holder 与 Reset Mounted Tree 的原生兼容分区 |
| content revision | 调用方所有的语义 Version，用于定向 Item 失效 |
| item span | `GridLayoutManager.SpanSizeLookup` |
| fixed/adaptive cells | 当前 `GridLayoutManager.spanCount`，更新时不替换 Adapter |
| sticky header | 与列表分离、由 Session 承载的 pinned holder，并支持下一 header 推离 |
| pinned header 指针输入 | 坐标变换后分发给 pinned holder |
| 非对称 content padding | RecyclerView 相对 padding |
| reverse layout | `LinearLayoutManager/GridLayoutManager.reverseLayout` |
| 用户滚动开关 | 触摸拦截门；程序滚动仍可用 |
| 嵌套初始预取数量 | 列表嵌套时使用的 LayoutManager Hint |
| item 缓存大小 | RecyclerView item-view cache |
| mounted-tree 缓存大小 | 框架所有、有界、确定性 Release 的 Reset Tree 缓存 |
| 布局状态 | scroll、layout 和 adapter observer 推送给 `LazyListState` |

Detach 且从未展示的 Holder 只有在 Renderer 已确认该 Content Type 的同步成本在预算内时，才会
借助 RecyclerView Prefetch 组合并构建 Android View 树。未知或昂贵 Type 只 Staging，不做原生
准备。这只是 Prepared Candidate，不是已提交子帧。Remember 激活、`SideEffect`、`DisposableEffect`、
`LaunchedEffect`、原生 `AndroidView.onCommit`、Overlay 与已提交诊断都会等到首次 Attach。
如果被观察 State 在 Attach 前变化，过期候选会被放弃，Activate 会渲染当前状态。已经激活的
Session 在普通 RecyclerView Cache Detach 期间继续保持 Active。Recycle 会结束其逻辑 Key
Session，兼容且已 Reset 的物理树随后可进入有界 Renderer 缓存；RecyclerView Pool 只收到空 Holder
外壳。

`AndroidView` 只有声明 `onReset` 才主动允许 Mounted Tree 跨 Key 复用。旧逻辑 Session 与 Effect
先 Dispose，再执行 Reset。缓存淘汰或最终 Container Dispose 恰好调用一次 `onRelease`。包含未声明
`onReset` 的互操作 View 的树会直接 Release。

pinned 副本不登记为第二个无障碍节点，普通列表 header 仍是语义源，避免 TalkBack 重复播报。

每个逻辑 Item Key 同时持有一个子 Saveable State Registry，因此兄弟 Row 可以重复使用 Item 内的
自动或显式 `rememberSaveable` Key。Holder Detach 或回收会保留逻辑 Item 的 Saved Map，重新
Attach 或重排时按 Item Key 恢复。分离的 Pinned Header 副本是不拥有持久化权的 Presentation
副本：它可以从 Owner 当前 Snapshot 初始化，但不能覆盖 Header 的持久化状态。

`contentPadding` 使用逻辑方向，并从集合捕获的布局方向解析 start/end。它会与物理
`Modifier.padding` 及选定的系统栏或 IME Insets 边相加。Renderer 会在
原生 View 复用与完整环境重绑期间保留这份合成 Padding，因此语言、方向、字体缩放、密度或资源
版本变化不会让内容短暂进入系统栏区域，也不会清除列表留白。

## 5. 不变量

1. 容器内 Key 非空且唯一。
2. 一个 Key 在重排期间持续标识同一逻辑 Item。
3. `contentType` 只能分组布局兼容的 Item 结构。
4. 单条 Item、Sticky Header、Page 或 Tab 必须在 `key` 后立即提供非空 `contentRevision`，再排列
   可选 `contentType` 与布局策略。`null` 不是静态哨兵；只有不存在变化的普通非 State 输入时才能用
   `StaticContentRevision`。批量可空 `{ it }` 默认值要求不可变值模型的 Equality 覆盖每个这类输入。
5. 每个普通 Typed `List` Declaration 都会在父 Composition 的每一轮执行中重新求值顺序、成员与 Item
   Selector；随后只有 Key、Content Revision、Environment、Content Type、Kind 与 Span 都相等时，
   才能复用已提交的逻辑 Item。
6. 精确的 `LazyItemsSnapshot` Identity/Environment Pair 可以绕过 Selector 与 Key 扫描；结构、
   保留数据、Selector Capture 或普通 Content Capture 变化时必须替换 Snapshot，Environment 变化
   始终重新执行 Selector。
7. 平台 Callback 发布不可变 Snapshot；Android 类型不得进入 `ui-contract`。
8. 对同一 RecyclerView Connector 的重新绑定不得重置滚动锚点。
9. 保存恢复只持久化首个可见 Index 与偏移。
10. Holder、Pinned Header 或容器释放时必须销毁对应 Item Session。
11. 集合、Modifier 与 Insets 的 Padding 贡献由 Renderer 合成为唯一原生值，并在定向 Patch 与
   完整环境重绑期间保持稳定。
12. Item Saveable State 按容器与稳定逻辑 Key 划分 Scope；重复 Provider 只在同一逻辑 Item Scope
   内被拒绝。
13. Prefetch Prepare 对外静默，不会把子 Submission 标记为 Committed；Activate 与后续 Active
   Render 保持正常事务式 Effect 顺序。
14. 逻辑 Key State 绝不进入 RecyclerView Pool 或 Mounted Tree 缓存；Reset 物理树不携带 Remember、
    Saveable、Subscription 或 Effect 标识。
15. Adaptive 列数变化只更新物理布局；不得重建 Keyed 逻辑 Item Session，也不属于应用持有的
    Content Revision。

## 6. 明确不包含的能力

Paging 3 adapter、远程加载/重试策略、自定义 fling 物理和编译器驱动的 item 内组合属于独立集成。
Paging 库可以驱动不可变列表，并读取 `isAtEnd`、`lastVisibleItemIndex` 和
`layoutInfo.totalItemsCount`，无需把 Android paging 类型耦合进核心契约。
