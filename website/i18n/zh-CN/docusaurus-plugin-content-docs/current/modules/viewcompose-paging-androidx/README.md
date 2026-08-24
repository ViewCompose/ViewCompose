---
translation_source: modules/viewcompose-paging-androidx/README.md
translation_source_hash: cac14a854d8e8f301df1de319ba320765543046d80579e489bed0471fadaf274
translation_status: current
---

# Paging AndroidX 集成

`viewcompose-paging-androidx` 把 AndroidX Paging 的 Generation 接入 ViewCompose 既有的
`LazyColumn` Renderer。AndroidX 仍是唯一的分页引擎；ViewCompose 负责一致的可观察 Presentation、
Item Session 标识、生命周期收集和原生 RecyclerView Reconciliation。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-paging-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。Collector、Items Owner 和 Container 是 Q3 引导型 API；
  `PagingLifecyclePolicy` 是 Q2，封闭条目为 Q1。
- 平台：Android 7.0（API 24）及以上。
- 依赖版本：AndroidX Paging 3.5.1 与 Kotlin Coroutines 1.10.2。
- 可选：任何聚合产物都不会自动包含本模块。
- `paging-common` 会暴露给 API，因为公共契约使用 `PagingData` 和 `CombinedLoadStates`；模块不会
  引入 `paging-runtime`、`paging-compose`、Paging Adapter 或第二个 Diff Owner。

## 基本用法

```kotlin
// `pages` 在进入 UI 前已由应用持有的 Scope 执行 `cachedIn`。
val pagingItems = repository.pages.collectAsViewComposePagingItems()

PagingLazyColumn(
    items = pagingItems,
    key = Contact::id,
    contentType = { "contact" },
    contentRevision = Contact::version,
) { contact ->
    ContactRow(contact)
}
```

当 `Pager` 启用 Placeholder 时，应选择显式 Overload，并独立设置 Placeholder 外观版本：

```kotlin
PagingLazyColumn(
    items = pagingItems,
    key = Contact::id,
    placeholderContentRevision = contactSkeletonVersion,
    placeholderContentType = "contact-placeholder",
    placeholderContent = { ContactPlaceholder() },
) { contact ->
    ContactRow(contact)
}
```

禁用 Placeholder 的 Overload 遇到未加载 Slot 会直接拒绝，让意外启用 Placeholder 的配置可见，
而不是静默渲染空 Item。

应用负责 `Pager`、`PagingSource`、可选的 `RemoteMediator`、存储、网络、Query、Cache 和
`cachedIn` Scope。集成层调用官方 `PagingDataPresenter`，不会重新实现加载、失效、Generation、
Retry、Refresh 或 Page Event。

## 一致状态与命令

`ViewComposePagingItems` 会原子发布 Item 与 `CombinedLoadStates` Snapshot。Page Event 会先更新
Presenter Store，但只有配套的最终 Load State 可用后才会对外可观察。因此 Composition 中的读取不会
把新 Item List 和上一轮 Load State Revision 混在一起。

`itemCount`、`loadedItemCount` 和 `loadStates` 都可观察。索引 `get` 会发送 Paging Access Hint；
`peek` 不触发加载，用于检查。`retry()` 重试当前 Generation 的失败加载，`refresh()` 请求 AndroidX
拥有的新 Generation。索引访问和命令都在 Android 主线程执行。收集调用离开 Composition 后，保留
引用仍可读取最终属性，但访问和命令会失败。

## 加载状态组合

`contentState` 是主内容投影，不是框架持有的 Layout。只有尚未加载任何 Item 时，它才返回
`InitialLoading`、`InitialError` 或 `Empty`。一旦存在已加载 Item，Refresh、Prepend、Append
加载或失败期间都由 `Content` 优先，方向性 UI 因此不会卸载 List：

```kotlin
when (val state = items.contentState) {
    PagingContentState.InitialLoading -> InitialLoading()
    is PagingContentState.InitialError -> InitialError(
        error = state.error,
        onRetry = items::retry,
    )
    PagingContentState.Empty -> EmptyResults()
    PagingContentState.Content -> key("contacts") {
        PagingLazyColumn(items = items, key = Contact::id) { contact -> ContactRow(contact) }
    }
}
```

要稳定组合 Header/Footer，可用 `loadStates.forLoadType(LoadType.PREPEND)` 或
`LoadType.APPEND` 一次选择某项操作，再在带 Key 的 List 前后渲染其 `combined` 状态。返回的
`PagingLoadStateSnapshot` 还会保留同一 AndroidX Snapshot 中的 `source` 与可空 `mediator` 状态，
即使 `combined` 选择一个可见状态，也可分别诊断 Source 和 Mediator。Helper 不发射 Node，也不决定
文案、分析、自动重试、离线或破坏性 Refresh 策略。失败加载使用 `retry()` 留在当前 Generation；
显式 `refresh()` 操作用于请求替换。

## 生命周期与上游所有权

默认 `Visible` 策略要求最近的 `LocalLifecycleOwner`，并在 `STARTED` 收集；`Retained` 在
`CREATED` 收集；`Composition` 面向自定义 Host 和测试 Fixture，不读取 Android 生命周期。生命周期
不活跃时保留最后一个一致 Presentation。Flow Identity 创建新的 Items Owner；只改变策略或不含
`Job` 的 Context 时，会在同一 Owner 上串行重启。离开 Composition 会取消收集并释放 Presenter
Listener。

生命周期重启遵循上游 Flow 契约。原始 `Pager.flow` 不支持第二次活跃收集；在 Flow 进入默认的生命周期
门控 Collector 前，应用必须在自己拥有的 Scope 中使用 AndroidX `cachedIn`。ViewCompose 不保存
`PagingData`、Page、Presenter 状态、数据库行或网络响应。上游 Flow 异常进入 Render Session 的协程
失败通道；Paging Load Failure 仍保留在 `CombinedLoadStates` 中。

## 标识、占位符与成本

已加载 Item 的 Key 必须稳定且唯一。`contentRevision` 必须覆盖 Item Content 捕获的每个变化普通值；
可观察 State 和框架 Environment 继续使用既有 Session 语义。Bridge 还会把当前 Presenter Index 折入
私有 Revision，因此未变化 Key 换位时会刷新 Access Routing，同时保留同一个 Key 持有的 Session 与
Saveable State。Paging Access Hint 只在 Item Session 激活时发送，不会在 Composition 扫描 Presented
List 时触发。RecyclerView、Android Renderer 与既有 Lazy List Policy 仍是滚动、Stable ID、复用、
Diff 和事务的唯一 Owner。

显式 Placeholder Overload 会构建紧凑索引 Table，其元数据与已加载 Item 数量成正比，而不是与
`itemCount` 成正比。未加载 Item 根据 Placeholder Count 在位置查询时计算；不会创建完整
Placeholder 对象表，也不公开 Placeholder Key。Placeholder Identity 是私有的、按位置生成，并按
Items Owner 与 Paging Generation 建立命名空间。Loaded Identity 会在另一个私有域中包装应用 Key，
因此两种域不会冲突。`placeholderContentRevision` 可以在不替换位置 Identity 的情况下让 Placeholder
外观失效；该位置变为 Loaded Item 时会终止 Placeholder Session。

标准 AndroidX Refresh、Prepend、Append 与 Page Drop Event 会转换为中立的有界 Range Update。
Renderer 若跳过中间 Table Revision，Bridge 会请求 `ReloadAll`，不会重放不安全 Event。被丢弃的
Loaded Key 会立即从 Table 消失，让 Renderer 释放 Key 持有的 Session、Effect 与 Saveable State。
该释放会在同一个已提交 Submission 中覆盖 Attached Holder 与 Detached Cache Holder。
非触发 Table 检查不会发送 Access Hint；Hint 由已提交 Child Session 的 `SideEffect` 在内容真正
激活后发送。未由 Paging Page Event 表示的 Theme/Local、Placeholder Revision/Type 或 Loaded
Selector 结果变化也会请求保守 Reload，确保安装新的 Declaration，同时不枚举 Placeholder。

## 验证与当前范围

确定性测试覆盖 Presenter Generation 与命令、Lifecycle/Release、Keyed Routing、Placeholder
替换与失效、Page Drop、跳过 Revision、主内容分支、按 `LoadType` 选择 Source/Mediator，以及
Detached Cache 释放且不会二次释放。Renderer 还覆盖一百万位置更新且不完整枚举；Q3 Sample
只使用公共 API 编译。

2026-08-25，Android 13/API 33 的 Pixel 4 XL 在 5.51 s 内通过两项 Debug 测试。一百万位置用例
增加 48,124 KiB PSS，555 ms 跳至最后位置，在 `maxSize = 96` 下保留 81 个 Loaded Item 并释放
初始 Session；有界滚动最终保持 96 个 Loaded Item。结论：内存、Jump/Drop 与所有权信心为
**improved**。该本地单设备/几何证据不代表 Frame、网络、真实 `RemoteMediator`、真机 Load State
UI 或 Demo；后续阶段继续负责这些路径。

## 相关文档

- [延迟集合指南](../../guides/lazy-collections.md)
- [Lifecycle AndroidX 模块](../viewcompose-lifecycle-androidx/README.md)
- [Paging 集成计划](https://docs.viewcompose.com/project/plans/paging3-integration)
- [源码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整生成参考位于
[`viewcompose-paging-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/)。
