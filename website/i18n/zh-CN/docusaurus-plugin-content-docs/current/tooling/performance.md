---
translation_source: tooling/performance.md
translation_source_hash: 943a06e702a039ebfe1b43e17a2c796e3a9cb69f697f68993ceac8a52ef34518
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

对 renderer 敏感的固定频率诊断必须控制所有可能执行被测帧的时钟域。RenderThread 或 GPU
仍受 DVFS 控制时，只锁 CPU 不足以形成有效对照。应把 CPU policy 的最低/最高频率、GPU
devfreq governor 和边界，以及设备公开时的 KGSL power-level 边界写入持久化
`clockPolicy`，并在 target 启动后再次核对当前频率。如果 Qualcomm `cpubw`、`gpubw` 等 CPU/GPU
内存互连 devfreq 域会改变 RenderThread 或 buffer-queue timing，就必须在同一策略中快照并控制
这些票；只有 core/GPU 频率稳定并不足以验收批次。只有证明 OEM 性能服务会覆盖请求的边界时
才可以停止它；批次开始前必须记录其原始状态、全部被修改的时钟边界或带宽票、充电/输入状态和
root 或策略变更，并在拉取最后一份结果后完整恢复。

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

可选 AndroidX Paging 集成的发布态权威基线是 `PagingPerformanceBenchmark`：

1. 它只驱动 ViewCompose 的 `performance.paging@1` 路由，因为被测契约是官方
   `PagingDataPresenter` 集成，不是引擎排名工作负载。
2. 确定性本地 Source 提供 1,000,000 个位置，使用 `pageSize = 32`、`prefetchDistance = 2`、
   `maxSize = 96`、Placeholder、Jump 和按 Query 隔离的稳定 Key。
3. `pagingAppendDrop`、`pagingQueryReplacement` 与 `pagingScroll` 分别执行八次 Append/Drop、
   围绕目标 256 的八次最新 Generation 替换，以及八次向下/向上手势，并检查加载窗口有界。
4. 每个 Release 方法使用五次 `CompilationMode.None` Iteration、Frame Timing 与进程最大内存
   指标；固定稳定窗口和 Ready 检查位于测量之外。
5. `tools/performance/summarize_paging_macrobenchmark.py` 只接受精确的三个方法、每方法五次运行且
   上下文一致的结果；报告 P50/P90/P95/P99、Peak Heap 中位数、可选 RSS 与 Run-P50 CV，使用
   `--enforce` 时拒绝超过 `0.15` 的 CV。

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
2. `diagnosticsThemeDebugToolingIdleLongFlingRevision1` 复用完全相同的手势与锚点契约，并使用
   `CompilationMode.None`，从而在不安装 Baseline Profile 的情况下比较包含可选 Preview 工具的
   Debug APK。
3. `collectionsScrollRevision3` 在 setup 阶段捕获直接场景 LazyColumn 边界，然后在 measured block 内
   每个方向执行 8 次固定 swipe，期间不执行 Accessibility 查询。每次 swipe 使用 500 ms 物理
   稳定窗口，因为 benchmark setup 会关闭 UiAutomator 隐式 idle timeout；省略该窗口会让惯性
   滚动重叠，并在 FrameTimeline 中产生与工作负载无关的 `Buffer Stuffing`。
4. `collectionsStressMutationRevision3` 执行 8 个完整 rotate/insert/reset 闭环，并断言每次 reset
   都恢复原始逻辑顺序。
5. 四个方法都使用相同的 measured block 外 5 秒启动稳定窗口。正式原始结果通过 AndroidX
   benchmark payload 记录 `scenario`、`workloadRevision` 和 `clockPolicy`。

2026-08-15 在 Samsung SM-G991B / Android 13 上验收的 fixture 基线使用 5 次 iteration、每个方法
从 `NONE`/`LIGHT` 起跑、`CompilationMode.Partial` 和 `unlocked-dvfs-preflight-v1` 时钟策略：

| 工作负载 | Frame CPU P50/P95 | Run-P50 CV |
| --- | ---: | ---: |
| `diagnostics.theme@2` 固定长 fling 往返 | 3.067 / 7.336 ms | 0.008 |
| `collection.stress@2` 嵌套列表滚动往返（已退役 fixture） | 3.357 / 6.288 ms | 0.018 |
| `collection.stress@2` 8 轮变更（已退役 fixture） | 4.358 / 10.507 ms | 0.018 |

2026-08-17 的人工审查修复移除了 `collection.stress` 的外层/内层列表嵌套，并把场景推进到
revision 3。Revision 2 数值仅保留为已退役 fixture 的历史证据，不能作为 revision 3 基线。

2026-08-21 的发布后运行使用已 root 的 Xiaomi MI 6 / Android 9 参考设备、目标源码
`9443edef`、benchmark harness 源码 `93afee0f`、R8/资源收缩目标 APK SHA-256
`179f26d15b35d9add9bfacccf03be046ff4e5dccac633c827e90fb3cada126f2`，以及实际测量的 benchmark
APK SHA-256 `b5d70245eeebbbdb90260b3157728772cce0241c3fb522ef9cf1cc52b6457b28`。harness 变更只为两个
revision-3 方法采集 `MemoryUsageMetric(Mode.Max)`，没有改变目标 fixture 或 measured action。
每个方法均执行 5 次 iteration，使用 measured block 外 5 秒 ready 稳定窗口；每个方法之间熄屏
冷却到不高于 37 摄氏度，8 个 CPU 全部在线，暂停充电并停止厂商性能服务，实际编译身份为
`run-from-apk`。AndroidX 报告 `cpuLocked=true`，热降频等待为 0。

初始 v3 策略把 CPU policy 0/4 固定为 1.4016/1.8048 GHz，并把 Adreno 固定为 515 MHz。滚动
复跑的 run-P50 CV 达到 `0.004`，但两批变更的 CV 分别为 `0.192` 和 `0.224`，均不通过。
Perfetto 显示应用主线程工作不变，而慢速变更 iteration 的 RenderThread 总时间从 `2.05` 增至
`3.25 s`；平均 `dequeueBuffer` 从 `0.319` 增至 `1.748 ms/frame`，GPU/CPU 内存互连票处于较低
平台。由于 renderer-sensitive 时钟契约要求控制所有实际执行域，全部 v3 数值只保留为诊断，
不能作为正式 revision-3 基线。

v4 策略进一步把 `cpubw` 和 `gpubw` 固定为最大 `13763` performance 票，并在原始 payload 中记录
`root-fixed-cpu-1401600-1804800-gpu-515000000-cpubw-gpubw-13763-perf-hal-off-v4`：

| Revision-3 动作/运行 | Frame CPU P50/P95/P99 | 帧数范围 | 峰值 heap 中位数（范围） | Run-P50 CV | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 滚动，首批 v4 | 4.206 / 6.221 / 6.680 ms | 803--804 | 4,824 KiB（4,115--5,793） | 0.191 | 已拒绝 |
| 滚动，完整 v4 复跑 | 4.173 / 6.248 / 6.765 ms | 803--805 | 5,149 KiB（4,294--6,251） | 0.196 | 已拒绝 |
| 8 轮变更 | 2.861 / 14.320 / 23.554 ms | 279--299 | 6,662 KiB（6,371--6,732） | 0.025 | 已验收绝对基线 |

两批滚动都形成相同的两个 run-P50 平台：约 `6.0 ms` 与 `4.03--4.11 ms`。成对 trace 中的 CPU、
GPU 与互连控制保持不变，应用主线程总时间也没有实质差异；但慢速运行的 RenderThread
`DrawFrame` 从 `3.298` 增至 `5.093 ms/frame`，`dequeueBuffer` 从 `0.261` 增至
`2.261 ms/frame`。一个每次 iteration 前重启目标进程的诊断 benchmark APK 仍以 CV `0.197`
失败；该实验没有改变生产源码，并在排除混合进程生命周期后删除。剩余滚动方差因此归因于
API 28 的 display/BufferQueue pipeline，而不是 revision-3 列表协调，但它仍会使 timing 基线失效。

设备重新连接后执行的同机 root 审计没有发现可在不改变渲染管线的前提下继续加入的公开控制项：
面板只公开一个固定 60 Hz 模式；framebuffer 的 `idle_time`、动态局部更新、command-mode 自动刷新
和动态 FPS 均已为 `0`；SurfaceFlinger 也已使用 `debug.sf.disable_backpressure=1` 与
`debug.sf.latch_unsignaled=1`。强制 MDP/HWC 合成、修改 SurfaceFlinger phase offset 或替换
HWUI renderer 会改变被测系统路径，而不是控制既有变量，因此不能用于基线验收。本次设备审计
关闭的是安全控制项搜索，而不是滚动门禁。

2026-08-25 的后续检查排除了两个看似可行的捷径。源码与历史记录证明，
`collectionsScrollRevision3` 在采集任何 revision-3 结果前，就已经在每次手势后包含 `500 ms`
稳定窗口；如果仅为了加入节奏控制而升级到 revision 4，实际是在给未变化的工作负载贴上错误版本。
Pixel 4 XL / Android 13 预检虽然发现系统提供 fixed-performance 命令，但该 user build 未实现所需
Power HAL AIDL 模式，实际调用失败。没有执行 benchmark，临时显示设置也已精确恢复为 Peak Refresh
`null`、Minimum Refresh `0.0`、自动旋转 `1` 和 User Rotation `0`。模拟器可以验证路由和结果管道，
但受宿主机约束的 GPU 与显示管线不能关闭这条物理设备基线。

当前没有设备符合剩余集合滚动验收运行的要求。已 root 的小米若不改变被测渲染路径，就无法消除其
显示缓冲平台；Pixel 无法进入可证明的固定性能模式；模拟器时序也不属于物理设备证据。因此正式
重采集因硬件条件而暂停，直到获得另一台合适的物理参考设备；现有设备不应继续用于这项门禁复跑。

限定结论为 `mixed`：变更已经拥有稳定的固定频率绝对基线；由于 revision 2 fixture 已退役，
方向性比较仍为 `inconclusive`。滚动仍为 `inconclusive`，因此发布后 Phase 1 门禁尚未完成。
限制包括仅覆盖一台 API 28 设备、`run-from-apk` JIT/code placement，以及尚未解决的系统显示
缓冲平台。替换设备不要求 Android 9/API 28，当前也没有符合要求的物理设备。下一步保持 revision 3
和 `0.15` 门禁不变，等待另一台能够证明整个批次 CPU、GPU 与显示管线控制稳定的物理参考设备，
然后才重新采集滚动；只有能够提供等价控制与观测时才可以不使用 root。不得仅为得到通过批次而
修改 swipe 次数、节奏或 fixture。

原始 revision-2 集合滚动预检也是手势驱动污染的参考案例。最初在 measured block 中重复定位
target 会增加 Accessibility 遍历；移除后，连续无间隔 swipe 仍产生约 3.6、7.2 与 14.7 ms 的
run-P50 平台。Perfetto 显示 `RV Scroll`、display-list recording 与 RenderThread draw 成本稳定，
只有 `dequeueBuffer` 等待变化，FrameTimeline 把慢帧归类为 `Buffer Stuffing`。调整刷新率和 ART
编译策略都没有消除它。显式逐手势稳定窗口产生的是已退役 fixture 的 `0.018` CV；revision 3 在
首次运行前就已经继承相同等待，因此该早期结果不是尚未尝试的 revision-3 修复。不得把没有节奏
控制的合成输入循环解释为框架滚动成本。

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

<div className="benchmark-evidence">

2026-08-17 的 Pixel 5 / Android 14 运行只比较 `performance.shadow-list@3` 中等价的
ViewCompose 与 Compose 方法；Android Views 的阴影实现不同，因而不参与。目标提交为
`0cf6515f305a7d230018f72599b1b52b2a0acf26`，被测 APK SHA-256 为
`fad1d21cbbf25ba40da5e507a4de997e35f4b3f23dda81bd0e17cfd726c34ea7`；协议采用 run-from-APK、
`Auto`（`ExactBitmap`）、5 次 iteration、5 秒 setup、4 轮上下滚动和 8 个变更闭环。固定性能
模式下 CPU policy 为 1.8048/2.208/2.4 GHz，GPU 活跃频率为 625 MHz，温控保持 `NONE`。
AndroidX Benchmark 1.5.0-beta01 仅替换 runner，以规避此设备上 1.4.1 的 Perfetto 关闭超时；
被测应用代码未改变。

| 工作负载/运行 | ViewCompose P50/P95 | Compose P50/P95 | ViewCompose/Compose run-P50 CV | ViewCompose 变化 | 分类 |
| --- | ---: | ---: | ---: | ---: | --- |
| `performance.shadow-list@3` 滚动 | 2.968 / 5.979 ms | 3.590 / 6.499 ms | 0.013 / 0.052 | 降低 17.3% / 8.0% | `improved` |
| `performance.shadow-list@3` 变更，首次运行 | 2.406 / 6.707 ms | 4.529 / 10.249 ms | 0.203 / 0.061 | 降低 46.9% / 34.6% | `inconclusive` |
| `performance.shadow-list@3` 变更，完整复跑 | 3.319 / 6.947 ms | 4.529 / 10.249 ms | 0.211 / 0.061 | 降低 26.7% / 32.2% | `inconclusive` |

滚动 P50 跨过 10% 与 0.3 ms 门禁，P95 方向同样有利。其 heap/RSS 中位数为
14,481/68,332 KiB，Compose 为 12,973/65,112 KiB（+11.6%/+4.9%），未跨过内存门禁，因此
滚动结论为 `improved`，伴随非实质内存成本。两次完整 ViewCompose 变更运行都未通过 `0.15`
稳定性门禁，并形成两组 iteration-P50 平台；变更结论为 `inconclusive`，复跑也不替换首次结果。
下一步先排查 Session/cache 启动状态。由于设备与工作负载 revision 不同，本轮不与 Samsung
revision-2 基线作纵向比较。

随后在同一台 Pixel 5 上，以相同被测应用、runner 和时钟策略完整运行了
`performance.complex-layout@4` 的 ViewCompose、Compose、Android Views 三方全部动作，并运行
`performance.shadow-complex-layout@3` 中 ViewCompose/Compose 两个等价动作。首轮矩阵生成 65
份 trace；对两个不稳定的普通更新动作分别进行三方完整复跑，又生成 30 份 trace。所有正确性、
action/reset 和恢复断言均通过。电池温度保持在 29.8--32.3 摄氏度，Android 温控状态始终为
`NONE`。

| 工作负载/运行 | ViewCompose P50/P95 | Compose P50/P95 | Android Views P50/P95 | Run-P50 CV（VC/C/Views） | Frame CPU 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| `performance.complex-layout@4` 滚动 | 3.737 / 4.139 ms | 3.844 / 8.222 ms | 3.123 / 4.075 ms | 0.009 / 0.052 / 0.008 | 相对 Compose 为 `improved`；相对 Views 为 `regressed` |
| 属性更新，首次运行 | 4.388 / 10.646 ms | 7.099 / 22.834 ms | 3.126 / 15.741 ms | 0.157 / 0.066 / 0.914 | `inconclusive` |
| 属性更新，完整复跑 | 3.666 / 10.486 ms | 7.204 / 22.434 ms | 3.904 / 8.200 ms | 0.171 / 0.020 / 0.159 | `inconclusive` |
| 结构更新，首次运行 | 3.651 / 6.409 ms | 6.198 / 12.763 ms | 3.001 / 7.802 ms | 0.055 / 0.175 / 0.189 | `inconclusive` |
| 结构更新，完整复跑 | 3.821 / 6.750 ms | 6.470 / 12.441 ms | 3.597 / 8.006 ms | 0.131 / 0.059 / 0.227 | 相对 Compose 为 `improved`；Views 为 `inconclusive` |
| `performance.shadow-complex-layout@3` 滚动 | 4.104 / 4.586 ms | 4.096 / 9.299 ms | 不适用 | 0.011 / 0.057 / 不适用 | `improved` |
| 阴影属性更新 | 3.869 / 11.523 ms | 7.559 / 22.915 ms | 不适用 | 0.147 / 0.020 / 不适用 | `improved` |

普通滚动的已验收 frame 结果方向分裂：相对 Compose，P50 降低 2.8%，未达实质门槛，P95
降低 49.7%；相对 Android Views，P50 实质升高 19.7%，P95 仅升高 1.6%。ViewCompose 的
heap/RSS 中位数为 18,770/85,580 KiB，Compose 为 18,373/74,964 KiB，Views 为
10,281/73,864 KiB。RSS 成本相对两方都跨过门禁，相对 Views 时两项内存指标均跨过门禁，
所以完整滚动分类为 `mixed`。

结构更新复跑建立了稳定的 ViewCompose/Compose 对照：ViewCompose P50/P95 分别降低
40.9%/45.7%，但 heap/RSS 中位数为 33,984/107,536 KiB，Compose 为
27,874.5/83,624 KiB（+21.9%/+28.6%）。因此完整的 ViewCompose/Compose 分类是 `mixed`，
而不是普遍胜出。Android Views 的 CV 仍为 `0.227`，该对照为 `inconclusive`。属性更新两次
完整运行都不稳定（ViewCompose CV 先后为 `0.157`、`0.171`，Views 为 `0.914`、`0.159`），
即使单个数字看起来有利，也不能解释为性能结论。

两个阴影复杂布局 frame 对照都稳定且为 `improved`：滚动 P50 中性的升高 0.2%，P95 降低
50.7%；属性更新 P50/P95 降低 48.8%/49.7%。内存抵消了部分收益：滚动 heap 增加 2.1%、
RSS 增加 21.9%；属性更新 heap 降低 17.5%、RSS 增加 16.8%。两项 RSS 变化都跨过 10% 与
4,096 KiB 门禁，因此两个场景的完整分类都是 `mixed`。

这些结果是已验收的逐帧分布，不是合成的事务耗时：Compose 更新方法产生 46 个测量帧，
ViewCompose 与 Android Views 约为 240--260 个。Android Views 的阴影实现不等价，仍不参与
阴影排名。下一步是在接受不稳定普通更新动作之前，对 action/reset 调度和初始 Session/cache
状态增加观测；同时降低 ViewCompose 复杂树 RSS，并缩小普通滚动相对 Views 的 P50 差距。
Pixel 5 工作负载 revision 不能与 Samsung 基线作纵向比较。

</div>

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
4. 纵向门禁保持 report-v1 兼容，只有 ViewCompose 原始指标和同轮 Control 归一化比值都超过阈值
   才失败。Compose 仍是优先 Control；没有 Compose 的场景使用 Android Views。如果 Current 或
   Baseline 的 Subject/Control 任一方不稳定，该行显示 `INCONCLUSIVE`，而不是 PASS 或 FAIL。
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

### 2.4 当前已接受证据

本节只保留可持续执行的决策，以及应用这些决策所需的绝对值。设备准备、APK 身份、被拒绝
的运行、Trace 和逐候选排查过程保存在
[Android Views 对照](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/android-views-performance-control.md)、
[Observed Property](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/observed-property-transactions.md)、
[Lazy 集合](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/lazy-collection-three-layer-hard-cut.md)、
[ConstraintLayout](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md)
和[动画](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/animation-compose-capability-expansion.md)
归档中。归档负责解释历史，下面的分类才是当前规范。

除非表格另有说明，已接受的物理证据都使用 Root 小米 6 / Android 9 参考设备、R8 与资源
收缩后的非 Debuggable 目标、5 次 `run-from-apk` 迭代、固定在 1.4016/1.8048 GHz 的
CPU Policy、515 MHz GPU、固定的可用 Interconnect Vote、暂停充电和停止厂商性能服务；
AndroidX 未报告热节流等待。每批结束后均恢复所有设备状态。

#### 2.4.1 Renderer、集合与内存结论

##### 2.4.1 复杂布局更新尾延迟排查 {/* #241-complex-layout-update-tail-latency-investigation */}

##### 2.4.2 Root 固定频率的 Revision 4 验收与剩余尾延迟 {/* #242-root-controlled-revision-4-acceptance-and-remaining-tails */}

##### 2.4.3 Lazy 集合与 RecyclerView 尾延迟硬切 {/* #243-lazy-collection-and-recyclerview-tail-latency-hard-cut */}

下表帧数据为 P50/P95 毫秒，Heap 为峰值中位数 KiB。

| 证据 | 绝对结果 | 归一化结果 | 分类 |
| --- | --- | --- | --- |
| `performance.list@5` 滚动 | ViewCompose `5.328/9.538`、Heap `7650`；Compose `4.743/7.616`、`7398`；Android Views `4.991/7.188`、`4049` | 相对 Compose `+12.3%/+25.2%`；相对 Android Views `+6.8%/+32.7%` | 相对两个对照均为 `regressed`，因为 P95 同时跨过两部分门槛。 |
| `performance.list@5` 更新 | ViewCompose `4.247/12.698`、Heap `8128`；Compose `5.207/18.568`、`8597`；Android Views `4.287/7.849`、`5864` | 相对 Compose `-18.4%/-31.6%`，Heap `-5.5%`；相对 Android Views `-0.9%/+61.8%`，Heap `+38.6%` | 相对 Compose 为 `improved`；相对 Android Views 为 `regressed`，原生尾部仍显著更低。 |
| `performance.complex-layout@4` 属性更新 | ViewCompose `5.709/33.050`；Compose `7.663/46.852`；Android Views `6.137/19.270` | 相对 Compose `-25.5%/-29.5%`；相对 Android Views `-7.0%/+71.5%` | 相对 Compose 为 `improved`；相对 Android Views 的 P95 为 `regressed`。 |
| `performance.complex-layout@4` 结构更新 | ViewCompose `5.590/46.009`；Compose `7.255/26.844`；Android Views `5.444/15.051` | 相对 Compose `-22.9%/+71.4%`；相对 Android Views `+2.7%/+205.7%` | 相对 Compose 为 `mixed`，相对 Android Views 为 `regressed`。 |
| 强 `LazyItemsSnapshot` 权衡 | 普通候选 `5.022/26.428`、Heap `8254`；两次已接受 Snapshot 运行 `6.432/17.268`、`8419` 与 `6.133/17.638`、`8533` | 相对普通路径，Snapshot P50 `+28.1%/+22.1%`，P95 `-34.7%/-33.3%`，峰值 Heap `+2.0%/+3.4%` | 整体为 `mixed`；对显式选择的事务尾延迟目标为 `improved`。 |
| Lazy 分配硬切 | 对照 P50/P95/P99 `5.218/9.248/11.004`、Heap `7709`；候选 `5.342/9.304/10.523`、`7591` | `+2.37%/+0.60%/-4.37%`；Heap `-118 KiB`（`-1.53%`）。GC 后归因减少 `129,518` 个浅层字节和 `6,276` 个对象 | 时序为 `no material change`；分配结果为 `improved`。 |

列表矩阵只证明 Ready 之后、预构建 Snapshot 的稳态行为，不覆盖启动、Snapshot 构造、
单调数据流、总能耗或干净的未编译 ART。不同引擎可能产生不同帧数，所以跨引擎结论使用已
接受的逐帧分布，不虚构跨引擎事务。下一步集合目标是 ViewCompose 滚动 P95/Heap、相对
Android Views 的更新尾部差距、冷构造和单调数据流。

Observed Property 事务显著减少完整树工作，并优于同批 Compose 属性对照，但原生属性失效
与 Traversal 仍拥有更低的尾延迟。已接受的 API 33 Trace 证明属性帧进入
`VC.ObservedPropertyRead` 和 `VC.ObservedPropertyRender`，且不返回根
`VC.Compose`/`VC.RenderTree`；未锁定 DVFS 将该 Trace 的结论限制为阶段存在性，固定
频率的小米矩阵才负责时序。

#### 2.4.2 导航与设计系统基线

##### 2.4.4 导航与设计系统诊断 {/* #244-navigation-and-design-system-diagnostics */}

| 导航动作 | P50/P95/P99，毫秒 | Run-P50 CV | 结论 |
| --- | ---: | ---: | --- |
| Push，不做预编译 | `5.552/12.598/41.929` | `0.039` | 已接受的绝对基线。 |
| Push，请求 Profile Guided 编译 | `5.601/11.173/42.148` | `0.070` | `no material change`；P95 降低 11.3%，P99 不变。 |
| System Back，不做预编译 | `5.558/15.618/40.089` | `0.039` | 已接受的绝对基线。 |
| System Back，请求 Profile Guided 编译 | `5.409/13.864/41.685` | `0.064` | `no material change`；P95 降低 11.2%，P99 仍约为 42 毫秒。 |

Android 9 把两种请求编译的变体都报告为 `run-from-apk`，因此不能证明不同的 ART 编译
状态；但它们足以排除普通预热可以解释导航 P99。API 28 的非 Debuggable 目标无法提供应用
Trace Section。

设计系统 revision 3 矩阵是绝对基线，不是对不同视觉系统的排名：

| 动作 | Cut Contrast | Rounded Reference | Cupertino Pressure | 结论 |
| --- | ---: | ---: | ---: | --- |
| 首次展示中位数，毫秒 | `531.254` | `558.753` | `561.880` | 稳定；归一化方向为 `inconclusive`。 |
| Patch P50/P95/P99，毫秒 | `7.934/23.008/25.716` | `7.959/23.855/26.222` | `7.842/15.907/24.841` | 稳定的绝对基线。 |
| 滚动 P50/P95/P99，毫秒 | `3.798/7.582/9.000` | `3.731/8.071/9.124` | `3.730/7.572/8.905` | 稳定，所有 P99 均低于一个 60 Hz 帧。 |
| 活跃动画 P50/P95/P99，毫秒 | `7.661/17.285/20.884` | `7.617/15.146/21.664` | `8.045/15.736/18.455` | 稳定，各自尾部继续监控。 |
| Cut Contrast Overlay P50/P95/P99，毫秒 | `4.535/27.499/39.833` | — | — | 稳定；Overlay 尾部是下一个设计包目标。 |

Run-P50 CV 范围为 `0.009..0.110`。在出现匹配的历史或未来基线前，不作设计系统方向性
结论。

#### 2.4.3 ConstraintLayout 发版安全结论

##### 2.4.5 ConstraintLayout 首发安全 {/* #245-constraintlayout-first-release-safety */}

##### 2.4.5 ConstraintLayout 首发性能安全

##### 2.4.6 ConstraintLayout 第一阶段协调预检 {/* #246-constraintlayout-phase-1-reconciliation-preflight */}

##### 2.4.7 ConstraintLayout 第四阶段受控矩阵 {/* #247-constraintlayout-phase-4-controlled-matrix */}

Revision 6 矩阵每次迭代直接执行 16 轮 Accessibility 更新/重置。Released/Candidate 有
7 对稳定，另 5 对因至少一侧超过 `0.15` Run-P50 CV 上限而保持 `inconclusive`。

| 动作 | Released P50/P95/P99，毫秒 | Candidate P50/P95/P99，毫秒 | Direct AndroidX P50/P95，毫秒 | 最终 CV Released/Candidate | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| `stable-10` | `9.116/10.918/13.854` | `8.803/11.462/14.612` | `3.438/4.676` | `0.143/0.120` | `no material change` |
| `stable-50` | `10.614/19.107/23.141` | `11.237/17.137/19.951` | `4.402/5.971` | `0.117/0.180` | `inconclusive` |
| `stable-100` | `12.674/24.434/26.283` | `12.585/24.398/28.491` | `5.635/7.486` | `0.111/0.121` | `no material change` |
| `scalar-10` | `10.478/13.931/14.977` | `9.776/13.316/14.977` | `4.828/6.421` | `0.179/0.171` | `inconclusive` |
| `scalar-50` | `11.588/21.484/24.060` | `11.553/22.947/25.301` | `7.392/9.032` | `0.229/0.205` | `inconclusive` |
| `scalar-100` | `15.597/36.150/41.827` | `16.100/34.624/38.874` | `11.538/14.207` | `0.021/0.125` | `no material change` |
| `helper-10` | `7.955/10.926/13.923` | `8.320/11.380/13.513` | `4.558/6.126` | `0.128/0.124` | `no material change` |
| `helper-50` | `7.678/12.484/14.134` | `7.346/12.243/14.959` | `6.301/8.201` | `0.227/0.140` | `inconclusive` |
| `helper-100` | `8.303/15.280/19.498` | `7.826/14.579/16.399` | `9.373/10.830` | `0.109/0.082` | `no material change` |
| `topology-10` | `9.774/12.489/14.589` | `10.390/14.101/19.010` | `4.811/6.366` | `0.124/0.137` | `no material change` |
| `topology-50` | `13.570/22.272/27.262` | `12.367/21.920/25.432` | `7.312/8.683` | `0.201/0.140` | `inconclusive` |
| `topology-100` | `15.719/32.688/34.876` | `15.390/34.778/38.771` | `11.409/12.850` | `0.110/0.098` | `no material change` |

稳定行中，Candidate 相对 Direct 的 P50 变化范围为 `-5.7%..+8.4%`，P95 为
`-4.0%..+14.3%`。Candidate 峰值 Heap 变化范围为 `-3.8%..+10.5%`，没有一行跨过 15%
与 2,048 KiB 的组合门槛。Direct AndroidX 在全部 12 个动作的 P95 和其中 11 个动作的 P50
更快。发版安全结论是 `no material change`：结构快速路径保留精确零工作和有界写入证据，
但不宣称整帧性能领先。后续整帧工作必须建立新的可归因计划，不能反复采样。

#### 2.4.4 动画与共享运动结论

##### 2.4.8 动画 Revision 1 的物理改造前基线 {/* #248-animation-revision-1-pre-physics-baseline */}

##### 2.4.9 动画 Revision 1 第一阶段物理候选 {/* #249-animation-revision-1-phase-1-physical-candidate */}

##### 2.4.10 动画 Revision 2 的 AnimatedContent 对照 {/* #2410-animation-revision-2-animatedcontent-comparison */}

##### 2.4.11 动画 Revision 3 的丰富可见性发版安全对照 {/* #2411-animation-revision-3-rich-visibility-release-safety-comparison */}

##### 2.4.12 动画 Revision 2 的可寻址 Transition 基线 {/* #2412-animation-revision-2-seekable-transition-baseline */}

##### 2.4.13 动画 Revision 1 的真实 Bounds 对照 {/* #2413-animation-revision-1-real-bounds-comparison */}

##### 2.4.14 导航 Revision 1 的共享内容对照 {/* #2414-navigation-revision-1-shared-content-comparison */}

物理动画硬切保留 revision 1 的动作和固定频率策略：

| 工作负载 | Duration 基线 P50/P95，毫秒；Heap KiB | Physical 候选 P50/P95，毫秒；Heap KiB | P50/P95/Heap 变化 | 结论 |
| --- | --- | --- | --- | --- |
| `animation.specs@1` | `8.854/13.067; 8113` | `6.114/8.303; 8123` | `-30.9%/-36.5%/+0.1%` | 帧 CPU 为 `improved`。 |
| `animation.content@1` | `7.102/10.454; 7341` | `5.291/8.444; 7776` | `-25.5%/-19.2%/+5.9%` | 帧 CPU 为 `improved`。 |
| `animation.content-size@1` | `4.850/7.258; 6514` | `2.835/6.727; 6383` | `-41.5%/-7.3%/-2.0%` | P50 为 `improved`，P95 未回退。 |
| `animation.transition@1` | `8.231/12.388; 8283` | `6.322/8.408; 8387` | `-23.2%/-32.1%/+1.3%` | 帧 CPU 为 `improved`。 |

所有 Run-P50 CV 都不高于 `0.012`，Heap 为 `no material change`。物理收敛会改变帧数，
所以这些是逐帧 CPU 证据，不是总时长或能耗证据。

| 能力 | 对照或基线 | 候选 | 归一化结果 | 分类 |
| --- | --- | --- | --- | --- |
| `animation.content@2` AnimatedContent | Crossfade P50/P95/P99 `5.680/8.678/10.785`、Heap `8022` | `5.589/9.329/10.996`、Heap `8334` | `-1.6%/+7.5%/+2.0%`；Heap `+3.9%` | 帧与 Heap 为 `no material change`。 |
| 丰富 Visibility revision 3 | Phase 3 前 `8.138/10.760/12.343`、Heap `7846` | `8.334/11.115/15.723`、Heap `8149` | `+2.4%/+3.3%/+27.4%`；Heap `+3.9%` | P50/P95/Heap 为 `no material change`；P99 是低于一个 60 Hz 帧的监控项。 |
| `animation.transition@2` Seek | 无 | `7.775/10.493/11.718`、Heap `8474`、CV `0.011` | 没有兼容的旧工作负载 | 稳定绝对基线；归一化方向为 `inconclusive`。 |
| `animation.bounds@1` | Snap `8.727/25.762/28.556`、Heap `6868` | Bounds `5.124/6.438/18.503`、Heap `6714` | `-41.3%/-75.0%/-35.2%`；Heap `-2.2%` | 活跃逐帧 CPU 为 `improved`；Heap 为 `no material change`。 |
| `navigation.shared-motion@1` | 普通运动 `3.989/8.487/30.020`、Heap `6651` | 两个共享对 `4.073/8.096/36.099`、Heap `6971` | `+2.1%/-4.6%/+20.3%`；Heap `+4.8%` | P50/P95/Heap 为 `no material change`；P99 继续作为导航尾部监控项。 |

Bounds 两侧有意产生 16 与 464 帧，因此不能推导总 CPU 工作或能耗。丰富 Visibility 也增加
编排覆盖面和帧数。共享运动覆盖已提交 Push 的 Snapshot 准备和释放，不覆盖单独的 Predictive
Back 帧基准。这些行只覆盖一台 OEM/API 28 设备、峰值而非 GC 后保留内存，不包含逐对象分配
事件或直接能耗测量。Retarget、一写入者、有界保留、回滚、清理和生命周期正确性由确定性测试
负责。

#### 2.4.5 Paging 集成发布态基线

首份验收的 Paging Release 基线于 2026-08-25 在已 Root 的 Xiaomi MI 6 / Android 9、60 Hz
环境采集。CPU 小核与大核 Policy 分别固定为 `1,401,600` 与 `1,804,800 kHz`，GPU 固定为
`515 MHz`，`cpubw`/`gpubw` 固定为 `13,763`；暂停充电并停止会覆盖配置的厂商性能服务。
三个方法使用同一份 R8 Target 与 Benchmark APK，`CompilationMode.None` 报告为
`run-from-apk`，每个方法运行五次计量 Iteration。Append/Query/Scroll 开始温度分别为
`33/34/35°C`，恢复后的设备最终为 `36°C`。独立的运行后回读确认 CPU/GPU/带宽默认 Governor
与边界、充电、输入和性能服务状态均已恢复。

| 动作 | P50/P90/P95/P99，毫秒 | Peak Heap 中位数，KiB | Peak RSS 中位数，KiB | Run-P50 CV | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| Append/Drop | `4.281/29.189/33.973/43.592` | `117,797` | n/a | `0.077` | 稳定的绝对基线。 |
| Query Replacement | `4.215/13.810/40.809/48.345` | `128,433` | n/a | `0.021` | 稳定的绝对基线。 |
| Scroll | `2.581/3.699/4.066/6.511` | `119,087` | n/a | `0.006` | 稳定的绝对基线。 |

所有行都通过冻结的 `0.15` 稳定性上限。由于这是首个兼容工作负载，归一化方向为
**inconclusive**：这些数值建立绝对发布态基线，并提升有界窗口、Generation 替换和真机信心，
但不能证明性能得到提升或某个引擎占优。

Android 9 的 `MemoryUsageMetric` 输出进程 Peak Heap，但不输出 RSS。这些值是进程峰值，不是增量
或 GC 后保留内存；该批次只覆盖一台 OEM、即时本地 Page，不包含数据库、网络、磁盘、校准能耗、
启动或总时长。Accessibility Polling 与 Action Settling 属于冻结的交互契约。未来若要作方向性
结论，必须保持相同路由 Revision、设备/系统、固定频率策略、Iteration 和 APK 上下文：

```bash
python3 tools/performance/summarize_paging_macrobenchmark.py \
  /path/to/paging-results \
  --output /path/to/paging-baseline.md \
  --json-output /path/to/paging-baseline.json \
  --enforce
```

#### 2.4.6 当前决策边界

当前证据只支持有范围的结论：

1. 已接受行中，ViewCompose 更新与属性工作可与 Compose 竞争或更快，但 Android Views 仍在
   重要滚动、Traversal 和更新尾部占优。
2. 强 Lazy Snapshot 是显式的中位数换尾部权衡；普通 `List` 路径仍是通用数据流默认值。
3. ConstraintLayout 结构快速路径因确定性的零工作边界而保留，不宣称整帧领先。
4. 动画与共享运动切片通过各自发版安全门槛；帧数变化阻止总工作量或能耗结论。
5. Paging 已建立稳定的首份绝对 Release 基线；没有兼容旧基线可支持归一化性能结论。
6. 没有任何矩阵证明通用的帧时或内存胜者。

后续工作必须从明确的剩余差距开始，保持相同工作负载身份和控制条件，并记录新的绝对值、
归一化值、稳定性、限制和下一步。来自不同设备或工作负载 Revision 的结果可以建立绝对基线，
但不能静默替换纵向对照。

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

### 2.5.1 动画时间线工具对比

Phase 7 于 2026-08-23 在已 Root 的 Xiaomi MI 6 / Android 9、60 Hz 环境完成验收。CPU 小核与
大核 Policy 分别固定为 `1,401,600` 与 `1,804,800 kHz`，GPU 固定为 `515 MHz`，Qualcomm CPU
最低频率投票清零；整批测试暂停充电并停止厂商性能服务。测试运行中回读确认三个频率都保持
固定。电池温度从 `36` 升至 `37°C`，AndroidX Thermal-throttle Sleep 为零。两组使用同一份
Debuggable Target APK `56b94faf26f5dc0f94b976343e3d3a1c868953027cf696d8c704a8122a605fad`
与 Benchmark APK `1ccd78d371ee5ed5890714511fe79c9464cc7be4cf9baf5f03cdb8669506f687`；
`CompilationMode.None` 报告为 `run-from-apk`。

每组都对 `animation.transition` 执行 5 个计量 Iteration；每个 Iteration 完成 4 次完整正向/反向
Round Trip，每轮恰好 200 帧。Inactive 组在 Setup 前删除报告并产生零次 Report Write。
Requested 组执行 40 次计量内的 500 ms Capture；AndroidX 未计量的 Validation Workload 另产生
8 个响应。每个响应都匹配 Nonce、Selected Identity、Success Status 与 `1..64` Sample 边界。

| 分组 | P50/P90/P95/P99，ms | Peak Heap 中位数（Run 范围），KiB | Run-P50 CV |
| --- | --- | --- | --- |
| Inactive | `12.236 / 14.113 / 15.311 / 18.265` | `9,493`（`9,462--9,537`） | `0.039` |
| Requested Capture | `12.398 / 14.482 / 15.464 / 20.422` | `9,823`（`9,411--10,951`） | `0.036` |

Requested Capture 的 P50 变化为 `+0.162 ms`（`+1.32%`），P95 为 `+0.153 ms`（`+1.00%`），
Peak Heap 中位数为 `+330 KiB`（`+3.48%`）。三项都没有同时跨过冻结门槛的两部分，因此局部
结论是 **no material change**。P99 为 `+2.157 ms`（`+11.81%`），继续作为冻结 P50/P95
决策门槛之外明确记录的 Debug Tooling Tail Watch Item。

验收前诊断发现两个分配缺陷：同一逻辑帧的多个 Channel Commit 被保留成不完整 Sample；每次
JSON 边界检查都会重新编码不断增长的前缀。验收实现会合并相同 Segment Version/Play Time 的
Commit，并以一个有界 Builder 配合增量 UTF-8 计数直接编码。这样在不缩减冻结的 500 ms、
64 Sample、32 Channel 与 256 KiB 上限的前提下，把最终 Requested Heap 增量降到 `330 KiB`。
局限包括仅一台 OEM/API-28 设备、Debuggable/JIT Target、Peak 而非 Post-GC Retained Memory、
没有逐对象 Allocation Trace，也没有直接能耗测量。下一步是完成仓库与真机验收，不为取得更
有利 P99 而继续重复采样。

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

状态：已完成。Render Tree、Patch、CompositionLocal、重组原因、跨 Session 关联、有界故障聚合、
节点高亮和有限逐节点耗时都已可读。耗时请求是带时钟开销度量的诊断采样，不能替代
Macrobenchmark。

2026-08-24 的收尾在同一台已 Root 的 Xiaomi MI 6 / Android 9 上，对比 Phase 4 提交
`da67ad78` 与 Phase 6 候选。两个 Debug APK 使用同一套五轮、十六次 Fling 的
`diagnostics.theme` 工作负载；CPU 固定为 1.4016/1.8048 GHz，GPU 固定为 515 MHz，
`cpubw` 与 `gpubw` 固定为 13763。Frame CPU P50 从 2.684531 变为 2.769011 ms
（+0.084480 ms、+3.15%），P95 从 5.012443 变为 5.200990 ms（+0.188547 ms、+3.76%）；
Run-P50 CV 为 0.0155/0.0153。两项指标都没有同时越过相对与绝对失败阈值，因此空闲结论为
**无实质变化**。两次运行的 v6/v7 工具报告写入都严格为零。

另外二十次显式 protocol-v7 Source 刷新都返回固定 32,633 bytes、两个 Session 的响应；从主机
广播到读取匹配报告的 P50/P95/最大值为 161.304/175.494/175.936 ms，开始和结束温度均为
34.0 °C。该成本低于两秒请求预算，且没有摊入空闲结果。由于目标是度量可选 Debug 工具而非
Release 性能，对照显式抑制 AndroidX 的 `DEBUGGABLE` 警告。主机耗时包含 adb 与轮询；单台
Android 9 手机和固定频率不能建立 OEM、校准功耗或 Release 矩阵。完整执行记录保留在
[已归档的诊断计划](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md)。

在前台运行 Debug Demo 后，可使用以下命令复现显式请求度量：

```bash
python3 tools/performance/measure_device_diagnostics_request.py \
  --serial "$ANDROID_SERIAL" \
  --operation source \
  --warmups 5 \
  --iterations 20 \
  --clock-policy <recorded-policy> \
  --output build/diagnostics-request.json
./gradlew testDeviceDiagnosticsRequestMeasurementTool
```

工具会先校验 Protocol、Nonce、Operation 与 Package 身份，再接受响应，并保留原始延迟样本而不是
只输出聚合值。

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
