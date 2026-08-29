---
schema_version: 2
document_id: architecture.ai-verifiable-development-tooling-boundary
doc_type: architecture
slug: /architecture/decisions/ai-verifiable-development-tooling-boundary
owner:
  kind: project
  id: ai-development-tooling
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
invariants:
  - 面向 AI 的知识必须由规范签名、Governance V2 记录、可编译 sample、文档和发布元数据生成；任何传输层都不得维护平行 API 清单。
  - knowledge、static、compiled、rendered 与 compared 是逐层累积且彼此不同的证据等级，较浅结果不得宣称更深状态。
  - AI 工具、项目检查、编译、转换、模型访问、凭据与缓存必须位于 ViewCompose 运行时制品和应用进程之外。
  - 不可信代码片段不得执行被检查项目的构建逻辑；编译使用固定的工具自有 harness，项目分析默认受限且只读。
  - 迁移和视觉输入使用带来源与不支持语义的工具侧 Design IR，不复用运行时 VNode 或渲染器状态。
evidence:
  - tools/ai/contracts/versions.json
  - tools/ai/contracts/knowledge-bundle-manifest.schema.json
  - tools/ai/contracts/tool-envelope.schema.json
  - tools/ai/contracts/design-ir.schema.json
  - tools/ai/evaluation/metrics.json
  - tools/ai/evaluation/corpus.json
  - tools/ai/scripts/verify-phase0.mjs
  - docs/project/plans/ai-verifiable-development-tooling.md
  - ./gradlew verifyAiToolingContracts
  - ./gradlew verifyDevelopmentToolingIsolation
translation_source: architecture/decisions/0025-ai-verifiable-development-tooling-boundary.md
translation_source_hash: 61912f686399ab2209b5a3ff0e749a8c7e957ee82b69343ca0171f7de43a4297
translation_status: current
---

# ADR-0025：AI 可验证开发工具边界

- 状态：已接受
- 日期：2026-08-29

## 背景

ViewCompose 已经具备规范公开签名与 KDoc、Governance V2 能力所有权、可编译文档 sample、
版本化制品元数据、结构化运行时诊断和 Layoutlib Preview runner。但编码 Agent 仍可能臆造 API、
选择错误制品或版本、误解生命周期规则，或者返回从未编译的代码。

如果只增加 `llms.txt`、MCP Server 或特定 Provider 的规则，只会通过新传输层暴露相同失败。
手写 MCP 清单还会与生成的 Capability Reference 漂移。把解析或符号查询冒充编译会错误标记证据；
运行被检查项目的 Gradle 则会执行任意构建脚本和插件。

迁移和视觉生成还会带来另一类风险。Android XML、Compose 源码、截图和 Figma 文档包含资源间接
引用、行为、状态、无障碍、不确定性和不支持内容，运行时 `VNode` 无法表达这些信息。把模型
Provider SDK 或凭据放入框架运行时制品，也会违反五层依赖模型和 ADR-0009 的按请求工具隔离。

这些边界会影响后续每项工具，并且在客户端产生依赖后很难修改。因此，实现开始前必须先接受一项
架构决策并建立可执行契约。

## 决策

### 规范知识血缘

面向 AI 的知识只有一条血缘：

```text
公开签名与规范 KDoc
  + Governance V2 能力、文档、sample 与制品记录
  + 可编译 sample 源码区段
  + 发布与版本元数据
  -> 确定性 AI Knowledge Bundle
  -> llms.txt、本地搜索、验证器、CLI、MCP、Skill 与评测
```

Bundle 由生成器产生；传输层不得抓取或维护另一份公开 API 清单。Manifest 记录精确框架标识、
源码修订、能力指纹、生成器版本、Schema 版本、文件哈希、大小和数量。同一输入必须逐字节生成
相同结果。任何本应改变 Bundle 的输入漂移，都会阻断新鲜度门禁，直到重新生成。

`llms.txt` 是紧凑发现入口。它指向精确版本通道、Bundle、规范文档、常见不变量、sample 与工具，
而不是 KDoc 或 Capability Reference 的完整副本。

### 独立版本契约

四类契约独立演进：

1. ViewCompose 框架与制品标识；
2. AI Knowledge Bundle Schema 与生成器；
3. 请求/结果工具 Envelope 与传输适配器；
4. 工具侧专用 Design IR。

评测语料和指标拥有独立 Schema，因为在工具升级之间也必须保持分母和阈值可复现。
`current-source` 表示一个精确 Git 修订，`released` 表示精确坐标和版本。两者都不接受 `latest`、
分支名或其他可移动别名作为身份。

兼容策略是主版本精确匹配。Consumer 遇到不支持的新主版本时必须拒绝并返回稳定诊断，不得猜测
字段、静默降级，也不得跨 Schema、框架、SDK、配置或生成器通道混用缓存。

### 预留能力所有权与质量等级

Phase 0 预留以下稳定身份。只有出现应用侧符号时才创建 Governance V2 记录，因此规划切片不会
创建占位符号或 sample。

| 能力 ID | 激活阶段 | 初始质量等级 | 适用契约重点 |
| --- | --- | --- | --- |
| `tooling.ai-knowledge` | Phase 1 | Q3 | 版本、血缘、新鲜度、确定性、依赖、限制 |
| `tooling.ai-validation` | Phase 2 | Q3 | 版本、线程、执行、安全、诊断、限制、性能 |
| `tooling.ai-protocol` | Phase 3 | Q3 | 版本、传输、兼容、取消、安全、限制、错误 |
| `tooling.ai-migration` | Phase 4 | Q3 | 源/目标版本、保留、不支持语义、来源、验证 |
| `tooling.ai-visual-generation` | Phase 5 | Q2 | Provider、隐私、资产、配置、比较、修复、限制 |

视觉生成从 Q2 开始，因为模型和感知差异无法支持稳定 Q3 声明。只有 Phase 6 的纵向证据冻结了
支持的 Provider、配置、容差、回退和兼容性后，才能提升到 Q3。其余能力激活时以 Q3 为目标，
因为受支持工具必须已经具备确定性契约和可执行 fixture，不能以不可验证的公开 Endpoint 交付。

### 进程、依赖与 Provider 隔离

具体 AI 工具是下游 `tools/` 进程或独立分发的开发制品。Runtime、UI Foundation、Android
Engine、Design System、Integration 和应用聚合制品都不得依赖它。Release classpath 不含 MCP
库、Parser、编译 harness、转换引擎、模型 SDK、网络客户端或 AI 缓存。

AI 工具不得因为依赖存在就向应用进程安装观察器或 Receiver。Preview 渲染可以复用现有显式
Debug/Test 工具协议，但激活仍必须遵守 ADR-0009 与 ADR-0022。非激活 Release 路径的 AI 自有
I/O、遍历、序列化、网络、线程、Listener、Callback、报告写入和周期任务都必须为零。

ViewCompose 不选择模型 Provider。Provider Adapter 是仅在显式请求下激活的可选下游进程。凭据
保留在客户端 Secret 机制中，绝不进入请求、源码、生成代码、截图、诊断、缓存、日志或证据。
确定性知识、静态验证、编译、渲染、转换和比较，在没有 Provider 或网络时仍然可用。

### 累积证据等级

每个工具结果声明以下等级之一：

| 等级 | 必需证据 |
| --- | --- |
| `knowledge` | 精确 Bundle 指纹、框架身份、能力/符号/sample 血缘 |
| `static` | knowledge 加确定性 Parser/Index/Rule 诊断 |
| `compiled` | static 加固定工具自有 Kotlin/Android 编译成功 |
| `rendered` | compiled 加 Preview runner 配置、输出指纹、Tree 与诊断 |
| `compared` | rendered 加具名结构、文本、语义、几何、资产和可选感知检查 |

证据逐层累积。Parser、符号匹配或生成字符串不得报告 `compiled`；只有编译也不得报告
`rendered`；像素或视觉相似度不得覆盖编译、语义、无障碍、不支持内容或安全失败。

结果使用稳定 `VC-AI-*` 诊断码，并记录严重程度、受限安全消息、下一步、适用源码位置、制品/
能力身份、耗时、缓存状态、截断状态，以及实际通过的最深证据。

### 不可信编译与项目检查

`validate_code` 只把提交源码和声明的有界资源放入工具自有 Harness。Harness 固定到已接受的
JDK、Kotlin、AGP、Android SDK、依赖坐标和 ViewCompose 身份。Allowlist 排除被检查项目的
Plugin、Settings、Repository、Init Script、Annotation Processor、Build Service、Task、Test 与
Shell Command。

`analyze_project` 默认只读。调用方提供一个规范化 Root；Analyzer 拒绝路径穿越和符号链接逃逸，
按策略忽略 Secret 与输出文件，并限制文件数量、字节数、深度、时间和输出。源码文本是不可信数据，
不是 Agent 指令。迁移写入路径必须先返回带源码到输出映射的有界 Patch Plan，再由客户端显式应用。

内容寻址缓存必须有界、可淘汰，并按所有契约和环境通道隔离。取消或超时必须停止子进程及其后代、
关闭文件、丢弃部分缓存，并返回稳定失败证据。

### 工具侧专用设计中间表示

XML、Compose、Prompt、截图和 Figma 输入收敛到开发工具拥有的版本化 Design IR。它表达节点
类型、布局关系、类型化属性、Modifier、资源、语义、事件占位、状态/可见性表达式、来源、置信度、
不支持源码和稳定身份。

IR 不是运行时 `VNode`、Renderer Node 或 Android `View`。这些类型表示已经可执行的树，无法
保留不完整源码、行为占位、Design Token、不确定性或不支持片段。因此 Runtime 兼容不等于 IR
兼容，IR 变化也不增加运行时依赖。

代码生成保留不支持片段和来源，再使用同一静态、编译、渲染与比较管线。它不得静默丢弃 Listener、
Expression、自定义节点、资源、状态/Effect 契约或不确定视觉决策。

### Phase 0 固定指标

`tools/ai/evaluation/metrics.json` 是机器可读权威。初始门禁包括：

- 不确定或过期知识文件为零，并完整解析所有规范能力；
- Top-5 检索 Recall 至少 0.95，精确符号查询的 Reciprocal Rank 为 1；
- 拒绝语料中所有臆造或已移除符号；
- 每条规则 Precision 至少 0.95、Recall 至少 0.90；
- 声明支持的 fixture 编译与渲染成功率均为 100%；
- 对抗安全 fixture 100% fail-closed，Release Runtime 出现次数为零；
- 同一核心请求的 CLI 与 MCP 语义差异为零；
- 声明支持的 XML 子集编译率 100%，标记的不支持迁移语义报告率 100%；
- 声明视觉语义精确匹配，至少 0.98 的几何节点位于固定容差内，并且无振荡修复最多五轮。

每项指标通过稳定语料 Case ID 指定分母、方向、阈值、单位和环境。模型相关报告还必须记录
Provider、Model、配置和日期，且不能代替任何确定性门禁。

## 威胁模型与必需控制

| 威胁 | 必需控制 | 失败行为 |
| --- | --- | --- |
| 臆造、移除或错误版本 API | 生成式 Bundle 血缘、静态 Index、隔离编译 | 通过符号/制品/版本诊断拒绝 |
| 恶意 Gradle、Settings、Plugin 或 Annotation Processor | 不执行项目构建逻辑；固定 Harness 与 Allowlist | 创建进程前拒绝操作 |
| 路径穿越或符号链接逃逸 | 规范 Root 包含检查与逐段符号链接策略 | 读写前拒绝 |
| Secret、签名密钥、Local Properties 或凭据泄露 | 默认排除、内容脱敏、受限安全诊断、禁止 Secret 日志 | 省略内容并报告脱敏 Finding |
| 源码或设计文本中的 Prompt Injection | 将检查内容视为数据；确定性操作忽略内嵌指令 | 只按源码数据保留文本 |
| 依赖替换或远程仓库漂移 | 精确坐标、Checksum/Lock、Repository Allowlist、离线确定性核心 | 依赖无法解析或不匹配时 fail-closed |
| 源码、资源、Zip、XML 或图片炸弹 | 字节/数量/深度/尺寸/解码/时间/输出限制 | 取消并返回限制诊断，不保留部分缓存 |
| 挂起或派生的编译/渲染进程 | Deadline、进程树取消、隔离输出、清理验证 | 终止后代并丢弃部分证据 |
| 缓存投毒或跨版本复用 | 内容寻址加 Schema/框架/工具链/配置 Namespace | Miss 或淘汰，绝不复用模糊条目 |
| 不安全 Patch 应用 | 默认只读、显式 Patch Plan、Root 包含、Preimage 指纹 | 拒绝已变化或逃逸目标并保留源码 |
| 携带凭据的 Provider 请求或日志 | 显式 Adapter、BYO Secret Channel、脱敏、默认无遥测 | 阻断请求或省略敏感字段 |
| 像素正确但语义错误的输出 | 累积证据和独立语义/无障碍门禁 | 无论视觉得分如何都判定比较失败 |

剩余模型风险包括不确定性、ViewCompose 无法控制的 Provider 保留、视觉歧义和源码许可证约束。
Provider Adapter 必须公开这些风险，即使确定性阶段稳定，也可以继续保持实验状态。

## 后果

### 正向结果

- 所有客户端消费同一版本化框架事实源。
- MCP、CLI、Skill 和未来 Adapter 共享一个核心，不重复实现验证。
- 编译与渲染能产生驱动有界修复的证据。
- 运行时制品保持 Provider 中立，不携带 AI 工具开销。
- 不支持的迁移和视觉语义会被保留供审查，而不是消失。
- 实现开始前，准确率与产品声明已经拥有可复现分母。

### 成本

- Phase 0 和知识生成会推迟可见 MCP 功能。
- 隔离 Android 编译与 Preview 渲染需要工具链打包、缓存、资源、取消和兼容工作。
- 严格版本身份拒绝方便但可移动的别名。
- 语料和指标维护成为每项受支持能力变更的必需工作。
- 视觉能力在纵向证据支持更窄稳定契约前保持 Q2。

## 拒绝的替代方案

### 手写 `llms.txt` 和 MCP API 清单

重复清单会与签名、Governance V2、sample、制品和发布漂移，因此拒绝。只有没有规范结构化所有者的
紧凑叙述规则可以手写。

### 让 MCP Server 调用首选模型

这会把凭据、网络、成本、隐私和 Provider 生命周期绑定到框架工具，并使确定性评测失效，因此拒绝。
模型由客户端编排。

### 只通过解析或符号查询验证

静态方式无法证明 Kotlin Overload、Receiver、类型、资源、依赖和编译器行为，因此拒绝。静态验证
仍然有价值，但不能宣称编译成功。

### 运行用户 Gradle 项目完成真实编译

构建脚本和 Plugin 是以用户权限执行的代码，因此拒绝。唯一受支持的编译执行器是工具自有 Harness。

### 复用运行时 `VNode` 作为转换 IR

可执行 Runtime Node 无法忠实表达来源、不确定性、资源、Style、事件占位、不支持源码和模型置信度，
因此拒绝。

### 把截图相似度当作正确性

像素可能掩盖文本语义、无障碍、行为、资源、不支持内容或无效代码，因此拒绝。

## 验证

Phase 0 门禁为：

```text
./gradlew verifyAiToolingContracts
```

它会验证契约 Schema 身份与主版本、示例 Envelope 与 IR、预留能力 ID 和质量等级、版本通道不可
移动性、指标定义、唯一 Case ID、指标分母、规范能力引用、fixture 包含关系、必需类别覆盖，以及
正反例诊断预期。该门禁属于 `qaQuick`。

后续阶段增加实现门禁，但不得削弱本契约。任何被接受的架构变化都必须新增 ADR，或采用明确兼容的
Schema 修订；不得为了实现捷径静默改写本项已接受决策。
