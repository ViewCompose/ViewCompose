---
translation_source: architecture/decisions/0017-typed-constraint-helper-expansion.md
translation_source_hash: 04c2e40b41be8adf4a92d0d4b0366cd6b5b4cdafac58adc111e70d459c0f891e
translation_status: current
---

# ADR-0017：类型化 ConstraintLayout Helper 展开

- 状态：已接受
- 日期：2026-08-21
- 扩展：[ADR-0016](./0016-constraintlayout-graph-and-helper-ownership.md)

## 背景

发布后的 ConstraintLayout 能力对齐阶段需要 Grid、分组 Circular Placement、显式物理边缘和
自定义 Chain Boundary，同时不能削弱 ADR-0016 建立的不可变 Graph 与单一所有者事务。

AndroidX Grid 通过紧凑 String 暴露 Span 与 Skip，并可能在 ViewCompose 注册表之外创建原生
结构。AndroidX CircularFlow 是可变 Helper View，其默认值与成员数组可能超出一次声明式候选的
生命周期。直接转发任一 API 都会把校验推迟到解析后，或再次分裂生成 Identity、Removal 与
Rollback 所有权。

Renderer 还必须精确保留 Baseline 与物理 Gone Margin。ConstraintLayout `2.2.2` 会把这些字段
记录在 `ConstraintSet` 中，但 Apply Path 不会把全部字段复制到目标 `LayoutParams`。

## 决策

ViewCompose 把新的 Helper Family 表示为类型化、无 Android 依赖的 Graph 声明，并把全部原生
展开保留在既有 Renderer 事务内。

Grid 声明包含有界的固定或推导 Axis、Fill Orientation、行列 Weight、dp Gap、类型化成员 Span
和类型化 Skip Rectangle。Graph 预检会在 `50 x 50` 上限内为每个成员解析唯一且不重叠的
Placement。Grid 语义 ID 只表达 Identity，不能用作 Anchor。

Android Renderer 会把每个已接受 Grid 展开为零厚度行/列 Proxy View。其稳定生成 ID、创建、
复用、清理与回滚都归同一个 Container-local 注册表所有。一个 Grid 因此最多拥有 50 个行 Proxy
和 50 个列 Proxy。Renderer 不实例化 AndroidX Grid，也不解析其 String Grammar。

CircularFlow 声明包含一个 Child Center 与显式 Child/Radius/Angle Item。Graph 预检会让该分组
独占每个成员的 Circular-position Ownership。Android Renderer 应用普通 Per-child Circle
Constraint，因此 CircularFlow 不创建 Helper View 或生成的原生 ID。Angle 使用 AndroidX 坐标：
`0f` 位于 Center 上方，数值沿顺时针增加。

逻辑 Start/End 与物理 Left/Right 保持独立 Anchor Plane。Child 或 Horizontal Chain 不能混用
这些平面。Chain Boundary 接受类型化 Parent、Child、Guideline 或 Barrier Target，以及显式非负
Margin。Parent-wrap 贡献使用一个穷举 Enum，而不是多个独立 Boolean Field。

`ConstraintSet.applyTo` 完成后，Android Renderer 会从已接受 Graph 恢复 Baseline Margin 与
物理 Left/Right Gone Margin，并重置未声明值。这是事务内部针对特定版本的平台 Workaround，
不是第二份 Layout Truth。

## 结果

- 无效 Span、Skip、Reference、Plane 或竞争的 Chain/Grid/CircularFlow Ownership 会在原生修改前
  拒绝完整候选。
- Grid 增加 `O(rows + columns)` 个原生 Proxy View，而不是一个 AndroidX Grid View。显式 50 轴
  上限使 Identity 与 Child Count 增长保持有界且可压力验证。
- Grid 与 CircularFlow Reference 只表示声明 Identity；应用代码不能依赖生成 View ID，也不能
  把它们用作 Anchor。
- 移除 CircularFlow 会清除普通 Circle Constraint，不需要 Helper Lifecycle。
- Renderer 分支必须同时实现类型化展开与 Margin 恢复；静默忽略任一 Transport Family 都会破坏
  Geometry 与 Rollback 正确性。

## 否决方案

### 直接包装 AndroidX Grid

否决，因为其 String Grammar 会丢失编译期结构，并把 Overlap、Bounds 与 Membership Failure
推迟到解析后。若不增加另一条 Reconciliation Path，其生成的原生所有权也无法适配已接受的
注册表事务。

### 暴露原始 Span 与 Skip String

否决，因为错误 Index、重复 Member 与重叠 Rectangle 会变成运行时 Parser 行为，而不是类型化
编写和整图校验。

### 把 AndroidX CircularFlow 保留为 Helper View

否决，因为显式 Radius 与 Angle 已可映射为普通 Circle Constraint。可变 Helper 只会增加
Identity、Removal 与 Rollback 工作，不会增加受支持语义。

### 把 Grid 行列 Proxy 暴露为 Anchor

否决，因为 Proxy 数量和形状属于 Renderer 实现细节，未来可以在不改变语义 Grid 契约的前提下
调整。

## 公开 API 与模块影响

- `viewcompose-ui-contract` 拥有物理 Anchor、Parent-wrap Policy、Chain Boundary Transport、
  类型化 Grid Transport 与声明式 CircularFlow Transport。
- `viewcompose-constraintlayout-androidx` 拥有用于 Inline 和可复用 Constraint Set 的 Q3 已校验
  DSL 与编译 Sample。
- `viewcompose-renderer-android` 拥有 Graph Placement、Proxy Identity、无 View 的 Circle 展开、
  精确 AndroidX Margin 恢复与原子回滚。
- Demo 与 Preview 提供独立 Grid/CircularFlow Fixture；生成的基础设施不会呈现为应用级 Child。

## 验证与发布

六条冻结的 `CL-P2-*` 用例要求精确 Chain、Margin、Wrap、Physical-edge、Grid 与 Circle Geometry，
并覆盖无效候选保留、LTR/RTL、Removal 与 1,000 次替换上限。编译 Q3 Sample、Demo/Preview 编译、
聚焦 Device Automation、严格 API 文档、文档结构、Release Intent 与 Phase 2 Changeset 都是合入
门禁。完整多配置 Visual/Lifecycle 验收仍属于 Phase 3；在 Phase 4 的 Direct-native/
Released-baseline/Candidate Matrix 之前，不接受性能结论。
