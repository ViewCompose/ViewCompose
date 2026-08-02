# Project Re-Audit (2026-03-06)

## 1. 审计范围

本次重新审计覆盖：

1. 根目录有效文档：`ARCHITECTURE/ROADMAP/MODIFIER/NODE_PROPS/THEMING/PERFORMANCE/SESSION_CONTAINER_CHECKLIST/WORKFLOW/CONTEXT`
2. 框架模块：`viewcompose-runtime/viewcompose-renderer/viewcompose-widget-core/viewcompose-overlay-android/viewcompose-image-coil`
3. 验证层：`app` demo 与 `app/src/androidTest`
4. 模块配置：`settings.gradle.kts` 与各模块 `build.gradle.kts`

## 2. 本轮已完成的文档去旧

已直接修正文档漂移点（见提交 `4e31ed9`）：

1. `SESSION_CONTAINER_CHECKLIST.md`：
   - 删除已失效的 `TabPagerDiffTest.kt` 引用
   - 更新为当前容器清单（`LazyRow/LazyVerticalGrid/HorizontalPager/VerticalPager`）
   - 新增“已覆盖/未覆盖”测试映射
2. `MODIFIER.md`：
   - 删除已完成阶段仍以“当前问题”描述的历史内容
   - 收敛为当前边界规范版（Modifier/Scope/Props/Theme）
3. `ROADMAP.md`：
   - Overlay 基线补齐 `ModalBottomSheet`
   - UI 状态从“全绿”修正为“分项缺口可见”
4. `ARCHITECTURE.md`：
   - 模块列表补齐 `viewcompose-benchmark`
   - 风险项补入 Fragment 生命周期暴露问题与延迟容器覆盖缺口

## 3. 当前发现（设计/实现偏差与改进空间）

### F1（高优先级）：Fragment 宿主 API 仍暴露 `RenderSession`

现状证据：

1. [AndroidHostBridge.kt](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/bridge/AndroidHostBridge.kt:60) 的 `Fragment.createUiContent(...)` 返回 `UiContentHost(root + session)`
2. [AndroidHostBridge.kt](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/bridge/AndroidHostBridge.kt:96) 仅对 `ComponentActivity` 做自动 `dispose` 绑定

影响：

1. Activity 与 Fragment API 行为不一致
2. Fragment 调用方容易遗漏 `onDestroyView` 时机的会话回收

建议：

1. 新增 `Fragment.setUiContent(...)`（绑定 `viewLifecycleOwner` 自动 dispose）
2. 将 `createUiContent(...)` 标记为低层 API 或迁移型 API

### F2（高优先级）：延迟 session 容器专项覆盖不均衡

现状证据：

1. 通用单测集中在 [LazyListDiffTest.kt](../../viewcompose-renderer/src/test/java/com/viewcompose/renderer/reconcile/LazyListDiffTest.kt) 与 [LazyItemSessionControllerTest.kt](../../viewcompose-renderer/src/test/java/com/viewcompose/renderer/view/LazyItemSessionControllerTest.kt)
2. UI 侧主要覆盖 [DemoVisualUiTest.kt](../../app/src/androidTest/java/com/viewcompose/DemoVisualUiTest.kt:248) 的 collections stress 与 [DemoVisualUiTest.kt](../../app/src/androidTest/java/com/viewcompose/DemoVisualUiTest.kt:273) 的 tab content
3. `LazyVerticalGrid` 与 `VerticalPager` 缺容器级专门回归

影响：

1. “空 diff + 闭包变化”类回归在新容器上仍可能漏检

建议：

1. 为 `LazyVerticalGrid`、`VerticalPager` 各补 1 条 unit + 1 条 instrumentation
2. 为 `HorizontalPager` 补容器级 unit（现有仅间接覆盖）

### F3（中优先级）：Overlay 已实现 `ModalBottomSheet`，但 UI 自动化缺专门断言

现状证据：

1. demo 已有 bottom sheet 场景：[DemoFeedbackPage.kt](../../app/src/main/java/com/viewcompose/demo/DemoFeedbackPage.kt:259)
2. 当前 UI 用例仅触发 snackbar/toast/dialog/popup：[DemoVisualUiTest.kt](../../app/src/androidTest/java/com/viewcompose/DemoVisualUiTest.kt:97)
3. bottom sheet 缺对应 testTag 常量：[DemoTestTags.kt](../../app/src/main/java/com/viewcompose/demo/DemoTestTags.kt:47)

影响：

1. Overlay milestone 中 `ModalBottomSheet` 回归声明与自动化资产不完全一致

建议：

1. 增加 `FEEDBACK_SHOW_BOTTOM_SHEET/FEEDBACK_BOTTOM_SHEET_*` 测试锚点
2. 在 `DemoVisualUiTest` 增加 show/dismiss/assert 路径

### F4（中优先级）：`viewcompose-widget-core` 与 Android bridge 仍耦合

现状证据：

1. [AndroidHostBridge.kt](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/bridge/AndroidHostBridge.kt)
2. [AndroidThemeBridge.kt](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/bridge/AndroidThemeBridge.kt)
3. [AndroidEnvironmentBridge.kt](../../viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/bridge/AndroidEnvironmentBridge.kt)

影响：

1. 目前不阻塞 Android 单平台演进
2. 若后续考虑多平台或更强模块隔离，core 迁移成本会上升

建议：

1. 暂不立即拆分（保持迭代稳定）
2. 在中期规划预留 `ui-host-android` 模块，把 bridge 逐步迁出 core

### F5（中优先级）：demo 页面文件体量过大，维护成本持续上升

现状证据：

1. [DemoWidgetShowcaseDetails.kt](../../app/src/main/java/com/viewcompose/demo/DemoWidgetShowcaseDetails.kt) 1310 行
2. [DemoFeedbackPage.kt](../../app/src/main/java/com/viewcompose/demo/DemoFeedbackPage.kt) 579 行
3. [DemoModifiersPage.kt](../../app/src/main/java/com/viewcompose/demo/DemoModifiersPage.kt) 607 行

影响：

1. 场景扩展时更容易引入回归与冲突
2. AI/人工审查时上下文噪音高

建议：

1. 以“章节 section 文件”拆分（每个文件 < 300 行）
2. testTag 常量与 scenario 元数据独立文件化

## 4. 优先级执行建议

P0（先做）：

1. Fragment 宿主自动 dispose API（F1）
2. `LazyVerticalGrid/VerticalPager` 容器级专项回归（F2）

P1（随后）：

1. ModalBottomSheet UI 自动化补齐（F3）
2. demo 大文件拆分（F5）

P2（中期演进）：

1. `viewcompose-widget-core` bridge 拆分预研与迁移路径（F4）

## 5. 结论

当前框架主架构方向仍正确，且文档主线已明显收敛；本次主要问题不在“核心能力缺失”，而在“宿主 API 一致性”和“专项回归覆盖不均衡”。按上述 P0/P1 节奏推进，可以在不打断现有迭代的情况下继续提升稳定性与可维护性。
