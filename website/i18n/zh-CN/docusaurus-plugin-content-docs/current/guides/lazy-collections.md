---
translation_source: guides/lazy-collections.md
translation_source_hash: 97fb77af37aff7bd83e9de5f9b85eb80477a2206b44fa66bca17303d7739b9ae
translation_status: current
---

# 选择并控制 Lazy 集合

先按交互方式选择原生后端容器，再提供稳定的逻辑身份。Session、回收和 Renderer 不变量见
[Lazy 集合架构](../architecture/lazy-collections.md)。

| 任务 | 容器 |
| --- | --- |
| 垂直滚动的行或吸顶分区 | `LazyColumn` |
| 水平滚动的 Chip 或卡片 | `LazyRow` |
| 固定列或按宽度自适应的单元格 | `LazyVerticalGrid` |
| 离散的水平页面，通常与 Tab 配合 | `HorizontalPager` |
| 离散的全高垂直页面 | `VerticalPager` |

## 观察并控制列表

为逻辑容器保留一个 `LazyListState`。它的可观察属性会更新组合；它的命令作用于当前挂载的原生
列表。动画命令在脱离挂载时不会执行，而 `scrollToItem` 还会保留请求的锚点。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyCollectionsGuideSamples.kt" region="lazy-collections-state" sample_id="guide.lazy-collections-state" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
data class InboxMessage(
    val id: Long,
    val subject: String,
    val version: Int,
)

fun UiTreeBuilder.MessageList(messages: List<InboxMessage>) {
    val listState = rememberLazyListState()

    Column {
        Text(
            "visible=${listState.firstVisibleItemIndex}..${listState.lastVisibleItemIndex}",
        )
        Button(
            text = "Go to latest",
            enabled = messages.isNotEmpty(),
            onClick = { listState.animateScrollToItem(messages.lastIndex) },
        )
        LazyColumn(
            items = messages,
            key = InboxMessage::id,
            contentType = { "message" },
            contentRevision = InboxMessage::version,
            state = listState,
            spacing = 8.dp,
        ) { message ->
            Text(message.subject, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

Key 是持久的消息身份。只有显示所依赖的普通数据变化时，Revision 才变化。`contentType` 是由兼容
Holder 共享的少量结构类别，不是消息 ID。

宿主重建时会保存首个可见 Index 和像素 Offset。可见几何、方向和正在滚动状态属于当前原生布局，
挂载后会重新观察。

## 构建自适应网格

产品设计要求精确列数时使用 `GridCells.Fixed`；单元格只规定最小宽度、由可用宽度决定列数时使用
`GridCells.Adaptive`。全行标题会跟随列数变化，而不会取得新的逻辑身份。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyCollectionsGuideSamples.kt" region="lazy-collections-grid" sample_id="guide.lazy-collections-grid" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
data class GalleryCard(
    val id: Long,
    val title: String,
)

fun UiTreeBuilder.AdaptiveGallery(cards: List<GalleryCard>) {
    LazyVerticalGrid(
        cells = GridCells.Adaptive(minSize = 120.dp),
        horizontalSpacing = 12.dp,
        verticalSpacing = 12.dp,
    ) {
        item(
            key = "gallery-heading",
            contentRevision = StaticContentRevision,
            span = GridItemSpan.FullLine,
        ) {
            Text("Gallery")
        }
        items(
            items = cards,
            key = GalleryCard::id,
            contentType = { "gallery-card" },
            span = { GridItemSpan.Single },
        ) { card ->
            Text(card.title)
        }
    }
}
```

批量声明默认以每个不可变 `GalleryCard` 作为 Revision；它的相等性必须覆盖 Item 渲染所读取的全部
普通值。否则应提供显式 Selector。单个静态标题没有捕获会变化的普通值，因此使用
`StaticContentRevision`。

## 协调 Pager 与 Tab

Pager 选择状态由应用控制。`PagerState` 观察原生 Motion 并发送翻页命令；同时传给 `TabRow` 后，
Indicator 也能跟随拖动进度。页面会独立挂载，并且必须只发出一个根节点。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyCollectionsGuideSamples.kt" region="lazy-collections-pager" sample_id="guide.lazy-collections-pager" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.PagerWithTabs() {
    val titles = listOf("Overview", "Activity", "Settings")
    val selectedPage = remember { mutableStateOf(0) }
    val pagerState = remember { PagerState() }

    Column {
        TabRow(
            selectedIndex = selectedPage.value,
            onTabSelected = { page ->
                selectedPage.value = page
                pagerState.animateScrollToPage(page)
            },
            pagerState = pagerState,
        ) {
            titles.forEach { title ->
                Tab(key = title, contentRevision = title) {
                    Text(title)
                }
            }
        }
        HorizontalPager(
            currentPage = selectedPage.value,
            onPageChanged = { page -> selectedPage.value = page },
            pagerState = pagerState,
        ) {
            titles.forEach { title ->
                Page(key = title, contentRevision = title) {
                    Text(title)
                }
            }
        }
    }
}
```

只有不同页面完成 Settled 后，`onPageChanged` 才运行。`VerticalPager` 使用相同的 Keyed `Page`
契约。内容可能被 IME 遮挡时，在页面内部提供垂直 Scroll Owner，因为 Pager 只拥有离散翻页，
不负责表单滚动。

## 选择提交与调优路径

- 默认使用普通不可变 `List`。每次父级组合都会运行 Selector，随后复用未变化的 Keyed Session。
- 只有测量确认 Selector/Key 扫描是问题，并且应用确实拥有不可变快照边界时，才使用
  `remember(dataVersion) { source.toLazyItemsSnapshot() }`。结构、保留数据、Selector Capture 或
  普通 Content Capture 变化时必须替换快照。
- 当 State 支持的不可变 Snapshot 频繁变化、外围屏幕仍保持结构稳定时，使用 Observed
  `LazyColumn` Overload。Item Content 会收到稳定 Key 与 `ObservedValue<T>`；叶子 Property 使用
  `map`，条件结构则留在普通 Composition 或显式 Revision Declaration 中。
- 产品需要加载、失效、重试、刷新、Placeholder 或丢页策略时，使用
  [`viewcompose-paging-androidx`](../modules/viewcompose-paging-androidx/README.md)，不要在 UI 层重建
  这些策略。
- 只在同一 Release 构建和设备上完成 Profile 后才使用 Prefetch 与 Reuse Policy。然后继续阅读
  [性能教程](../tutorials/lazy-list-performance.md)。

## 验证结果

插入并移动带 Key 的 Item，确认局部状态跟随 Key。调整自适应网格尺寸或旋转屏幕，确认全行内容
仍占满一行。双向滑动页面并点击 Tab，确认受控选择、Settled 回调和 Indicator 进度一致。最后在
真实 Android 页面上验证 TalkBack 和键盘焦点；仅编译样例不能证明平台无障碍或焦点一致性。
