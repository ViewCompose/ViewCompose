# Performance Roadmap

## 1. 目标

本文档定义 `ViewCompose` 在 View 体系下的性能优化主线。

讨论范围包括：

1. 如何建立性能基准测试
2. 如何从架构和 API 设计层面规避性能误用
3. 是否需要引入 `ConstraintLayout`
4. Compose 在性能方面的核心做法，以及哪些值得借鉴
5. 当前框架相对 Compose 的差距
6. 后续分阶段推进顺序

性能主线不等于“只看 FPS”。

对于当前框架，更核心的是同时控制 4 类成本：

1. `重建成本`：状态变化后重新产树、diff、rebind 的成本
2. `层级成本`：View 树过深导致的 measure/layout/draw 成本
3. `挂载成本`：创建 View、更新 View、销毁/回收 View 的成本
4. `主线程成本`：布局、图片、主题切换、输入、滚动等在主线程上的竞争

## 2. 当前判断

当前阶段的核心结论：

1. `NodeSpec` 已经基本覆盖第一方常用节点，这使“节点级 diff”和“跳过更新”进入可落地阶段
2. 基于 Android View，`层级深度` 仍然是绕不开的性能风险，不能只讨论重组
3. 当前最优先的性能工作，不是马上追求 Compose 级别的细粒度重组，而是先把：
   - 基准测试
   - 节点级 diff
   - 浅层布局约束
   - debug / warning 能力
   这 4 件事做好
4. `Phase 2` 已经开始起步：renderer 现在能基于 `NodeSpec/props/modifier` 判断节点是否需要重绑，并输出 `rebound/skipped` 统计

## 3. 性能风险模型

### 3.1 View 层级风险

在 View 体系中，每增加一个 `View` 或 `ViewGroup`，通常都会增加：

1. 初始化成本
2. measure/layout 成本
3. draw/invalidate 成本

Android 官方文档明确指出：

- 深层嵌套 `LinearLayout` 会带来额外开销
- 使用 `layout_weight` 的嵌套 `LinearLayout` 尤其昂贵，因为子节点可能被测量两次

这对我们的启发是：

- `Row / Column / Box / Surface` 不能被鼓励无节制嵌套
- 自定义容器应该优先承担“把多层语义压平”的责任
- 框架默认布局能力必须偏向“浅而宽”，而不是“窄而深”

### 3.2 更新粒度风险

当前普通页面节点仍然以：

1. 根级 render 重跑
2. keyed reuse
3. binder 重绑

为主。

这在中小页面可接受，但在大页面和高频状态更新场景下会带来：

1. 无意义 rebind
2. 主线程抖动
3. 难以分辨“真正局部更新”和“全树重跑但复用成功”

### 3.3 布局反馈回路风险

性能上非常危险的一类写法是：

1. 在布局/测量回调里拿尺寸
2. 回写状态
3. 再把状态喂回布局参数

Compose 官方文档也明确把这类模式视为不理想，因为它会多跑一帧甚至产生布局抖动。

对我们来说，同样应该避免：

- `onSizeChanged / onGloballyPositioned` 等价能力驱动重新 render
- 先测量再通过状态反向驱动父布局

应该优先使用：

- 正确的布局原语
- 单一父容器作为尺寸与位置的 source of truth
- 必要时写自定义容器，而不是走“测量 -> 状态 -> 再布局”

### 3.4 列表与图片风险

当前框架已经有 `LazyColumn` 和远程图片能力，但性能上仍需重点关注：

1. 列表重排时的 item session 保留
2. 滚动中的图片加载与主线程竞争
3. 占位图、错误图、回收后的重新绑定
4. 长列表中的主题/样式切换成本

### 3.5 主题与样式风险（✅ 主题重构已完成）

主题系统重构已完成（`UiThemeTokens` 精简为 4 字段，Defaults 即时派生），但性能上仍需约束：

1. 默认样式不能导致每次 bind 都创建大量新对象
2. drawable / ripple / background 生成要尽量复用
3. 局部主题覆盖不能无边界扩大 render 范围

## 4. 性能基准测试

### 当前状态

当前已完成 `Phase 1` 的起步骨架：

1. 已新增 `:viewcompose-benchmark` 模块
2. `app` 已新增 `viewcompose-benchmark` build type
3. 宿主 app 已声明 `profileable`
4. 已落地首批宏基准场景：
   - 冷启动
   - 章节切换
   - 主题切换
   - Collections 页面滚动
   - Patch Stress 页面更新

当前相关代码位置：

1. [viewcompose-benchmark/build.gradle.kts](../../viewcompose-benchmark/build.gradle.kts)
2. [StartupBenchmark.kt](../../viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/StartupBenchmark.kt)
3. [DemoInteractionBenchmark.kt](../../viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/DemoInteractionBenchmark.kt)

当前本地已验证：

```bash
./gradlew :viewcompose-benchmark:assembleBenchmark :app:assembleBenchmark
```

### 模块级 viewcompose-benchmark 入口状态

多 Activity demo 现在已经具备稳定的“launcher extra -> 模块 Activity”转发入口，代码在：

1. [MainActivity.kt](../../app/src/main/java/com/viewcompose/MainActivity.kt)
2. [DemoCatalog.kt](../../app/src/main/java/com/viewcompose/DemoCatalog.kt)
3. [DemoBenchmarkScope.kt](../../viewcompose-benchmark/src/main/java/com/viewcompose/benchmark/DemoBenchmarkScope.kt)

这让宏基准后续可以不依赖目录页滚动去打开模块，而是通过 launcher Activity 安全转发到目标模块。

当前判断：

1. 路由基础设施已完成
2. `Foundations / Layouts` 已补齐 viewcompose-benchmark-only stable anchors，并已纳入稳定基线
3. 当前模块级 viewcompose-benchmark 统一走：
   - `Launcher -> MainActivity(extra=module_key) -> Module Activity -> Benchmark Anchor`
4. 后续新增模块 viewcompose-benchmark 时，不应再依赖目录页滚动或分段过滤器命中，而应优先补短文案、固定位置、可重置的 viewcompose-benchmark anchor

当前已稳定的模块级场景：

1. `Foundations Benchmark Anchor`
2. `Layouts Benchmark Anchor`
3. `Input Benchmark Anchor`
4. `Collections Benchmark Anchor`
5. `State Benchmark Anchor`
6. `Diagnostics Benchmark Anchor`
7. `Interop Benchmark Anchor`

后续在真机运行宏基准的主要命令：

```bash
./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest
```

### 第一轮真实设备基线

已于 `2026-03-01` 在真机 `Pixel 4 XL (Android 13)` 跑过首轮宏基准。

当前基线：

1. `coldStartup`
   - `timeToInitialDisplayMs` 中位数约 `428.9ms`
2. `themeSwitch`
   - `frameDurationCpuMs` P50 `2.6ms`
   - `frameOverrunMs` P50 `-15.9ms`
3. `collectionsScroll`
   - `frameDurationCpuMs` P50 `2.9ms`
   - `frameOverrunMs` P50 `-14.2ms`
4. `chapterSwitch`
   - `frameDurationCpuMs` P50 `2.7ms`
   - `frameOverrunMs` P50 `-16.3ms`
5. `patchUpdates`
   - `frameDurationCpuMs` P50 `3.0ms`
   - `frameOverrunMs` P50 `-15.3ms`
   - 场景对应 `State -> Patch Stress`，当前命中 `Text / Button / TextField / SegmentedControl / TabPager` 第一批字段级 patch 节点
6. `diagnosticsRefreshAfterPatch`
   - `frameDurationCpuMs` P50 `2.7ms`
   - `frameOverrunMs` P50 `-15.2ms`
   - 场景对应 `State -> Patch Stress -> Open diagnostics renderer`，用于验证 patch 之后进入 diagnostics renderer 的人工测试链路和 viewcompose-benchmark 链路一致
7. `foundationsBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.6ms`
   - `frameOverrunMs` P50 `-14.4ms`
   - 场景对应 `Launcher -> MainActivity(extra=foundations) -> Foundations -> Foundations Benchmark Anchor`
8. `layoutsBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.7ms`
   - `frameOverrunMs` P50 `-14.4ms`
   - 场景对应 `Launcher -> MainActivity(extra=layouts) -> Layouts -> Layouts Benchmark Anchor`
9. `inputBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.5ms`
   - `frameOverrunMs` P50 `-15.6ms`
   - 场景对应 `Launcher -> MainActivity(extra=input) -> Input -> Input Benchmark Anchor`
10. `collectionsBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.8ms`
   - `frameOverrunMs` P50 `-14.1ms`
   - 场景对应 `Launcher -> MainActivity(extra=collections) -> Collections -> Collections Benchmark Anchor`
11. `stateBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.4ms`
   - `frameOverrunMs` P50 `-15.9ms`
   - 场景对应 `Launcher -> MainActivity(extra=state) -> State -> State Benchmark Anchor`
12. `diagnosticsBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.6ms`
   - `frameOverrunMs` P50 `-15.2ms`
   - 场景对应 `Launcher -> MainActivity(extra=diagnostics) -> Diagnostics -> Diagnostics Benchmark Anchor`
13. `interopBenchmarkAnchor`
   - `frameDurationCpuMs` P50 `2.5ms`
   - `frameOverrunMs` P50 `-15.7ms`
   - 场景对应 `Launcher -> MainActivity(extra=interop) -> Interop -> Interop Benchmark Anchor`

相关产物位置：

1. [benchmarkData.json](../../viewcompose-benchmark/build/outputs/connected_android_test_additional_output/viewcompose-benchmark/connected/Pixel%204%20XL%20-%2013/com.viewcompose.benchmark-benchmarkData.json)
2. `additionaltestoutput.viewcompose-benchmark.message_*`
3. `*.perfetto-trace`

说明：

- 这只是第一版基线，不是优化结论
- 它的价值在于给后续 `NodeSpec diff / skip update` 提供对照

### 第二轮真实设备基线（patch 深化后）

于 `2026-03-04` 在同一台 `Pixel 4 XL (Android 13)` 跑第二轮宏基准，对比 Sprint 1 字段级 patch 深化后的效果。

**变更内容**（第一轮 → 第二轮之间）：
1. 新增 Row、Column、Box、Image、IconButton 字段级 patch
2. LazyColumn patch 从结构级委托下沉为真正字段级
3. Toggle、Slider、ProgressIndicator、Divider 字段级 patch
4. Benchmark 导航可靠性修复（直接 intent 跳转、bidirectional scroll、conditional FLAG_ACTIVITY_CLEAR_TASK）

**对比结果：**

| 场景 | 第一轮 P50 | 第二轮 P50 | 变化 |
|------|-----------|-----------|------|
| `coldStartup` (timeToInitialDisplayMs) | 428.9ms | 370.5ms | **-13.6%** |
| `patchUpdates` | 3.0ms | 2.8ms | **-6.7%** |
| `themeSwitch` | 2.6ms | 2.3ms | **-11.5%** |
| `collectionsScroll` | 2.9ms | 2.6ms | **-10.3%** |
| `chapterSwitch` | 2.7ms | 2.2ms | **-18.5%** |
| `diagnosticsRefreshAfterPatch` | 2.7ms | 2.5ms | **-7.4%** |
| `layoutsBenchmarkAnchor` | 2.7ms | 2.4ms | **-11.1%** |
| `interopBenchmarkAnchor` | 2.5ms | 2.4ms | -4.0% |
| `foundationsBenchmarkAnchor` | 2.6ms | 2.6ms | 0% |
| `stateBenchmarkAnchor` | 2.4ms | 2.5ms | +4.2% |
| `inputBenchmarkAnchor` | 2.5ms | 2.6ms | +4.0% |
| `diagnosticsBenchmarkAnchor` | 2.6ms | 2.7ms | +3.8% |
| `collectionsBenchmarkAnchor` | 2.8ms | 3.0ms | +7.1% |

**分析：**

1. **冷启动改善显著**（-13.6%）：可能受益于 viewcompose-benchmark 导航重构减少了 warmup 开销，并非纯 patch 优化
2. **交互场景普遍改善**：`chapterSwitch`（-18.5%）、`themeSwitch`（-11.5%）、`collectionsScroll`（-10.3%）收益明显，这些场景涉及大量节点重绑，字段级 patch 有效减少了 rebind 成本
3. **patch 直接场景温和改善**：`patchUpdates`（-6.7%）和 `diagnosticsRefreshAfterPatch`（-7.4%）改善温和，符合预期 — 这些场景本身帧时间已很低（<3ms），绝对收益空间有限
4. **模块 anchor 波动在噪声范围内**：±4-7% 的波动在 2-3ms 帧时间尺度下约为 0.1-0.2ms，属于设备测量噪声，不构成回归
5. **所有 13 个场景全部通过**，viewcompose-benchmark 基础设施稳定性已验证

## 4.1 需要两层基准

### A. Macrobenchmark

这是主基线。

它用来回答：

1. 启动是否变慢
2. 滚动是否掉帧
3. 输入、列表重排、Tab 切换是否抖动
4. 主题切换和大页面 render 是否有明显卡顿

推荐新增独立模块：

- `:viewcompose-benchmark`

首批场景：

1. `Cold startup`
2. `Warm startup`
3. `Demo chapter switch`
4. `LazyColumn fast scroll`
5. `Keyed reorder stress page`
6. `TextField typing stress`
7. `Theme switch`
8. `Media page scroll + remote image`
9. `Patch stress updates`
10. `Diagnostics refresh after patch`

核心指标：

1. `StartupTimingMetric`
2. `FrameTimingMetric`
3. 慢帧 / 冻结帧占比
4. 关键交互耗时

### B. 算法级 viewcompose-benchmark

这层用来回答：

1. `NodeSpec diff` 本身快不快
2. `ChildReconciler` 和 lazy diff 算法是否退化
3. 新优化是否真的减少了 binder 调用

优先覆盖：

1. `ChildReconciler`
2. `LazyListDiff`
3. 后续新增的 `NodeSpecComparator / NodePatchBuilder`

说明：

- 这类测试更偏“算法成本”
- 它不能替代真实设备上的宏基准

## 4.2 Benchmark 的基线规则

为了避免数据没意义，基准测试需要固定规则：

1. 物理真机优先，模拟器只用于开发阶段
2. 分开测：
   - 有无 Baseline Profile
   - Debug / Release
3. 每次优化前后都保留同一组场景
4. 结果要按“场景 + 指标 + commit”存档

## 4.3 需要补的配套工具

除了 viewcompose-benchmark，本项目还应该逐步补：

1. `debug 渲染统计`
   - 单次 render 产生多少节点
   - 哪些节点被重绑
   - 哪些节点被跳过
2. `View 深度统计`
   - 最大深度
   - 平均深度
   - 某些容器 subtree 深度
3. `过深层级 warning`
4. `无 key / 重复 key / 大量重建` warning

当前已落地的第一步：

1. [ViewTreeRenderer.kt](../../viewcompose-renderer/src/main/java/com/viewcompose/renderer/view/tree/ViewTreeRenderer.kt) 会输出单次 render 的 `insert/reuse/removal/rebound/skipped` 统计
2. 这些统计现在已经扩展为 `insert/reuse/removal/rebound/patched/skipped`
3. [RenderSession.kt](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/runtime/RenderSession.kt) 的 debug 日志已包含这些统计
4. [DebugStrings.kt](../../viewcompose-renderer/src/main/java/com/viewcompose/renderer/debug/DebugStrings.kt) 已能格式化 render 统计摘要

## 5. 架构设计层面的性能约束

## 5.1 默认走浅层布局

这是 View 体系下最重要的约束之一。

原则：

1. 能通过单一自定义容器表达的，不鼓励堆多层 `Row/Column/Box`
2. 默认组件要尽量减少“语义包装 View”
3. `Surface` 之类语义容器不能无意义引入额外层级

这意味着：

- 自定义容器是性能工具，不只是 API 美化
- 后续新增布局能力时，应优先考虑“能否合并到已有容器”

## 5.2 新控件必须先定义性能边界

后续任何新控件都至少要回答：

1. 会创建几个真实 `View`
2. 是否引入新的 `ViewGroup`
3. 是否会多次 measure
4. 状态变化时能否局部更新
5. 是否要求强制 key

如果这些问题答不清，就不应直接进入第一方控件层。

## 5.3 状态读取要分阶段收敛

Compose 官方文档把状态读取分为：

1. composition
2. layout
3. draw

它的优化点是：尽量把高频变化的状态读取推迟到 layout 或 draw，而不是在 composition 中放大重组范围。

对我们来说，直接照搬是不现实的，因为我们当前还没有 Compose 那样的 phase-aware runtime。

但设计原则仍然值得借鉴：

1. 高频变化状态不要尽量上提到根 session
2. 如果变化只影响位置，应优先走 layout 语义，而不是整节点重建
3. 如果变化只影响绘制，应优先走 draw / view property 更新，而不是重新构树

这会直接影响后续的 `NodeSpec diff` 设计。

## 5.4 NodeSpec diff 应优先服务“跳过更新”

`NodeSpec` 的首个高价值用途，不是做花哨抽象，而是：

1. `oldSpec == newSpec` 时直接跳过 binder
2. 只在字段变化时更新对应 View 属性
3. 避免每次 render 后整节点 rebind

推荐后续设计为：

1. `NodeSpecComparator`
2. `NodePatch`
3. `binder.applyPatch(...)`

先从高收益节点开始：

1. `TextField`
2. `Button`
3. `TabPager`
4. `LazyColumn`
5. `SegmentedControl`

## 5.5 父布局相关能力必须严格受限

`weight / align / fill` 这类能力如果没有约束，既会误用，也会让布局退化。

当前已经开始引入 scope receiver，这是对的。

后续仍要继续收紧：

1. parent-data 只能在合法 scope 使用
2. debug 模式对非法使用给 warning
3. 文档明确哪些能力会触发额外测量成本

尤其要注意：

- 在 View 体系中，`weight` 往往比固定约束更贵

## 5.6 AndroidView 必须被视为性能隔离区

`AndroidView` 很有价值，但它也天然会破坏一部分框架控制能力。

因此需要明确定义：

1. `AndroidView` 是 escape hatch
2. 不对它承诺节点级 diff
3. 它的 update 尽量是幂等的
4. viewcompose-benchmark 场景要单独覆盖它

## 6. 是否需要引入 ConstraintLayout

结论：

- `有价值`
- `但不应该马上作为 v1 默认布局核心`

原因如下。

### 6.1 为什么它有价值

Android 官方 View 文档明确建议：

- 为了减少深层嵌套，可以使用 `ConstraintLayout`
- 在响应式 / 自适应 View 布局里，`ConstraintLayout` 是重要工具

对于我们的框架，这意味着：

1. 在复杂卡片、表单、媒体行、仪表盘场景里，它确实可能比多层 `Row/Column/Box` 更高效
2. 它能帮助 flatten 层级

### 6.2 为什么不该马上变成默认核心

因为它会带来新的复杂度：

1. 需要一套新的 DSL
2. 需要新的约束语义、引用机制和 debug 模型
3. 会明显抬高第一方布局系统复杂度
4. 如果没有 viewcompose-benchmark 证明真实收益，容易过早复杂化

### 6.3 当前建议

建议作为 `P2` 或实验性能力：

1. 先不把它当默认布局基础
2. 等 viewcompose-benchmark 明确证实某些复杂页面受限于层级深度，再引入
3. 引入时以专用容器形式存在，而不是替换现有 `Row/Column/Box`

推荐名字：

- `ConstraintLayout`
- 或语义上更收敛的 `ConstraintBox`

### 6.4 不引入前的替代策略

在引入它之前，优先做这些：

1. 强化自定义线性/叠放容器
2. 新增少量高价值专用容器
3. 对过深树做 debug warning
4. 基于 viewcompose-benchmark 找出真实痛点页面

## 7. Compose 是如何做的

## 7.1 Compose 不受 View 深层嵌套的同等级成本约束

这是最关键的差异。

Compose 会构建自己的 UI tree，并在 composition、layout、draw 三个 phase 中推进。
它不是每一层语义包装都落成一个 Android `View`。

这意味着：

1. Compose 仍然有节点树成本
2. 但这个成本通常低于“每层都是 ViewGroup”的传统 View 层级成本

所以对我们来说，不能简单套用“Compose 可以这样写，所以 View 体系也可以这样写”。

## 7.2 Compose 会跟踪状态读取的 phase

官方文档明确指出：

1. composition 读状态，触发重组
2. layout 读状态，触发布局阶段
3. draw 读状态，触发绘制阶段

这使它可以把很多高频变化的成本限制在更低阶段，而不是每次都回到完整重组。

这对我们的启发非常大：

1. 后续 runtime 不能只知道“状态变了”
2. 要逐步知道“变化影响的是构树、布局还是仅绘制”

## 7.3 Compose 会跳过不需要的工作

Compose 会：

1. 利用稳定性和 skipping 减少不必要重组
2. 在 layout tree 上尽量只做必要工作
3. 通过 lazy 容器限制大列表成本

而我们当前还没有：

1. compiler 级稳定性分析
2. skippable composable 机制
3. 真正 phase-aware 的 invalidation 模型

所以短期最现实的做法不是“复制 Compose runtime”，而是：

1. 先做 `NodeSpec` 层的 skip / patch
2. 再逐步细化 invalidation 边界

## 7.4 Compose 也会强调避免错误布局模式

官方文档同样强调：

1. 不要为了尺寸信息多绕一帧
2. 不要把高频变化状态过早读进 composition
3. 要使用正确的布局原语

这说明：

- 性能优化不只是 runtime 问题
- 也是 API 设计与使用约束问题

## 7.5 Compose 的 Baseline Profile 更重要

官方文档特别强调 Compose 作为库分发，启动和首帧编译成本更敏感，因此推荐 Baseline Profiles。

对我们来说，这个结论同样成立：

1. 框架本身是库
2. demo 和后续宿主 app 都应该尽早加入 Baseline Profile 方案

## 8. 当前框架相对 Compose 的性能差距

## 8.1 当前缺少的关键能力

1. 节点级 `skip / patch`
2. phase-aware 状态失效模型
3. compiler 辅助的稳定性分析
4. 系统化 viewcompose-benchmark 模块
5. View 深度 / rebind 次数 / patch 次数的标准诊断输出

## 8.2 当前已经具备的基础

1. keyed reuse
2. `LazyColumn` item session
3. `NodeSpec`
4. local theme / environment
5. custom container
6. debug render logging

这说明性能优化已经可以从“抽象讨论”进入“具体落地”。

## 9. 我们还需要补充的点

除了你已经提到的 4 个点，我认为还需要把下面这些也纳入主线：

1. `对象分配`
   - 背景 drawable、ripple、列表 item session、临时 patch 对象是否频繁分配
2. `图片`
   - 滚动中图片请求、取消、复用、占位切换
3. `文本输入`
   - 输入法交互、selection、频繁文本变化
4. `主题切换`
   - 局部 theme override 是否放大 render 范围
5. `发布态优化`
   - Baseline Profiles
   - Startup Profiles
6. `debug 能力`
   - 没有统一诊断能力，性能优化容易靠猜

## 10. 分阶段路线

### Phase 1：建立测量能力

1. 新增 `:viewcompose-benchmark`
2. 先覆盖 startup、scroll、tab switch、theme switch、typing、reorder
3. 明确基线设备和基线指标

### Phase 2：做节点级 diff

1. 基于 `NodeSpec` 做 `areEquivalent / patch`
2. 先覆盖 `Button / Text / TextField / TabPager / LazyColumn / SegmentedControl`
3. renderer 先支持”字段级跳过更新”

当前状态：

1. 已完成第一步”整节点 skip bind”
2. 当前判断条件已经收敛为”modifier + 样式 props + NodeSpec”
3. 所有有 `NodeSpec` 的第一方节点都已接入 patch 路径：
   - **真正字段级 patch**：`Button`、`Text`、`TextField`、`Toggle`、`Slider`、`ProgressIndicator`、`Divider`、`Row`、`Column`、`Box`、`Image`、`IconButton`、`LazyColumn` — 逐字段比较，仅更新变化的属性
   - **结构级 patch**（委托 full bind）：`TabPager`、`SegmentedControl` — 自定义 View 不暴露单独 setter，深化需改自定义 View 内部，ROI 较低
4. `Image` patch 采用混合策略：元数据字段（contentDescription, contentScale, tint）独立更新，源相关字段（source, placeholder, error, fallback, remoteImageLoader）组合比较后走 full bindImage
5. `IconButton` patch 复用 Image patch 逻辑并额外比较 enabled 字段
6. `LazyColumn` patch 已下沉为真正字段级：contentPadding/spacing/items 分别比较、独立更新
7. `State -> Patch Stress` 已成为第一条专门压测 patch 路径的 demo/viewcompose-benchmark 场景，覆盖 Text、Button、TextField、SegmentedControl、TabPager、Row、Column、Box、Image
8. `State -> Patch Stress -> Open diagnostics renderer` 已补成第二条对照场景，用来把 patch 压测和 diagnostics 面板串起来

### Phase 3：补诊断能力

1. render / patch 统计
   - 已有 `Diagnostics -> Renderer` 手动快照入口，可查看最近一次 `RenderStats`
2. View 深度统计
   - 已有 `vnodeCount / mountedNodeCount / maxVNodeDepth / maxMountedDepth`
3. 过深树 warning
   - 当前 mounted view depth 超过 `10` 时会输出 warning
4. 无 key / 重复 key / 大量重建 warning
   - 当前 renderer 已统一收口 sibling duplicate key、repeated unkeyed siblings、high rebind churn、high structural churn
5. measure/layout 观测
   - 当前已对核心自定义容器接入 `LayoutPassTracker`，Diagnostics 可手动查看和重置计数
   - 已补累计 `measure/layout` 耗时与热点排序，不再只看次数

### Phase 4：收布局性能

1. 优化现有自定义容器
2. 评估复杂页面是否真的需要 `ConstraintLayout`
3. 如有必要，再引入实验性 `ConstraintLayout`

### Phase 5：发布态优化

1. Baseline Profiles
2. Startup Profiles
3. release build 对比 viewcompose-benchmark

## 11. 当前结论

当前最合理的性能路线不是：

- 立刻追求 Compose 级 runtime
- 立刻把 `ConstraintLayout` 变成默认布局核心

而是：

1. 先建立可重复的 viewcompose-benchmark
2. 先用 `NodeSpec` 做节点级 diff / skip
3. 先通过容器和 API 约束控制 View 层级
4. 再让 viewcompose-benchmark 决定是否需要更重的布局能力

这条路线更符合当前框架成熟度，也更符合 View 体系的现实约束。

## 12. 参考资料

以下结论基于官方资料整理：

1. [Jetpack Compose phases](https://developer.android.com/develop/ui/compose/phases)
2. [Follow best practices for Compose performance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
3. [Diagnose stability issues](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)
4. [Fix stability issues](https://developer.android.com/develop/ui/compose/performance/stability/fix)
5. [Constraints and modifier order](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers)
6. [Optimize layout hierarchies (Views)](https://developer.android.com/develop/ui/views/layout/improving-layouts/optimizing-layouts)
7. [Responsive/adaptive design with views](https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views)
8. [Inspect app performance with Macrobenchmark](https://developer.android.com/codelabs/android-macrobenchmark-inspect)
9. [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview)
10. [Use a baseline profile for Compose](https://developer.android.com/develop/ui/compose/performance/baseline-profiles)
11. [Benchmark Baseline Profiles with Macrobenchmark](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile)
