# A档：SlotTable Lite + 子树级重组执行计划（2026-03）

## 1. 基线

- 当前渲染模型：`RenderSession` 每次失效后整树 `buildVNodeTree(content)`，再交给 renderer diff/patch。
- 当前失效来源：`RuntimeObservation.observeReads(onInvalidated = ::scheduleRender)` 为 session 级观察。
- 当前 runtime 机制：`remember/effect/sideEffect/key/local` 以 ThreadLocal + store 方式驱动。
- 当前目标：不引入 compiler plugin、不改 DSL 对外 API，硬切到节点组级脏区重组。

## 2. 范围与约束

- 范围固定：
  - `viewcompose-runtime`：新增 SlotTable Lite 组合内核
  - `viewcompose-widget-core`：组合 API 改为 composer 驱动 + UiTreeBuilder 组级复用
  - `viewcompose-renderer`：补 vnode 同引用 fast-path
  - 文档与测试同轮收口
- 约束固定：
  - 不引入 Compose compiler plugin
  - 不新增 DSL 公共 API
  - 不保留旧“全树重建”路径开关（硬切）

## 3. 完成标准

1. `RenderSession` 从整树重建切换为“初次 compose + 脏组重组”。
2. `remember/key/DisposableEffect/SideEffect` 由 composer 驱动并保持对外用法不变。
3. `emit` 建立节点组边界并支持未脏组 vnode 复用。
4. renderer 同引用 vnode 走 `SkipSubtree` 快路径。
5. `qaQuick` 通过；里程碑步骤补跑 `qaFull`（设备可用时）。
6. 文档 `ARCHITECTURE/PERFORMANCE/ROADMAP/WORKFLOW` 与实现状态一致。

## 4. 实施 Checklist

- [x] Step 1 新增执行计划文档并首提
- [x] Step 2 runtime 引入 Composition Kernel（SlotTable Lite）
- [x] Step 3 widget-core 运行时 API 迁移到 Composer 驱动
- [x] Step 4 UiTreeBuilder 接入节点组重组语义
- [x] Step 5 RenderSession 硬切为初次 compose + 增量 recompose
- [x] Step 6 renderer 增加 vnode 同引用 fast-path
- [x] Step 7 测试补齐与门禁
- [x] Step 8 文档收口

## 5. 提交记录

- `DONE` docs: add recomposition-lite execution plan
- `DONE` feat: add runtime composition kernel with slot table lite
- `DONE` refactor: migrate remember/effect/key to composer-backed runtime
- `DONE` refactor: add node-group compose semantics in UiTreeBuilder emit path
- `DONE` refactor: hard-cut render session to incremental subtree recomposition
- `DONE` perf: add vnode identity fast-path in reconcile pipeline
- `DONE` test: add composition kernel and subtree-recompose coverage
- `DONE` fix: refresh session-backed closures during incremental recomposition
- `DONE` fix: avoid stale stress item snapshots in collections demo
- `DONE` docs: align architecture/performance roadmap for recomposition-lite

## 6. 阻塞记录

- `2026-03-08` `qaFull` 剩余 1 条 instrumentation 失败：`DemoVisualUiTest.inputSearch_focusSearchBar_doesNotAutoScrollList`
  - 现象：焦点后 recycler 锚点偏移 `-25px`（阈值 `12px`），断言失败。
  - 已验证：`qaQuick` 通过；`collectionsStress_*` 与 `statePatch*` 相关回归均已通过。
  - 处理：在 `ROADMAP.md` 保留该项 UI 风险状态，后续以输入焦点/IME 策略专项处理。
- `2026-03-09` 阻塞已解除：
  - 复跑用例 `DemoVisualUiTest.inputSearch_focusSearchBar_doesNotAutoScrollList` 已通过（`1/1`）。
  - 阻塞上下文归档见 [RECOMPOSITION_LITE_BLOCKER_CONTEXT_2026-03.md](RECOMPOSITION_LITE_BLOCKER_CONTEXT_2026-03.md)。
