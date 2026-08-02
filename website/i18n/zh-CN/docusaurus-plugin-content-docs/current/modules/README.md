---
translation_source: modules/README.md
translation_source_hash: 7511d21a76f0b5257f556dbe32a79f2b99f3593afd46302c87bc874e772ffe28
translation_status: current
---

# 已发布模块目录

本目录是 ViewCompose 公共 Maven 产物的权威文档登记表。它与
`gradle/viewcompose-publishing.properties` 保持同步，并由 `verifyDocumentationStructure`
自动验证。

模块专属手册会逐步添加到 `docs/modules/<artifact-id>/README.md`。在此之前，
`docs/README.md` 中的架构和指南仍是当前事实来源。`规划中` 仅表示独立模块手册尚未发布，
不代表模块尚未实现或未发布到 Maven Central。

| 产物 | 分类 | 运行时职责 | 手册 |
| --- | --- | --- | --- |
| `viewcompose-runtime` | 基础 | 平台无关的状态与观察运行时 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-runtime) |
| `viewcompose-text-core` | 基础 | 平台无关的文本编辑模型 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-text-core) |
| `viewcompose-ui-contract` | 基础 | 平台无关的 UI 契约与节点规范 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract) |
| `viewcompose-navigation-core` | 导航 | 平台无关的导航状态与事务 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-core) |
| `viewcompose-navigation` | 导航 | Android 导航宿主集成 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation) |
| `viewcompose-renderer` | 渲染 | Android View 渲染与协调引擎 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer) |
| `viewcompose-widget-core` | UI | 核心 DSL、组件、主题与 Local 值 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-widget-core) |
| `viewcompose-host-android` | Android 宿主 | Activity、Fragment 与 View 宿主集成 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-host-android) |
| `viewcompose-overlay-android` | Android 宿主 | Android 浮层呈现后端 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-overlay-android) |
| `viewcompose-image-coil` | 集成 | 基于 Coil 的远程图片加载 | 规划中 |
| `viewcompose-lifecycle` | 集成 | 感知生命周期的状态收集 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-lifecycle) |
| `viewcompose-viewmodel` | 集成 | ViewModel 与 SavedStateHandle 集成 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-viewmodel) |
| `viewcompose-preview-core` | 预览工具 | 预览注解与工具协议 | 规划中 |
| `viewcompose-preview-gradle-plugin` | 预览工具 | 预览发现与 Gradle 任务 | 规划中 |
| `viewcompose-preview-runner` | 预览工具 | Layoutlib 预览渲染运行时 | 规划中 |
| `viewcompose-preview-worker-host` | 预览工具 | 隔离的预览 Worker 宿主 | 规划中 |
| `viewcompose-preview` | 预览工具 | 开发预览与快照集成 | 规划中 |
| `viewcompose-animation-core` | 动画 | 平台无关的动画引擎契约 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-animation-core) |
| `viewcompose-animation` | 动画 | 动画 DSL 与组合集成 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-animation) |
| `viewcompose-gesture-core` | 手势 | 平台无关的手势策略 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-gesture-core) |
| `viewcompose-gesture` | 手势 | 手势 DSL 与状态 API | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-gesture) |
| `viewcompose-graphics-core` | 图形 | 平台无关的图形模型 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-graphics-core) |
| `viewcompose-graphics` | 图形 | 绘制 DSL 与组合集成 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-graphics) |
| `viewcompose-shadow-android` | 可选 Android 后端 | 高级 Android 阴影渲染 | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-shadow-android) |
| `viewcompose-widget-constraintlayout` | 可选控件 | ConstraintLayout DSL | [可用](https://docs.viewcompose.com/zh-CN/modules/viewcompose-widget-constraintlayout) |

## 目录规则

1. 产物 ID 同时是目录名和未来公共 URL 键，必须与 Maven `artifactId` 完全一致。
2. 只有链接的 `README.md` 满足模块文档契约后，模块手册才能标记为“可用”。
3. 新增、重命名或停用产物时，必须同时更新本目录和发布元数据。
4. Demo 应用、Benchmark 等内部模块写入架构或工具文档，不作为 Maven 产物加入本表。
5. 站点生成时从发布元数据读取模块版本，不要手工维护另一份可变版本表。
