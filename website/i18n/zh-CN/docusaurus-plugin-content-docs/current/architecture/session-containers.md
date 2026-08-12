---
translation_source: architecture/session-containers.md
translation_source_hash: 6a4991be9b38eed1416b502cd37b96d361b724f5b26e9f830f5b0a8c9a3f6e80
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
6. `TabRow + pager page`（页面内容通过 `LazyListItemSession` 承载）
7. Navigation destination page（页面内容通过 `NavDestinationSession` 承载）

## 3. 架构硬约束

每个延迟 session 容器都必须满足：

1. diff 为空时，不能回退到旧 item/page 实例
2. 已绑定 holder/session 必须有“无结构变化刷新”路径
3. `localSnapshot`、主题、环境、父层闭包在 update 路径重新注入
4. 创建路径和更新路径都能驱动 `RenderSession.render()`
5. `dispose/recycle` 语义与 holder 生命周期对齐
6. `Change` 更新优先走 payload 通道，避免无条件全量变更通知
7. key 不可用回退 `ReloadAll` 时，应尽量保持当前滚动锚点，避免交互后列表跳顶
8. 输入控件获取焦点时，不得触发无关的列表跳位。容器启用焦点跟随策略后，只能滚动到足以完整
   显示焦点编辑器的位置，并保持逻辑 Item 锚点不变
9. 一次父级集合提交对应一个单调递增的子 Session 修订。保留子项的更新只能由父渲染帧的
   commit effect 在 composition commit 之后发布；父帧回滚会直接丢弃更新，不得运行子 composition
   或 effect
10. 回调对象身份不是修订。Renderer 提交新发射的不可变 item/page 快照，只抑制同一提交修订下
    平台重复派发的 bind
11. 主动刷新仅面向已 attach 或独立展示的 holder。已 detach 的缓存 holder 只暂存最新修订并在
    attach 时渲染；重复 key 存在歧义时，绝不能通过 first-match 查询猜测 holder 归属
12. Pager 对唯一 key 使用无碰撞稳定 ID，并按 `contentType`/kind 组合划分结构不兼容的原生
    View Type。无 key 缓存页保留位置归属；带 key 的移动只有在前后快照中 key 均唯一时才解析

## 4. 必测场景

每个容器至少覆盖以下 8 类场景：

1. 结构稳定、闭包变化：`key` 不变时，可见内容在父级成功提交后更新
2. 结构稳定、局部上下文变化：主题/local/environment 更新可见
3. `contentToken` 变化：复用或受控重建语义符合预期
4. keyed reorder：顺序正确、状态不串位
5. detach/attach/recycle：detach 缓存不运行子 effect，attach 展示最新已提交修订，recycle 不泄漏状态
6. 空 diff 刷新：已 attach holder 对每个已提交修订只刷新一次
7. 父帧失败：保留子项的 update/render/effect 均不运行
8. key 缺失或重复：保守 reload，不猜测 holder 身份

## 5. 当前测试映射（2026-08）

基础单测（通用机制）：

1. [`LazyListDiffTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/reconcile/LazyListDiffTest.kt)
2. [`LazyHolderRegistryTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/LazyHolderRegistryTest.kt)
3. [`LazyItemSessionControllerTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/LazyItemSessionControllerTest.kt)
4. [`LazyListAdapterTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/lazy/adapter/LazyListAdapterTest.kt)
5. [`ViewTreeRenderTransactionTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ViewTreeRenderTransactionTest.kt)
6. [`PagerAdapterTest.kt`](https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/PagerAdapterTest.kt)

当前已覆盖专项：

1. `LazyColumn`：`collectionsStress_toggleUpdatesVisibleControls`（UI）
2. `LazyVerticalGrid`：`collectionsGrid_spanToggle_refreshesVisibleItemContent`（UI）
3. `TabRow + HorizontalPager`：`statePatchStress_refreshesStableTabContent`（UI）
4. `HorizontalPager`：`statePatchStress_horizontalPagerContentUpdatesAcrossAdvances`（UI）
5. `VerticalPager`：`statePatchStress_verticalPagerContentUpdatesAcrossAdvances`（UI）
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
5. 基线更新（2026-08-12）：Lazy、Pager 与 Tab 的子提交统一进入父级 commit-effect 边界。
   已 attach holder 对每个显式提交修订只渲染一次；detach 缓存与回滚父帧不会运行子 render/effect。
6. Pager 移动会在提交后主动刷新已 attach 且 key 唯一的页面。Hash 冲突 key 仍保持不同稳定 ID；
   无 key 的 detach 页面在再次 attach 时按已绑定位置解析已提交快照。

## 6. 新容器接入流程

新增延迟 session 容器时，必须同步完成：

1. 架构登记：在 [overview.md](overview.md) 标记该容器
2. 清单登记：加入本文档并补测试映射
3. 单测：至少覆盖“diff empty but closure changed”、父帧回滚与 detach holder 再 attach
4. instrumentation：补真实 Activity 交互回归
5. diagnostics：确认 render/layout 诊断可观测

## 7. 排查优先级

遇到“文本没刷新 / 状态错乱 / 页面过期”时，固定按顺序排查：

1. 是否属于延迟 session 容器
2. 父级 commit effect 是否发布了最新 item/page 提交修订
3. holder 当前是 attached、detached cache，还是存在 key 歧义
4. holder 是否对该修订恰好渲染一次
5. 最后再排查 demo 业务写法
