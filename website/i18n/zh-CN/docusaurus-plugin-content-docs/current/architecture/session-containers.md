---
translation_source: architecture/session-containers.md
translation_source_hash: 4518a1c1048d47f6c35a188a830b75932f15314c69df55f3897bdcc836daabf3
translation_status: current
---

# 延迟 Session 容器检查清单

## 1. 文档定位

本文档追踪“延迟创建 + holder/session 复用”容器的稳定性风险。

这类容器的共性是：

1. 内容不会立即挂在父节点下
2. 内部有 holder/session 复用
3. 结构 diff 与可见内容刷新可能解耦

因此，它们是“结构不变但内容过期”问题的高风险区。

## 2. 当前已落地容器

1. `LazyColumn`
2. `LazyRow`
3. `LazyVerticalGrid`
4. `HorizontalPager`
5. `VerticalPager`
6. Navigation destination page（页面内容通过 `NavDestinationSession` 承载）

`TabRow` 被明确排除：数量少且常驻的 Tab 作为父组合中的普通 Eager Keyed Child 渲染。

## 3. 架构硬约束

每个延迟 session 容器都必须满足：

1. diff 为空时，不能回退到旧 item/page 实例
2. Key、Content Revision 与 Environment Revision 相等时，必须完全跳过 Item Session
3. `localSnapshot`、主题、环境、父层闭包在 update 路径重新注入
4. 延迟创建路径可以 Prepare 原生子树，但只有 Activate 或 Active Update 才能跨越子 Composition/
   Effect Commit 边界
5. `activate` 最多一次；后续 `render` 应用 Active Submission，`dispose/recycle` 语义与 Holder
   生命周期对齐
6. `Change` 更新优先走 payload 通道，避免无条件全量变更通知
7. key 不可用回退 `ReloadAll` 时，应尽量保持当前滚动锚点，避免交互后列表跳顶
8. 输入控件获取焦点时，不得触发无关的列表跳位。容器启用焦点跟随策略后，只能滚动到足以完整
   显示焦点编辑器的位置，并保持逻辑 Item 锚点不变
9. 一次父级集合提交对应一个单调递增的子 Session 修订。保留子项的更新只能由父渲染帧的
   commit effect 在 composition commit 之后发布；父帧回滚会直接丢弃更新，不得运行子 composition
   或 effect
10. Callback 对象身份不是 Revision。变化的普通捕获值必须成为 State 或进入
    `contentRevision`；仅 Callback 分配绝不刷新内容。单条 Item、Sticky Header、Page 与 Tab
    Declaration 必须在 `key` 后立即提供非空 Revision，再排列可选物理复用与布局参数；`null` 不是
    哨兵。`StaticContentRevision` 承诺不存在这类普通输入变化；批量可空 `{ it }` 默认值仅适用于
    Equality 覆盖 Item Content 所读取全部普通输入的不可变值模型
11. 每个普通 Typed `List` Declaration 都会在父 Composition 的每一轮执行中重新求值顺序、成员，以及
    `key`、`contentType`、`contentRevision` 和网格 Span Selector。只有 Key、Content Revision、
    Environment、Content Type、Kind 与 Span 全部相等时，Collector 才能复用已提交的逻辑 Item。
    Collector 会保留已提交的有序 List，以及每个当前 Key 至多一个 Previous Semantic Variant；当候选
    顺序中每个位置的 Item 对象身份都与已提交顺序相同时，`build` 会直接返回已提交的 List 实例。
    顶层与 `ScrollableScope` 的均质容器也可以接收 `LazyItemsSnapshot`。其 Factory 会浅拷贝有序 Item
    引用并分配不透明 Identity，不执行 Selector。每个 Collector 保留当前和上一个成功提交的已求值
    Snapshot，以精确 Source Identity 与框架 Environment 为 Key。精确命中会以常量时间恢复有序 List
    与 Key Map，不执行 Selector 或 Key 扫描；Environment 不匹配时重新执行全部 Selector。Scoped
    Declaration 没有 Snapshot Overload。只有 Item Content 在 Active Session 中执行时读取的 State
    会独立观察。Selector 读取的 State 或其他变化输入要求替换 `LazyItemsSnapshot`；顺序、成员、保留
    的 Item 数据、Selector Capture 或普通 Item Content Capture 变化时也必须替换

    唯一一次 Miss 遍历会预计算被替换的 Variant、恢复上一个 Snapshot 所需的反向 Variant，以及 Key
    Membership Delta。只有父帧成功提交后的 `SideEffect` 才会发布已求值 Snapshot 与 Cache 状态。
    Selector 失败或 Key 重复不会发布任何状态，因此 Retry 会重新执行全部 Selector。如果延迟执行的
    Side Effect 发现 Cache Generation 已推进，则会基于当前已提交 Generation 重算 Membership 与
    两个方向的 Variant，而不会发布过期预计算。父帧 Rollback 不会发布候选 Item Binding。
    ViewCompose 不接受能够绕过普通 `List` 校验的裸聚合调用方 Token
12. Detach 且从未激活的 Holder 可以 Prepare 已由父级提交的 Submission，但不得运行 Remember
    激活、Effect、原生 Commit Callback、Overlay 或已提交帧诊断。Activate 会提交有效候选而不重建。
    已 Active 的 Detach Holder 只暂存最新修订并在 Reattach 时渲染；重复 Key 存在歧义时，绝不能
    通过 First Match 查询猜测 Holder 归属
13. Pager 对唯一 Key 使用无碰撞稳定 ID，并按 `contentType`/Kind 组合划分结构不兼容的原生
    View Type。所有公开 Page 都要求唯一且稳定的 Key
14. 每个独立组合的 Item/Page 都必须接收由父组合 Holder 和稳定逻辑 Key 持有的子
    `SaveableStateRegistry`。回收会保留该 Registry 的 Saved Map，重排跟随 Key，嵌套容器递归
    应用同一层级
15. Renderer 并发创建的 Presentation 副本可以恢复逻辑 Owner 当前的 Saveable Snapshot，但不得
    为相同逻辑 Key 注册第二个持久化 Owner
16. Recycle 必须先结束逻辑 Key Session，再 Reset 物理树。兼容 Mounted Tree 只存在于框架所有、
    有界且可确定淘汰的缓存中；原生 Pool 只保留空 Holder 外壳
17. `AndroidView` 只有声明 `onReset` 才参与跨 Key 复用；最终淘汰必须恰好调用一次 `onRelease`

## 4. 必测场景

每个容器至少覆盖以下 8 类场景：

1. 结构稳定、闭包变化但 Revision 相等：不执行 Item Render；非 State 内容变化必须显式改变
   Revision
2. 结构稳定、局部上下文变化：主题/local/environment 更新可见
3. `contentRevision` 变化：复用或受控重建语义符合预期
4. keyed reorder：顺序正确、状态不串位
5. prepare/attach/detach/recycle：从未激活的缓存不运行子 Commit 工作，Attach 展示最新已提交修订，
   Active Detach 不重启生命周期工作，Recycle 不泄漏状态
6. 空 Diff 提交：Attached Holder 不执行 Item Render 或原生 Patch
7. 父帧失败：保留子项的 update/render/effect 均不运行
8. 低层 Item Key 重复：保守 Reload，不猜测 Holder 身份；公开 DSL 在构建快照时拒绝缺失或重复 Key
9. 可保存状态所有权：兄弟项 Local Key 不冲突，Keyed 回收恢复不串状态，Presentation 副本不能
   覆盖逻辑 Owner
10. 跨 Key 物理复用：旧 Effect 先 Dispose 再 Reset，新逻辑状态从空开始，失败 Rebind 不能调用旧
    Updater，淘汰只 Release 一次

## 5. 当前测试映射（2026-08）

基础单测（通用机制）：

1. [`TypedLazyCollectionContractTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/TypedLazyCollectionContractTest.kt)
2. [`LazyListDiffTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/reconcile/LazyListDiffTest.kt)
3. [`LazyHolderRegistryTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/LazyHolderRegistryTest.kt)
4. [`LazyItemSessionControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/LazyItemSessionControllerTest.kt)
5. [`LazyListAdapterTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/lazy/adapter/LazyListAdapterTest.kt)
6. [`ViewTreeRenderTransactionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt)
7. [`PagerAdapterTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/PagerAdapterTest.kt)

当前已覆盖专项：

1. `LazyColumn`：`collectionsStress_toggleUpdatesVisibleControls`（UI）
2. `LazyVerticalGrid`：`collectionsGrid_spanToggle_refreshesVisibleItemContent`（UI）
3. `TabRow + HorizontalPager`：Eager Keyed Tab 状态与 Pager Revision 场景（UI）
4. `HorizontalPager`：`statePatchStress_horizontalPagerContentUpdatesAcrossExplicitRevisions`（UI）
5. `VerticalPager`：`statePatchStress_verticalPagerContentUpdatesAcrossExplicitRevisions`（UI）
6. `LazyVerticalGrid/HorizontalPager/VerticalPager`：`NodeBindingDifferTest` 容器 patch 单测（U）
7. `LazyColumn`：`collectionsStress_rotateOrder_refreshesVisibleIdsAcrossToggles`（UI）
8. Navigation destination：`NavDestinationSessionStoreTest` 覆盖候选离屏首帧、失败回滚、
   Local/内容闭包刷新、显隐层级、永久移除和 owner 释放（U）
9. Transactional navigation host：`TransactionalNavHostCoordinatorTest` 覆盖 attach、
   push/pop/replace/reset、揭页刷新失败、初始失败重试、重入串行化和生命周期封顶（U）
10. 公共导航：`:samples:tutorials` 真机测试通过生产 `NavHost` 覆盖 push 与 Back
    （instrumentation）

当前门禁基线：

1. `qaFull` 继续作为应用行为的连接设备门禁。
2. 基线更新（2026-03-07）：`Lazy/Pager` 已统一走 DiffUtil + payload `Change` 路径，保留空 diff 刷新语义。
3. 导航基线更新（2026-07-26）：候选页面先离屏提交首帧，已提交页面刷新最新
   `UiLocalSnapshot` 与内容闭包，回滚/移除按 session → owner 顺序释放。
4. 事务导航更新（2026-07-26）：返回栈只在候选首帧或揭页刷新成功后提交；失败候选产生的
   重入命令不会泄漏到旧栈。
5. 基线更新（2026-08-12）：Lazy 与 Pager 子提交统一进入父级 Commit-Effect 边界。
   已 attach holder 对每个显式提交修订只渲染一次；detach 缓存与回滚父帧不会运行子 render/effect。
6. Pager 移动会在提交后主动刷新已 attach 且 key 唯一的页面。Hash 冲突 key 仍保持不同稳定 ID；
   无 key 的 detach 页面在再次 attach 时按已绑定位置解析已提交快照。
7. 基线更新（2026-08-13）：从未激活的 Lazy Holder 使用 Prepared → Active → Disposed 协议。
   RecyclerView Prefetch 可以在 Attach 前构建 Composition 与原生树，而既有 Transaction 会推迟
   Remember 激活、Effect、原生 Commit 工作、Overlay 与诊断。被观察 State 变化会在 Activate 前
   使候选失效。
8. 基线更新（2026-08-14）：Item/Page 快照使用调用方 Content Revision 与框架 Environment
   Revision。相等 Revision 跳过 Child Render，变化 Revision 只定向一个 Item。
9. 基线更新（2026-08-14）：逻辑 Session 与物理 Mounted Tree 分离所有权。TabRow 使用 Eager
   Keyed Child；只有可 Reset 树能通过有界 Renderer 缓存跨 Lazy Key。
10. 基线更新（2026-08-16）：普通 `List` Declaration 保留逐轮 Selector 校验；显式
    `LazyItemsSnapshot` 路径为均质 List、Row 与 Grid Overload 提供有界两代精确 Identity 快路。
    Environment 变化或 Snapshot 替换会重新执行 Selector；Scoped Declaration 继续使用普通安全路径。

## 6. 新容器接入流程

新增延迟 session 容器时，必须同步完成：

1. 架构登记：在 [overview.md](overview.md) 标记该容器
2. 清单登记：加入本文档并补测试映射
3. 单测：至少覆盖相等 Revision 跳过、显式 Revision 更新、父帧回滚与 Detach Holder 再 Attach
4. instrumentation：补真实 Activity 交互回归
5. diagnostics：确认 render/layout 诊断可观测

## 7. 排查优先级

遇到“文本没刷新 / 状态错乱 / 页面过期”时，固定按顺序排查：

1. 是否属于延迟 session 容器
2. 父级 commit effect 是否发布了最新 item/page 提交修订
3. holder 当前是 attached、detached cache，还是存在 key 歧义
4. holder 是否对该修订恰好渲染一次
5. 最后再排查 demo 业务写法
