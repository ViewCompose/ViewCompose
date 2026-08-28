---
translation_source: migration/render-session-pre-frame-inspection.md
translation_source_hash: 38d6978494783995900aa5799e6d91fd0e32aab551f8c373cdf557f9df143ade
translation_status: current
schema_version: 2
document_id: migration.render-session-pre-frame-inspection
doc_type: migration
owner:
  kind: capability
  id: diagnostics.session-inspection
version_lane: released
capability_ids:
  - diagnostics.session-inspection
artifact_ids:
  - viewcompose-ui-foundation
sample_ids:
  - tooling.diagnostics-session-inspection
source_state: 自定义检查工具会穷举原有三个 RenderSessionInspectionPolicy 值，并且只能在成功 Native Frame 后注册。
target_state: 自定义检查工具为显式 Armed 单帧采集处理 TrackSessionBeforeFirstFrame，同时保留普通 Post-frame Policy。
---

# 迁移 Pre-frame Render Session Inspection

`RenderSessionInspectionPolicy` 新增 `TrackSessionBeforeFirstFrame`。对使用 Kotlin `when` 穷举该枚举
的调用方，这是源码不兼容的 Alpha 变更；使用更新后的 UI Foundation 产物重新编译前，必须添加新
分支。没有安装自定义 `RenderSessionInspectionTooling` 的普通应用无需修改。

只有显式工具请求选中一个候选 Logical Session 后才能使用新 Policy。Session 会在首帧前立即调用
`register`，并传入空 `sourceCandidates` 列表。在该 Registration 中同步启动 Timing Capture，会把
采集附着到正在进入的帧，不会请求嵌套结构 Render。即使首帧回滚，Registration 仍负责 Dispose。

原有分支继续承担原有职责：

- `Ignore` 不安装检查状态；
- `TrackSession` 在第一个成功 Native Frame 后注册，但不采集 Source；
- `TrackSessionAndCaptureSources` 在第一个成功 Native Frame 后携带有界 Source Candidate 注册。

不得把 Pre-frame Policy 用作通用 Eager-registration 模式。Adapter 仍受平台 Render Thread 约束，
必须在不保留应用 Key 的情况下完成 Armed 决策，并且不能给非激活路径增加周期 Observer、Traversal、
Timing 或 Report 工作。可编译 `renderSessionInspectionToolingSample` 同时展示一次性 `LazyItem` Arm
和普通 Policy。
