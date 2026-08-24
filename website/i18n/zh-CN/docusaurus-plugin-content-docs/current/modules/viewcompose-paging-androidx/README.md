---
translation_source: modules/viewcompose-paging-androidx/README.md
translation_source_hash: 056708acd433d078c23bf7bf2da3ee59eac7065a0e7f2ed0b90ac525ef08b6aa
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

确定性测试覆盖初始 Refresh、Append/Prepend Access、Retry、Refresh、Invalidation、最新 Query
替换、生命周期停止/恢复保留、释放、重复 Key、稳定 Key/索引安全 Access Routing、显式 Placeholder
启用、Placeholder 到 Loaded 替换、Placeholder Revision 失效、Page Drop、跳过 Revision 的 Reload
安全性，以及 Detached Cache 的立即释放且后续不会二次释放。Renderer 测试还证明一百万位置的直接
更新不会完整枚举 Table。Q3 Sample 只使用各模块公共 API 并参与编译。

2026-08-25，Android 13/API 33 的 Pixel 4 XL 使用本地确定性数据源和 48 dp Row，在 5.51 s
内通过两项聚焦真机测试。一百万位置的 Placeholder Presentation 相对同一已启动进程基线增加
48,124 KiB PSS，555 ms 跳至最后位置，在 `maxSize = 96` 下保留 81 个 Loaded Item，并释放初始
Item Session；顺序滚动 Page Window 最终保持配置的 96 个 Loaded Item，并释放初始可见 Session。
结论：该路径的紧凑内存、Jump/Drop 与所有权信心为 **improved**。限制：这只是一个 Debug Build、
一个设备/API、本地数据与一种 Row 几何，不是 Frame Benchmark，也未覆盖 RemoteMediator、网络错误、
Load State UI 或交互式 Demo；这些仍属于后续计划阶段。

## 相关文档

- [延迟集合指南](../../guides/lazy-collections.md)
- [Lifecycle AndroidX 模块](../viewcompose-lifecycle-androidx/README.md)
- [Paging 集成计划](https://docs.viewcompose.com/project/plans/paging3-integration)
- [源码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整生成参考位于
[`viewcompose-paging-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-paging-androidx/current/)。
