# ConstraintLayout Virtual Helpers 补齐执行计划（2026-03）

## 基线
- `:viewcompose-widget-constraintlayout` 与 renderer 已覆盖 anchors / dimension / bias / baseline 扩展 / guideline / barrier / chain(+weights) / decoupled `ConstraintSet`。
- 与 `androidx.constraintlayout.widget.ConstraintLayout` 对比，Virtual Helpers 仍缺：
  - `Flow`
  - `Group`
  - `Layer`
  - `Placeholder`

## 本轮范围
- 固定范围：`P2 Virtual Helpers`（不含 MotionLayout/TransitionManager，继续走 interop 方向）。
- 模块边界保持：
  - `ui-contract`：helper 语义模型
  - `viewcompose-widget-constraintlayout`：DSL 与 `constraintSet` builder
  - `viewcompose-renderer`：Android `ConstraintLayout` helper 映射
  - `app`：demo 与 UI 回归锚点

## 完成标准
1. `ConstraintHelpersSpec` 支持 `flows/groups/layers/placeholders`。
2. DSL 支持在 `ConstraintLayout {}` 与 `constraintSet {}` 中创建上述 helper。
3. renderer 在 `DeclarativeConstraintLayout` 内真实创建并应用 helper 行为。
4. 补齐 DSL 单测 + renderer 单测 + demo UI instrumentation 锚点。
5. 门禁：
   - 每步 `qaQuick`
   - 里程碑与收口：`ANDROID_SERIAL=98101FFBA003AE ./gradlew qaFull`

## Checklist
- [x] Step 1: 新增执行计划文档并提交。
- [x] Step 2: 扩展 `ui-contract` helper 数据模型（Flow/Group/Layer/Placeholder）。
- [x] Step 3: 扩展 widget DSL 与 `constraintSet` builder API。
- [x] Step 4: renderer 接入 Virtual Helpers 创建与应用逻辑。
- [x] Step 5: 测试补齐（DSL + renderer + UI）。
- [x] Step 6: 文档收口并归档/状态同步（ROADMAP/ARCHITECTURE/WORKFLOW）。

## 提交记录
1. `docs: add constraintlayout virtual helpers execution plan`
2. `feat: add virtual helper specs to constraintlayout contract`
3. `feat: add constraintlayout dsl apis for virtual helpers`
4. `feat: add renderer support for constraintlayout virtual helpers`
5. `test: add virtual helper demo anchors and ui regression coverage`
6. `docs: sync constraintlayout virtual helper status and record qaFull blocker`
7. `docs: close and archive constraintlayout virtual helpers execution plan`

## 阻塞记录
- 2026-03-11（Step 6）：`ANDROID_SERIAL=98101FFBA003AE ./gradlew qaFull` 触发 instrumentation 进程崩溃，失败模式为 `Activity never becomes requested state "[RESUMED]"` + `Instrumentation run failed due to Process crashed`。已记录到 [CONSTRAINT_LAYOUT_BLOCKER_CONTEXT_2026-03.md](CONSTRAINT_LAYOUT_BLOCKER_CONTEXT_2026-03.md)。
- 2026-03-11（阻塞解除）：同设备复跑 `ComponentFamilySmokeUiTest#keyComponentFamilies_haveVisibleSmokeAnchors` 与 `qaFull` 通过，阻塞关闭并归档。
