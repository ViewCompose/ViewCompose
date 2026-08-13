---
translation_source: guides/lazy-collections.md
translation_source_hash: f8c6e5120f55bbb82745898715d76c2fe02488e73547fac4193cf0f276555f1d
translation_status: current
---

# ViewCompose 延迟集合

## 1. 范围

延迟集合继续使用 Android `RecyclerView` 和布局管理器承载滚动、回收、焦点、无障碍、嵌套滚动、
fling 与边缘效果。ViewCompose 拥有平台无关的 item 模型、可观察状态、保存恢复锚点和渲染映射。

支持 `LazyColumn`、`LazyRow` 和 `LazyVerticalGrid`；Pager 状态仍是独立的页面模型。

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
        initialPrefetchItemCount = 4,
        itemViewCacheSize = 4,
    ),
) {
    stickyHeader(
        key = "contacts-header",
        contentType = "header",
    ) {
        Text("Contacts")
    }

    items(
        items = contacts,
        key = { contact -> contact.id },
        contentType = { "contact-row" },
    ) { contact ->
        ContactRow(contact)
    }
}
```

列表 scope 支持 `item`、`items` 和 `stickyHeader`。网格 scope 还支持逐 item span；网格
sticky header 占满整行。均质数据便捷重载同样要求稳定 key，并委托给结构化模型。

## 4. 渲染器映射

| 契约 | Android 映射 |
| --- | --- |
| 稳定 key | adapter 内无冲突的 stable ID |
| content type | RecyclerView view type / 回收池分区 |
| item span | `GridLayoutManager.SpanSizeLookup` |
| sticky header | 与列表分离、由 Session 承载的 pinned holder，并支持下一 header 推离 |
| pinned header 指针输入 | 坐标变换后分发给 pinned holder |
| 非对称 content padding | RecyclerView 相对 padding |
| reverse layout | `LinearLayoutManager/GridLayoutManager.reverseLayout` |
| 用户滚动开关 | 触摸拦截门；程序滚动仍可用 |
| 初始预取数量 | layout manager 初始预取 |
| item 缓存大小 | RecyclerView item-view cache |
| 布局状态 | scroll、layout 和 adapter observer 推送给 `LazyListState` |

Detach 且从未展示的 Holder 可以借助 RecyclerView Prefetch 组合并构建 Android View 树。这只是
Prepared Candidate，不是已提交子帧。Remember 激活、`SideEffect`、`DisposableEffect`、
`LaunchedEffect`、原生 `AndroidView.onCommit`、Overlay 与已提交诊断都会等到首次 Attach。
如果被观察 State 在 Attach 前变化，过期候选会被放弃，Activate 会渲染当前状态。已经激活的
Session 在普通 RecyclerView Cache Detach 期间继续保持 Active，并在 Holder Recycle 或 Container
释放时 Dispose。

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

1. 容器内 key 非空且唯一，并在重排时持续标识同一逻辑 item。
2. `contentType` 只能分组布局兼容的 item 结构。
3. 平台回调发布不可变 snapshot；Android 类型不得进入 ui-contract。
4. 对同一 RecyclerView connector 的重新绑定不得重置滚动锚点。
5. 保存恢复只持久化首个可见 index 与偏移。
6. holder、pinned header 或容器释放时必须销毁对应 item Session。
7. 集合、Modifier 与 Insets 的 Padding 贡献由 Renderer 合成为唯一原生值，并在定向 Patch 与
   完整环境重绑期间保持稳定。
8. Item Saveable State 按容器与稳定逻辑 Key 划分 Scope；重复 Provider 只在同一逻辑 Item Scope
   内被拒绝。
9. Prefetch Prepare 对外静默，不会把子 Submission 标记为 Committed；Activate 与后续 Active
   Render 保持正常事务式 Effect 顺序。

## 6. 明确不包含的能力

Paging 3 adapter、远程加载/重试策略、自定义 fling 物理和编译器驱动的 item 内组合属于独立集成。
Paging 库可以驱动不可变列表，并读取 `isAtEnd`、`lastVisibleItemIndex` 和
`layoutInfo.totalItemsCount`，无需把 Android paging 类型耦合进核心契约。
