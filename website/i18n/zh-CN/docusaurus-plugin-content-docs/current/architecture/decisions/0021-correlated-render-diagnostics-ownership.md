---
translation_source: architecture/decisions/0021-correlated-render-diagnostics-ownership.md
translation_source_hash: 8e56d53c966d20f2da5c04e1fc9571abc7508588a2778e5b1d8497b763dadeb5
translation_status: current
---

# ADR-0021：关联式渲染诊断归属

- 状态：已接受
- 日期：2026-08-23
- 相关：ADR-0009 开发工具隔离、ADR-0011 预取 Session 激活、ADR-0012 惰性布局逻辑/物理
  归属，以及 ADR-0015 可观察属性事务

## 背景

诊断目前分散在 `onRenderStats`、`onRenderResult` 和 `onRenderFailure`。根节点可安装三者，
惰性列表和分页 Session 只传播结果，导航只传播失败，浮层则全部不传播。延迟条目还可能在
新的 Local 快照省略监听器后保留旧结果监听器，跨越逻辑归属。帧 ID、源码工具 ID 和
`UiSourceSessionRole` 无法关联这些路径，也无法区分物理 View 复用和新的逻辑拥有者。

当前仍处于 alpha 兼容阶段，可以硬切；适配器只会保留多套不完整的归属体系。ADR-0009
同时禁止可选工具未激活时产生周期回调、遍历、序列化、写入或逐节点时钟读取。

## 决策

### 身份与归属图

Phase 1 引入以下公共契约：

```kotlin
@JvmInline
value class RenderSessionTraceId internal constructor(val value: Long)

enum class RenderSessionRole {
    Host, Preview, NavigationDestination, LazyItem, PagerPage, OverlaySurface,
}

data class RenderDiagnosticContext(
    val sessionId: RenderSessionTraceId,
    val parentSessionId: RenderSessionTraceId?,
    val role: RenderSessionRole,
    val frameId: Long?,
    val eventSequence: Long,
    val monotonicTimestampNanos: Long,
)
```

Trace ID 是构造器为 internal 的非零、进程内单调递增 `Long`。它不是随机值、可保存值、
跨进程重建稳定值，也不能作为应用、分析、导航、惰性条目、账号或无障碍身份。
`eventSequence` 只在单个 Session 内递增；不同 Session 只能通过单调时间戳比较。
`frameId` 仍为 Session 局部值，仅在能证明属于某次同步帧时非空，否则为 `null`。

| 创建者 | 角色与生命周期 | 父级 |
| --- | --- | --- |
| Activity、Fragment、公共低层根节点 | `Host`；主机创建至销毁 | 无 |
| 静态或交互 Preview 根节点 | `Preview`；一次 Preview Session | 无 |
| 导航候选或保留页面 | `NavigationDestination`；候选失败结束，保留时延续 | 所属 `NavHost` |
| 惰性条目、吸顶头或网格条目 | `LazyItem`；跟随逻辑条目而非回收树 | 捕获的条目 Local |
| 横向或纵向分页页面 | `PagerPage`；同 key 移动保留，替换则更新 | 捕获的页面 Local |
| Dialog、Popup 或 Modal Surface | `OverlaySurface`；创建至关闭/销毁 | 捕获的浮层内容 |

即时子节点、标签页、Snackbar/Toast 队列项和渲染器合成 View 都归属于现有 Session。
私有父上下文进入不可变 Local 快照，并只在创建子 Session 时消费一次。缺少上下文即创建
根节点；View 或容器不能保留或恢复父边；显式根诊断会新建诊断树。

Phase 1 删除 `UiSourceSessionRole` 和 `UiSourceSessionContainerHandle`。源码工具对符合条件
的 Host、导航和分页 Session 使用同一 Trace ID、父级和角色。普通惰性条目继续禁止与请求
无关的源码栈采集，但根节点安装 Sink 时会获得关联身份。

### 统一事件契约与硬切

```kotlin
enum class RenderFrameDiagnosticLevel { None, Stats, Tree }

data class RenderDiagnosticCollection(
    val lifecycle: Boolean = true,
    val failures: Boolean = true,
    val frameLevel: RenderFrameDiagnosticLevel = RenderFrameDiagnosticLevel.None,
)

class RenderDiagnostics(
    val collection: RenderDiagnosticCollection,
    val sink: RenderDiagnosticsSink,
)

fun interface RenderDiagnosticsSink {
    fun onEvent(event: RenderDiagnosticEvent)
}

sealed interface RenderDiagnosticEvent {
    val context: RenderDiagnosticContext
}
```

密封族包含 `RenderSessionStarted`、`RenderSessionActivityChanged`、
`RenderFailureObserved`、`RenderFrameCompleted` 和 `RenderSessionEnded`。
`RenderFrameCompleted` 携带权威 `RenderFrameReport` 以及可空的 `RenderStats` 和
`RenderTreeResult`。`Stats` 采集计数，`Tree` 额外采集有界树、补丁、警告和组合详情，
`None` 两者均不构建；回滚帧不暴露候选统计或树。

以下顺序具有规范性：

1. 订阅的开始事件必须最先出现；
2. 原始失败在恢复状态确定后按发生顺序发布；
3. 每次同步尝试在 `lastFrameReport` 定稿后发布一次帧完成事件；
4. 活动事件只在真实状态切换后发布；
5. 结束事件在逻辑清理后发布并终结 Session；
6. 成功准备在激活前保持静默；准备失败发布最小的开始、失败、回滚帧、结束序列。

调用同步执行、单个 Session 内串行，并运行在 Session 的平台线程。Sink 重入渲染、活动
状态变更或销毁会快速失败。Sink 异常不能改变帧或原始失败，也不会递归发布；平台记录它，
本地将其保存为 `DiagnosticsSink` 失败，并对该 Session 永久禁用此 Sink。可选组合 Sink
分别隔离子 Sink。

Phase 1 从所有根 API 移除 `onRenderStats`、`onRenderResult`、`onRenderFailure`，删除
`LocalRenderResultListener`，把 `RenderFailurePhase.DiagnosticsCallback` 改名为
`DiagnosticsSink`，并同时迁移 Demo、Preview、测试与教程。不保留弃用重载、别名、适配器
或双重发布。`lastRenderFailure` 和 `lastFrameReport` 继续作为直接查询。`debug` 仍只控制
日志和慢操作警告；只有非空且不可变的根 `RenderDiagnostics` 决定采集级别并由子级继承。

### 模块归属

`viewcompose-runtime` 持有有限组合计时端口；`viewcompose-ui-foundation` 持有中立身份、事件、
父 Local、失败和计时记录；渲染器持有请求门控的协调/绑定计时与弱 View/Token 快照；主机持有
平台安装、时钟/线程交接和中立发现。新的可选 `viewcompose-diagnostics` 持有有界聚合和 Sink
辅助能力；Preview 持有调试协议、采集、高亮、计时控制、响应和缓存；Studio 插件持有请求、
验证、界面及过期/超时处理。

运行时产物不提供进程全局 Sink。应用显式向根节点传入诊断配置。可选聚合器不包含供应商、
网络、数据库、Worker、Manifest 组件、持久化或上传能力。

### 生产失败聚合

Q3 `BoundedRenderFailureAggregator` 默认保存 64 个指纹（有效范围 `1..128`），默认单调
时间窗口为 15 分钟（有效范围 1 分钟至 24 小时）。不可变快照/重置结果不会改变存活
Session 的 Trace 身份。默认指纹只包含失败阶段、恢复方式、可选操作、异常二进制类型，
以及最多三个仅含类名和方法名的 `com.viewcompose.*` 栈帧；排除消息、应用栈、文件/行号、
原始 key、View 文本、Local、URL、媒体、凭据、Cause、完整堆栈和 `Throwable` 本身。

聚合项包含次数、首次/末次单调时间、最新安全上下文、窗口 ID 与丢弃/淘汰计数。计数在
`Long.MAX_VALUE` 饱和；满容量时确定性淘汰最久未更新项；记录或读取快照时清除过期窗口，
不安装定时器。Sink 或导出失败不回灌。持久化、用户同意、账号关联、跨进程采样、网络上传
与供应商元数据均由应用负责。

### 高亮与计时

`RenderNodeToken` 是从有界请求快照产生的 Q2 进程内不透明值，不含应用 key；节点替换、
跨拥有者复用、不兼容树代际、Session 销毁或请求过期都会令其失效。nonce/trace/token
请求在 Android 主线程解析当前 View 的弱引用，捕获屏幕边界、裁剪边界、挂载和可见状态，
并显示一个非交互浮层。结果区分已选、缺失、过期、已回收、隐藏、完全裁剪、不支持的合成
节点、已结束 Session 和被拒绝请求。

浮层不改变布局、应用状态、焦点、语义、无障碍焦点、触摸或回调。它在五秒、显式清除、
替换、View 释放、Session 结束、窗口/主机销毁或进程停止中最先发生的条件下清除；只保留
View/窗口弱引用，不安装周期布局、滚动、绘制、触摸、帧或重组监听器。

计时只支持组合求值（包含时间和扣除子级后的自身时间）、协调决策（包含时间和自身时间），
以及直接渲染器绑定/补丁操作。一次采集使用同一个注入的单调纳秒时钟。Android 测量/布局、
绘制、GPU、RenderThread、SurfaceFlinger、解码、网络、数据库和外部 SDK 明确不支持。

只有请求会激活计时。一个进程最多一个采集，完成八帧或两秒即结束；每帧最多 64 个节点，
总计 512 条记录，深度 32，最多 128 个不同字符串且截断至 256 个 UTF-16 单元。结果包含
尝试/保留的时钟读取、空时钟对开销、截断、不支持领域与丢弃数。未激活时逐节点时钟读取和
记录/历史分配必须为零；运行时与渲染器只允许一次经批准的可空端口/请求状态检查。

### 绝对上限

| 资源 | 硬上限与溢出行为 |
| --- | --- |
| 存活工具 Session | 每进程 64；先删无效弱引用，再删最旧非活动项并报告丢弃 |
| 最近事件 | 每请求 512、每 Session 64；确定性截断最旧项 |
| 源码候选 | 每 Session 32；每候选 24 调用点、每字符串 1,024 字符、每候选 12 KiB、每 Session 48 KiB |
| 已挂载节点请求 | 访问 2,048、返回 512、深度 64；达到限制即停止并报告截断 |
| 高亮 | 每进程一个、五秒 |
| 失败指纹 | 绝对 128，公共默认 64 |
| 计时 | 每进程一个；8 帧或 2 秒；每帧 64 节点；512 记录；深度 32 |
| Nonce | 1--128 个 ASCII `[A-Za-z0-9._-]` |
| 其他字符串 | 256 个 UTF-16 单元 |
| 序列化响应 | 256 KiB UTF-8，含信封与截断元数据 |

限制在保留/编码下一项之前执行；响应始终有效并报告丢弃；达到限制不能改变渲染行为。

### API 质量

| API 族 | 级别 | 必须说明的契约字段 |
| --- | --- | --- |
| Trace ID、角色、上下文 | Q2 | 行为、输出、身份、进程生命周期、顺序、兼容性、隐私 |
| 采集与帧级别 | Q2 | 输入/默认值、采集行为、状态、性能、兼容性 |
| 诊断、Sink、事件 | Q3 | 行为、归属、生命周期、线程、回调顺序/频率/重入、隔离、性能、兼容性 |
| 变更后的主机 API | Q3 | 默认值、继承、生命周期、主线程、回调/失败行为、性能、硬切迁移 |
| 节点 Token 与高亮 | Q3 族/Q2 值 | 身份、输入、坐标、弱归属、超时、主线程/窗口、状态、性能、兼容性 |
| 计时 | Q3 族/Q2 值 | 单位/时钟、包含语义、生命周期/取消、线程、截断、开销、性能、兼容性 |
| 聚合器与快照/重置 | Q3 | 范围、输出、归属、窗口/重置、同步、隔离、边界、隐私、兼容性 |
| 指纹与聚合值 | Q2 | 含义、脱敏、身份、时间单位、饱和、兼容性 |

某 API 族不拥有 Android 资源/UI、持久化/网络 I/O、恢复或取消时，对应字段不适用。每个
Q3 API 族必须同时提供规范英文 KDoc、可编译示例、所属模块文档、中文公共文档，以及回调
到事件的迁移说明。

## 后果

结果获得独立于 View 复用的统一逻辑归属，且仅失败采集继续保持低成本。Alpha 调用方必须
迁移；慢 I/O 必须在复制有界不可变数据后进入应用队列。可选产物需要目录、手册、API、
依赖、Changeset 和 Maven 消费者工作。请求式检查可能明显增加开销，但范围有限且不会
遗留周期工作。

## 被拒绝的方案

给现有回调加 ID 或保留弃用适配器会延续不一致的传播和第二套归属体系。应用 key 可能
敏感，View 身份跟随物理生命周期，UUID 暗示不需要的跨进程稳定性。Foundation 不应持有
可选策略或存储，持续诊断树违反 ADR-0009；节点归属可验证前，Android 布局/GPU 计时会
产生误导。

## 验证

Phase 1 必须验证成功、回滚、异步、准备、保留、复用和销毁 Session 的事件顺序，六种父图，
全部采集级别，Sink 异常/重入隔离，旧回调/角色完整删除，Q3 示例与中英文文档、API 校验、
Changeset 和 Maven 消费者。后续 Phase 必须覆盖每个边界、确定性淘汰、并发/高基数失败、
高亮清理/过期、校准计时、发布 classpath，以及同设备未激活/已请求性能。

活动诊断计划维护精确的定向检查。设备和性能证据在实现 Phase 被接受前必须提供，本文档
契约冻结阶段无需提供。
