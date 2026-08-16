---
translation_source: tooling/performance.md
translation_source_hash: 73d789052a7d9e0f706208355e348e863b70b685ecda5a56315ae1858f60ca39
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

## 2. 当前性能基线（2026-08）

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
19. 列表性能对比在同一 target 中，以同一份 1000 项数据和同一套交互脚本运行 ViewCompose
    `LazyColumn`、Compose `LazyColumn` 与惯用的 Android Views `RecyclerView`，覆盖双向快速滚动、
    keyed reorder 和 payload 更新。
20. 复杂布局对比以同一份 18 卡片仪表盘模型运行 ViewCompose `ScrollableColumn`、Compose
    `Column.verticalScroll` 与保留式 Android Views `ScrollView`/ViewGroup 树，覆盖深层滚动、
    全卡片更新和条件详情子树变更。
21. 两组对照均采集帧耗时与最大 heap/RSS。`compare_macrobenchmarks.py` 生成引擎中立的 report-v2
    Markdown/JSON，输出绝对值、ViewCompose/Compose 与 ViewCompose/Android Views 对照；历史纵向
    门禁仍以同轮 Compose 为控制组，并可读取已验收的 report-v1 基线。
22. 高级阴影建立独立有界外/内栅格缓存，平移/缩放/旋转/alpha 重绘复用同一栅格；`ShadowPerformanceComparisonBenchmark` 覆盖 1000 项 Lazy 与复杂布局的滚动/变更，并用 Compose 作为同轮设备波动控制组。
23. 应用进程内开发工具遵守“零持续工作”契约。可选的真机 DSL 定位器在滚动期间不写报告、也不
    检查实时 View；一次带 Nonce 的显式 IDE 请求只产生一次有界快照与响应。
24. warm interaction benchmark 在启动并定位 fixture 后，于 measured block 之外等待 5 秒；
    cold-start workload 不等待，因为启动本身就是被测操作。
25. 导航动效继续分开测量 push 与 pop，但每个 measured iteration 执行 8 次同方向转场；
    pop 在 measured block 外预先压入 8 个 destination。
26. Type、Environment 与 NodeSpec 均未变化的复用节点会执行仅 Modifier Patch。纯视觉更新会
    保留 LayoutParams 并跳过完整 Node Binding；布局 Modifier 只替换 LayoutParams，不会重建或
    重新执行原生 View 的语义绑定。
27. `LocalContext` 会保存已安装的不可变 `LocalSnapshot`，不再为每个 Group 或发射 Node 重建
    Snapshot 对象。Snapshot 创建量只随 Provider 边界增长；一次批量 `ProvideLocals` 调用会为全部
    Binding 只安装一份 Snapshot。

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

设备基准并生成引擎对照报告：

```bash
./gradlew benchmarkCompare
```

结果默认写入：

1. `build/reports/benchmarks/engine-comparison.md`
2. `build/reports/benchmarks/engine-comparison.json`

对已有结果重新生成报告：

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json
```

对容易升温的真机，应让每个必需的 ViewCompose、Compose 或 Android Views 方法都从相同的
`NONE`/`LIGHT` 温控
等级开始，方法之间完成冷却，并把各自 JSON 放入一个干净目录。将该目录作为
`benchmarkResult` 时，工具只会合并设备、系统、时钟策略和编译上下文完全一致的结果；
benchmark 方法重名、引擎集合不完整或上下文不同会直接失败，不会任意选择最新的局部结果。没有显式时钟
策略的旧结果仍要求 AndroidX `cpuLocked` 快照一致。

拆分方法批次开始前只安装一次 target 和 benchmark APK。安装后停止两个进程、熄屏，等待
温控等级和 CPU 最低频率回到正常状态，再直接调用已安装的 `AndroidJUnitRunner`。每个方法
都通过 `androidx.benchmark.output.payload.clockPolicy=unlocked-dvfs-preflight-v1` 记录经过主机
预检的消费设备协议；报告比较该协议并保留全部 AndroidX 原始锁定快照，不能事后改写。

显式的 unlocked-DVFS 策略只标识主机协议，不代表 OEM 会保持稳定工作频率，run-P50 CV 门禁
仍然必须执行。如果方法出现两个频率平台，或温控等级不变时 `scaling_max_freq` 仍切换，应直接
拒绝，不能重跑到偶然通过为止。AndroidX 的 Runtime Image 警告需要结合工作负载解释：冷启动、
首次构建、导航、任何把类加载纳入 measured block 的结果，或任何宣称为洁净
`CompilationMode.None` 的结果都必须拒绝。只有完整 target 已就绪、固定稳定窗口位于 measured
block 外、所有 control 共享报告中的编译身份且稳定性通过时，steady-state 交互结果才可以按
`run-from-apk` 身份验收，绝不能改写成洁净未编译启动数据。该警告会收窄结论范围，不会从证据中
消失。`cmd power set-fixed-performance-mode-enabled` 只有在设备证明整个测量期间最低和最高频率
稳定时才能视为锁频。消费设备无法满足门禁时，必须改用可 root 或其他可控制时钟的参考设备。

与同设备历史基线比较并执行回归门禁：

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json \
  -PbenchmarkBaseline=/path/to/baseline-engine-comparison.json
```

两份 Markdown/JSON 对照报告都按场景 ID、工作负载修订号、引擎和动作标识每一行。report v2
可以读取已验收的 `compose-comparison.json` report-v1 基线并保持其 ViewCompose/Compose 语义，
但不会为旧基线虚构缺失的 Android Views 数据。纵向门禁的 baseline 必须是上一份带修订信息的
对照 JSON，不能直接传入原始 Macrobenchmark JSON；工作负载修订号不同的结果会被拒绝比较。

发布态权威基线是 `ReleaseBaselineBenchmark`：

1. target 为 R8 优化、resource shrink、非 debuggable 的 benchmark variant。
2. `CompilationMode.None` 隔离 ART 预编译收益，直接暴露交付二进制回归。
3. 固定场景为冷启动和 state patch。
4. 正式物理交互基准使用 5 次洁净迭代；冷启动保留 10 次，因为真实的首轮冷缓存波动
   会让单个样本支配较小样本集的稳定性判断。每个方法开始时 Android 温控等级必须为
   `NONE` 或 `LIGHT`，方法之间必须停进程并冷却，达到 `SEVERE` 的批次直接作废。
5. 结果只在同设备、同系统版本、同迭代协议和同温控策略下纵向比较。

三引擎列表对照基线是 `ListPerformanceComparisonBenchmark`：

1. 三个引擎运行在同一个 R8 target 中，排除应用配置、资源和进程环境差异。
2. 使用 `CompilationMode.None`，避免 ART 预编译掩盖框架交付成本。
3. `viewComposeListScroll/composeListScroll/androidViewsListScroll` 使用相同手势轨迹。
4. `viewComposeListMutation/composeListMutation/androidViewsListMutation` 使用相同的 37 项旋转和
   每 16 项内容更新；
   每个测量 iteration 执行 8 个完整 mutation/reset 闭环，确保 run 级帧分布足以进行稳定性门禁。
5. target 就绪后，每个 iteration 都会在 measured block 外等待 5 秒，避免 OEM Activity 启动
   boost 让第一次交互出现不真实的加速。
6. 对比结论必须来自同一次设备运行；不同设备产生的数据不能横向相除。
7. Android Views 对照使用 `RecyclerView`、稳定 ID、同步 `DiffUtil` 与 payload-aware binding，
   表示惯用的直接平台复用，而不是模拟声明式全树重建。

三引擎复杂布局对照基线是 `ComplexLayoutPerformanceComparisonBenchmark`：

1. `viewComposeComplexLayoutScroll/composeComplexLayoutScroll/androidViewsComplexLayoutScroll`
   对比非 Lazy 整树滚动。
2. `viewComposeComplexLayoutUpdate/composeComplexLayoutUpdate/androidViewsComplexLayoutUpdate`
   同时更新 18 个卡片的数据，并在
   每个测量 iteration 中执行 8 个完整 update/reset 闭环来切换条件详情子树。
3. 三个引擎都采用相同的 measured block 外 5 秒启动稳定窗口。
4. 三端保持相同的卡片、指标、标签、条件内容数量和嵌套顺序。
5. 该场景专门观察 ViewGroup 深度、全树 measure/layout 与局部 patch 成本，不用于评价 Lazy 容器。
6. Android Views 对照保留每个卡片层级、原地 patch 文本，并真实添加或移除条件详情行。它是惯用的
   imperative 参考，不宣称与两个声明式引擎的算法完全等价。

2026-08-15 在 Samsung SM-G991B / Android 13 上验收的替换基线使用 5 次 iteration、每个方法从
`NONE`/`LIGHT` 起跑、5 秒 setup 稳定窗口和 `unlocked-dvfs-preflight-v1` 时钟策略：

| 工作负载 | ViewCompose P50/P95 | Compose P50/P95 | ViewCompose/Compose run-P50 CV |
| --- | ---: | ---: | ---: |
| `performance.list@3` 滚动 | 4.620 / 9.048 ms | 5.098 / 8.554 ms | 0.041 / 0.072 |
| `performance.list@3` 变更 | 4.651 / 9.278 ms | 9.163 / 24.855 ms | 0.009 / 0.034 |
| `performance.complex-layout@3` 滚动 | 5.596 / 8.603 ms | 5.221 / 8.457 ms | 0.011 / 0.037 |
| `performance.complex-layout@3` 更新 | 6.063 / 42.505 ms | 9.527 / 50.296 ms | 0.079 / 0.082 |

这些数值是带工作负载修订号的基线，不代表某个引擎在所有场景都更快。列表与复杂布局的更新路径
也采用不同于滚动的框架策略。该表继续作为完整的历史双引擎基线，不会被局部三引擎批次覆盖。

同日按 fail-fast 协议执行的 Android Views 批次验收了 3 个完整 steady-state action。9 个方法都
从 `NONE`/`LIGHT` 起跑，使用同一设备/构建、measured block 外 5 秒稳定窗口、
`unlocked-dvfs-preflight-v1`，并报告为 `run-from-apk`。所有方法都带 Runtime Image 警告，因此这些
数值只适用于 target 就绪后的交互，不是洁净未编译启动证据：

| 工作负载 | ViewCompose P50/P95 | Compose P50/P95 | Android Views P50/P95 | Run-P50 CV（VC/C/AV） |
| --- | ---: | ---: | ---: | ---: |
| `performance.list@3` 变更 | 4.237 / 9.990 ms | 8.236 / 22.222 ms | 3.940 / 8.666 ms | 0.013 / 0.091 / 0.030 |
| `performance.complex-layout@3` 滚动 | 5.412 / 7.363 ms | 5.141 / 7.920 ms | 4.662 / 6.913 ms | 0.082 / 0.070 / 0.008 |
| `performance.complex-layout@3` 更新 | 5.780 / 36.928 ms | 8.620 / 40.324 ms | 6.912 / 16.007 ms | 0.122 / 0.032 / 0.141 |

ViewCompose 列表滚动 control 在另外两个引擎运行前就被拒绝。其 P50/P95 为 `4.212/8.624 ms`，
但 run P50 `4.296/4.362/2.627/4.648/4.554 ms` 产生 CV `0.182`。因此列表滚动在 2026-08-15
批次中为 `inconclusive`；第 2.4.2 节已用修正后的 revision-4 root 固定频率矩阵消除这项设备
限制。该轮已验收的内存峰值方向同样混合：相对
Android Views，ViewCompose 在列表变更中的 heap/RSS 高 `12.2%/2.8%`，复杂布局滚动高
`19.9%/3.5%`，复杂布局更新低 `15.3%/7.9%`。这些进程级峰值不支持声明通用内存赢家。

`DemoInteractionBenchmark` 保留下列不参与 Compose 对照的 fixture 基线：

1. `diagnosticsThemeLongFlingToBottomAndBackRevision2` 在每个方向执行 8 次固定大力度 fling，并在
   对应手势序列后分别验证真实底部和顶部锚点。
2. `collectionsScrollRevision2` 在 setup 阶段捕获嵌套 LazyColumn 边界，然后在 measured block 内
   每个方向执行 8 次固定 swipe，期间不执行 Accessibility 查询。每次 swipe 使用 500 ms 物理
   稳定窗口，因为 benchmark setup 会关闭 UiAutomator 隐式 idle timeout；省略该窗口会让惯性
   滚动重叠，并在 FrameTimeline 中产生与工作负载无关的 `Buffer Stuffing`。
3. `collectionsStressMutationRevision2` 执行 8 个完整 rotate/insert/reset 闭环，并断言每次 reset
   都恢复原始逻辑顺序。
4. 三个方法都使用相同的 measured block 外 5 秒启动稳定窗口。正式原始结果通过 AndroidX
   benchmark payload 记录 `scenario`、`workloadRevision` 和 `clockPolicy`。

2026-08-15 在 Samsung SM-G991B / Android 13 上验收的 fixture 基线使用 5 次 iteration、每个方法
从 `NONE`/`LIGHT` 起跑、`CompilationMode.Partial` 和 `unlocked-dvfs-preflight-v1` 时钟策略：

| 工作负载 | Frame CPU P50/P95 | Run-P50 CV |
| --- | ---: | ---: |
| `diagnostics.theme@2` 固定长 fling 往返 | 3.067 / 7.336 ms | 0.008 |
| `collection.stress@2` 嵌套列表滚动往返 | 3.357 / 6.288 ms | 0.018 |
| `collection.stress@2` 8 轮变更 | 4.358 / 10.507 ms | 0.018 |

集合滚动预检也是手势驱动污染的参考案例。最初在 measured block 中重复定位 target 会增加
Accessibility 遍历；移除后，连续无间隔 swipe 仍产生约 3.6、7.2 与 14.7 ms 的 run-P50 平台。
Perfetto 显示 `RV Scroll`、display-list recording 与 RenderThread draw 成本稳定，只有
`dequeueBuffer` 等待变化，FrameTimeline 把慢帧归类为 `Buffer Stuffing`。调整刷新率和 ART 编译
策略都没有消除它；显式的逐手势稳定窗口把 run-P50 CV 降到 0.018。因此不得把没有节奏控制的
合成输入循环解释为框架滚动成本。

高级阴影对照基线是 `ShadowPerformanceComparisonBenchmark`：

1. ViewCompose 与 Compose 使用相同的阴影层数、颜色、尺寸、shape、列表数据和复杂布局模型。
2. 固定覆盖阴影列表滚动/变更、阴影复杂布局滚动/更新，共 8 个成对方法。每个变更/更新
   iteration 执行 8 个完整 action/reset 闭环并断言恢复结果，与已验收的非阴影对照协议一致，
   同时为 run 稳定性门禁提供足够的帧样本。
3. `shadowRenderPolicy=exact_bitmap|render_node|auto` 只切换 ViewCompose 后端，不改变工作负载；Compose 结果用于归一化设备温度和后台噪声。
4. 2026-07-30 在 Samsung SM-G991B / Android 13 上各运行 10 轮，RenderNode 相对 ExactBitmap 的 P50、P95 与 RSS 方向混杂，没有证明稳定收益。
5. 因此 `Auto` 继续固定为 `ExactBitmap`；`RenderNodeDisplayList` 保留为显式实验策略，不能作为发布默认值。

2026-08-15 在 Samsung SM-G991B / Android 13 上验收的 `Auto`（`ExactBitmap`）替换基线使用
5 次 iteration、每个方法从 `NONE`/`LIGHT` 起跑、5 秒 setup 稳定窗口、8 个变更/更新闭环，
并记录 `unlocked-dvfs-preflight-v1` 时钟策略：

| 工作负载 | ViewCompose P50/P95 | Compose P50/P95 | ViewCompose/Compose run-P50 CV |
| --- | ---: | ---: | ---: |
| `performance.shadow-list@2` 滚动 | 4.613 / 9.650 ms | 5.022 / 8.708 ms | 0.052 / 0.044 |
| `performance.shadow-list@2` 变更 | 4.860 / 9.750 ms | 9.035 / 24.787 ms | 0.023 / 0.117 |
| `performance.shadow-complex-layout@2` 滚动 | 5.728 / 8.724 ms | 5.481 / 8.695 ms | 0.016 / 0.046 |
| `performance.shadow-complex-layout@2` 更新 | 6.236 / 41.506 ms | 10.348 / 46.824 ms | 0.049 / 0.044 |

8 个方法全部通过 `0.15` run 稳定性门禁。对照报告保留 AndroidX 原始 `cpuLocked` 混合快照，
同时依据主机预检并显式记录的时钟策略验收该批次。更新场景的高 P95 仍属于基线的一部分：
两个引擎都会重建多张带阴影卡片和条件子树，不能只用 P50 解释结果。

当时导航 revision 6 和设计系统 bundle revision 3 尚无已验收的物理基线。在同一台三星设备上，
每个 run 的 4 次导航转场已经提供 202--223 帧，但 Android 温控最终为 `NONE` 时，OEM 频率
上限仍在满速和受限值之间切换。unlocked 与 fixed-performance 试验的 run-P50 CV 为
`0.308`--`0.372`，代表性的 Cut Contrast patch 方法也以 `0.262` 失败。该设备拒绝 shell 清理
ART profile，fixed-performance 和增强处理模式也无法形成真正锁频。这些结果属于被拒绝的
设备能力证据，不是框架回归或正式基线；两组矩阵必须在可控制时钟的参考设备上完成。

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
2. 每个无阴影场景都要求同一 benchmark 上下文中的 ViewCompose、Compose 和 Android Views
   结果；阴影场景继续要求 ViewCompose 与 Compose。JSON 可以是同一上下文中分别冷却的方法结果
   在内存中的确定性合并结果。
3. 历史回归只允许同设备型号、系统 fingerprint、显式时钟策略和 compilation mode；没有时钟
   策略的旧结果回退为严格比较 AndroidX CPU-lock 快照。
4. 纵向门禁保持 report-v1 兼容，只有“ViewCompose 原始指标超过阈值”和
   “ViewCompose/Compose 归一化比值超过阈值”同时成立才失败。在未来显式调整控制组策略前，
   Android Views 只作为绝对值与成对观察。
5. 默认阈值维护在 `tools/performance/benchmark_policy.json`，小于绝对噪声下限的变化不会失败。
6. 报告只对严格为正、具备比例尺度的 frame CPU duration 计算各 iteration P50 的变异系数；
   超过 `0.15` 标记为不稳定，数据应重跑而不是直接形成结论。`frameOverrunMs` 是围绕零点的
   有符号指标，仍保留结果和回归门禁，但不计算会因均值接近零而失真的 CV。
7. 迭代更多不等于证据更强；持续升温的批次即使总体变异系数低于阈值也属于无效数据。

### 2.3 Benchmark 结论契约

一次运行通过验收后，只有在本文或更具体的所属有效页中完成解释，文档才算闭环。每项结论必须记录
workload 及其 revision、对照环境、绝对值、归一化变化、稳定性结果、局限、决策和后续行动，并且
只能选择一个主要分类：

- `improved`：决策指标实质改善，且没有重要反向指标退化；
- `regressed`：至少一项决策指标实质变差，其他决策指标也没有改变这一解释；
- `mixed`：重要指标方向相反，包括中位数更好但尾部更差；
- `no material change`：没有决策指标跨过适用的归一化与绝对组合门禁，且相反方向也不需要归类为
  `mixed`；
- `inconclusive`：稳定性、环境不匹配、覆盖不足或其他有效性问题阻止形成方向性结论。

Frame CPU duration 越低越好。归一化变化采用
`(ViewCompose / control - 1) * 100`；报告使用更明确的“降低”和“升高”，避免只靠正负号解释。
结论同时应用所属门禁的归一化阈值与绝对阈值，必须分别解释 P50 和 P95。当相对结果有利但绝对值
仍超出帧预算时，要保留绝对风险。当前对照策略只在 P50 同时超过 10% 和 0.3 ms、P95 同时超过
15% 和 0.8 ms 时视为实质变化；后文 Debug Tooling 使用的是刻意不同的门槛。被拒绝的运行也要
作为设备能力证据保留，不能静默挑选偶然通过的样本。原始数据、绿色任务或单个有利指标都不等于
结论。

### 2.4 当前对照结论

上文已验收的 2026-08-15 Samsung SM-G991B / Android 13 数据，与同轮 Compose control 形成以下
长期结论：

| 工作负载 | P50 变化 | P95 变化 | 分类 | 解释 |
| --- | ---: | ---: | --- | --- |
| `performance.list@3` 滚动 | 降低 9.4% | 升高 5.8% | `mixed` | 中位数方向更低、尾部方向更高；两者都没有跨过组合对照门禁。 |
| `performance.list@3` 变更 | 降低 49.2% | 降低 62.7% | `improved` | Keyed 变更和 payload 更新是明确的相对优势。 |
| `performance.complex-layout@3` 滚动 | 升高 7.2% | 升高 1.7% | `no material change` | 两项指标方向都更慢，但均未跨过组合对照门禁。 |
| `performance.complex-layout@3` 更新 | 降低 36.4% | 降低 15.5% | `improved` | 整树更新快于 control，但 42.505 ms 的绝对 P95 仍有尾延迟风险。 |
| `performance.shadow-list@2` 滚动 | 降低 8.1% | 升高 10.8% | `mixed` | 中位数与尾部方向相反。P95 的 0.942 ms 绝对差超过噪声下限，但 10.8% 增幅仍低于 15% 失败阈值。 |
| `performance.shadow-list@2` 变更 | 降低 46.2% | 降低 60.7% | `improved` | 带阴影的 keyed 变更延续了无阴影场景的变更优势。 |
| `performance.shadow-complex-layout@2` 滚动 | 升高 4.5% | 升高 0.3% | `no material change` | 两项绝对变化都在噪声下限内；方向略慢，但不足以支持回归结论。 |
| `performance.shadow-complex-layout@2` 更新 | 降低 39.7% | 降低 11.4% | `improved` | 相对更新成本改善，但 41.506 ms 的绝对 P95 仍有尾延迟风险。 |

因此，2026-08-15 已验收批次的结论有明确边界，不能概括为“整体比 Compose 更快”：

1. 在这批已验收数据中，变更与整树更新工作负载持续快于 Compose control；
2. 滚动并非持续占优，但已验收滚动行都没有触发自动回归门禁：阴影列表 P95 是首要方向性优化
   目标，其次是非 Lazy 复杂布局 P50；普通列表 P95 也仍是需要监测的方向性缺口；
3. 两个复杂布局更新场景即使相对结果有利，仍是绝对尾延迟优化目标；
4. 诊断与集合 fixture 只有已验收的 ViewCompose 稳定性基线，不构成 Compose 排名；
5. 导航 revision 6 和设计系统 bundle revision 3 已在第 2.4.2 节形成稳定的 root 固定频率绝对
   基线；设计系统因缺少同口径历史基线而继续保持方向性 `inconclusive`，Android 9 也无法提供
   non-debuggable 自定义 Trace 归因；
6. 已验收同轮内存方向混合，因此不声明通用内存赢家。
7. 该局部 Android Views 批次只证明 3 个 steady-state action，不代表完整场景：列表变更存在原生
   尾部回退，复杂布局滚动存在原生中位数回退，复杂布局更新则中位数更好但尾部明显更差；列表
   滚动保持 `inconclusive`。

相对同轮 Android Views control，已验收的局部批次结论为：

| 工作负载 | P50 变化 | P95 变化 | 分类 | 解释 |
| --- | ---: | ---: | --- | --- |
| `performance.list@3` 变更 | 升高 7.5% | 升高 15.3% | `regressed` | P50 仍在组合门禁内，但 `+1.323 ms` P95 差距同时跨过两个尾部阈值。 |
| `performance.complex-layout@3` 滚动 | 升高 16.1% | 升高 6.5% | `regressed` | `+0.750 ms` 中位数差距同时跨过两个 P50 阈值，P95 仍在门禁内。 |
| `performance.complex-layout@3` 更新 | 降低 16.4% | 升高 130.7% | `mixed` | 中位数改善 `1.132 ms`，但 P95 恶化 `20.921 ms`；patch 吞吐与尾延迟方向相反。 |

正确复用、组级失效与跳过无效工作、稳定的容器刷新语义仍是优化方向。`SlotTable Lite` 和子树级
重组已进入主链路且 `qaQuick` 通过，但这些实现事实不能覆盖已测得的混合滚动结果。设备门禁状态
继续记录在 [roadmap](../project/roadmap.md)。

#### 2.4.1 复杂布局更新尾延迟排查

2026-08-16 在同一台 Samsung SM-G991B / Android 13 设备上继续排查原生尾部差距，但不替换已
验收的 revision 3 基线。新鲜 ViewCompose control 基于 DSL 收敛分支，使用 R8 benchmark 构建、
`CompilationMode.None`、5 次迭代、5 秒非测量稳定窗口、`NONE`/`LIGHT` 起始热状态和
`unlocked-dvfs-preflight-v1`，Frame CPU P50/P95 为 `6.023/41.187 ms`。单独冷却后运行的 Android
Views control 为 `7.253/16.222 ms`。两轮都出现 Runtime Image 警告，而且该设备无法锁定 CPU
频率，因此它们是相邻诊断对照，不是新的纵向基线。

Perfetto 证明慢样本来自实际 update/reset 帧，而不是自动化轮询帧。代表性最差帧在
`VC.Compose` 消耗 `16.985-17.166 ms`、在 `VC.RenderTree` 消耗 `42.555-55.960 ms`，随后 Android
View traversal 还需要 `36-38 ms`。这些区间内应用线程始终可运行，没有阻塞 I/O、锁等待或前台
GC。最差样本中 OEM 调度器把长时间声明式工作放到了 LITTLE CPU，使通常 `4-6 ms` 的 composition
和 `12-20 ms` 的 render 阶段被成倍放大。原生 control 通过保留的 View 引用执行相同字段更新，
通常能在这种调度敏感性被放大前完成。

下面的相邻实验都保持 `performance.complex-layout@3`；所有被否决的源码候选都在测试后移除：

| 候选 | ViewCompose P50/P95 | 相对新鲜 control | 结论 |
| --- | ---: | ---: | --- |
| 纯文本 `TextDocument` 绑定直接返回原始 `String` | 6.197 / 40.785 ms | P50 升高 2.9%；P95 降低 1.0% | `no material change`；无分配纯文本转换具有确定性且保留富文本 span，因此保留，但它不能解决尾部问题。 |
| 删除约 90 个冗余 Demo `Surface` 包装 | 6.042 / 40.330 ms | P50 升高 0.3%；P95 降低 2.1% | `no material change`；物理 View 深度不是主因，workload fixture 保持不变。 |
| 递归证明值相同的重建子树稳定 | 6.254 / 44.171 ms | P50 升高 3.8%；P95 升高 7.2% | `no material change` 且方向不利；递归证明成本超过 skip 收益，已回退。 |
| 不构造通用 keyed plan 中间对象，流式处理同位置复用 | 6.137 / 41.163 ms | P50 升高 1.9%；P95 降低 0.1% | `no material change`；reconcile/preflight 容器分配不是尾部根因，该路径已回退。 |

完整 ART 编译得到 `6.261/38.589 ms`，P95 相对新鲜 control 也只降低 6.3%；其他 checkpoint、分组
和精确引用快路要么没有实质收益，要么让 P95 更差。综合证据把当前结果归类为 `mixed`：一行纯
文本分配优化有效，但绝对尾延迟仍未解决。根边界是顶层 State 读取：一次 revision 会让包含 18
张卡片的声明同步重建并整树 diff，而原生 control 直接更新保留字段。完整树事务内部的常量级优化
无法消除这一区别。

Q3 Observed-property Transaction 架构现已完成这次算法硬切。显式选择该能力的 State Read 由
`RenderSession` Property Registry 持有，全部 Dirty Reader 使用同一个 Snapshot；Android
Renderer 接收一次精确 Target Batch，跳过 Root Composition、Tree Wrapping 与 Child
Reconciliation。候选依赖通过带失效 Guard 的 Prepared Replacement 管理；Renderer 会先校验完整
Batch，任一 Patch 失败时恢复此前全部原生值。`performance.complex-layout@4` 因此把 Primary
Property Action 与 Secondary Structural Add/Remove Action 分开，不再让两类成本互相掩盖。

同一设备上的三轮最终构建 Property 结果分别为 `6.261/25.087 ms`、`5.601/20.436 ms` 和
`5.436/20.206 ms` Frame CPU P50/P95。第一轮 P95 比新鲜 revision 3 整树 Control 低 39.1%；
其配对的 revision 4 Compose 与 Android Views Control 分别为 `9.066/42.353 ms` 和
`7.922/16.006 ms`。相对这些 Control，ViewCompose 的 P50/P95 比 Compose 低 30.9%/40.8%；
相对直接 Android Views，P50 低 21.0%，但 P95 高 56.7%。第一轮最终 Trace 共包含 16 个 Property
Frame：`VC.FrameRender` 平均/最大 `5.895/13.469 ms`，`VC.ObservedPropertyRead` 为
`1.572/4.216 ms`，`VC.ObservedPropertyRender` 为 `3.640/8.391 ms`；剩余 Android Traversal
最大值还包括 `10.334 ms` Measure 与 `17.048 ms` Draw。

尽管方向显著且可重复，这组三星设备证据作为正式 Baseline 仍归类为 `inconclusive`。三轮最终运行的
run-P50 CV 分别是 `0.201`、`0.208` 和 `0.215`，均超过 `0.15` 验收上限。该非 Root 设备还会
输出 Runtime Image 警告，并因 Macrobenchmark 无法清理应用 Profile 而在连续重跑中变快。
第 2.4.2 节给出了随后完成的 root 固定频率六方法验收矩阵。剩余 ViewCompose 相对原生的 P95
差距来自 Android 属性失效及 Measure/Draw 尾部，而非整树协调。

#### 2.4.2 Root 固定频率的 revision 4 验收与剩余尾延迟

2026-08-16 验收批次使用已 Root 的 Xiaomi MI 6 / Android 9、R8 benchmark target、
`CompilationMode.None`、5 次迭代、8 核全部在线、暂停充电，并将 policy 0/4 的
`performance` governor 分别固定在 1.4016 GHz 和 1.8048 GHz。所有当前核心方法均记录
`run-from-apk`、`cpuLocked=true` 和 `root-fixed-1401600-1804800-v1`。电池温度保持在
30--39 摄氏度，AndroidX 未触发温控等待。所有接受样本的 run-P50 CV 均不高于 `0.111`；
一份 CV 为 `0.159` 的 Compose 滚动样本被保留为拒绝证据，并由 CV `0.060` 的重跑替代。

第一轮列表测试暴露了工作负载缺陷：ViewCompose 保留了 RecyclerView 条目动画，而 Compose
没有请求 `animateItem`，Android Views 对照也明确关闭了 animator。结果是 ViewCompose
mutation 约产生 217 个测量帧，对照只有 41/48 帧，并稀释了真实尾延迟。fixture 现已关闭
这项不对等动画，工作负载升级为 `performance.list@4`；修正后的 ViewCompose mutation
记录 48 帧。revision 3 列表结果仅保留为历史证据，不能与 revision 4 直接比较。

| 工作负载 | ViewCompose P50/P95，ms | Compose P50/P95，ms | Android Views P50/P95，ms | Run-P50 CV，VC/C/Android | 相对 Compose | 相对 Android Views |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `performance.list@4` 滚动 | 5.294 / 10.825 | 5.651 / 9.790 | 4.110 / 8.679 | 0.070 / 0.076 / 0.019 | `mixed`：P50 低 6.3%，P95 高 10.6%，两个方向都未同时跨过门禁。 | `regressed`：P50/P95 高 28.8%/24.7%。 |
| `performance.list@4` 变更 | 4.558 / 40.332 | 7.147 / 23.978 | 5.648 / 9.583 | 0.099 / 0.096 / 0.051 | `mixed`：P50 低 36.2%，但 P95 高 68.2%。 | `mixed`：P50 低 19.3%，但 P95 高 320.9%。 |
| `performance.complex-layout@4` 滚动 | 5.798 / 13.935 | 6.023 / 11.192 | 4.636 / 10.422 | 0.065 / 0.060 / 0.013 | `regressed`：P50 中性，P95 高 24.5%。 | `regressed`：P50/P95 高 25.1%/33.7%。 |
| `performance.complex-layout@4` 属性更新 | 5.709 / 33.050 | 7.663 / 46.852 | 6.137 / 19.270 | 0.067 / 0.076 / 0.059 | `improved`：P50/P95 低 25.5%/29.5%。 | `regressed`：P50 中性，P95 高 71.5%。 |
| `performance.complex-layout@4` 结构更新 | 5.590 / 46.009 | 7.255 / 26.844 | 5.444 / 15.051 | 0.028 / 0.111 / 0.082 | `mixed`：P50 低 22.9%，但 P95 高 71.4%。 | `regressed`：P50 中性，P95 高 205.7%。 |

同批次各行 ViewCompose/Compose/Android Views 的最大 heap 中位数依次为
`8541/7531/4904`、`10443/9374/6007`、`7052/9421/7014`、`11757/14102/7670` 和
`12925/13642/9048 KiB`，因此不能得出通用内存赢家。

属性事务使 P95 从新鲜的 revision 3 ViewCompose 诊断对照 `41.187 ms` 降至
`33.050 ms`，改善 19.8%，并明确优于同轮 Compose 对照；但它没有消除 Android View
遍历和属性失效成本，与原生 P95 的差距仍然显著。正确性测试还覆盖同一个 State 同时被
observed property 和结构 `RecomposeBoundary` 读取的情况：完整结构帧现在会在同一个
Snapshot 内刷新脏 observed value，不会提交新旧状态混合帧。Android 9 的非 debuggable
APK 无法公开应用 trace section，因为 manifest `profileable` 从 API 29 才受支持；因此六方法
耗时矩阵已接受，但计划要求的最终 `VC.ObservedProperty*` Perfetto 归因仍需 API 29 或更高版本
参考设备。

修正后的列表 mutation trace 将剩余尾部定位在帧对齐框架事务，而不是 Android traversal。
代表性最慢帧在 Choreographer `animation` 阶段消耗 27.7--41.3 ms，traversal 最大 9.5 ms；
ART 同时会 JIT 编译较大的 `LazyListAdapter.submitItems` 路径。该路径会为 1000 项同步执行
keyed identity 分析、`DiffUtil`、key/sticky 索引、changed-key 发现、通知和已挂载 Holder
刷新。下一步列表优化应减少这次事务中的重复整表工作，或拆小其编译表面，同时不能重新引入
不对等动画，也不能削弱 key、revision、Session、reset 和 release 语义。

导航 revision 6 也形成稳定的固定频率诊断数据：

| 导航动作 | P50/P95/P99，ms | Run-P50 CV | 结论 |
| --- | ---: | ---: | --- |
| Push，无预编译 | 5.552 / 12.598 / 41.929 | 0.039 | 接受的绝对基线。 |
| Push，请求 profile-guided 编译 | 5.601 / 11.173 / 42.148 | 0.070 | `no material change`；P95 改善 11.3%，未达到组合 15% 门槛，P99 不变。 |
| 系统 Back，无预编译 | 5.558 / 15.618 / 40.089 | 0.039 | 接受的绝对基线。 |
| 系统 Back，请求 profile-guided 编译 | 5.409 / 13.864 / 41.685 | 0.064 | `no material change`；P95 改善 11.2%，未达到组合门槛，P99 仍约 42 ms。 |

Android 9 将两种请求的编译变体都记录为 `run-from-apk`，所以 profile-guided 行只能作为诊断，
不能证明 ART 编译状态确实不同；但这些结果仍否定了“普通预热足以解释约 42 ms 导航 P99”这一
推测。API 29 以下会主动省略导航自定义 `TraceSectionMetric`，而不是报告误导性的零值。

设计系统 revision 3 矩阵建立了第一份 Root 固定频率绝对基线；不同视觉系统不是等价工作量，
因此在出现同口径历史或未来基线前，方向性比较仍为 `inconclusive`：

| 场景 | Cut Contrast | Rounded Reference | Cupertino Pressure | Run-P50 CV 范围 | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| 首次显示中位数，ms | 531.254 | 558.753 | 561.880 | 0.039--0.088 | 稳定绝对基线；方向结论 `inconclusive`。 |
| Patch P50/P95/P99，ms | 7.934 / 23.008 / 25.716 | 7.959 / 23.855 / 26.222 | 7.842 / 15.907 / 24.841 | 0.056--0.079 | 稳定绝对基线；方向结论 `inconclusive`。 |
| 滚动 P50/P95/P99，ms | 3.798 / 7.582 / 9.000 | 3.731 / 8.071 / 9.124 | 3.730 / 7.572 / 8.905 | 0.009--0.034 | P99 稳定处于一个 60-Hz 帧内；方向结论 `inconclusive`。 |
| 活跃动画 P50/P95/P99，ms | 7.661 / 17.285 / 20.884 | 7.617 / 15.146 / 21.664 | 8.045 / 15.736 / 18.455 | 0.077--0.110 | 稳定绝对基线；各自 P95/P99 尾部继续监控。 |
| Cut Contrast 浮层生命周期 P50/P95/P99，ms | 4.535 / 27.499 / 39.833 | — | — | 0.055 | 稳定基线；浮层 P95/P99 是下一个 design-bundle 尾部目标。 |

### 2.5 Debug Tooling 回归门禁

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

状态：列表与复杂布局的 Compose control、Android Views 源码对照、内存指标、引擎中立报告和
ViewCompose/Compose 归一化门禁已建立；修正后的 revision-4 root 固定频率 15 方法矩阵已验收
三个引擎的全部 5 个 action。
目标：继续收敛列表变更、结构更新和高频容器布局的尾延迟，同时保持三引擎工作负载契约一致。

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
6. [已归档 Android Views 性能对照计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/android-views-performance-control.md)
