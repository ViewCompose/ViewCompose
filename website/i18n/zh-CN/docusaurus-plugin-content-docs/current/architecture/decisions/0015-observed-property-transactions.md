---
translation_source: architecture/decisions/0015-observed-property-transactions.md
translation_source_hash: 2530352d9a1c167387d4da2ddc96f983efb3b6328575ffe85c2ba127d593fc93
translation_status: current
---

# ADR-0015：可观察属性事务

- 状态：已接受
- 日期：2026-08-16
- 扩展：[ADR-0008](./0008-transactional-effect-lifecycle.md) 与
  [ADR-0012](./0012-lazy-collection-logical-and-physical-ownership.md)

## 背景

ViewCompose 已支持不依赖编译器的分组重组。显式边界可以跳过稳定的声明体，新 VNode 到达
Android Renderer 后也能原地 patch 复用的 View。但是，当一次 State 读取决定许多叶子属性时，
它仍会让祖先声明失效、重建 VNode 结果，并把完整 Root 树送入 reconcile。

已验收的复杂布局对照暴露了后果。ViewCompose 的中位数低于直接 Android Views control，
但 P95 为 `41.187 ms`，原生只有 `16.222 ms`。Perfetto 没有发现锁、I/O 或前台 GC 停顿；慢帧
同步执行完整声明、树 diff 和 Android View traversal，落到 LITTLE CPU 后成本进一步放大。
局部分配、物理树、子树证明、reconcile 和编译状态实验都没有实质缩小尾部差距。

项目无法使用 Compose 编译器生成 changed flags 和 restart lambda，因此不能推断哪些 Kotlin
表达式可以独立安全地重跑，也不能对任意 capture 承诺自动属性跳过。但框架可以提供显式契约，
并让其所有权和失败语义与现有 Snapshot、RenderSession、VNode 和原生回滚模型一致。

## 决策

ViewCompose 增加渲染器中立的可观察属性事务，作为显式 Q3 能力。

可观察值或可观察 NodeSpec 声明同步 reader 与显式普通 inputs。reader 中的 Snapshot State
读取属于 Session 所有的属性观察，而不是外围 Composer scope。inputs 相等时可以继续使用已提交
reader 和值；非 State capture 变化却未更新 inputs 属于不支持用法，这是没有编译器时必须公开
的边界。

每个 RenderSession 的 Registry 独占全部观察。它把属性源关联到发出它的逻辑 scope，捕获当前
Local 环境、合并失效，并在一个固定 Snapshot 中读取全部 dirty source；依赖和值的变化在发布前
保持候选状态。完整组合以事务方式 reconcile Registry 成员。只有逻辑所有权结束后，移除和 Session
dispose 才释放观察。

可观察属性只能替换同一逻辑节点的 NodeSpec。节点 type、key、Modifier、children 与环境所有权
都是结构信息，必须走完整组合。违反契约时应报告错误，不能静默回退，否则既会隐藏过时 capture，
也会隐藏无上界的性能。

成功的完整 Renderer frame 返回不透明属性 target。属性 frame 直接定位这些 target，比较前后
VNode，绝不 reconcile sibling 或 child。Renderer 对整个批次做 preflight 和 checkpoint。任何
reader 或原生绑定失败都会放弃候选观察，并在旧属性值继续作为权威值之前恢复此前的全部原生
变化。只有整个批次成功后才运行 commit effect。

框架环境变化仍由 Host 所有。locale、资源、主题、density、font scale 或 layout direction 变化
触发的完整 render 会替换捕获的 Local snapshot 与 reader。结构和属性工作合并时，结构 frame
优先，并在现有单一 frame 边界提交两类候选。

首个类型化组件接入是可观察 Text 内容，同时提供渲染器中立的低层可观察 NodeSpec 路径供自定义
节点使用。后续类型化接入必须复用同一 Registry 和 Renderer 事务，不得增加组件专属 Listener
或 Renderer 所有的 State 订阅。

## 结果

- 属性密集型 State 更新可以接近保留 Android View 的直接修改，同时保留声明式来源和原生回滚契约。
- 显式 inputs 取代编译器稳定性推断；API 比 Compose 语法更刻意，但行为可预测。
- 结构更新仍然更昂贵并单独测量；可观察属性不会形成第二套隐藏子树模型。
- VNode 与 Core Render SPI 增加不透明属性身份和精确 target 事务概念。
- RenderSession 是唯一可以组合 composition、property、native、effect、overlay 与 diagnostic
  提交顺序的协调器。
- Renderer 必须原子实现可观察属性 patch，或者拒绝启用该能力，不能部分应用批次。
- 调试工具可以描述属性 patch，但根据 ADR-0009，未激活工具仍不能进入热路径。

## 未采用的方案

### 在 Android Binder 中增加 State 重载和 Listener

不采用，因为 Android View 会拥有逻辑观察，dispose 和依赖变化会逃逸 RenderSession，其他
Renderer 也会分叉，原生回调还可能在回滚和 Effect 顺序之外修改已提交 frame。

### 把每个 RecomposeBoundary 当成属性事务

不采用，因为边界可以发出零个、一个或多个节点，也可以改变结构。独立更新它们需要第二套子树
所有权和锚点模型。它们继续作为显式结构重启原语。

### 继续重跑 Root，只优化更多比较

不采用，因为测量已经否决。多轮比较、分配、分组、物理深度和编译实验都没有实质改善 P95；
在直接 Android 修改结束前，算法仍然访问完整更新表面。

### 从任意 State capture 自动推断属性

不采用，因为项目没有 Compose 编译器、changed flags、稳定性推断或安全的 restart lambda 生成。
运行时反射无法恢复同等语义。

### 属性改变结构时静默回退

不采用，因为这会让有界 API 变为依赖 workload 的整树工作，并可能隐藏错误的 inputs 列表。
契约错误必须可观察，并给出明确的结构式替代路径。

## 公开 API 与模块影响

- `viewcompose-ui-contract` 在 VNode 上携带不透明可观察属性身份。
- `viewcompose-ui-foundation` 拥有 Q3 可观察值、可观察 NodeSpec 发出、候选 Registry、frame
  调度、失败报告和 Core Render SPI。
- `viewcompose-host-android` 转换 Core property frame，且不向上暴露 Android View。
- `viewcompose-renderer-android` 拥有精确 Mounted target 索引、Binder preflight、原子 apply、
  rollback、commit effect 与诊断。
- Demo 与 benchmark fixture 分离属性和结构更新 action，并在一个带 revision 的 workload
  契约下比较 ViewCompose、Compose 与 Android Views。

## 验证与发布

实现证据保留在
[可观察属性事务归档计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/observed-property-transactions.md)。
硬切要求 Q3 KDoc 与编译样例、Fake Engine 和 Android 失败注入、Local 与资源变化测试、生命周期与
dispose 测试、API 和模块文档、中文镜像、单一 release changeset、已验收的三引擎 benchmark
证据，以及仓库全部质量门禁。
