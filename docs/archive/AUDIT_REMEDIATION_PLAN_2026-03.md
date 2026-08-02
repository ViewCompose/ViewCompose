# Audit Remediation Plan (2026-03)

## 1. 文档定位

本文件是 `PROJECT_AUDIT_2026-03-05` 的执行中计划文档。

目标：

1. 防止线程/上下文丢失
2. 逐步记录每个整改项的实施状态
3. 每完成一步即回写状态
4. 全部完成后迁移到 `docs/archive/`

审计来源：

- [PROJECT_AUDIT_2026-03-05.md](PROJECT_AUDIT_2026-03-05.md)

## 2. 当前基线（2026-03-06）

已核验事实：

1. `:viewcompose-renderer:compileDebugKotlin` 通过，审计里“TabRow 断裂编译”问题已不复现。
2. `:app:connectedDebugAndroidTest` 已通过：16/16。
3. 审计提到“Chip/SearchBar 等无 demo 使用”已过期：当前 demo 已存在调用。
4. 审计提到的 overlay host 重复实现仍存在（Dialog/Popup/ModalBottomSheet 三套近似 commit/clear 逻辑）。
5. `WORKFLOW` 已补齐“完成态门禁命令 + 计划防丢失”约束。
6. `qaQuick` 与 `qaFull` 均已通过。

## 3. 执行总原则

1. 按小步提交推进，每个可独立验证步骤一个 commit。
2. 每步完成后立即更新本文件的状态与结果。
3. 任何步骤如果影响到根目录规范文档，代码与文档同一步或相邻提交。
4. 全部步骤完成后，将本文件迁移到 `docs/archive/` 并在归档索引登记。

## 4. 工作分解与状态

### W0 - 计划与文档状态同步

- [x] 创建本执行文档
- [x] 扫描根目录文档中的“进行中/未完成/Next”标记
- [x] 把“当前真实阻塞”同步到主文档（ROADMAP/PERFORMANCE/WORKFLOW）

完成标准：

1. 主文档状态与当前测试/编译事实一致
2. 不出现“文档显示稳定、实际回归失败”的矛盾

---

### W1 - 质量门禁收紧（完成态定义）

- [x] 在 `WORKFLOW.md` 增加双层门禁命令清单
- [x] 在 `ROADMAP.md` 增加能力项完成态字段（编译/单测/demo/UI）
- [x] 提供统一执行入口（`qaQuick` + `qaFull`，以 Gradle 任务或脚本形式）

完成标准：

1. 能用一条命令执行快速门禁
2. 设备可用时能执行全量门禁
3. 文档明确“何时允许标记完成”

---

### W2 - UI 测试基线恢复（P0）

- [x] 新增稳定测试锚点能力（`Modifier.testTag`）
- [x] 重构 `DemoUiTestHelpers` 为 tag-first，不再依赖易变文案滚动
- [x] 迁移 `DemoVisualUiTest` 到稳定锚点断言
- [x] 跑通 `:app:connectedDebugAndroidTest`

完成标准：

1. `connectedDebugAndroidTest` 全绿
2. 文案调整不会导致大面积测试失效

---

### W3 - 验证资产回填（P1）

- [x] 为审计提及组件补最小单测闭环（Chip/SearchBar/NavigationBar/Scaffold/LazyRow/FlowRow/Scrollable）
- [x] 为关键组件族补至少 1 条 instrumentation smoke 回归
- [x] 更新 `ROADMAP` 的对应状态

完成标准：

1. 新增组件不再只有 DSL/Defaults 实现而无验证
2. 单测与 UI 测试均可追溯到具体组件族

---

### W4 - Overlay Host 去重（P1）

- [x] 抽象 session-bound overlay host 通用 reconcile 模板
- [x] Refactor `DialogOverlayHost` / `PopupOverlayHost` / `ModalBottomSheetOverlayHost`
- [x] 补齐 `ModalBottomSheetOverlayHost` 测试

完成标准：

1. 三个 host 不再维护重复的 commit/clear 模板
2. 现有 overlay 相关测试全绿

---

### W5 - 目录与大文件治理（P1/P2）

- [x] `app` demo 文件按子目录归类（保持行为不变）
- [x] 拆分大文件（优先 `DemoFoundationsPage.kt`、`InputWidgetsDsl.kt`、`ContainerViewBinder.kt`）
- [x] 文档同步更新模块/目录归属说明

完成标准：

1. `app/src/main/java/com/viewcompose` 不再平铺大量文件
2. 大文件拆分后可读性提升且无行为回归

## 5. 执行日志

### 2026-03-06

1. 建立执行文档并登记 W0~W5。
2. 完成基线核验：编译通过、connected 测试失败 16/19。
3. 已同步主文档状态：
   - `ROADMAP.md`：补充当前 connected 测试阻塞与里程碑完成态门禁。
   - `PERFORMANCE.md`：补充性能推进与 UI 回归稳定化联动约束。
   - `WORKFLOW.md`：新增“执行计划防丢失约定”。
4. W0 已完成，下一步进入 W1（质量门禁收紧）。
5. W1 进展：
   - 已新增 root Gradle 统一门禁入口：`qaQuick` / `qaFull`。
   - 已在 `WORKFLOW.md` 增加双层门禁命令清单与完成态约束。
6. 已执行 `./gradlew qaQuick`，当前失败点：`IconButtonTest.icon button emits themed defaults`。
7. 已在 `ROADMAP.md` 增加完成态字段（`C/U/D/UI`）及统一判定口径。
8. W1 清单项已完成；当前门禁结果仍为阻塞态（unit/UI 未全绿），需在 W2/W3 中继续收口。
9. W2 进展：
   - 已新增 `Modifier.testTag`，渲染链路支持写入 view tag（`ui_framework_test_tag`）。
   - 已补文档 `MODIFIER.md` 中的能力归类。
   - `:viewcompose-renderer:testDebugUnitTest` 已通过。
10. 已完成 `DemoUiTestHelpers` tag-first 改造，新增 testTag 定位/点击/滚动可见性辅助能力。
11. 已完成 `DemoVisualUiTest` 到 testTag 断言迁移，去除易变文案滚动依赖。
12. 已跑通 `:app:connectedDebugAndroidTest`（15/15）。
13. 已修复 `IconButtonTest` 默认变体断言漂移，`qaQuick` 通过。
14. 已跑通 `qaFull`（含 connected UI 测试）。
15. W3 进展：已新增 `AdditionalWidgetCoverageTest`，补齐 Chip/SearchBar/NavigationBar/Scaffold/LazyRow/FlowRow/Scrollable 最小单测闭环。
16. `:viewcompose-widget-core:testDebugUnitTest` 已通过。
17. 已新增 `ComponentFamilySmokeUiTest`，覆盖 Actions/Input/Navigation/Collections/Layouts 关键组件族 smoke 锚点断言。
18. 已跑通 `:app:connectedDebugAndroidTest`（16/16）。
19. 已同步 `ROADMAP.md` 与 `PERFORMANCE.md` 的基线状态。
20. W3 已完成，下一步进入 W4（Overlay Host 去重）。
21. 已新增 `SessionBoundSurfaceOverlayHost` 通用模板，统一会话级 diff/reconcile/clear 流程。
22. `DialogOverlayHost`、`PopupOverlayHost`、`ModalBottomSheetOverlayHost` 已完成模板化重构。
23. 已新增 `ModalBottomSheetOverlayHostTest`，补齐 show/update/dismiss/clear 覆盖。
24. 已跑通 `qaFull`（含 connected UI 测试）。
25. W4 已完成，下一步进入 W5（目录与大文件治理）。
26. 已完成 `app` 目录首轮归类：`activity/`（入口）与 `demo/`（演示逻辑）分层，保持包名不变。
27. 已在 `ARCHITECTURE.md` 增补 `app` 目录落位基线说明。
28. 大文件拆分进展：`ContainerViewBinder.kt` 已抽离 `ContainerViewSpecReader`，保留原 API 并减少主文件复杂度。
29. 大文件拆分进展：`InputWidgetsDsl.kt` 已按输入控件族拆分为 `TextFieldWidgetsDsl`、`InputControlsDsl`、`SearchBarDsl`。
30. 大文件拆分进展：`DemoFoundationsPage.kt` 已拆为主调度 + section 文件，降低单文件认知负担。
31. W5 已完成，W0~W5 全部完成；下一步执行计划文档归档与归档索引登记。
