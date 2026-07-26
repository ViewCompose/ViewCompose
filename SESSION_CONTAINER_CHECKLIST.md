# Delayed Session Container Checklist

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
8. 输入控件获取焦点时，不应触发列表自动滚动跳位（除非显式滚动请求）

## 4. 必测场景

每个容器至少覆盖以下 6 类场景：

1. 结构稳定、闭包变化：`key` 不变时可见内容立即更新
2. 结构稳定、局部上下文变化：主题/local/environment 更新可见
3. `contentToken` 变化：复用或受控重建语义符合预期
4. keyed reorder：顺序正确、状态不串位
5. detach/attach/recycle：不泄漏、不丢状态
6. 空 diff 刷新：`updates.isEmpty()` 时已绑定 holder 仍刷新

## 5. 当前测试映射（2026-03）

基础单测（通用机制）：

1. [LazyListDiffTest.kt](/Users/gzq/AndroidStudioProjects/UIFramework/viewcompose-renderer/src/test/java/com/viewcompose/renderer/reconcile/LazyListDiffTest.kt)
2. [LazyHolderRegistryTest.kt](/Users/gzq/AndroidStudioProjects/UIFramework/viewcompose-renderer/src/test/java/com/viewcompose/renderer/view/LazyHolderRegistryTest.kt)
3. [LazyItemSessionControllerTest.kt](/Users/gzq/AndroidStudioProjects/UIFramework/viewcompose-renderer/src/test/java/com/viewcompose/renderer/view/LazyItemSessionControllerTest.kt)

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

当前缺口（需补专项回归）：

1. Navigation destination 的真实 Activity 交互回归将在 Stage 4 公开 transactional `NavHost`
   后接入；当前 Stage 3 仅暴露内部页面会话协议
2. 门禁基线：`qaFull`（Pixel 4 XL API 33）21/21 通过
3. 基线更新（2026-03-07）：`Lazy/Pager` 已统一走 DiffUtil + payload `Change` 路径，保留空 diff 刷新语义。
4. 导航基线更新（2026-07-26）：候选页面先离屏提交首帧，已提交页面刷新最新
   `UiLocalSnapshot` 与内容闭包，回滚/移除按 session → owner 顺序释放。

## 6. 新容器接入流程

新增延迟 session 容器时，必须同步完成：

1. 架构登记：在 [ARCHITECTURE.md](/Users/gzq/AndroidStudioProjects/UIFramework/ARCHITECTURE.md) 标记该容器
2. 清单登记：加入本文档并补测试映射
3. 单测：至少覆盖“diff empty but closure changed”
4. instrumentation：补真实 Activity 交互回归
5. diagnostics：确认 render/layout 诊断可观测

## 7. 排查优先级

遇到“文本没刷新 / 状态错乱 / 页面过期”时，固定按顺序排查：

1. 是否属于延迟 session 容器
2. diff 是否保留了最新 item/page 实例
3. 绑定 holder 是否在空 diff 路径下触发刷新
4. 最后再排查 demo 业务写法
