---
title: 迁移 Lazy 集合 Revision 与复用
translation_source: migration/lazy-collection-revision-and-reuse.md
translation_source_hash: 49c3c7909092bc4aa5969e2ed300b4a262e04889f983b551c93ca1e08774f302
translation_status: current
---

# 迁移 Lazy 集合 Revision 与复用

## 范围

本指南说明从依赖 Callback 的 `contentToken` 行为，硬切到显式逻辑条目 Revision 与独立物理呈现
复用。适用于 `LazyColumn`、`LazyRow`、`LazyVerticalGrid`、`HorizontalPager`、`VerticalPager`、
`TabRow`、自定义 `LazyListItemSession`，以及包含 `AndroidView` 的 Lazy Item。

## 用语义 Revision 替换 Content Token

把 Item 与 Page 的 `contentToken` 参数改为 `contentRevision`。它不再是宽松提示：Key、Content
Revision 和框架 Environment Revision 相等时，条目会完全跳过 Render。变化的普通 Kotlin 捕获值
必须进入 Revision。

```kotlin
LazyColumn(
    items = messages,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
) { message ->
    MessageRow(message)
}
```

批量 Item Overload 只有在不可变值模型的 Equality 覆盖 Item Content 所读取全部普通非 State 值时，
才能保留 `{ it }` 默认值。可变模型需要显式不可变 Version 或 Snapshot。Item Content 在 Active
Session 中读取的 ViewCompose `State` 已经可观察，无需重复放入 Revision。

单条 `item`、`stickyHeader`、Pager `Page` 与 `Tab` Declaration 不再把 Key 默认用作 Content
Revision，必须显式声明 Revision。只有 Declaration 没有会变化的普通非 State 输入时，才能使用
`StaticContentRevision`：

```kotlin
stickyHeader(
    key = "messages-header",
    contentRevision = StaticContentRevision,
) {
    Text("Messages")
}
```

Pager Page 现在暴露全部调用方快照字段：

```kotlin
Page(
    key = account.id,
    contentType = "account-page",
    contentRevision = account.version,
) {
    AccountPage(account)
}
```

Pager Page 与 Tab 现在都要求显式且唯一的 Key。位置是物理排布，不是逻辑标识，框架不再猜测同一
位置的无 Key Child 拥有旧 Child 的 Remember、Saveable State 或 Effect。

框架自动把主题、Android 资源、Locale、方向、Density、Font Scale 与其他 Active Local 捕获进
`environmentRevision`，应用无需在 `contentRevision` 中重复这些值。

## 用显式 Snapshot 值替换聚合 Token

Typed `LazyColumn`、`LazyRow`、`LazyVerticalGrid`、Scoped `items` 及其 `ScrollableScope`
Wrapper 不再接受调用方持有的聚合 Snapshot Revision。使用过中间版本 API 的调用应删除
`snapshotRevision`：

```kotlin
LazyColumn(
    items = messages,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
) { message ->
    MessageRow(message)
}
```

现在每次 Declaration Pass 都会求值 List 顺序与成员，并调用 `key`、`contentType`、
`contentRevision` 和网格 Span Selector。框架不会信任 List 身份、List Equality 或独立维护的
Version 来绕过这些校验，从而避免调用方忘记推进平行 Token 时产生过期顺序、成员或 Selector
结果；Scoped Declaration 也不再需要调用方定义 Token 命名空间。

执行 Selector 不会放弃 Keyed 复用。求值完成后，Key、Content Revision、框架 Environment、
Content Type、Item Kind 与 Span 都相等时，会复用已提交的逻辑 Item 与 Session Binding；变化的
Row 仍会定向刷新。Item Session 内读取的可观察 State 会独立跟踪。ViewCompose 没有能够识别任意
Kotlin Capture 的编译器转换，因此 Item Content 读取的每个变化普通非 State 值仍必须进入受影响
Item 的 `contentRevision`。针对中间版本聚合参数 Method Descriptor 编译的调用方必须为本次 Alpha
硬切重新编译。

对于顶层或 `ScrollableScope` 的均质容器，已经持有不可变 List Submission 的应用可以选择强类型
整表 Snapshot 快路：

```kotlin
val lazyMessages = remember(messages) {
    messages.toLazyItemsSnapshot()
}

LazyColumn(
    items = lazyMessages,
    key = { message -> message.id },
    contentType = { "message-row" },
    contentRevision = { message -> message.version },
) { message ->
    MessageRow(message)
}
```

`toLazyItemsSnapshot()` 会浅拷贝有序 Item 引用并创建新的不透明 Identity；它不接受或执行 Selector。
每个消费容器第一次在某个框架 Environment 中声明该 Identity 时执行 Selector，并保留当前和上一个
成功提交的 Snapshot/Environment Pair。精确 Pair 会以常量时间恢复有序逻辑 Item List，不执行
Selector 或 Key 扫描。新 Identity 或 Environment 变化会 Cache Miss，并走普通 Keyed
Canonicalization 路径。
只有 Item Content 在 Active Session 中执行时读取的 State 会独立观察。Selector 读取的 State 或其他
变化输入要求替换 Snapshot，因为精确命中会跳过 Selector。Selector 失败或 Key 重复不会发布已求值
Snapshot，因此用相同 Identity 与 Environment Retry 时会重新执行全部 Selector。

顺序、成员、保留的 Item 数据、Selector Capture 或普通非 State Item Content Capture 变化时，
必须替换 `LazyItemsSnapshot`。这些 Item Content Capture 还必须进入受影响的 `contentRevision`；
框架仍没有能够推断它们的编译器转换。每轮 Composition 都新建 Snapshot 仍然正确，但会失去
Identity 快路。Scoped `LazyColumn { items(...) }` 与 `LazyVerticalGrid { items(...) }` 有意不提供
`LazyItemsSnapshot` Overload，并继续在每轮 Declaration Pass 执行 Selector。

## 更新原生互操作复用

包含 `AndroidView` 的 Lazy Mounted Tree，只有所有互操作节点都声明 `onReset` 才能跨 Key。
Reset 只做可重放配置清理，一次性发布放在 `onCommit`，永久资源清理放在 `onRelease`。

```kotlin
AndroidView(
    factory = { context -> PlayerView(context) },
    update = { view -> bindPlayer(view as PlayerView, item) },
    onReset = { view -> resetPlayer(view as PlayerView) },
    onRelease = { view -> (view as PlayerView).release() },
)
```

旧逻辑 Session、Remember State、Subscription 与 Effect 会先结束，Renderer 才能把相同
`contentType` 的物理树交给另一个 Key。有界 Renderer 缓存会在淘汰时最终 Release；RecyclerView
只池化空 Holder 外壳。无法安全支持该生命周期时不要提供 `onReset`。

## 更新容器假设

- Pager `offscreenPageLimit` 默认使用 ViewPager2 原生 `-1` Policy；只有明确需要额外驻留页时才传
  至少 `1`。
- `TabRow` 是 Eager Keyed Parent Content，不再拥有 Lazy Child Session。稳定 Tab Key 在重排时保留
  Remember/Saveable Identity，选择变化只失效旧选中项与新选中项。
- `CollectionReusePolicy.mountedTreeCacheSize` 限制每个集合保留的 Reset 物理树；`0` 会关闭 Mounted
  Tree 缓存，但不改变逻辑正确性。
- `LazyLayoutPrefetchPolicy.nestedInitialPrefetchItemCount` 替换 `initialPrefetchItemCount`。未知或昂贵
  Type 不做同步原生准备。

## 更新自定义 Session 与 Renderer

自定义 `LazyListItemSession` 必须保持完整生命周期：可选且不对外发布的 `prepare`；首次呈现前一次
`activate`；只在内容或环境 Revision 改变时 `render`；通过 `disposeForReuse` 结束全部逻辑 Owner，
再返回 Reset 物理呈现；最终 `dispose` 与 `ReusableItemPresentation.release` 必须幂等。

`activate` 与 `render` 现在只有在已安装内容真正 Commit 时才返回 `true`。Rollback 后应返回
`false`，这样 Renderer 不会推进 Item Revision，并可重试同一 Submission。原生帧一旦 Commit，
后续 Side Effect 或诊断失败不会改变返回值。

`LazyListItem.sessionUpdater` 现在是必填项，必须把最新 Content Closure 或等价不可变输入安装到现有
Session。Key 与 Type 相同时，Revision 变化绝不允许用替换逻辑 Session 作为实现兜底。

Adopt 返回 `false`，或在所有权转移前抛出异常时，呈现会立即 Release。第一次跨 Owner Rebind
失败时不能调用旧逻辑 Owner 的 Update，也不能恢复其可见帧；被 Adopt 的树必须释放。

## 验证

运行仓库单测与文档门禁，再使用 Release 构建检查 Diagnostics Route。在 Theme、Renderer 与 Gaps
之间切换后，立即执行到底部并返回顶部的大力度长 Fling。确认相等 Revision 不 Render，变化
Revision 只更新目标 Key，旧 Effect 在原生 Reset 前释放，缓存淘汰只 Release 一次。

当前架构参见 [ADR-0012](../architecture/decisions/0012-lazy-collection-logical-and-physical-ownership.md)
与 [Lazy 集合指南](../guides/lazy-collections.md)。
