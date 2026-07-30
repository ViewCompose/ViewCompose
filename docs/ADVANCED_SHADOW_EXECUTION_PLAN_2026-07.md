# Advanced Shadow / Decoration Layer 执行计划（2026-07）

## 1. 目标

在不放弃 Android View 内容、输入、无障碍和互操作能力的前提下，建立独立于
`View.elevation` 的高级阴影渲染链路，支持：

1. 有序多层外阴影。
2. 阴影颜色、模糊半径、扩散半径和二维偏移。
3. 后续可扩展的内阴影与前景装饰。
4. `zIndex` 与阴影高度解耦。
5. 根 RenderSession、Lazy item RenderSession 和导航 destination RenderSession 的一致生命周期。
6. 静态阴影缓存、可观测诊断和发布态性能门禁。

本轮为硬切设计，不为错误或不完整的旧阴影语义保留兼容分支。

## 2. 非目标

1. 不替换 TextView、EditText、RecyclerView 等原生内容控件。
2. 不建立单 View 全场景树，不自行实现文本输入或无障碍虚拟节点系统。
3. 不通过 SurfaceView、TextureView 或独立 GL Surface 绘制普通界面阴影。
4. 不把软件层作为所有节点的默认阴影后端。
5. 不保证系统 `elevation` 在不同 Android/OEM 上像素级一致。

## 3. 模块边界

### `viewcompose-ui-contract`

负责平台无关公开契约：

- `UiShadow`
- `Modifier.dropShadow(...)`
- `Modifier.dropShadows(...)`
- 后续的 `Modifier.innerShadow(...)`

Modifier 中保存有序、不可变的阴影列表，不使用“最后一个覆盖前一个”的单值模型。

### `viewcompose-shadow-android`

新增 Android library 模块，只负责：

- 阴影后端选择。
- 阴影 mask / bitmap / RenderNode 缓存。
- Android Canvas / RenderNode / RenderEffect 执行。
- 阴影绘制统计、缓存命中和降级原因。

模块只依赖 `viewcompose-ui-contract`，不依赖 `viewcompose-renderer`、`widget-core` 或 `app`。

### `viewcompose-renderer`

负责：

- 从 `ResolvedModifiers` 读取阴影列表。
- 提交 View 几何、shape、transform、clip chain 和绘制顺序。
- 安装可嵌套 Decoration Host。
- 在节点 create/patch/move/remove/recycle 时维护 decoration entry 生命周期。

Renderer 单向依赖 `viewcompose-shadow-android`。

### `viewcompose-widget-core`

只负责组件默认值消费。Card、Menu、FAB 等仍优先使用语义 `elevation`；
只有需要精确视觉阴影的组件才显式选择 `dropShadow`。

## 4. 渲染模型

每个 RenderSession 拥有一个可嵌套 Decoration Host：

```text
DecorationHost
├─ drop-shadow plane
├─ native View content plane
└─ foreground-decoration plane（后续 inner shadow / focus ring）
```

根 Session、Lazy item Session 和导航 destination Session 都使用相同生命周期模型。
首版不跨 RenderSession 提升阴影，避免把 Lazy 回收、页面转场和跨宿主坐标绑定到全局单例。

每个带高级阴影的节点提交：

```text
ShadowEntry(
    stableNodeIdentity,
    view,
    orderedShadows,
    shape,
    environment,
    zIndex,
)
```

View 的最终 bounds/transform 在绘制前解析，避免每次 patch 主动维护全局坐标镜像。

## 5. 后端策略

### Native elevation fast path

以下条件继续使用系统 `View.elevation`：

- Material 高程语义。
- 单层系统阴影。
- 不要求显式 blur/spread/offset。
- 接受平台视觉差异。

### Cached mask backend（API 24+）

精确静态外阴影默认后端：

1. 根据 shape 和尺寸创建 alpha mask。
2. 应用 spread、offset、blur 和颜色。
3. 以 shape/size/density/spec 为 key 缓存。
4. 节点移动只更新绘制坐标，不重建缓存。

圆角矩形优先评估可伸缩九宫格缓存，任意 shape 使用完整尺寸缓存。

### RenderNode backend（API 29+）

使用独立 display list 缓存稳定绘制命令和变换；不把 RenderNode 本身的 elevation
当成高级阴影模型。

### RenderEffect backend（API 31+）

仅用于通过基准验证的动态模糊路径。大面积、多层或列表场景必须受预算策略约束，
不能无条件创建离屏效果层。

## 6. 正确性约束

1. `zIndex` 只控制绘制顺序，不允许继续通过 `translationZ` 改变阴影。
2. `elevation` 与 `dropShadow` 可以同时存在，但语义和后端独立。
3. 多层阴影严格保留声明顺序。
4. 阴影不参与 measure/layout，不改变节点占位尺寸。
5. 阴影默认允许超出节点 bounds，但必须受显式 clip chain 控制。
6. View recycle/remove/dispose 后不得残留 ShadowEntry、bitmap 或 RenderNode。
7. Environment density/layoutDirection 变化必须使相关 shape/cache key 失效。
8. 失败 patch 不得提前发布 decoration 状态，必须与 View tree transaction 同步提交或回滚。

## 7. 分阶段交付

### Phase 0：契约与边界

1. 新增 `UiShadow` 与有序 modifier 契约。
2. 增加模块边界检查和 modifier resolve/diff 测试。
3. 建立 `viewcompose-shadow-android` 空壳与纯后端选择契约。

### Phase 1：多层外阴影闭环

1. 建立可嵌套 Decoration Host。
2. 接入节点 create/patch/move/remove。
3. 实现圆角/切角、多层、颜色、blur/spread/offset。
4. 解决父容器默认裁切。
5. 硬切解耦 `zIndex` 与 `translationZ`。

### Phase 2：缓存与 Lazy

1. 实现稳定 cache key、容量预算和回收。
2. Lazy item recycle/dispose 清理。
3. 滚动期间禁止每帧重建静态阴影。
4. 增加 cache hit/miss/eviction 和绘制耗时诊断。

### Phase 3：前景装饰与内阴影

1. 建立 foreground-decoration plane。
2. 实现单层/多层内阴影。
3. 验证 clipping、ripple、文本输入和手势不受影响。

### Phase 4：高级后端与性能门禁

1. API 29+ RenderNode display-list 后端。
2. API 31+ RenderEffect 动态模糊试验。
3. 列表、复杂布局、页面转场三类基准。
4. 根据数据决定 Auto/Fast/Exact 策略，不凭实现直觉选择默认后端。

当前 Phase 4 已建立 API 29+ RenderNode display-list 试验后端，以及列表滚动/变更、
复杂布局滚动/更新的 ViewCompose/Compose 成对基准。`Auto` 暂时保持
`ExactBitmap`；RenderNode 只能通过基准参数显式启用，待多轮同机数据通过后再决定默认策略。

后端对比必须使用同一台设备和相同温控条件，分别保存结果，不能用单轮冒烟数据作结论：

```shell
# 精确 Bitmap 基线（默认 10 轮）
./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.benchmark.ShadowPerformanceComparisonBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shadowRenderPolicy=exact_bitmap

# 保存上一步生成的 benchmarkData.json 后，再运行 RenderNode
./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.benchmark.ShadowPerformanceComparisonBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shadowRenderPolicy=render_node

# 使用 Compose 控制组归一化设备波动，并执行回归门禁
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/absolute/path/to/render-node-benchmarkData.json \
  -PbenchmarkBaseline=/absolute/path/to/exact-bitmap-benchmarkData.json
```

调试构建可通过 `shadow_render_policy=render_node` 启动性能页，并在
`ViewComposeShadow` 日志中确认实际后端和降级原因。API 29 以下或软件 Canvas 会自动回退
到 Bitmap；运行时 RenderNode 失败也会回退且计入诊断。

RenderEffect 动态模糊和页面转场阴影基准暂不进入默认链路：当前公开阴影契约是静态规格，
先以列表和复杂布局数据确定 display-list 是否有稳定收益，再为动画阴影建立独立预算。

#### 首轮真机结论

2026-07-30 在 Samsung SM-G991B（Android 13）上完成 ExactBitmap 与 RenderNode
各 8 个方法、每个方法 10 轮的 release 对照：

| 场景 | RenderNode 相对 ExactBitmap P50 | P95 | RSS |
| --- | ---: | ---: | ---: |
| 阴影列表滚动 | +2.9% | +4.1% | +9.5% |
| 阴影列表变更 | -4.7% | +3.4% | +0.6% |
| 阴影复杂布局滚动 | -2.3% | +1.5% | -4.2% |
| 阴影复杂布局更新 | -0.7% | -1.4% | -2.1% |

两组的设备、系统、最高频率与构建模式一致，但 AndroidX 报告的 `cpuLocked` 标记不同，
因此严格门禁正确拒绝了直接判定。允许上下文差异后的 Compose 归一化结果仍然方向混杂，
且部分交互场景稳定性不足，不能证明 RenderNode 有稳定收益。

结论：`Auto` 继续选择 ExactBitmap；RenderNode 保留为显式实验策略，用于后续设备矩阵和
动态阴影研究，不进入默认路径。

### Phase 5：Demo 与文档

状态：已完成。

1. `Graphics -> 外阴影` 已覆盖单层/多层/彩色/偏移/正负 spread/cut shape。
2. `Graphics -> 内阴影` 已覆盖单层/多层与 TextField/Button 输入互操作。
3. `Graphics -> Lazy/诊断` 已覆盖 1000 项稳定 key、外/内缓存统计、实际后端和降级原因。
4. 单元模型测试与 Samsung SM-G991B / Android 13 定向 instrumentation 已通过。
5. `ARCHITECTURE.md`、`MODIFIER.md`、`PERFORMANCE.md`、`ROADMAP.md` 与 `SHADOWS.md` 已同步。

## 8. 验证与提交

每阶段独立提交。最低验证：

1. 契约阶段：纯 JVM 单测 + `qaQuick`。
2. Android 后端阶段：Robolectric + Paparazzi。
3. Lazy/动画阶段：instrumentation + `benchmarkRelease`。
4. 收口阶段：`qaQuick`、`qaPreview`、设备可用时 `qaFull`。

性能结论只接受同设备、同构建类型、同温控条件下的前后对比。
