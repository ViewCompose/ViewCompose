---
translation_source: modules/viewcompose-paging-androidx/README.md
translation_source_hash: a817f8cba12f549b3ec038441c040cafcc400b20c82ca47ffd2846c09f7995a6
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

当前 Alpha Slice 要求禁用 Placeholder。出现未加载 Slot 时，`PagingLazyColumn` 会在发布候选前拒绝
该候选。当前实现为每个已加载 Item 构建一个 Declaration 和 Key Table Entry，因此 Composition 与
Reconciliation 成本与已加载 Item 数量线性相关。Placeholder、Page Drop 和紧凑索引表由下一阶段
负责；当前不会创建完整占位对象表，也不公开 Placeholder Key。

## 验证与当前范围

确定性测试覆盖初始 Refresh、Append/Prepend Access、Retry、Refresh、Invalidation、最新 Query
替换、生命周期停止/恢复保留、释放、重复 Key，以及稳定 Key/索引安全 Access Routing。Q3 Sample 只使用模块
公共 API 并参与编译。本 Slice 不声称已经完成真机性能、Placeholder/Drop、Mediator 专用 UI Helper
或交互式 Demo；这些属于后续计划阶段。

## 相关文档

- [延迟集合指南](../../guides/lazy-collections.md)
- [Lifecycle AndroidX 模块](../viewcompose-lifecycle-androidx/README.md)
- [Paging 集成计划](https://docs.viewcompose.com/project/plans/paging3-integration)
- [源码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整生成参考位于
[`viewcompose-paging-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/)。
