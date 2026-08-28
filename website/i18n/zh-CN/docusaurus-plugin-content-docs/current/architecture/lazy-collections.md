---
translation_source: architecture/lazy-collections.md
translation_source_hash: 2e84db7844d32f1dc7add0096e72d21f992031fa7f801171661db0a4c392c7a0
translation_status: current
---

# Lazy 集合运行时架构

## 1. 所有权边界

UI Contract 拥有 Renderer 无关的 Item Table、Key、Revision、Span 与 Padding Policy、滚动快照
和 Connector 命令。UI Foundation 把列表、网格、Pager 和 Tab 声明转换为带 Key 的逻辑内容。
Android Renderer 继续使用 `RecyclerView` 及其 Layout Manager 负责滚动、回收、焦点、无障碍、
嵌套滚动、Fling 和边缘效果。

应用拥有集合数据和受控 Pager 选择；ViewCompose 拥有逻辑 Session 与可保存状态；Android 拥有当前
物理 Holder 与几何。这三种身份相互独立。

## 2. 声明与 Session 身份

每个 Item 与页面 Key 在容器内都必须非空且唯一。插入、删除或移动后，同一个 Key 必须继续表示
同一份逻辑内容。重复 Key 会原子拒绝候选声明，不会弱化 Diff。

`contentRevision` 是延迟内容读取的普通非 State 输入的语义版本。Key、Revision、捕获的框架环境、
Kind、Content Type 和网格 Span 相等时，可以保留已提交 Item 与 Session。在该 Session 渲染时读取
的可观察 State 仍独立跟踪。框架无法推断任意 Kotlin Lambda Capture，因此漏掉变化输入属于正确性
错误，而不只是错过优化。

单个 Item、Sticky Header、页面和 Tab 必须提供显式 Revision。`StaticContentRevision` 是声明没有
捕获变化普通值的具名承诺。批量 Item 声明默认以 Item 值为 Revision Selector，只有值相等性覆盖
全部普通输入时才安全。

每个独立挂载的 Lazy Item 或 Pager 页面必须只发出一个根节点。该根节点是原生 Holder 的测量和放置
边界。Tab 是父级组合中的 Eager Keyed Child，不创建 Lazy Session。

## 3. 普通列表与不可变快照

普通 `List` 声明在每次父级组合时都会重新评估顺序、成员、Key、Content Type、Revision 和网格
Span Selector。框架不信任 List 引用身份或相等性，因为 Kotlin List 可能存在可变别名。得到的
Keyed Entry 仍可复用所有未受影响的已提交 Session，因此运行 Selector 不代表重新渲染全部 Item。

`LazyItemsSnapshot` 是显式的整表快速路径。`toLazyItemsSnapshot()` 浅拷贝有序 Item 引用并分配
不透明身份，不会评估 Selector。容器保留当前和上一个成功提交的 Snapshot/Environment 组合；精确
命中时以常数时间返回完整已评估 Item Table，不运行 Selector 或扫描 Key。

顺序、成员、保留的 Item 数据、Selector Capture 或普通 Item Content Capture 变化时，应用必须
替换该快照。新 Environment 总是 Miss，以保证 Theme、资源、Locale、布局方向、Density 和 Font
Scale 正确。Selector 失败或 Key 重复不会发布缓存结果；重试会重新评估整个候选。

Observed `LazyColumn` Overload 会把该不可变 Submission Read 移入 Property Transaction。一个一致
Snapshot 会求值全部 Dirty Declaration，Renderer Patch 精确挂载列表，并且只有 Native Commit
成功后才发布已求值 Table、Dependency Replacement 与 Saveable Key Membership。Item Content
收到稳定 Key 与 `ObservedValue<T>`，因此叶子 Payload 变化可以 Patch 现有节点，不必重建父层或
Row 结构。Abort 会继续保留先前 Table、Observation 与逻辑 Owner。

## 4. Renderer 映射与复用

Android Adapter 消费 Q3 `LazyItemTable`。有限的 Foundation 声明发布 Indexed Table；紧凑型集成
可以按需计算 Position 并发布中立的 Range Update。Stable ID 会避免冲突并延迟分配。Table 拥有
Key 到 Position 的查询，并可提供 Sticky Header 元数据，且不会让 Android 类型进入 UI Contract。

`contentType` 只划分物理结构兼容的内容。Model 值和 Revision 不属于 Content Type。一个已挂载容器
最多接受 1,024 种 Kind/Type 组合，使动态 Taxonomy 直接失败，而不是保留无界 View Type 历史。

只有一个类型已证明同步成本有界后，RecyclerView Prefetch 才能准备未挂载的 Tree。准备阶段不能
激活 Remember State、Effect、Overlay、原生 Commit 回调或已提交诊断。首次 Attach 会用当前 State
激活。普通缓存 Detach 让已激活 Session 保持存活；Recycle 会终止逻辑 Key Session。

跨 Key 的 Mounted Tree 复用要求 Tree 内每个 Interop `AndroidView` 都提供 `onReset`。旧 Session 与
Effect 在 Reset 前结束。有界缓存只携带已 Reset 的物理 Tree；逻辑 Key State 不会进入缓存或
RecyclerView Pool。缓存淘汰或容器释放会且只会调用一次 `onRelease`。

Renderer 可以在本地 Recycled Pool 中保留一个 Reset 兼容 Presentation。滚动 Idle 后，它可以在
Gesture 路径之外 Prepare 一个非相邻候选。只有 Declaration Strategy 显式接受时才允许跨 Key
Session 复用；Runtime 随后会事务式替换 Remember、Observation、Effect、Callback 与 Saveable
Ownership，同时让相等的纯结构结果保留 Identity。Adapter 仅通过弱引用缓存交替 Submission 所需的
两个精确不可变循环 Transition，不保留完整历史列表序列。

## 5. 布局、状态与 Pager 契约

`LazyListState` 观察最新不可变锚点、可见 Item 几何、边界、方向和滚动状态。立即命令也更新保留的
锚点；动画命令依赖 Renderer 快照。宿主重建只保存首个可见 Index 与像素 Offset；临时几何和 Motion
仍属于已挂载布局状态。

网格 Cell 是 Renderer 无关 Policy。`GridCells.Fixed` 保留精确正数列数；`GridCells.Adaptive` 根据
内部宽度、Density、间距和最小尺寸推导至少一列。`FullLine` 跟随物理列数变化，不改变 Item 的逻辑
身份、Remembered State 或 Effect。

Pager 选择由 `currentPage` 和 `onPageChanged` 控制。`PagerState` 观察 Current、Settled、Target、
Offset、Count 和 Motion，并向已挂载 Presentation 发送命令；它不会在 Detach 或重建后替代受控选择。
`TabRow` 可以共享该 State 以获得 Indicator 进度，同时保留 Eager Keyed Tab 身份。

## 6. 焦点、无障碍与持久化

每个逻辑 Item Key 拥有一个子 Saveable State Registry，因此兄弟行可以复用局部 Saveable Key。
Detach 与 Reorder 按 Item Key 保留 Map。固定在顶部的 Sticky Header 副本是不拥有状态的
Presentation Replica，不能覆盖所有者的持久状态。普通 Header 仍是唯一无障碍节点，避免 TalkBack
重复播报。

获得焦点的后代通过真实垂直 Scroll Owner 使用 Android Child Rectangle 传播。即使关闭用户直接
滚动，程序焦点可见性仍有效。内容可能被遮挡的 Pager 页面应提供自己的垂直 Scroll Owner；Pager
自身只拥有离散页面移动。应用任务和可选 Paging 边界见
[Lazy 集合指南](../guides/lazy-collections.md)。
