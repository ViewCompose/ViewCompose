---
translation_source: project/api-documentation-quality.md
translation_source_hash: 33f43fab63f5161905e63b4f80dbc0d452402c2f97b1fac5a18e8fd0647d2480
translation_status: current
---

# 源码文档与 API 注释规范

## 目的

本文定义 ViewCompose 的强制源码文档风格，覆盖公共 KDoc/Javadoc、受保护扩展点和长期实现注释，
把[文档治理规范](documentation-governance.md#kdoc-与-javadoc-契约)中的 API 文档契约转为
可重复执行的写作、审查与自动化标准。

目标不是增加注释数量，而是让使用者无需阅读实现、猜测生命周期或在线上发现失败行为，就能
正确使用 API。

## 规范依据与优先级

ViewCompose 参考 Kotlin library、AndroidX、Jetpack Compose 和 Android framework：

- [Kotlin KDoc 语法](https://kotlinlang.org/docs/kotlin-doc.html)；
- [Kotlin 编码约定](https://kotlinlang.org/docs/coding-conventions.html#documentation-comments)；
- [AndroidX KDoc 指南](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/docs/kdoc_guidelines.md)；
- [Compose 组件 API 指南](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md#documentation-for-the-component)；
- [Android API 指南](https://android.googlesource.com/platform/developers/docs/+/master/api-guidelines/index.md)。

发生冲突时以本文为准。ViewCompose 采用 AndroidX library 规则：每个公共 API 元素都显式记录，
同时不复述签名已经表达的信息。文中的 **MUST**、**MUST NOT**、**SHOULD**、**SHOULD NOT**
和 **MAY** 具有规范效力。本基线最近于 2026-08-02 对照上游文档复核。

## 范围

本规范适用于已发布 Maven 制品、会被 Dokka 渲染的：公共 type、type alias、annotation、
constructor、property、function、extension，以及作为正式扩展点的 protected declaration、同制品
Java API 和尚未到移除版本的 deprecated API。

无需为 Dokka 抑制的编译器生成/显然成员、private/internal 实现、test fixture、Demo API，或契约
完全不变的 override 单独写注释。未记录的 protected member 不能通过隐藏 Dokka 解决：要么把它
文档化为支持的扩展点，要么降低可见性。

private/internal 声明无需覆盖率注释，但非显然 invariant、workaround、算法、并发或性能决策仍须
遵循[实现注释](#实现注释)规则。

## 文档完成定义

文档属于实现，不是后续清理。一个变更只有在同一 PR 完成以下工作才算完成：

1. 记录每个新增已发布 declaration 和每个变化的 public/protected contract；
2. 同步参数名、默认值、状态所有权、生命周期、线程、失败、性能与 Android interop 变化；
3. 为 Q3 API 新增或更新可编译 sample；
4. 按影响矩阵更新所属模块手册与 migration/release 材料；
5. 运行所属模块文档 audit 和相关行为测试。

既有未触及债务可以按有效计划渐进修复，但不能用来合并新债务。placeholder KDoc、`TODO` 文档、
复制签名或“之后补文档”都不满足完成定义。

## 权威语言

公共 API 与长期实现注释以英文为权威语言。一个 KDoc block 中不得同时维护完整中英文副本。
修改声明或审查 API family 时，应把旧双语 block 收口为一份准确英文契约。中文教程和模块手册
可以解释 API 并链接权威生成 Reference；标识符、代码、单位与平台术语保持不变。

## 质量级别

| 等级 | 含义 | 验收 |
| --- | --- | --- |
| Q0 — 缺失 | 无有用文档，或只复述名称 | 新发布 API 永不接受 |
| Q1 — 可发现 | 说明用途和可观察结果 | 仅适合不言自明的常量、enum entry、marker type 等低风险声明 |
| Q2 — 契约完整 | 覆盖本文所有适用行为字段 | 普通 public/protected API 最低要求 |
| Q3 — 有指导 | Q2 加可编译 sample、决策指导或运维示例 | 高风险或非平凡 API family 必需 |

一组不言自明 enum entry 或常量可由所属 type 统一描述，但只有每个成员的映射都无歧义时有效。

## 契约字段

每个 declaration 先用一句话完成“This API…”的语义，再加入所有影响正确使用的适用字段：

| 关注点 | 必需信息 |
| --- | --- |
| 行为 | 可观察结果、重要 invariant、幂等与顺序 |
| 输入 | 含义、单位、坐标空间、范围、默认值和 sentinel |
| 输出 | 所有权、可变性、snapshot/live、null 语义和 identity |
| 状态 | owner、保留、恢复、观察与重组关系 |
| 生命周期 | 启动、attach、dispose、reuse 与销毁后行为 |
| 并发 | 线程限制、同步、重入、取消与 last-writer 策略 |
| 回调 | 时间、线程、频率、顺序与重入支持 |
| 失败 | 校验、异常、部分副作用、rollback、重试和 fallback |
| Android | API level、host、配置变化、资源/主题和平台差异 |
| 性能 | 复杂度、分配、缓存、阻塞和影响 API 选择的成本 |
| 兼容 | 稳定性、experimental opt-in、弃用替代和迁移限制 |

拥有 mutable state/resource、跨 Android host、启动异步工作、暴露 callback/Flow、参与事务、接受
单位/坐标、执行 I/O、具有非显然恢复或重要性能取舍的 API 都属于高风险，要求 Q3。

## 各类声明规则

### Type 与 constructor

- 首句用使用者语言定义 abstraction，不得只说“Represents”或重复 type 名。
- 说明角色、owner、lifetime 和重要 non-goal。
- type parameter 用 `@param`，promoted primary-constructor property 用 `@property`，其他公开
  constructor 参数用 `@param`，顺序与声明一致。
- 只有 construction 本身有行为、校验、owner 或副作用时才用 `@constructor`。
- 说明调用者直接构造还是使用 factory/builder/`remember`。
- interface/abstract class 记录实现义务、回调顺序、生命周期、线程与允许默认行为。
- sealed hierarchy 说明消费者是否 exhaustive 处理，以及兼容版本能否新增 subtype。

### Function 与 property

- action 以 active present-tense verb 开头，query 以“Returns”开头，不写“This function”。
- 记录全部 type parameter、extension receiver 和 value parameter，解释语义、单位、坐标、范围、
  默认或 sentinel，不复述 type/name。
- 非 `Unit` function 使用 `@return`，说明 owner、identity、可变性、缓存、snapshot/live、null 和失败。
- mutable property 说明写入者、观察方式、线程与 lifecycle；read-only 在相关时说明 live/snapshot/
  cached/derived。
- Boolean 说明 `true` 的效果；overload 说明各自语义差异。

### 常量、enum、annotation 与 type alias

- 常量说明单位、格式、有效用途和 sentinel；enum entry/sealed subtype 必须语义明确。
- annotation 说明行为、target、retention、tool/runtime 消费方式和缺失时行为。
- type alias 说明它只是源码别名而非独立 runtime type，并解释命名或迁移目的。
- 默认值选择策略时，解释其含义，不只复制 literal。

### Suspend、Flow 与 callback

说明何时开始、dispatcher/线程、取消传播与部分副作用。Flow 还说明 cold/hot、replay、completion、
error propagation 和 collection lifecycle；callback 说明时间、顺序、次数、线程与重入。

### Override 与继承文档

契约不变的 override 应省略重复 KDoc。若缩窄行为、改变调度/副作用/失败或增加 Android 特性，
必须只记录差异并链接 base contract。protected override point 说明 subclass 可调用内容、是否要求
`super` 以及必须保持的 invariant。

### Deprecated 与 experimental API

`@Deprecated` 命名并尽量链接可用替代项，说明无法机械替换的语义差异。experimental API 说明
不稳定契约和 opt-in annotation。移除时间线放在 migration/release 文档并由 API 注释链接。

## KDoc 与 Javadoc 形式

### 结构与语气

- 首段是独立、简洁、最好一句话的 summary，供 Dokka index/search 使用。
- 从使用者视角用主动现在时描述可观察行为，不叙述实现步骤。
- summary、详情、示例和 block tag 之间空一行；KDoc 使用段落与短 Markdown list。
- 相关 overload/type 之间链接共享契约，不复制长文本。
- 保留 `View`、`ViewGroup`、`VNode`、`NodeSpec`、`Modifier`、`Flow` 等规范大小写。

### KDoc tag 与顺序

适用 tag 的顺序为：

1. 行为正文后的 `@sample`；
2. 声明顺序的 type `@param T`；
3. extension `@receiver`；
4. constructor 的 `@property`/`@param` 或 function `@param`；
5. `@return`；
6. 按调用者遇到顺序排列的 `@throws`；
7. class-qualified `@see`；
8. 只有首个已发布制品版本确定时使用 `@since`，不得写 `Unreleased`、Git branch 或仓库总版本。

tag 名必须解析到 declaration。KDoc type parameter 写 `@param T`，不写 Javadoc 的
`@param <T>`。Kotlin 弃用使用 `@Deprecated` 和安全可表达的 `ReplaceWith`；不得用 `@suppress`
隐藏意外公共 API，应修可见性或边界。

### 链接、literal 与示例

- 用 `[RenderSession]` 等 KDoc link；不创建 self-link。
- 需要限定解析时用 `[label][Qualified.symbol]`；`@see` 使用纯 symbol target。
- literal、Gradle coordinate、参数名和 `null`/`true`/`0` 等值使用反引号。
- 外链指向权威稳定文档，已发布行为不得依赖指向 `main` 的可变源码链接。
- 非平凡用法放入可编译 `@sample`，避免漂移的 inline example。

### Javadoc

Java API 使用相同契约内容但采用 Javadoc 语法：`{@link Type}`、`{@code value}` 和 HTML
结构。不得把 KDoc Markdown 复制进 Javadoc，也不得把 HTML 格式复制进 KDoc。

### 禁止内容

- 只说“Represents”“Handles”“Gets”“Sets”“Callback for”而无使用者契约；
- 双语重复段落、作者简介、changelog、issue history 或营销；
- 除受支持性能/interop 契约外的实现叙述；
- 未解释缩写、歧义代词，或未定义边界的“normally”“etc.”“safe”“thread-safe”；
- 测试与实现未保护的承诺；
- 注释掉的代码、旧 debug note 和文档 placeholder。

以下形式聚焦契约：

```kotlin
/**
 * Applies [block] to the current composition and commits its state when rendering succeeds.
 *
 * Only one prepared composition may be active at a time. If [block] fails, the previous slot and
 * observation state remains active and the exception is propagated to the caller.
 *
 * @param block computation executed in the current composition context.
 * @return the value produced by [block] after a successful commit.
 * @throws IllegalStateException if another prepared composition is still active.
 */
fun <T> composeRoot(block: () -> T): T
```

## ViewCompose 专用契约

### DSL 组件与布局容器

公共 `UiTreeBuilder` DSL 文档按以下顺序：用途与原生 UI 角色；能力与可观察行为；controlled/
uncontrolled state 和事件顺序；测量/放置/裁切/滚动/content slot；Modifier、Environment、theme、
density、layout direction、accessibility；只有稳定时才记录 Android View 映射；standalone/non-trivial
state 的可编译 sample；全部参数与 content receiver。

不能因 API 名相似就声称 Compose 等价，必须记录语义差异或链接维护中的比较/迁移页。

### Modifier 与 parent data

说明影响 construction/measure/layout/draw/input/semantics/platform binding 的哪个阶段、chain 顺序、
数值坐标与单位、no-op/coercion/conflict/parent scope，以及 state/allocation/invalidation/performance。
scope-specific parent data 必须标识消费 parent 和越界行为。

### State、environment 与 effect

state API 说明 owner、observation boundary、equality policy、invalidation、retention、restoration 和
dispose。`remember` 类 API 还说明 key 比较与重建。Environment/Local 说明默认解析、嵌套、snapshot
和观察后代。effect/async API 说明 start/commit/dispose、取消、rollback/partial effect、线程，以及
composition 或 Android host 销毁后的行为。

### Android interop 与原生资源

说明 main-thread、原生 View owner/reuse、attach/detach、Lifecycle/SavedState owner、配置变化、
theme/resource lookup、最低 API 和 fallback。只有消费者可作为兼容契约依赖时才记录具体 native
class 映射。

## 写作模板

使用能完整表达契约的最小模板，删除不适用段落，不留 placeholder heading。

### 有状态 DSL 组件

```kotlin
/**
 * Displays one selectable destination and reports user requests through [onSelected].
 *
 * Selection is controlled by [selected]. The component does not mutate caller state; invoke a
 * state update from [onSelected] to reflect the new selection. The callback runs on the Android
 * main thread after the click is accepted and before the next render pass.
 *
 * [modifier] is applied to the component's root node. The component resolves colors and shape
 * tokens from the current [Environment] during rendering.
 *
 * @sample com.viewcompose.samples.navigationDestination
 * @param selected whether this destination is rendered as selected
 * @param onSelected callback invoked for an accepted user selection request
 * @param modifier modifiers applied to the root node in chain order
 * @param content content rendered inside the destination
 */
fun UiTreeBuilder.NavigationDestination(
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
)
```

### 有状态资源 owner

```kotlin
/**
 * Creates a render session that owns the native view tree until [RenderSession.dispose].
 *
 * Calls are confined to the Android main thread. Disposing the session detaches observations and
 * releases host references; subsequent render attempts throw [IllegalStateException].
 *
 * @param host Android host that owns the rendered view hierarchy
 * @return a new session with no rendered root
 * @throws IllegalStateException if [host] is already bound to an active session
 */
fun createRenderSession(host: RenderHost): RenderSession
```

模板只演示形式，不能复制为契约；每句话都要从所属实现与测试验证。

## 实现注释

实现注释保存代码无法表达的原因：Android workaround 或特殊顺序、跨函数/模块 invariant、并发/
owner/rollback/lifecycle 假设、算法/分配取舍及阈值，以及看似简单方案为什么错误。

局部原因用 `//`，后续区域适用的算法/invariant 用短 block comment。不得逐行叙述；能用命名
function/type 或 test 表达时优先用结构。`TODO` 必须包含 issue URL/编号、具体缺失结果和删除条件，
且不得作为公共 API 文档；使注释失真的同一变更中删除旧注释。

## Sample

Q3 使用 `@sample`。目标函数必须在维护的 sample/test source set 中编译并只用 public API，一次
演示一个聚焦契约。短 literal、命令或签名可以 inline；非平凡 standalone snippet 必须来自可编译
代码或有 compilation test。无法编译的 sample 是 API 文档失败。

## 生成 Reference 要求

除注释质量外还要求 public/protected coverage、模块名和独立版本、固定到 tag/immutable revision
的源码链接、可解析 KDoc 与依赖链接、保留 deprecated API 与替代建议，以及抑制无价值生成成员。
source-link pinning 与 package/module overview 不应通过每个注释硬编码链接模拟。

## 自动与人工门禁

Dokka 的 `reportUndocumented` 报告无文档可见声明，`failOnWarning` 把所有 warning 变成失败。

```bash
./gradlew auditViewComposeApiDocs

./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime,viewcompose-ui-contract

./gradlew verifyAssembledViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime
```

所有 published module 都是 strict。机械覆盖无法判断 Q1/Q2/Q3；reviewer 必须应用契约矩阵、验证
sample，并拒绝只复述名称/签名的注释。

## 强制执行

初始 rollout 已完成，以下规则永久生效：

1. 所有新增/变化 public/protected declaration 在同一 PR 遵循本规范；
2. published module 必须在 `apiDocs.strictModules`，发布验证拒绝遗漏；
3. `auditViewComposeApiDocs` 全仓启用 `reportUndocumented` 与 `failOnWarning`；
4. 本地选定生成同样严格，并验证 alias 与 immutable source link；
5. 生产文档使用拒绝 partial module/catalog 的 `verifyCompleteViewComposeApiDocs`；
6. 不允许永久 undocumented allowlist；临时例外必须在有效计划写 owner、原因和移除 milestone。

## 审查清单

- 文档与 API 同时编写；Q level 与风险匹配。
- 首段解释用途/结果，所有适用字段与测试/实现一致。
- type parameter、receiver、property、parameter、非 `Unit` result 按顺序记录，异常/弃用完整。
- state、async、callback、Android 和 resource owner 行为明确。
- DSL 记录 state owner、layout/content、Modifier/environment、accessibility 与稳定 native mapping。
- Q3 sample 可编译；链接可解析且已发布行为不指向可变分支。
- 注释为权威英文，无完整翻译副本；实现注释只解释原因/invariant，无旧 narration/TODO。
- 格式或 symbol 关系变化时检查生成页面。
