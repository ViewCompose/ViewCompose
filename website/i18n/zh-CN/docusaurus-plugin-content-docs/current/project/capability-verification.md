---
translation_source: project/capability-verification.md
translation_source_hash: 184ae158ecb15aab88421f0be3131717525d69ced1dc00d09609435918119660
translation_status: current
---

# 能力验证

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

## 导航生命周期与资源证据收口

运行 `./gradlew verifyNavigationCoverage` 验证选定关键路径。门禁拒绝缺失的执行数据或 class
bundle，并要求 Core 行/分支覆盖率不低于 `80%`/`70%`、Android 不低于 `70%`/`60%`；XML
与 HTML 位于 `build/reports/viewcompose-quality/navigation-coverage/`。这些比例只描述归属明确的
reducer、生命周期/scene、executor、owner/session、保留策略、Back、runtime 与 host 路径，
并非两个模块的所有 class。

运行 `./gradlew verifyNavigationBenchmarkTraceContracts`，拒绝 runtime 导航/帧 trace section 与
release benchmark collector/label 之间的漂移。`qaQuick` 会执行两项导航验证任务。

使用明确的 `ANDROID_SERIAL` 运行 `NavigationBackDeviceTest`。当前 target 设备必须运行完整
测试类；API 28–30 设备还必须运行终态 pop 可达性、bounded 淘汰可达性和深度 13 保留证据。
终态 pop 必须释放 presentation、LifecycleOwner 与 ViewModel；`Bounded(2)` 必须释放被淘汰的
presentation，同时保留逻辑 owner 与 ViewModel。资源样本在相同预热与 GC 流程下记录活跃
presentation 数、Java/native 已分配 heap、PSS 与同步 pop 中位耗时；只有结构性的
presentation 数是硬阈值。接受的绝对值、归一化比较、设备/构建上下文、局限与下一步记录在
主动导航演进计划中。

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

## 发版后 Demo 压力矩阵

使用以下命令运行确定性的真机矩阵：

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.DemoPostReleaseVisualMatrixUiTest
```

该套件使用场景 ID 与 Android 资源 ID，不依赖可见文案。每次运行只接管并替换自身场景的
证据文件；目标应用窗口不在前台时拒绝截图，并为每张截图生成一份对应的元数据文件。套件
执行以下成对矩阵：

| 配置 | 语言 | 主题 | 方向 | 字体缩放 | 密度缩放 |
| --- | --- | --- | --- | ---: | ---: |
| 默认参考 | 英语 | 浅色 | LTR | `1.0` | `1.0` |
| 压力参考 | 简体中文 | 深色 | RTL | `1.3` | `1.25` |

2026-08-22 接受的 Xiaomi MI 6/API 28 运行在 `71.693 s` 内通过 3/3 个方法；覆盖 12 个场景的
32 张截图与元数据全部通过自动断言和人工目检。证据包括 Popup/阴影几何与关闭、精确三列
Grid 边界、分段控件内边距、标准与 One UI 导航按下/释放态、双向嵌套列表交接，以及五种
滚动 owner 的焦点编辑器在 IME 上方完整展示。

更早的 26 帧运行因 MIUI/窗口污染、弱 Grid 断言和仅释放态导航证据而被拒绝。加固增加 6 帧
（`+23.1%`），结论为覆盖 `improved`，并非速度提升。完整 Demo APK 随后在 135/137 暴露
Activity 触摸坐标与 Collections 标签耦合；硬切两项契约并在设备级点击前关闭 IME 后，
`742.903 s` 内通过 137/137。这是行为/隔离证据，不是性能基线。

局限：一台 API 28 设备与成对矩阵不能覆盖完整笛卡尔积和全部平台层级。截图证明可见几何；
原生触摸、关闭、嵌套滚动、焦点、IME 与重置断言证明行为。受影响系统变化时应重跑；要求
完整矩阵的版本还需加入 API 24 与当前 target。

## 失败排查

- `RolledBack` 必须保留旧可见树，且不执行候选 commit effect。
- `Committed` 可包含隔离的 post-commit 失败；其余回调与清理仍须执行。
- 原生失败必须包含 `operation` 和 `nodeKey`，缺失元数据是框架缺陷。
- 第三方 View 若在 `update` 中修改隐藏内部状态，需要 adapter 让 `update` 可重放，并把外部动作
  移到 `onCommit`。
