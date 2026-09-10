---
schema_version: 2
document_id: project.module-catalog
doc_type: project
owner:
  kind: project
  id: publishing
version_lane: version-agnostic
capability_ids: []
artifact_ids:
  - viewcompose-runtime
  - viewcompose-text-core
  - viewcompose-ui-contract
  - viewcompose-navigation-core
  - viewcompose-navigation-kotlinx-serialization
  - viewcompose-navigation-android
  - viewcompose-renderer-android
  - viewcompose-ui-foundation
  - viewcompose-diagnostics
  - viewcompose-host-android
  - viewcompose-material3
  - viewcompose-material3-android
  - viewcompose-oneui7
  - viewcompose-android
  - viewcompose-overlay-android
  - viewcompose-overlay-material3-android
  - viewcompose-overlay-oneui7-android
  - viewcompose-image-coil
  - viewcompose-image-glide
  - viewcompose-lifecycle-androidx
  - viewcompose-viewmodel-androidx
  - viewcompose-preview-core
  - viewcompose-preview-gradle-plugin
  - viewcompose-preview-runner
  - viewcompose-preview-worker-host
  - viewcompose-preview
  - viewcompose-animation-core
  - viewcompose-animation
  - viewcompose-gesture-core
  - viewcompose-gesture
  - viewcompose-graphics-core
  - viewcompose-graphics
  - viewcompose-shadow-android
  - viewcompose-constraintlayout-androidx
  - viewcompose-media3-androidx
  - viewcompose-exoplayer2-android
  - viewcompose-google-maps-android
  - viewcompose-camerax-androidx
  - viewcompose-paging-androidx
sample_ids: []
workflow: 让公共制品登记、所属模块手册与发布清单严格同步。
validation:
  - ./gradlew verifyDocumentationStructure verifyViewComposePublishingConfiguration
lifecycle: 公共制品新增、重命名、退役、发布或调整模块分类时更新。
translation_source: modules/README.md
translation_source_hash: 0d077766ef9f11d5d03efe016aa93d5f0ecb3a09a737bb0f4556f273c05c5cd4
translation_status: current
---

# 已发布模块目录

本目录是 ViewCompose 公共 Maven 产物的权威文档登记表。它与
`gradle/viewcompose-publishing.properties` 保持同步，并由 `verifyDocumentationStructure`
自动验证。

每个产物都链接到已提供的 `docs/modules/<artifact-id>/README.md`。发布与站点校验会拒绝缺失手册、
只存在于目录中的产物，或未登记到本表的已发布产物。跨模块概念仍以架构与指南页面为事实来源。

全局侧栏链接到本目录。通过“手册”列打开各模块的当前文档；这些链接在本地构建和托管站点中
都保持当前选择的语言。

| 产物 | 分类 | 运行时职责 | 手册 |
| --- | --- | --- | --- |
| `viewcompose-runtime` | Kernel | 平台无关的状态与观察运行时 | [可用](./viewcompose-runtime/README.md) |
| `viewcompose-text-core` | Kernel | 平台无关的文本编辑模型 | [可用](./viewcompose-text-core/README.md) |
| `viewcompose-ui-contract` | Kernel | 平台无关的 UI 契约与节点规范 | [可用](./viewcompose-ui-contract/README.md) |
| `viewcompose-navigation-core` | Kernel | 平台无关的导航状态与事务 | [可用](./viewcompose-navigation-core/README.md) |
| `viewcompose-navigation-kotlinx-serialization` | Integration | 可选 Kotlinx Serialization Route Codec | [可用](./viewcompose-navigation-kotlinx-serialization/README.md) |
| `viewcompose-renderer-android` | Android Engine | Android View 渲染与协调引擎 | [可用](./viewcompose-renderer-android/README.md) |
| `viewcompose-ui-foundation` | UI Foundation | 核心 DSL、组件、token 与 Local 值 | [可用](./viewcompose-ui-foundation/README.md) |
| `viewcompose-diagnostics` | Integration | 有界且隐私安全的生产故障聚合 | [可用](./viewcompose-diagnostics/README.md) |
| `viewcompose-host-android` | Android Engine | 底层 View 宿主、session、状态与互操作引擎 | [可用](./viewcompose-host-android/README.md) |
| `viewcompose-material3` | Design System | Material 3 主题与动态色适配 | [可用](./viewcompose-material3/README.md) |
| `viewcompose-material3-android` | Aggregate | 具名 Material 3 Android 应用集成 | [可用](./viewcompose-material3-android/README.md) |
| `viewcompose-oneui7` | Design System | One UI 7 五组件 Alpha Token 与组件集 | [可用](./viewcompose-oneui7/README.md) |
| `viewcompose-android` | Aggregate | 中立 Android 应用入口依赖 | [可用](./viewcompose-android/README.md) |
| `viewcompose-navigation-android` | Integration | Android 导航宿主集成 | [可用](./viewcompose-navigation-android/README.md) |
| `viewcompose-overlay-android` | Integration | 不依赖 Material 的 Android Overlay 传输 | [可用](./viewcompose-overlay-android/README.md) |
| `viewcompose-overlay-material3-android` | Integration | 基于 Material 的 Android 浮层呈现 | [可用](./viewcompose-overlay-material3-android/README.md) |
| `viewcompose-overlay-oneui7-android` | Integration | 不依赖 Material 的 One UI Snackbar 与底部对话框呈现 | [可用](./viewcompose-overlay-oneui7-android/README.md) |
| `viewcompose-image-coil` | 集成 | 基于 Coil 的通用图片加载 | [可用](./viewcompose-image-coil/README.md) |
| `viewcompose-image-glide` | 集成 | 基于 Glide 的通用图片加载 | [可用](./viewcompose-image-glide/README.md) |
| `viewcompose-lifecycle-androidx` | 集成 | 感知 AndroidX 生命周期的状态、Effect 与已提交原生 View 协同 | [可用](./viewcompose-lifecycle-androidx/README.md) |
| `viewcompose-viewmodel-androidx` | 集成 | ViewModel 与 SavedStateHandle 集成 | [可用](./viewcompose-viewmodel-androidx/README.md) |
| `viewcompose-preview-core` | 预览工具 | 预览注解与工具协议 | [可用](./viewcompose-preview-core/README.md) |
| `viewcompose-preview-gradle-plugin` | 预览工具 | 预览发现与 Gradle 任务 | [可用](./viewcompose-preview-gradle-plugin/README.md) |
| `viewcompose-preview-runner` | 预览工具 | Layoutlib 预览渲染运行时 | [已提供](./viewcompose-preview-runner/README.md) |
| `viewcompose-preview-worker-host` | 预览工具 | 隔离的预览 Worker 宿主 | [可用](./viewcompose-preview-worker-host/README.md) |
| `viewcompose-preview` | 预览工具 | 开发预览与快照集成 | [已提供](./viewcompose-preview/README.md) |
| `viewcompose-animation-core` | Kernel | 平台无关的动画引擎契约 | [可用](./viewcompose-animation-core/README.md) |
| `viewcompose-animation` | UI Foundation | 动画 DSL 与组合集成 | [可用](./viewcompose-animation/README.md) |
| `viewcompose-gesture-core` | Kernel | 平台无关的手势策略 | [可用](./viewcompose-gesture-core/README.md) |
| `viewcompose-gesture` | UI Foundation | 手势 DSL 与状态 API | [可用](./viewcompose-gesture/README.md) |
| `viewcompose-graphics-core` | Kernel | 平台无关的图形模型 | [可用](./viewcompose-graphics-core/README.md) |
| `viewcompose-graphics` | UI Foundation | 绘制 DSL 与组合集成 | [可用](./viewcompose-graphics/README.md) |
| `viewcompose-shadow-android` | Integration | 高级 Android 阴影渲染 | [可用](./viewcompose-shadow-android/README.md) |
| `viewcompose-constraintlayout-androidx` | Integration | AndroidX ConstraintLayout DSL | [可用](./viewcompose-constraintlayout-androidx/README.md) |
| `viewcompose-media3-androidx` | Integration | 生命周期安全的 AndroidX Media3 PlayerView 托管 | [可用](./viewcompose-media3-androidx/README.md) |
| `viewcompose-exoplayer2-android` | Integration | 冻结的旧版 ExoPlayer 2 StyledPlayerView 兼容层 | [可用](./viewcompose-exoplayer2-android/README.md) |
| `viewcompose-google-maps-android` | Integration | 生命周期安全的 Google Maps MapView 托管 | [可用](./viewcompose-google-maps-android/README.md) |
| `viewcompose-camerax-androidx` | Integration | 精确且受生命周期约束的 CameraX PreviewView 托管 | [可用](./viewcompose-camerax-androidx/README.md) |
| `viewcompose-paging-androidx` | Integration | 面向 ViewCompose LazyColumn 的生命周期感知 AndroidX Paging 集成 | [可用](./viewcompose-paging-androidx/README.md) |

## 目录规则

1. 产物 ID 同时是目录名和未来公共 URL 键，必须与 Maven `artifactId` 完全一致。
2. 只有链接的 `README.md` 满足模块文档契约后，模块手册才能标记为“可用”。
3. 新增、重命名或停用产物时，必须同时更新本目录和发布元数据。
4. Demo 应用、Benchmark 等内部模块写入架构或工具文档，不作为 Maven 产物加入本表。
5. 站点生成时从发布元数据读取当前模块版本。不可变的已发布版本追加记录到
   `gradle/viewcompose-documentation-releases.properties`；不要再手工维护任何重复登记表。
