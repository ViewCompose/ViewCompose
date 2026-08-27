---
translation_source: project/capability-verification.md
translation_source_hash: 271d85d2c86324c9d0e11a36470afa208f05c83f22c05100cf0e7527a02b8182
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

2026-08-22 接受的运行使用 API 28 的 Xiaomi MI 6。3 个 instrumentation 方法全部通过，
耗时 `71.693 s`，覆盖 12 个场景 ID，生成 32 张截图与 32 份元数据。自动断言和人工目检
均接受全部 32 帧：包括 Popup 锚定、圆角几何、四边阴影采样与外部触摸关闭；等宽主题色块；
精确三列 Grid 状态及分别覆盖首行/末行的裁剪证据；分段选择和内边距；标准与 One UI 导航
的按下/释放反馈；双向嵌套列表边界交接；以及 LazyColumn、LazyVerticalGrid、
ScrollableColumn、VerticalPager 和 PullToRefresh 所有者在 IME 上方完整展示焦点编辑器。

首次截图因 MIUI 启动确认与窗口过渡污染多帧而被拒绝；当时 Grid 只断言“状态发生变化”，
没有精确断言三列，导航也只捕获释放态。加固后的运行把聚焦证据从 26 帧增加到 32 帧
（`+6`、`+23.1%`）：新增 4 个按下态帧，并把 Grid 顶部与底部覆盖拆开。结论：`improved`。
这代表覆盖改进，不是性能比较。

清理验收还使用同一组重新构建的应用 APK，运行了完整 Demo instrumentation APK。首次运行
通过 135/137 项，并暴露两个可稳定复现的测试基础设施缺陷：经 Activity 分发的触摸错误地
使用屏幕坐标而非窗口坐标；Collections 的一个场景角色仍依赖细粒度字符串标签。硬切修正
了手势坐标契约，让设计系统压力路径在设备级点击导航和分段控件前先关闭 IME，并把
Collections 迁移到其拥有的 Android 资源 ID，不保留别名。最终运行在 `742.903 s` 内通过
全部 137 项。这是行为与隔离证据，不是性能基线。

局限：该结果只覆盖一台 API 28 设备和成对压力矩阵，并非完整笛卡尔积，也没有覆盖全部必需
平台层级。截图只证明几何与可见状态；同一套测试仍保留原生触摸、Popup 关闭、嵌套滚动
所有权、焦点、IME 与重置断言作为行为证据。任一被覆盖系统变化时都应重跑本矩阵；受影响
版本需要完整平台矩阵时，再加入 API 24 与当前 target API 设备。

## 失败排查

- `RolledBack` 必须保留旧可见树，且不执行候选 commit effect。
- `Committed` 可包含隔离的 post-commit 失败；其余回调与清理仍须执行。
- 原生失败必须包含 `operation` 和 `nodeKey`，缺失元数据是框架缺陷。
- 第三方 View 若在 `update` 中修改隐藏内部状态，需要 adapter 让 `update` 可重放，并把外部动作
  移到 `onCommit`。
