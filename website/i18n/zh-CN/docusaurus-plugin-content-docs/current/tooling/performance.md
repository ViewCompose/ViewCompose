---
translation_source: tooling/performance.md
translation_source_hash: 9903a2a87e9f3f069cc68be3aeae209edfa0fd918727aa082148556723755514
translation_status: current
---

# ViewCompose 性能

## 1. 文档定位

本文档是性能规范版，定义：

1. 当前性能基线
2. 性能门禁指标
3. 设计与实现层约束
4. 后续优化路线

历史长版分析见：

- [PERFORMANCE_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/PERFORMANCE_FULL_2026-03-06.md)

## 2. 当前性能基线（2026-07）

### 2.1 已建立能力

1. viewcompose-benchmark 模块已接入，具备稳定测试入口。
2. renderer 已具备节点级“是否重绑”判断能力（`rebound/skipped` 统计）。
3. diagnostics 已有 render/layout 基础指标可观测能力。
4. runtime 已切到 `SlotTable Lite`：`RenderSession` 采用“首帧 compose + 节点组级增量 recompose”，未脏组复用 `VNode` 引用。
5. 组级失效队列支持祖先合并去重（`InvalidationQueue`），并对 `emit(spec/modifier)` 输入变化做脏标记，避免参数变化漏更新。
6. patch pipeline 已支持 subtree skip（`SkipSubtree`）并新增 `skippedSubtrees` 统计；`previousVNode === nextVNode` 命中同引用快路径。
7. 延迟 session 容器的 keyed diff 已切到 `DiffUtil` 引擎（保留 key 缺失/重复 fallback）。
8. framework 托管的 `RecyclerView` 容器默认不共享 `RecycledViewPool` 且保留系统 `itemAnimator`；可按需通过容器参数 `reusePolicy/motionPolicy` 对单个容器启用共享池与动画器策略。
9. renderer 内部尺寸换算统一走 `viewcompose-renderer-android/view/DimensionUtils.kt`，避免容器层重复定义 `density/dpToPx` 带来的行为漂移。
10. runtime 状态系统已升级为 `SnapshotMutationPolicy + MVCC + MutableSnapshot` 事务模型；重组读取运行在一致性快照内。
11. `RenderSession` 失效重绘调度已升级为 `Choreographer` 帧对齐合并；显式 `render()` 仍保持立即执行语义。
12. 动画主链已统一到 `MonotonicFrameClock`（host 注入 `Choreographer` 实现），`animate*AsState/Animatable/Transition` 与重组调度对齐。
13. `graphicsLayer` 已接入 renderer resolved modifier 与 patch 语义，状态驱动变换不再依赖全量重绑。
14. 手势分发已统一为单 view dispatcher，消费优先级固定为“gesture > clickable fallback”，并支持方向锁/slop/priority 冲突策略。
15. 列表/分页容器支持 opt-in motion 策略（insert/remove/move/change），与 `DiffUtil + ItemAnimator` 协同且不改变默认容器行为。
16. graphics 主链已落地 Canvas 节点与 draw modifiers；`drawWithCache` 支持跨帧缓存命中/失效，避免高频绘制场景重复构建命令。
17. graphics 执行器已收口 v2 基线：`DrawRoundRect` 四角半径语义正确、`Drawable` 绘制支持 `DrawPaint` 组合、`ImageFilterModel.Chain` 可递归合并生效。
18. 发布态基线使用 R8 + resource shrink 的非 debuggable `benchmark` target；`ReleaseBaselineBenchmark` 固定覆盖无 ART 预编译的冷启动与 state patch 帧耗时。
19. 列表性能对比使用同一 target、同一份 1000 项数据与完全一致的交互脚本，分别运行 ViewCompose `LazyColumn` 和 Jetpack Compose `LazyColumn`；覆盖双向快速滚动与 keyed reorder + payload 内容更新。
20. 复杂布局对比使用同一份 18 卡片仪表盘模型，分别运行 ViewCompose `ScrollableColumn` 与 Compose `Column.verticalScroll`；全部子树一次挂载，覆盖深层嵌套滚动、全卡片字段更新和条件详情子树变更。
21. 两组对照均采集帧耗时与最大 heap/RSS；`compare_macrobenchmarks.py` 自动生成 Markdown/JSON 配对报告，并支持以 Compose 为同次运行控制组的归一化回归门禁。
22. 高级阴影建立独立有界外/内栅格缓存，平移/缩放/旋转/alpha 重绘复用同一栅格；`ShadowPerformanceComparisonBenchmark` 覆盖 1000 项 Lazy 与复杂布局的滚动/变更，并用 Compose 作为同轮设备波动控制组。
23. 应用进程内开发工具遵守“零持续工作”契约。可选的真机 DSL 定位器在滚动期间不写报告、也不
    检查实时 View；一次带 Nonce 的显式 IDE 请求只产生一次有界快照与响应。

### 2.2 发布态基准入口

构建门禁：

```bash
./gradlew qaRelease
```

该任务同时构建 R8 优化的 `release`、非 debuggable `benchmark` target 和 benchmark
instrumentation APK，可在没有设备时发现 shrink/R8/variant 回归。

设备基准：

```bash
./gradlew benchmarkRelease
```

设备基准并生成 Compose 对照报告：

```bash
./gradlew benchmarkCompare
```

结果默认写入：

1. `build/reports/benchmarks/compose-comparison.md`
2. `build/reports/benchmarks/compose-comparison.json`

对已有结果重新生成报告：

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json
```

与同设备历史基线比较并执行回归门禁：

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json \
  -PbenchmarkBaseline=/path/to/baseline-compose-comparison.json
```

两份 Markdown/JSON 对照报告都按场景 ID、工作负载修订号和动作标识每一行。纵向门禁的
baseline 必须是上一份带修订信息的对照 JSON，不能直接传入原始 Macrobenchmark JSON；
工作负载修订号不同的结果会被拒绝比较。

发布态权威基线是 `ReleaseBaselineBenchmark`：

1. target 为 R8 优化、resource shrink、非 debuggable 的 benchmark variant。
2. `CompilationMode.None` 隔离 ART 预编译收益，直接暴露交付二进制回归。
3. 固定场景为冷启动和 state patch。
4. 正式物理基准的每个方法使用 5 次洁净迭代；每个方法开始时 Android 温控等级必须为
   `NONE` 或 `LIGHT`，方法之间必须停进程并冷却，达到 `SEVERE` 的批次直接作废。
5. 结果只在同设备、同系统版本、同迭代协议和同温控策略下纵向比较。

Compose 对照基线是 `ListPerformanceComparisonBenchmark`：

1. 两个引擎运行在同一个 R8 target 中，排除应用配置、资源和进程环境差异。
2. 使用 `CompilationMode.None`，避免 ART 预编译掩盖框架交付成本。
3. `viewComposeListScroll/composeListScroll` 使用相同手势轨迹。
4. `viewComposeListMutation/composeListMutation` 使用相同的 37 项旋转和每 16 项内容更新。
5. 对比结论必须来自同一次设备运行；不同设备产生的数据不能横向相除。

复杂布局对照基线是 `ComplexLayoutPerformanceComparisonBenchmark`：

1. `viewComposeComplexLayoutScroll/composeComplexLayoutScroll` 对比非 Lazy 整树滚动。
2. `viewComposeComplexLayoutUpdate/composeComplexLayoutUpdate` 同时更新 18 个卡片的数据，并切换条件详情子树。
3. 两端保持相同的卡片、指标、标签、条件内容数量和嵌套顺序。
4. 该场景专门观察 ViewGroup 深度、全树 measure/layout 与局部 patch 成本，不用于评价 Lazy 容器。

高级阴影对照基线是 `ShadowPerformanceComparisonBenchmark`：

1. ViewCompose 与 Compose 使用相同的阴影层数、颜色、尺寸、shape、列表数据和复杂布局模型。
2. 固定覆盖阴影列表滚动/变更、阴影复杂布局滚动/更新，共 8 个成对方法。
3. `shadowRenderPolicy=exact_bitmap|render_node|auto` 只切换 ViewCompose 后端，不改变工作负载；Compose 结果用于归一化设备温度和后台噪声。
4. 2026-07-30 在 Samsung SM-G991B / Android 13 上各运行 10 轮，RenderNode 相对 ExactBitmap 的 P50、P95 与 RSS 方向混杂，没有证明稳定收益。
5. 因此 `Auto` 继续固定为 `ExactBitmap`；`RenderNodeDisplayList` 保留为显式实验策略，不能作为发布默认值。

同机后端对比入口：

```bash
./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.benchmark.ShadowPerformanceComparisonBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shadowRenderPolicy=exact_bitmap

./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.benchmark.ShadowPerformanceComparisonBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shadowRenderPolicy=render_node
```

完整数据与决策见[高级阴影执行记录](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/ADVANCED_SHADOW_EXECUTION_PLAN_2026-07.md)。

自动报告与回归规则：

1. 对照表固定输出 frame CPU P50/P95、frame overrun P50/P95、heap max 与 RSS anon max。
2. 每个场景的 ViewCompose 与 Compose 结果必须来自同一份 benchmark JSON。
3. 历史回归只允许同设备型号、系统 fingerprint、CPU lock 状态和 compilation mode。
4. 门禁必须同时满足“ViewCompose 原始指标超过阈值”和“ViewCompose/Compose 归一化比值超过阈值”才失败。
5. 默认阈值维护在 `tools/performance/benchmark_policy.json`，小于绝对噪声下限的变化不会失败。
6. 报告会计算各 iteration P50 的变异系数；超过 `0.15` 标记为不稳定，数据应重跑而不是直接形成结论。
7. 迭代更多不等于证据更强；持续升温的批次即使总体变异系数低于阈值也属于无效数据。

### 2.3 当前结论

1. 当前阶段优先级不是“追求极限 FPS”，而是先控制回归风险和错误用法。
2. 最关键收益来自：
   - 正确复用
   - 组级脏区重组 + 跳过不必要更新
   - 容器刷新语义稳定
3. 基线更新（2026-03-08）：`SlotTable Lite` + 子树级重组已接入主链路，`qaQuick` 通过；`qaFull` 结果按当前设备门禁状态在 roadmap 持续登记。

### 2.4 Debug Tooling 回归门禁

Release Macrobenchmark 无法发现只存在于可调试构建中的开销。因此，任何在应用进程中执行的
Tooling 都必须为它可能观察的每条热路径增加同设备 Debug 对照。设备型号、系统 Build、应用
Commit、Workload、刷新率、电源模式与温度状态必须保持一致，并记录 Frame CPU P50/P95 与工具操作
计数器。

默认验收规则为组合条件：P50 只有在同时超过 5% 和 0.3 ms 时失败；P95 只有在同时超过 10% 和
0.8 ms 时失败。空闲滚动期间工具报告写入次数必须严格为零。开发者主动触发的检查请求应单独测量，
不能摊入空闲结果。

2026-08-13 的定位器事故是参考失败：在 Samsung SM-G991B / Android 13、SurfaceFlinger 活动模式为
60 Hz 时，持续滚动/布局报告进入 `viewcompose-host-android` 后，Demo 首页列表 Frame CPU P50 从约
5--7 ms 上升到 11--12 ms；仅移除滚动发布即可恢复到约 7 ms。架构修正把实现移入可选
`viewcompose-preview` 制品，并把发布改为按请求触发。

## 3. 性能门禁指标

每次性能相关改动，至少关注下面 4 类成本：

1. 重建成本：状态变化后产树与 reconcile 开销
2. 绑定成本：View rebind 与 patch 执行开销
3. 布局成本：measure/layout 次数与深度
4. 容器成本：延迟 session 容器的刷新与复用稳定性

建议固定输出：

1. viewcompose-benchmark 数据（同机型、同路径）
2. render stats（含 rebound/skipped）
3. layout pass 关键计数

## 4. 设计约束（必须遵守）

1. 新控件必须先定义“高频路径”和“可接受开销”，再扩参数。
2. 新 modifier/props 不得引入无条件全量 rebind。
3. 复用型容器必须有“结构稳定仍刷新可见内容”路径。
4. `AndroidView` 视为性能隔离区；可重放配置放在 `update/onReset/nativeView`，外部提交副作用放在 `onCommit`，资源解绑放在只执行一次的 `onRelease`。
5. 不为短期优化破坏模块边界和可维护性。
6. 节点组开发必须保持 group key 稳定；若无法稳定，需显式接受“祖先回退重组 + 告警”成本。
7. 状态并发写入必须通过 snapshot apply 语义验证，禁止在性能优化中绕过冲突合并与失败路径。
8. 禁止将重组调度回退到 `container.post`；帧对齐路径是默认实现边界。
9. 动画实现必须复用 `MonotonicFrameClock`，禁止在动画 API 内新增并行调度器破坏帧对齐收敛。
10. 手势冲突策略调整必须同步验证滚动容器（Lazy/Scrollable/Pager）场景，避免通过“全量拦截”掩盖性能退化。
11. 图形绘制链路优化必须优先保证 `drawWithCache` 语义稳定（依赖变化才重建缓存）；禁止把缓存重建放回每帧路径。
12. 图像绘制优化不得牺牲语义：`Drawable` 路径必须保持 `DrawPaint` 生效，`ImageFilter.Chain` 不得被静默降级为 no-op。
13. 静态阴影缓存 key 必须覆盖尺寸、density、layout direction、shape 与完整阴影规格；不得缓存 View、Session 或可变业务对象。
14. 节点仅发生 translation/scale/rotation/alpha 变化时必须复用已有阴影栅格；blur/spread/shape/尺寸变化才允许重建。
15. 阴影后端默认策略的任何调整都必须提供同设备、同构建、同工作负载的多轮配对数据，并通过 Compose 归一化门禁。
16. 大尺寸、逐帧 blur/spread 或 RenderEffect 路径必须先定义内存/离屏预算；预算落地前不得进入默认列表或转场路径。
17. 应用进程 Tooling 禁止在滚动、全局布局、绘制、触摸、Animation Frame 或重组热路径安装持续
    Listener。确需持续观察时，必须新增 ADR、显式静态门禁 Allowlist 与同设备 Debug Benchmark
    证据。

## 5. 反模式清单

1. 用深层嵌套布局代替明确的容器策略。
2. 无基准数据支撑就引入复杂优化。
3. 把性能问题都归因于运行时，而忽略页面/容器结构问题。
4. 在无回归测试情况下改动核心渲染热点。

## 6. 分阶段路线

### Phase 1：基线与可观测性

状态：已完成基础落地
目标：viewcompose-benchmark 路径稳定、核心指标可读取

### Phase 2：跳过更新能力

状态：已完成（本轮闭环）
目标：扩大节点级 skip 更新覆盖，降低无效 rebind
阶段备注（2026-03-07）：`Lazy/Pager` keyed diff 引擎已切换至 `DiffUtil`，并已新增 `SkipSubtree + skippedSubtrees` 路径与统计；后续增量在 Phase 3/4 持续推进。

### Phase 3：诊断增强

状态：核心可视化已完成
目标：render tree、patch、CompositionLocal 与重组原因已可直接读取；后续补节点高亮、跨 session 关联与逐节点耗时

### Phase 4：容器与布局收口

状态：列表、复杂布局 Compose 对照、内存指标、自动报告与归一化回归门禁已建立
目标：收敛高频容器和复杂页面的布局开销

### Phase 5：发布态优化

状态：R8 release 基准已建立，baseline profile 待推进
目标：在当前无 ART 预编译基线上继续量化 baseline profile 等发布链路收益

### Phase 6：高级阴影后端

状态：静态精确后端、缓存、Compose 对照和首轮设备决策已完成；动态 RenderEffect 仍为研究项
目标：维持 `Auto = ExactBitmap`，继续积累设备矩阵；只有在明确的内存与帧预算内评估动态 blur/转场阴影

## 7. 评审与提交流程

性能相关 PR 至少满足：

1. 说明改动针对哪类成本
2. 提供改动前后关键指标
3. 说明是否影响容器刷新语义
4. 同步更新本文件或相关规范文档

若改动涉及可见行为（布局、交互、overlay、输入），额外要求：

1. 至少补一条对应 instrumentation 回归，或登记明确豁免与补齐时间

协作规则见：

- [workflow.md](../project/workflow.md)

## 8. 关联文档

1. 架构规范：[overview.md](../architecture/overview.md)
2. 容器专项清单：[session-containers.md](../architecture/session-containers.md)
3. 统一路线图：[roadmap.md](../project/roadmap.md)
4. 文档入口：[docs/README.md](../README.md)
5. 状态快照规范：[state-snapshots.md](../architecture/state-snapshots.md)
