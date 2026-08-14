---
title: 迁移 Lazy 集合 Revision 与复用
translation_source: migration/lazy-collection-revision-and-reuse.md
translation_source_hash: 291747f0cbfc08867afb76ac84b9c8342a81f147b46ff1524ab9980c461bbfea
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

不可变 Data Class 可以继续用默认条目值作为 Revision；可变模型需要显式不可变 Version 或 Snapshot。
由 ViewCompose `State` 承载的值已经可观察，无需重复放入 Revision。

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
