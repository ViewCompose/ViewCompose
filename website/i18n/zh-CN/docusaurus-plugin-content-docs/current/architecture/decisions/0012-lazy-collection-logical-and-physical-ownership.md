---
translation_source: architecture/decisions/0012-lazy-collection-logical-and-physical-ownership.md
translation_source_hash: 4a2220922d02ed8ebbc9d60f373f262bebf6722354a661573be1482fa1d36448
translation_status: current
---

# ADR-0012：Lazy 集合的逻辑与物理所有权

- 状态：已接受
- 日期：2026-08-13
- 替代：[ADR-0011](./0011-prefetched-session-activation-boundary.md) 中关于标识与保留的部分
- 部分已被替代：[ADR-0018](./0018-focus-visibility-and-pager-selection-ownership.md) 替换了
  ViewPager2 物理宿主和离屏默认值决策；本记录中的逻辑 Page Session 所有权仍然有效

## 背景

ViewCompose 将声明式内容映射到 Android View。Lazy List 和 Pager 条目因此同时经过两套复用
系统：框架组合 Session 保留逻辑标识、状态和 Effect，RecyclerView 与 ViewPager2 则保留物理
Holder 与原生 View。旧实现把两种所有权都挂在一个 Holder 上，还把 Callback 引用变化当作隐式
失效信号，并允许推测性准备执行无界同步工作。

这种模型通过保守渲染保证正确性，却让未变化提交也很昂贵，阻止不同逻辑 Key 复用原生树，并让
性能取决于偶然的 Lambda 分配。若不先定义明确边界就扩大原生复用，还可能让回收的物理 Holder
携带旧 Key 的 `remember`、可保存状态或 Effect。

ViewCompose 没有编译器生成的变化掩码与稳定性推断，因此必须建立公开 Revision 契约；Runtime
无法可靠比较普通捕获变量。

## 决策

### 三层所有权

每个虚拟化条目由三个独立层次表示：

1. 不可变逻辑快照包含 `key`、`contentType`、`contentRevision`，以及框架自动捕获的
   `environmentRevision`。
2. Key 所有的逻辑 Session 包含组合状态、`rememberSaveable` 所有权、Effect 与 State 订阅。
   Effect 只在该逻辑 Session 处于 Active 时存在。
3. 物理呈现包含 Holder、已挂载原生树和 Android View。它可以按 `contentType` 复用，但绝不拥有
   或传递旧 Key 的逻辑标识。

所有公开 Lazy List、Grid、Pager Page 与 Tab 声明都必须提供非空且在容器内唯一的 Key。位置只
表示物理排布，绝不作为逻辑标识兜底。

### 绑定规则

Runtime 不使用 Callback 引用兜底，严格执行：

- Key 相同且内容与环境 Revision 相等：完全跳过条目渲染。
- Key 相同但 Revision 改变：只重组并 Patch 该条目。
- Key 不同但 `contentType` 相同：创建新逻辑 Session，再尝试 Reset 并 Rebind 框架所有的原生树。
- `contentType` 不同（包括 Key 相同时）：终止旧 Session、释放其呈现并创建新原生树。

主题、Android 资源、Locale、布局方向、Density、Font Scale 以及其他已捕获 Local 都进入
`environmentRevision`。State Read 仍独立可观察。条目内容捕获的非 State 值必须进入
`contentRevision`。单条 `item`、`stickyHeader`、`Page` 与 `Tab` Declaration 必须在 `key` 后立即提供
非空 Revision，再排列可选物理复用与布局策略。`null` 不是静态哨兵；`StaticContentRevision` 才是
普通捕获内容对当前 Key 保持稳定的显式承诺。批量 Item Selector 仍可空，并对不可变值模型默认使用
Item 值。
Session Update 操作是强制契约：Renderer 不能因为实现缺少 Content Installer，就替换 Key 与 Type
均相同的逻辑 Session。
`LazyListItemSession.activate` 与 `render` 会报告已安装候选是否真正 Commit。Rollback 绝不推进
Item 或父 Submission Revision，同一语义 Revision 仍可重试；原生帧 Commit 后才报告的失败不会
撤销该结果。

### 原生 View 复用生命周期

每个原生互操作节点都必须主动允许跨 Key 复用。`AndroidView` 只有声明 Reset Callback 才能参与。
旧逻辑 Session 先释放，再执行 Reset，最后由新 Key 绑定原生树。Mounted Tree 最终淘汰时恰好执行
一次 Release。

RecyclerView 的不透明全局池不拥有 Mounted Tree。按 `contentType` 分组的有界框架缓存提供可观测、
确定性的淘汰，并释放每棵被淘汰的树；RecyclerView 仍可池化空 Holder 外壳。包含不可 Reset
互操作 View 的树会直接释放。

### 容器专用策略

- Lazy List 使用虚拟化 Key Session、自适应且有成本上限的推测准备，以及三层复用模型。
- Pager 使用延迟 Page Session，并把驻留交给 ViewPager2 原生 Offscreen Policy；默认不再额外强制
  框架页数。
- Tab Row 是父树中 Eager 的普通 Keyed Child。选择变化只失效前后两个 Tab，不创建 Lazy Item
  Session 或独立可保存状态 Owner。

## 公开 API 与兼容性影响

这是有意的硬切。Lazy 与 Pager DSL 用 `contentRevision` 替换 `contentToken`。Revision 是调用方可见
的正确性契约，而非尽力而为的性能提示。单条 Declaration 把必传非空 Revision 排在 `key` 后、
可选 `contentType` 或布局策略前；有意的静态内容必须使用 `StaticContentRevision`，不能使用 `null`。
批量 `items` 保留可空 Revision Selector，Page 声明暴露四个快照字段，Tab 内容提供显式 Revision，
Pager Offscreen 默认值跟随 ViewPager2 原生策略。

参数重排会破坏源码兼容性，所有使用方都必须重新编译。相邻 `Any?`/`Any` 值可能擦除为相同的 JVM
`Object` Descriptor，因此针对旧顺序编译的 Binary 不保证链接失败，还可能把物理 `contentType` 与
逻辑 `contentRevision` 传入相反位置。

这些 API 属于 Q3：错误的 Revision 或生命周期用法会造成陈旧 UI、原生资源泄漏，或为错误逻辑条目
发布 Effect。同一变更必须包含规范英文 KDoc、可编译 Sample、Module Manual、迁移指南和确定性测试。

## 后果

- 稳定条目的相等提交不会执行条目组合或原生 Patch。
- 一个变化条目不会刷新无关 Attached Holder。
- 可跨 Key 摊销原生分配，且不会传递组合状态或 Effect。
- 框架拥有池化 Mounted Tree 的可观测最终释放边界。
- 不相等的 Keyed Group 如果持久 Saveable Path Hash 冲突，会在注册 Provider 前失败，绝不会静默
  合并两个逻辑 Owner。
- 首次遇到的大条目仍可能占用一帧；自适应准备会避免把未知或昂贵条目提前塞进 Fling 帧，但无法
  抢占任意用户代码，应用仍需选择合理条目粒度。
- 调用方必须把变化数据做成可观察 State，或纳入 `contentRevision`。没有编译器时无法消除该边界。

## 被否决的替代方案

### 把每个新 Callback 对象视为内容变化

对象分配标识不是语义标识。它会让每次父级渲染都做无用工作，并掩盖缺失 Revision，因此否决。

### 把逻辑 Session 保存在回收 Holder 中

Holder 是物理容量，不是逻辑标识；跨 Key 复用会把 Remember State、Saveable Owner、Subscription
和 Effect 移给无关内容，因此否决。

### 把完整 Mounted Tree 直接放入 RecyclerView 共享池

池溢出与 Holder 永久丢失没有足够清晰的 Release Callback，无法可靠实现
`AndroidView.onRelease`，因此必须由框架拥有淘汰。

### 用同一种 Item Session 抽象 TabRow、LazyList 与 Pager

三类容器的驻留和所有权需求不同。统一形状会保留不必要 Session，并阻止最低成本的 Eager Keyed
Patch，因此否决。

## 验证

硬切必须确定性覆盖四条绑定规则、Key 所有的 Saveable State、跨 Key Rebind 前 Effect Dispose、
Reset-Before-Bind、最终 Release 恰好一次、缓存淘汰、不可 Reset 互操作回退、环境 Revision、Tab
选择失效、原生 Pager 驻留、Sticky Header 查找、State 发布与重复提交。

性能验收使用 Release 构建和精确 Demo Route。切换 Diagnostics Tab 后必须立刻执行到达底部并返回
顶部的大力度长 Fling；解释耗时前先用操作计数证明条目 Render 被跳过、变化工作被定向、Mounted
Tree 得到复用。
