---
translation_source: modules/viewcompose-paging-androidx/README.md
translation_source_hash: 03d74272ba4dedbc83ed37d0c6110315bb397d5448d47985fe15807d93249265
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
class ContactsViewModel : ViewModel() {
    val pages = Pager(config, pagingSourceFactory = repository::contacts)
        .flow
        .cachedIn(viewModelScope)
}

val pagingItems = viewModel.pages.collectAsViewComposePagingItems()

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

`contentState` 是主内容投影，不是框架持有的 Layout。没有 Loaded Item 时，Combined、Source 或
Mediator Refresh 任一失败都会选择 `InitialError`；否则任一 Refresh 正在加载就选择
`InitialLoading`，只有全部 Refresh 都完成才选择 `Empty`。这样即使 AndroidX 的 Combined
Refresh 延后采用已安装的 Mediator，本地 Source 失败也不会被显示为空。存在 Loaded Item 后，
Refresh、Prepend、Append 加载或失败期间均由 `Content` 优先，方向性 UI 不会卸载 List：

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

生命周期重启遵循上游 Flow 契约。原始 `Pager.flow` 不支持重复收集；在 Flow 进入生命周期门控
Collector 前，应当只在应用持有的 Scope（例如 ViewModel）中执行一次 AndroidX `cachedIn`。隐藏
`Visible` 目标只会取消 UI Collector；恢复或重建时会重放缓存 Generation，不会重复上游加载。取消
应用 Scope 才会结束缓存。ViewCompose 不保存 `PagingData`、Page、Presenter 状态、数据库行或网络
响应。上游 Flow 异常进入 Render Session 的协程失败通道；取消不会转换为 Load Failure。

使用 `RemoteMediator` 时，应用数据库或等价存储仍是唯一真实来源：Mediator 写入数据并使其
`PagingSource` 失效。ViewCompose 只观察 AndroidX 的真实 Combined/Source/Mediator 状态，不持有
存储或网络工作。若 Mediator 跳过初始 Refresh，Combined Refresh 可能采用 Mediator 的
`NotLoading`，而 Source Refresh 已失败；`contentState` 会保留该 Source Failure，
`forLoadType(REFRESH)` 则暴露其准确来源。

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

## Demo 与确定性测试

在 Demo 目录中打开 `collection.paging`。真实的进程内 `Pager + PagingSource` 会暂停每次加载，
直到 `完成待处理加载` 应用 Data、Empty 或 Error；主操作随后变为 `请求下一页` 或
`重试失败加载`，`重置数据代` 则替换 Flow。无需 I/O 或发布生产 Fake 即可检查 Initial、Append、
Retry、Empty 与 Error 状态。

自动化应按 `collection.paging` 选择场景，并使用稳定的 `root`、`ready`、`primary_action`、
`secondary_action`、`reset`、`state`、`target` 与 `secondary_target` Role。State Target 报告
Body、Refresh/Append 和 Loaded Count。编译型 Q3 Sample 覆盖支持 Placeholder 的
`pagingLazyColumnSample` 与方向状态 `pagingLoadStateCompositionSample`。Repository 和
`PagingSource` 在 UI 之下测试，Snapshot 使用 AndroidX `paging-testing`，真机只验证渲染与交互。
Mediator Fixture 可使用 Fake Storage/Remote Result，但必须运行真实 AndroidX 协调，不能改用 UI
Boolean。

## 迁移

从 Compose Paging 迁移时，保留 `Pager`、Source/Mediator、Repository 与 ViewModel 持有的
`cachedIn` Flow。用 `collectAsViewComposePagingItems()` 替换 `collectAsLazyPagingItems()`，用
`PagingLazyColumn` 替换 Compose Item Count 循环，并提供稳定 Key 与完整 `contentRevision`。
`contentState` 映射主内容，`forLoadType(...)` 映射方向来源，`retry()`/`refresh()` 保持 AndroidX
语义。

有限列表继续使用 `LazyColumn`，除非需要 Paging Generation、Invalidation、Eviction、Jump、
Retry 或 Mediator。采用后把 Callback/End Reached 加载迁入 `PagingSource`，不得保留第二套
`isLoading`/`isAtEnd` 引擎。Placeholder 只使用显式 Overload；位置型 Loaded Key 和物化
Placeholder List 不受支持。

## 依赖与许可证声明

发布模块会暴露 `androidx.paging:paging-common:3.5.1`；测试还使用
`androidx.paging:paging-testing:3.5.1`。模块不要求 `paging-runtime` 或 `paging-compose`。
AndroidX Paging 使用 Apache License 2.0 分发，并已记录在 `THIRD_PARTY_NOTICES.md`。

## 验证与当前范围

确定性测试覆盖三种生命周期策略、隐藏/恢复导航、Composition 重建时的 `cachedIn` 重放、精确取消、
真实 `Pager + RemoteMediator` 的 Refresh/Append 失败、独立 Source 失败、Presenter Generation、
Placeholder、Page Drop 与 Detached Cache 释放。Q3 Sample 只使用公共 API 编译。Demo 还提供
受控的真实 `PagingSource` 路径，以及覆盖 Initial、Append、Empty、Error、Retry 与 Reset 的稳定
自动化 Role。

2026-08-25，Pixel 4 XL Android 13 的两项测试在一百万位置下增加 48,124 KiB PSS，555 ms 跳至
末尾，在 `maxSize = 96` 下保留 81 个 Item、释放 Drop Session，并以 96 个 Item 结束有界滚动。
另一项 13 s 的受控 Demo 测试与人工检查覆盖 Initial、10 行、Append Error、Retry 至 20 行、
Reset、Empty 与 Initial Error，保留内容易读且可滚动。结论：内存/所有权与 Demo 信心为
**improved**。限制是一台设备和确定性进程内 Storage/Remote 数据；真实 I/O、Prepend UI、更广
设备、Frame、Demo 内存与 Release 性能仍未得到证明。

## 相关文档

- [延迟集合指南](../../guides/lazy-collections.md)
- [Lifecycle AndroidX 模块](../viewcompose-lifecycle-androidx/README.md)
- [Paging 集成计划](https://docs.viewcompose.com/project/plans/paging3-integration)
- [源码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整生成参考位于
[`viewcompose-paging-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/)。
