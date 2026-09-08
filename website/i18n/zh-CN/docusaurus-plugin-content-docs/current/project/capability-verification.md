---
translation_source: project/capability-verification.md
translation_source_hash: 932316dfde29989ab3b1012b3ce71251990dd8affcb21193042b26b6982590d7
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

## 框架契约审查验收

2026-09-06 的审查基线 `d64710459df73f3b42067767bb2f4f273b9eff33` 通过了 818 项
独立 JVM 测试和 39 项 AI 测试，但十个探针仍复现缺陷，另两项由源码确认。十二项修正现已
与主分支 `048b63af03ca51d8f44a042fc7b26d9bae85ed01` 集成，包含上游 PR #274。

在 macOS 14.1.2 arm64、JDK 21、Kotlin 2.2.10、Gradle 9.3.1 和 Node 24.19.0 环境中，
集成候选通过了框架、应用及集成项目全部 40 个选定任务的 2118 项测试，其中包括十二个变更
制品的 1270 项测试；两组统计存在重叠。AI 验证通过 397/397 项测试及 Phase 0 契约检查。
集成阶段新增的两个回归用例覆盖临时 Node 路径别名和生成 Preview 的任务历史隔离。
测试数量增长表示覆盖范围扩大，不代表归一化性能提升。

完整 `qaQuick`、`qaPreview`、文档结构／开发工具隔离／发布意图三项必要门禁，以及固定编译
和渲染语料检查均通过。完整版本化 API 验证重建了全部六个不可变源码版本；网站的类型、构建、
无障碍、体积预算、外壳与版本检查均通过。本地 Maven 发布使用短期测试签名密钥；此次验收
没有向公共 Maven 或 npm 仓库发布制品。

刷新后的知识包包含 85 项能力、540 个符号和 220 个样例。静态推断、决策、生成以及真实的
已发布 Maven 渲染、比较和修复证据均按精确源码身份重新采集。37 个变更视觉夹具文件仅有
身份字符串及派生哈希差异。XML 和截图的 PNG／渲染树字节与验收阈值均保持一致。
截图基线通过 12/12 项语义和 15/15 项结构检查，比较的 2,523,781 个像素中差异为 0。
故意制造的回归仍产生 2221 个差异像素；生成的回滚将其恢复为 0，并通过全部六项门禁。
无效、取消、撤销与复用授权用例继续按约定拒绝。这组固定已发布环境的像素结论为
**无实质变化**，不代表当前源码的真机显示一致性。

集成还修正了两个执行环境缺陷：临时 Node 可执行文件经物理路径或临时目录别名访问时仍被
识别为临时文件；生成 Preview 请求各自保留 Gradle 任务历史，同时共享依赖下载缓存。
图片绑定 Preview 从空构建目录执行后能够保留所需资源类，复现已接受的图像与比较分母。
未发布的 `0.8.0` 包通过确定性打包及安装后 CLI／MCP／Agent 流程，取代最初本地的
`0.7.1` 候选；公共消费者仍使用 `0.7.0`。

结论是受审查的事务、订阅、文本历史、资源、焦点、清理、进程、帧和缓存边界的正确性
**改善**。[最初实现记录](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/records/framework-contract-audit/20260906-implementation.json)
保留此前的部分验收状态；[集成记录](https://github.com/ViewCompose/ViewCompose/blob/main/docs/project/records/framework-contract-audit/20260908-integration.json)
记录完整验证、源码哈希、环境和包身份。

真机资源外观与 OEM IME 行为继续由 Host、Renderer 和图片适配器负责人承接；Windows
进程树行为由 AI 工具负责人承接。缓存复用与帧性能仍需受控测量。保留这些回归测试，并在
宣称更广的设备或性能结论前完成上述后续验证。

## 失败排查

- `RolledBack` 必须保留旧可见树，且不执行候选 commit effect。
- `Committed` 可包含隔离的 post-commit 失败；其余回调与清理仍须执行。
- 原生失败必须包含 `operation` 和 `nodeKey`，缺失元数据是框架缺陷。
- 第三方 View 若在 `update` 中修改隐藏内部状态，需要 adapter 让 `update` 可重放，并把外部动作
  移到 `onCommit`。
