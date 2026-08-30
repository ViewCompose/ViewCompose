---
schema_version: 2
document_id: architecture.version-bound-ai-tooling-upgrades
doc_type: architecture
slug: /architecture/decisions/version-bound-ai-tooling-upgrades
owner:
  kind: project
  id: ai-development-tooling
version_lane: next
capability_ids: []
artifact_ids: []
sample_ids: []
invariants:
  - AI 工具升级绝不能只依据工具发布时间选择框架知识。
  - 每个可供 Consumer 选择的 Knowledge Pack 都必须绑定 ViewCompose Artifact 精确版本与不可变发布 Revision。
  - Consumer 依赖版本无法解析或互相冲突时，必须保持现有接入不变并失败关闭。
evidence:
  - tools/ai/contracts/framework-compatibility-profile.schema.json
  - tools/ai/contracts/examples/framework-compatibility-profile.json
  - ./gradlew verifyAiToolingContracts
translation_source: architecture/decisions/0025-version-bound-ai-tooling-upgrades.md
translation_source_hash: 1c0b848e9d9213cc502b53a814d6fa60cdfeb539e638da3a4992d65ae0bcd5f4
translation_status: current
---

# ADR-0025：绑定框架版本的 AI 工具升级

- 状态：已接受
- 日期：2026-08-30

## 背景

AI 工具 Package 有自己的发布节奏，而 ViewCompose Maven Artifact 采用独立版本。Agent
Executable 或 Skill Workflow 更新，并不代表其中携带的 API 知识适用于已有 Android Project。
如果不检查 Project 的 ViewCompose Coordinate 就选择最新 AI 工具 Release，可能暴露晚于 Project
Dependency 的 API，导致生成 Kotlin 无法编译；更危险的是，代码看似合理却表达了错误契约。

首个公开工具 Package 包含精确的 `current-source` Knowledge Bundle，以及固定已发布 Maven
Coordinate 的 Harness。两种身份各自确定，但这不能证明 Knowledge Bundle 对应 Consumer Project
的依赖集合。因此，升级路径必须先建立兼容身份，再实现自动下载。

ViewCompose 不能为此使用一个虚构的统一框架版本。每个已发布 Artifact 都拥有自己的版本和不可变
源码 Revision，所以兼容身份是一组精确的 `com.viewcompose:<artifact>:<version>` Entry，而不是
一个标量。

## 决策

1. AI 工具 Runtime 版本与框架知识身份相互独立。Runtime 可以比 Knowledge Pack 更新，但只有
   Consumer Project 匹配候选框架 Profile 时才能切换生效 Pack。
2. 每个可供 Consumer 选择的框架 Profile 都记录各 ViewCompose Coordinate、精确版本、不可变的
   40 位发布 Revision、Knowledge Bundle Fingerprint，以及编译/渲染 Harness 使用的精确 Maven
   Coordinate。Profile ID 由这些规范数据按内容寻址产生。
3. 只有从记录的 Artifact 发布 Revision 生成的 `released` Knowledge Pack 才能供 Consumer
   选择。`current-source` Bundle 仍适用于精确源码 Checkout 和贡献者 Workflow，但绝不会被推断为
   代表已发布 Consumer Project。
4. Project 检测必须有界且只读。它可以解析精确 Gradle Literal Coordinate、标准 Version Catalog
   声明和 Dependency Lock Record，但不得执行 Consumer Gradle Settings、Plugin、Task 或任意
   Build Logic。
5. 每个检测到的 ViewCompose Artifact 都必须只有一个精确版本。Dynamic Version、Range、无法
   解析的 Variable 或 Alias，以及冲突声明都必须拒绝初始化或升级；除非后续另行治理的显式解析机制
   能证明精确依赖图。
6. 不含 ViewCompose Dependency 的 Project 属于新项目，可以选择最新稳定 Consumer Profile。
   如果 Project 已含 ViewCompose Import 或 Coordinate，但版本无法解析，则不能视为空项目。
7. 升级只选择框架 Profile 与所有已检测 ViewCompose Artifact 及版本精确匹配的最新 AI 工具
   Release。它绝不会先选择最新 Release，也不会静默修改 Project 的框架依赖。
8. 在修改 Project 前，下载候选必须复现不可变 Tag、声明的 Asset Inventory、SHA-256 Checksum、
   Package Metadata、框架 Profile 和支持的 Contract Major。
9. MCP 配置和规范 Skill 作为一个事务迁移。只有精确的既有受管理字节可以替换；用户修改内容、未知
   MCP Owner、不兼容 Profile 或任何写入失败，都必须保持旧接入有效。
10. Package 使用版本化并行安装，使正在执行的 Upgrader 与最后可用 Package 在迁移成功前都保持
    可用。Cache 清理是独立的可恢复操作，不属于升级事务。

## 影响

- 框架 API 更新后，必须先产生新的 Released Knowledge Pack 与兼容 Profile，Agent 升级才能在
  Consumer Project 中使用这些 API。
- 如果新工具 Release 声明同一精确框架 Profile，Runtime 或 Skill 修复可以升级而不改变框架知识。
- 使用受支持旧框架的 Project 会停留在该 Profile 所兼容的最新工具 Release，而不是跟随全局
  `latest` Pointer。
- 把版本隐藏在任意 Convention Logic 后的 Project 需要后续显式解析路径。失败关闭可能多出一个
  可操作设置步骤，但能避免无提示 API Drift。
- 独立模块版本会让兼容 Manifest 更大，但它忠实保留框架发布模型，并允许 Project 只使用其中的
  匹配子集。

## 被否决的方案

### 始终安装最新 AI 工具 Release

否决原因：工具发布时间不能说明已有 Project 可用哪些框架 API。编译修复也不能让不存在或语义已变的
API 变得正确。

### 只比较一个主要 ViewCompose Artifact 版本

否决原因：模块独立版本化，Application 通常会组合来自不同发布 Revision 的 UI、Material、
Navigation、Lifecycle、Image 和 Preview Artifact。

### 让模型从编译错误推断兼容性

否决原因：错误知识在编译前已经影响生成；编译不能覆盖所有语义契约，也不能证明一个碰巧能编译的替代
实现保留了请求行为。

### 默认执行 Consumer Gradle Build 获取解析图

否决原因：这会执行不受信任的 Project Settings、Plugin 和 Build Logic，违反只读 Consumer
边界。未来若提供 Opt-in Resolver，必须另行定义授权、隔离和证据契约。

## 验证与推进

1. 框架兼容 Profile Schema 与 Example 必须持续通过 Phase 0 Contract Gate。
2. Released Pack Generator 必须依据不可变发布历史证明 Artifact 版本与源码 Revision，并拒绝
   未发布或可移动身份。
3. Project 检测 Fixture 覆盖 Literal、Version Catalog、Lock Record、新项目、Dynamic Version、
   冲突声明、不支持 Artifact、路径穿越和符号链接，且不得调用 Gradle。
4. 候选解析测试证明精确子集匹配、同 Profile Runtime 升级、旧 Profile 保留、无候选、Checksum
   失败和 Contract Major 拒绝。
5. 安装后 Package 必须在开放公共 `upgrade` 命令前，为 Codex、Claude Code 与 Cursor 复现成功、
   冲突、中断、Rollback 和恢复。
6. 公共文档必须区分当前安装的 Runtime 版本、生效框架 Profile、Knowledge Bundle Fingerprint 和
   已取得的证据 Lane。
