---
translation_source: architecture/decisions/0016-constraintlayout-graph-and-helper-ownership.md
translation_source_hash: f4dbdf777114fe7d3c10b9ffaeee4b84ef4f1953fbf7007bb39fd482a12d9b4f
translation_status: current
---

# ADR-0016：ConstraintLayout 图与 Helper 所有权

- 状态：已接受
- 日期：2026-08-18
- 扩展：[ADR-0008](./0008-transactional-effect-lifecycle.md)

## 背景

Alpha 版 ConstraintLayout 集成已经分离了渲染器中立传输、公开编写 DSL 与 AndroidX
渲染，但原生协调没有唯一权威的候选模型。每次重建都会克隆在线
`ConstraintLayout`、清空条目、重建部分图，再由 `ConstraintSet.applyTo` 修改容器。
Flow、Group、Layer 与 Placeholder View 归渲染器所有，Guideline 与 Barrier View 却是原生
副作用。

这会阻塞首个发布：删除 Helper 声明无法证明原生 View 已被移除；缺失引用可能只跳过
错误链接而继续应用无效候选；原生异常也可能发生在在线树已被部分修改之后。公开尺寸
契约还能用独立字段表达矛盾组合，接受 `match_parent`，并暴露 AndroidX 原始比例字符串。

首个 Maven 版本需要唯一的正确性模型。与其保留 Alpha 兼容性，不如在消费者依赖这些
行为之前移除歧义状态。

## 决策

ViewCompose 将 ConstraintLayout 集成硬切为一个不可变候选图和一个原生 Helper 所有者。

`viewcompose-ui-contract` 拥有无 Android 依赖的传输契约。尺寸只能是内容包裹、受约束
内容包裹、固定 dp，或带单一 spread/wrap/percent 模式及可选 min/max 的 match
constraints。比例包含正数宽高项与可选受约束轴。契约不再包含 `match_parent`、独立
min/max/percent/constrained 标志或原始比例字符串。

`viewcompose-constraintlayout-androidx` 拥有 Q3 编写能力。空引用、重复声明、无效局部
范围、单成员或重复成员 Chain、相互竞争的 baseline/边缘声明，以及 circle/边缘组合会
同步失败。必须结合已挂载内容才能判断的关系合法性仍由渲染器预检。

Android Renderer 在修改 View 之前，把 inline 与可复用声明合并为
`ResolvedConstraintGraph`。编译器校验完整 child/helper 命名空间、引用存在性、锚点平面、
Chain 所有权、Helper 依赖、尺寸、比例和数值范围。每个直接内容 child 都必须有非空语义
ID。Flow 与 Placeholder 是可约束 Helper 节点；Guideline、Barrier、Group 和 Layer 不是
普通约束条目源。

一个渲染器注册表拥有每种 Guideline、Barrier、Flow、Group、Layer 与 Placeholder 的
View 实例、稳定生成的 View ID、引用数组、类型与删除。AndroidX 不再通过
`ConstraintSet.applyTo` 副作用创建未跟踪 Helper。Helper 类型变化是在同一个原生提交内
执行的删除/创建操作。

已接受图从干净的原生 set 应用，而不是从在线布局克隆。修改前，渲染器会快照受影响的
ID、LayoutParams、Helper 成员关系、可见性、无障碍与变换属性。只有原生 apply、Helper
配置、陈旧 Helper 删除和运行时属性恢复都成功后，才发布候选图。失败时会恢复此前的
Helper 注册表、View 状态、环境与已接受图，然后按尝试 revision、identity 和 reason
发出有界结构化拒绝。

Layer 布局后工作只有一个带 generation 校验的 pre-draw 所有者，并在替换或 detach 时
取消。禁止第二套旧协调引擎、兼容开关、局部链接恢复分支或无界字符串警告缓存。

AndroidX 运行时基线升级到稳定版 ConstraintLayout `2.2.2`。兼容性与设备证据仍是发布
门禁；如果门禁暴露问题，不得静默退回 `2.2.1`。

## 结果

- 无效候选会整体拒绝，不再形成部分可见布局。
- Helper View 数量、ID、诊断和回调都有明确有界所有权，可以进行压力验证。
- Alpha 源码迁移是破坏性的，但会移除没有可靠求解语义的组合。
- 原生工作前会分配不可变候选图。首发性能验收要求无实质回退；拓扑/标量快路径留给
  发布后计划。
- Flow 与 Placeholder 作为显式图节点接受约束，保留其 AndroidX 布局角色，同时不通过
  DSL 暴露原生 View。
- AndroidX 字符串、常量、LayoutParams 与 Helper 实例仍只存在于 Android Renderer。

## 否决方案

### 保留 clone-and-clear 并增加异常捕获

否决，因为捕获异常无法撤销已经发生的原生修改，而且克隆在线树会让历史副作用成为
下一候选的事实来源。

### 让 ConstraintSet 创建 Guideline 与 Barrier

否决，因为缺少对称所有权与清理就无法证明删除、稳定类型或有界保留 child 数量。

### 通过弃用保留旧 Alpha API

否决，因为弃用的独立字段仍能表达矛盾尺寸，并迫使渲染器保留两条解释路径。

### 只跳过无效链接

否决，因为生成的图会偏离编写图，可能让无关约束看似成功而几何实际错误。

### 通过开关同时发布新旧协调

否决，因为两套生产引擎会使生命周期与回滚面翻倍。按 Git revision 对比或独立 APK
即可比较，不需要发布双所有权。

## 公开 API 与模块影响

- `viewcompose-ui-contract` 拥有 Q3 `ConstraintDimension`、`ConstraintMatchMode`、
  `ConstraintRatioSide`、`ConstraintRatio` 和统一 baseline 链接传输。
- `viewcompose-constraintlayout-androidx` 拥有 Q3 已校验引用、child builder、Helper builder
  与编译迁移示例。
- `viewcompose-renderer-android` 拥有图编译、稳定 ID、完整 Helper 生命周期、原生提交/
  回滚、有界诊断与精确几何测试。
- Demo fixture 与模块文档只能使用硬切契约，不得保留旧示例。

## 验证与发布

实现与验收遵循当前有效的
[ConstraintLayout 首发加固计划](https://docs.viewcompose.com/project/plans/constraintlayout-native-engine-hardening)。
发布要求包括纯图与 DSL 测试、Robolectric 精确几何与回滚、1,000 次 Helper 切换压力、
聚焦真机与无警告 Demo 证据、已解释的性能安全对照、Q3 编译示例、API/文档门禁、中文
镜像以及不可变 Changeset。
