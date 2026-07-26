# ViewCompose Performance

## 1. 文档定位

本文档是性能规范版，定义：

1. 当前性能基线
2. 性能门禁指标
3. 设计与实现层约束
4. 后续优化路线

历史长版分析见：

- [PERFORMANCE_FULL_2026-03-06.md](/Users/gzq/AndroidStudioProjects/UIFramework/docs/archive/PERFORMANCE_FULL_2026-03-06.md)

## 2. 当前性能基线（2026-03）

### 2.1 已建立能力

1. viewcompose-benchmark 模块已接入，具备稳定测试入口。
2. renderer 已具备节点级“是否重绑”判断能力（`rebound/skipped` 统计）。
3. diagnostics 已有 render/layout 基础指标可观测能力。
4. runtime 已切到 `SlotTable Lite`：`RenderSession` 采用“首帧 compose + 节点组级增量 recompose”，未脏组复用 `VNode` 引用。
5. 组级失效队列支持祖先合并去重（`InvalidationQueue`），并对 `emit(spec/modifier)` 输入变化做脏标记，避免参数变化漏更新。
6. patch pipeline 已支持 subtree skip（`SkipSubtree`）并新增 `skippedSubtrees` 统计；`previousVNode === nextVNode` 命中同引用快路径。
7. 延迟 session 容器的 keyed diff 已切到 `DiffUtil` 引擎（保留 key 缺失/重复 fallback）。
8. framework 托管的 `RecyclerView` 容器默认不共享 `RecycledViewPool` 且保留系统 `itemAnimator`；可按需通过容器参数 `reusePolicy/motionPolicy` 对单个容器启用共享池与动画器策略。
9. renderer 内部尺寸换算统一走 `viewcompose-renderer/view/DimensionUtils.kt`，避免容器层重复定义 `density/dpToPx` 带来的行为漂移。
10. runtime 状态系统已升级为 `SnapshotMutationPolicy + MVCC + MutableSnapshot` 事务模型；重组读取运行在一致性快照内。
11. `RenderSession` 失效重绘调度已升级为 `Choreographer` 帧对齐合并；显式 `render()` 仍保持立即执行语义。
12. 动画主链已统一到 `MonotonicFrameClock`（host 注入 `Choreographer` 实现），`animate*AsState/Animatable/Transition` 与重组调度对齐。
13. `graphicsLayer` 已接入 renderer resolved modifier 与 patch 语义，状态驱动变换不再依赖全量重绑。
14. 手势分发已统一为单 view dispatcher，消费优先级固定为“gesture > clickable fallback”，并支持方向锁/slop/priority 冲突策略。
15. 列表/分页容器支持 opt-in motion 策略（insert/remove/move/change），与 `DiffUtil + ItemAnimator` 协同且不改变默认容器行为。
16. graphics 主链已落地 Canvas 节点与 draw modifiers；`drawWithCache` 支持跨帧缓存命中/失效，避免高频绘制场景重复构建命令。
17. graphics 执行器已收口 v2 基线：`DrawRoundRect` 四角半径语义正确、`Drawable` 绘制支持 `DrawPaint` 组合、`ImageFilterModel.Chain` 可递归合并生效。
18. 发布态基线使用 R8 + resource shrink 的非 debuggable `benchmark` target；`ReleaseBaselineBenchmark` 固定覆盖无 ART 预编译的冷启动与 state patch 帧耗时。

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

发布态权威基线是 `ReleaseBaselineBenchmark`：

1. target 为 R8 优化、resource shrink、非 debuggable 的 benchmark variant。
2. `CompilationMode.None` 隔离 ART 预编译收益，直接暴露交付二进制回归。
3. 固定场景为冷启动和 state patch。
4. 结果只在同设备、同系统版本、同温控状态下纵向比较。

### 2.3 当前结论

1. 当前阶段优先级不是“追求极限 FPS”，而是先控制回归风险和错误用法。
2. 最关键收益来自：
   - 正确复用
   - 组级脏区重组 + 跳过不必要更新
   - 容器刷新语义稳定
3. 基线更新（2026-03-08）：`SlotTable Lite` + 子树级重组已接入主链路，`qaQuick` 通过；`qaFull` 结果按当前设备门禁状态在 roadmap 持续登记。

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

状态：待推进  
目标：收敛高频容器和复杂页面的布局开销

### Phase 5：发布态优化

状态：R8 release 基准已建立，baseline profile 待推进
目标：在当前无 ART 预编译基线上继续量化 baseline profile 等发布链路收益

## 7. 评审与提交流程

性能相关 PR 至少满足：

1. 说明改动针对哪类成本
2. 提供改动前后关键指标
3. 说明是否影响容器刷新语义
4. 同步更新本文件或相关规范文档

若改动涉及可见行为（布局、交互、overlay、输入），额外要求：

1. 至少补一条对应 instrumentation 回归，或登记明确豁免与补齐时间

协作规则见：

- [WORKFLOW.md](/Users/gzq/AndroidStudioProjects/UIFramework/WORKFLOW.md)

## 8. 关联文档

1. 架构规范：[ARCHITECTURE.md](/Users/gzq/AndroidStudioProjects/UIFramework/ARCHITECTURE.md)
2. 容器专项清单：[SESSION_CONTAINER_CHECKLIST.md](/Users/gzq/AndroidStudioProjects/UIFramework/SESSION_CONTAINER_CHECKLIST.md)
3. 统一路线图：[ROADMAP.md](/Users/gzq/AndroidStudioProjects/UIFramework/ROADMAP.md)
4. 文档入口：[CONTEXT.md](/Users/gzq/AndroidStudioProjects/UIFramework/CONTEXT.md)
5. 状态快照规范：[STATE_SNAPSHOT.md](/Users/gzq/AndroidStudioProjects/UIFramework/STATE_SNAPSHOT.md)
