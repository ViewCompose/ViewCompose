---
translation_source: project/capability-verification.md
translation_source_hash: b55581a8d3595ec6653b820133eac7b352f815a770cc6cf52bd8f6e864318354
translation_status: current
---

# P1 核心能力验证

本矩阵覆盖 P1 焦点/按键输入、嵌套滚动和渲染失败/原生副作用边界。快速 JVM/Robolectric
测试仍是默认门禁；连接 Android 设备验证原生 View 分发路径。

## 自动门禁

运行完整编译与单测：

```bash
./gradlew qaQuick
```

只运行 P1 真机用例：

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.P1CoreCapabilitiesUiTest
```

`P1CoreCapabilitiesUiTest` 在真实 Android runtime 验证：

1. `FocusRequester` 到达原生焦点目标，硬件按键按 preview 再 bubble 顺序分发。
2. 透明 nested-scroll host 实现 AndroidX `NestedScrollingParent3` 并报告原生 pre-scroll 消费。
3. `AndroidView.update` 失败会恢复旧 View 配置、发出结构化 `RenderFailure`，且不会发布失败
   候选的 `onCommit`。

debug-only 测试 Activity 使用 `showWhenLocked` 与 `turnScreenOn`，不会关闭或改变设备 keyguard。

## 必需设备矩阵

修改以下系统的版本发布前必须覆盖：

| 领域 | 必需用例 |
| --- | --- |
| 焦点 | 触摸、程序请求、清除、前后移动、四向 D-pad、group 进出、keyed 移除/重新挂载恢复 |
| 硬件按键 | Tab/Shift+Tab、Enter/Space、方向键、Back/Escape、preview 拦截、target bubble、重复与 modifier flag |
| 文本共存 | 进出 `TextField`、IME 开关、硬件输入、selection key、无重复回调 |
| 嵌套滚动 | 横纵拖动、pre/post 消费、触摸到 fling、Lazy/Pager/Scrollable 组合、overscroll 边界 |
| 原生互操作 | AndroidX child/parent、代表性第三方 nested-scrolling View、非 nested AndroidView 回退 |
| 渲染失败 | composition、factory/update/reset、回滚 rebind、`onCommit` 隔离、release、Session dispose |
| 生命周期 | 焦点或滚动后旋转重建、pending frame 时前后台切换、pending coroutine/fling 时 dispose |

最低平台覆盖 API 24、一台 API 28–30 设备和当前 target API。焦点遍历或按键映射变化时增加
硬件键盘或 TV/ChromeOS 目标。

## 失败排查

- `RolledBack` 必须保留旧可见树，且不执行候选 commit effect。
- `Committed` 可包含隔离的 post-commit 失败；其余回调与清理仍须执行。
- 原生失败必须包含 `operation` 和 `nodeKey`，缺失元数据是框架缺陷。
- 第三方 View 若在 `update` 中修改隐藏内部状态，需要 adapter 让 `update` 可重放，并把外部动作
  移到 `onCommit`。
