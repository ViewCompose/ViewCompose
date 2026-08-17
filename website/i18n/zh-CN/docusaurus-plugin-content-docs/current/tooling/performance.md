---
translation_source: tooling/performance.md
translation_source_hash: fdd594701a9a39a421715aec9aedfc2bbc2dafa836a1cefd9e3035436972f98b
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
`clockPolicy`，并在 target 启动后再次核对当前频率。只有证明 OEM 性能服务会覆盖请求的边界时
才可以停止它；批次开始前必须记录其原始状态、全部被修改的时钟边界、充电/输入状态和 root 或
策略变更，并在拉取最后一份结果后完整恢复。

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
2. `collectionsScrollRevision3` 在 setup 阶段捕获直接场景 LazyColumn 边界，然后在 measured block 内
   每个方向执行 8 次固定 swipe，期间不执行 Accessibility 查询。每次 swipe 使用 500 ms 物理
   稳定窗口，因为 benchmark setup 会关闭 UiAutomator 隐式 idle timeout；省略该窗口会让惯性
   滚动重叠，并在 FrameTimeline 中产生与工作负载无关的 `Buffer Stuffing`。
3. `collectionsStressMutationRevision3` 执行 8 个完整 rotate/insert/reset 闭环，并断言每次 reset
   都恢复原始逻辑顺序。
4. 三个方法都使用相同的 measured block 外 5 秒启动稳定窗口。正式原始结果通过 AndroidX
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
目前尚未验收 revision 3 benchmark 批次，因此性能结论为 `inconclusive`：语义工作负载得以
保留，但几何与手势 Ownership 已改变。下一步是在相同 SM-G991B 协议上重新采集滚动与 8 轮
变更批次，针对明确匹配的 control 比较绝对 P50/P95 与归一化差异，并且仅在现有稳定性和时钟
策略 Gate 通过后验收结果。

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
2. 滚动并非普遍占优。Samsung revision-2 阴影列表 P95 仍是历史方向性目标；Pixel 5
   revision-3 阴影列表对照方向有利，但不能跨设备、跨工作负载 revision 关闭该目标；非 Lazy
   复杂布局 P50 与普通列表 P95 也仍是需要监测的缺口；
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

随后在 2026-08-16 使用相同设备、固定频率、构建、工作负载和 48 帧协议验收了该优化。
ViewCompose 列表变更达到 `4.392/26.862 ms` P50/P95，run-P50 CV 为 `0.083`，最大 heap
中位数为 `8507 KiB`。相对上一份 ViewCompose 结果，纵向结论为 `improved`：P50 降低
3.7%，P95 降低 33.4%，最大 heap 中位数降低 18.5%。实现将 key/sticky/identity 索引合并为
一次遍历，避免 Adapter 构造不会消费的公开更新序列，只检查已挂载 Holder 的同步刷新，并移除
Lazy Item 收集器的重复副本和回调对象。复测 trace 中 Adapter JIT 热点已消失。跨引擎结论仍为
`mixed`：该 P50 分别比 Compose 和 Android Views 低 38.6% 和 22.2%，但 P95 仍分别高
12.0% 和 180.3%。因此下一目标是更上层的帧事务和原生 traversal，而不是放松逻辑 Session
正确性。

2026-08-16 的第二阶段诊断通过临时阶段计时拆分了该事务。重复 mutation 帧在
`VC.Compose` 中约消耗 `2.1--15.5 ms`，`VC.RenderTree` 通常为 `1.0--3.8 ms`；reset
composition 仅约 `0.5--1.3 ms`。差异来自 fixture 在每次 mutation 中重复构造同一份 1000 行
revision-1 模型。测试基座现在保留最近一次非零 revision 的不可变快照：每个进程仍保留一次冷
构造，其余七轮专注测量提交和 reconciliation。帧调度器与 Lazy Holder 热路径也改用专用内部
host，避免通用捕获回调。renderer lowering 预检候选得到 `4.859/27.755 ms` P50/P95，额外树
扫描未降低事务尾部，因此已拒绝并回退。

最终固定频率五轮结果为 P50/P95/P99 `4.514/25.677/29.374 ms`，run-P50 CV `0.100`，
最大 heap 中位数 `8391 KiB`，每轮均为 48 帧。相对紧邻的上一份 ViewCompose 结果，P50 上升
2.8%（`+0.122 ms`，无实质变化），P95 降低 4.4%（`-1.185 ms`），P99 降低 21.7%，heap
降低 1.4%。纵向结论为 `improved`：上尾收敛且中位数没有实质回退。由于快照复用改变了三个
引擎共享的 fixture 准备方式，旧 Compose 与 Android Views 数字不能作为本 revision 的跨引擎
对照；形成新的相对结论前需重跑三引擎矩阵。下一框架目标是冷 composition/JIT 表面，而不是
再次增加 renderer 预检或削弱 Item Session 语义。

#### 2.4.3 Lazy 集合与 RecyclerView 尾延迟硬切

该测量阶段没有继续增加 Renderer 预检，而是同时纳入 Keyed 逻辑 Item 复用与 RecyclerView 提交
优化。其 Benchmark APK 还包含一项调用方聚合 Revision 试验，可以绕过 Typed List 的全部 Selector
求值；后续 API 审计已删除该公开 Token：它重复逐 Item Revision 契约，调用方推进错误时会产生
过期顺序、成员或 Selector 结果，而且 Benchmark 反复切换两份更新/重置 Snapshot 的 Fixture 会
异常放大其收益。普通 `List` DSL 会在父 Composition 的每一轮执行中求值顺序、成员与 Selector，
再按相等的 Key、`contentRevision`、Environment、Content Type、Kind 与 Span 复用已提交的逻辑
Item 和 Session Binding。后续新增的 `LazyItemsSnapshot` 显式快路只会浅冻结有序元素引用，并向
框架提供一个不透明身份，用于有界的已求值 Snapshot Cache。只有该身份成功提交后才能跳过
Selector 求值；它不会削弱逐 Item 或 Environment Revision 契约。

Android Adapter 现在以线性复杂度规划同顺序变更和循环位移，为循环位移发送最少 move，只把其他
结构变更交给 `DiffUtil`。精确的 submission 与 item 实例确认会消除冗余的排队 payload bind；
关闭条目动画时，纯语义变更不再通知 RecyclerView，但同步 Session 提交失败会得到一次定点重试。
预取计费会分离冷激活与权威的 detached prepare 成本，并在一次超预算样本后保守熔断。以上路径都
保留 key 身份、逻辑 Item Session 所有权、原生 Holder 复用和 reset/release 边界。

历史 revision 4 诊断首先在 Xiaomi MI 6 / API 28 上建立了下文继续使用的 Root 控制协议。
`2695fbfb` 的两组对照都因 run-P50 CV `0.192/0.157` 被拒绝，第二组还遗漏了策略 Payload。
硬切候选的 P50/P95/P99 为 `5.505/16.534/30.841 ms`，最大 heap `8212 KiB`，CV `0.075`；
但它同时包含后来删除的聚合跳过与保留的 Item/Adapter 改动，所以纵向结论仍为 `inconclusive`，
只作为 APK `020582a9` 的绝对结果。Material Host JIT 实验也使冷尾部回退并已撤销。下方
revision 5 A/B 提供有效的同策略 ViewCompose 对照，2026-08-17 的矩阵则完成跨引擎跟进。

<div className="benchmark-evidence">

2026-08-17 在与 `9ac164af` 相同的 Xiaomi 设备、五轮 `run-from-apk` 和 v3 固定时钟策略下重建
`2695fbfb`。Revision 4 滚动 P50/P90/P95/P99 为 `5.356/8.914/9.603/11.523 ms`（heap
`7833 KiB`、CV `0.016`），mutation 为 `4.244/15.852/22.681/24.947 ms`（`8593 KiB`、
`0.079`）。Revision 5 的对应变化为 `-0.5%/+0.6%/-0.7%/-7.1%` 和
`+0.1%/-31.2%/-44.0%/-39.3%`，heap 低 2.3%/5.4%。两项均通过协议与稳定性门禁，未复现回退；
但 Workload 已从 `performance.list@4` 变为 `@5`，正式结论仍为 `inconclusive`。若体感持续，下一步
应测量诊断 Tab 切换后立即 Fling 的精确路径。

</div>

2026-08-16 的 revision 5 A/B 使用同一台 Xiaomi MI 6 / API 28 设备、R8 benchmark target、
五轮与 48 帧协议、`run-from-apk` 编译身份，以及
`root-fixed-cpu-1401600-1804800-gpu-515000000-perf-hal-off-v3` 策略。工作负载包含 1,000 行，
每轮执行八个更新/重置周期。所有分支都会在 Ready 标记之前准备 revision 0 和 1 的不可变行
List。候选 B0/B1 Fixture 还会在 Ready 之前构造并常驻两份强类型 Snapshot Wrapper；B0 仍提交
原始 List，因此对于定时 mutation 之外的额外 Wrapper 常驻成本，它是偏保守的普通路径对照。
被测 steady path 始终只在两份预构建输入之间交替。A1 和 A2 是 `bb542f00` 的两次独立普通
`List` 参考重复；B0 通过候选实现的普通 `List` Overload 运行；B1 通过同一候选实现的
`LazyItemsSnapshot` 运行。

| 运行 | 路径 | Frame P50/P90/P95/P99，ms | 三帧总和 P50/P95，ms | 三帧最大值 P50/P95，ms | 最大 heap 中位数，KiB | Run-P50 CV | 验收 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| A1 | `bb542f00`，普通 `List` | 4.437 / 25.468 / 26.364 / 30.505 | 29.910 / 37.459 | 24.383 / 27.705 | 8313 | 0.127 | 接受的参考重复。 |
| A2 | `bb542f00`，普通 `List` | 4.825 / 25.580 / 26.293 / 28.353 | 30.165 / 36.434 | 24.899 / 27.175 | 8667 | 0.111 | 接受的参考重复。 |
| B0 | 候选，普通 `List` | 5.022 / 25.750 / 26.428 / 29.727 | 30.963 / 38.229 | 24.955 / 28.198 | 8254 | 0.047 | 接受的普通路径对照。 |
| B1 | 候选，`LazyItemsSnapshot` | 6.432 / 14.723 / 17.268 / 25.068 | 23.694 / 33.306 | 13.160 / 24.549 | 8419 | 0.096 | 接受的 Snapshot 运行。 |
| B1 重复 | 候选，`LazyItemsSnapshot` | 6.175 / 15.098 / 19.683 / 23.989 | 22.918 / 32.346 | 13.408 / 22.855 | 8872 | 0.193 | 拒绝：run-P50 CV 超过 0.15；只作方向性证据。 |
| B1 第三次 | 候选，`LazyItemsSnapshot` | 6.133 / 14.610 / 17.638 / 26.508 | 23.500 / 34.097 | 13.386 / 23.994 | 8533 | 0.131 | 接受的 Snapshot 复现。 |

汇总两次已接受的 A 参考重复后，Frame P50/P95/P99 为 `4.589/26.322/29.134 ms`。B0 分别
高 9.5%（`+0.434 ms`）、0.4%（`+0.107 ms`）和 2.0%（`+0.593 ms`）。其三帧总和
P50/P95 高 3.4%/2.7%，三帧最大值 P50/P95 高 1.1%/1.9%，最大 heap 中位数则低 2.8%。
没有决策指标同时跨过绝对值和归一化门槛，因此普通 `List` 的纵向分类是
`no material change`：新的 Collector 与 Cache 机制没有在普通路径上形成实质回退证据。

相对 B0，两次已接受的 Snapshot 运行 B1 与 B1-third 让 Frame P50 分别高 28.1%
（`+1.409 ms`）和 22.1%（`+1.111 ms`），但 Frame P95 分别降低 34.7%
（`-9.160 ms`）和 33.3%（`-8.790 ms`），P99 分别降低 15.7%（`-4.659 ms`）和
10.8%（`-3.219 ms`）。两者的三帧事务总和 P50/P95 分别降低 23.5%/12.9% 和
24.1%/10.8%，事务最大值 P50/P95 分别降低 47.3%/12.9% 和 46.4%/14.9%。排除每轮第一次
冷事务后，事务最大值 P95 从 `27.237 ms` 降至 `20.517/19.972 ms`，降低 24.7%/26.7%；
最大 heap 仅高 2.0%/3.4%。B1-repeat 的尾部方向一致，但其 CV 为 `0.193`，不能进入任何
归一化决策。因此 Snapshot 的主要分类是 `mixed`：Frame 中位数发生实质回退，P95/P99 和三帧
事务尾部则有实质改善。更窄的尾延迟结论为 `improved`；这不代表所有 Frame Time 都得到改善。

已验收的 2026-08-17 三引擎矩阵在同一台 Xiaomi MI 6 / API 28 设备上运行 commit
`3e0cc43a` 与 `performance.list@5`。Target APK SHA-256 为
`88eeacc3e4add75551088a9fdab7c0514414be747909223a22ec266b858ca55d`。AndroidX Benchmark
1.4.1 使用 AOSP 专属的 `su root` 命令形式，Magisk 30.6 无法在该设备上执行它，因此只在该命令
传输边界适配了 Test APK：原始 SHA-256 为
`d36f6a138c949fddd334c2c1b55f65b6ba02b2d296a2a45efa79439c53701c9c`，适配后 SHA-256 为
`cc9cce7c00de8c6f530c713257f91ecc2012473b644384cec971c3f2ef73d562`，通过等长命令别名转发至
`magisk su -c`。Target APK、Benchmark Workload、指标采集和结果 JSON 均未改写。采集结束后已
删除该别名、安装的 Benchmark 包和临时 APK。

六个方法均使用五轮、`run-from-apk`、精确 v3 策略、锁定 CPU/GPU、停止 Performance HAL、零
温控等待，并分别熄屏冷却至 34--37 摄氏度。MIUI 熄屏后会重置 CPU Policy，因此唤醒后重新应用；
UiAutomation 要求屏幕可交互，失败的熄屏预检未产生样本。

| 动作 | 引擎 | 每轮帧数 | P50/P90/P95/P99，ms | 最大 heap 中位数，KiB | Run-P50 CV |
| --- | --- | --- | ---: | ---: | ---: |
| 滚动 | ViewCompose | `160/163/166/162/163` | 5.328 / 8.964 / 9.538 / 10.702 | 7650 | 0.107 |
| 滚动 | Compose | `163/163/163/162/162` | 4.743 / 7.063 / 7.616 / 8.495 | 7398 | 0.091 |
| 滚动 | Android Views | `112/114/112/112/113` | 4.991 / 6.425 / 7.188 / 8.826 | 4049 | 0.045 |
| 变更 | ViewCompose | `48/48/48/48/48` | 4.247 / 10.907 / 12.698 / 15.155 | 8128 | 0.082 |
| 变更 | Compose | `41/41/41/41/41` | 5.207 / 15.040 / 18.568 / 26.250 | 8597 | 0.141 |
| 变更 | Android Views | `48/48/48/48/48` | 4.287 / 6.658 / 7.849 / 9.076 | 5864 | 0.125 |

所有引擎都通过 `0.15` 稳定性门禁。滚动场景中，ViewCompose 的 P50 比 Compose 高 12.3%
（`+0.585 ms`），P95 高 25.2%（`+1.922 ms`）；其 P50 比 Android Views 高 6.8%
（`+0.337 ms`），P95 高 32.7%（`+2.350 ms`）。相应 P99 分别高 26.0% 与 21.3%。两组
滚动对比均归类为 `regressed`：即使对 Android Views 的 P50 增量未过门禁，P95 也同时跨过了
归一化与绝对值门槛。ViewCompose 滚动 heap 比 Compose 高 3.4%，比 Android Views 高 88.9%。

变更场景中，ViewCompose 的 P50、P95 与 P99 分别比 Compose 低 18.4%（`-0.960 ms`）、
31.6%（`-5.870 ms`）和 42.3%（`-11.095 ms`），heap 低 5.5%，该对比归类为
`improved`。相对 Android Views，ViewCompose P50 仅低 0.9%（`-0.040 ms`），可视为持平；
但 P95 高 61.8%（`+4.849 ms`），P99 高 67.0%（`+6.079 ms`），heap 高 38.6%，该对比归类
为 `regressed`。因此矩阵整体结论是 `mixed`：强 Snapshot 变更路径优于 Compose，并达到原生
中位成本，但原生 RecyclerView 仍拥有更好的变更尾部，而且两个对照的滚动都优于 ViewCompose。

动作协议完全相同，但不同引擎可能合并或产生不同数量的测量帧；Compose 变更每轮稳定产生 41 帧，
另两个引擎则为 48 帧。因此该矩阵比较已验收的逐帧分布，不人为构造跨引擎三帧事务。它也是
`run-from-apk` 下两份预构建 Snapshot 的 Ready 后稳态证据，不代表启动、Snapshot 构造、单调
数据流或洁净的未编译 ART。下一步是分析 ViewCompose 滚动 P95/heap 差距与相对 Android Views
剩余的变更尾部差距，再增加冷构造和单调数据流 Workload。

前述 A/B 证据只覆盖 revision 5 两份已构建 Snapshot 的 steady 交替，直接有利于有界的两代身份
Cache。它没有测量 `toLazyItemsSnapshot()` 构造、首次求值、从不复用身份的单调数据流或 List
滚动，因此不能外推到这些成本。应把强 Snapshot 路径作为显式尾延迟取舍接受，同时保留普通
`List` 路径处理一般数据流；上方三引擎矩阵是独立的跨引擎结论。

#### 2.4.4 导航与设计系统诊断

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
