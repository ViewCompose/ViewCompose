---
translation_source: architecture/decisions/0006-root-scoped-overlay-backend-selection.md
translation_source_hash: e18ba2d9cd73b4be8115ca63993e8e8dda1cf00620b5a0bdaae7e8fa8616d9ac
translation_status: current
---

# ADR-0006：Root 作用域 Overlay Backend 选择

- 状态：已接受
- 日期：2026-08-09
- 替代：ADR-0002 与 ADR-0003 中关于退役/重命名 `viewcompose-overlay-android` 的部分

## 背景

UI Foundation 已经把声明式 Overlay 请求、Session 身份、队列策略与捕获的 Surface 内容从 Android
呈现中分离。但最初的 Android Backend 仍在一个产物中混合通用 `Dialog`、`PopupWindow`、Toast、
嵌套渲染容器、Material Snackbar 与 Material Bottom Sheet。

五层硬切把该产物重命名为 `viewcompose-overlay-material3-android`，虽然明确了 Material 所有权，
却也把通用 Android 传输放进 Material 模块。Host Android 又通过 `ServiceLoader` 选择第一个完整
Host，导致中立或 One UI Root 可能仅因 Runtime Classpath 存在 Material 产物而获得 Material 行为。

Overlay 选择必须与 Root 的 Context、Token、Recipe 和诊断快照一致，同时保留 Android Window
生命周期与原生行为，并避免向 UI Foundation、Host Android 或 Renderer 添加设计系统分支。

## 决策

1. 从 `0.1.0-alpha04` 起恢复 `viewcompose-overlay-android`，作为唯一不依赖 Material 的 Android
   Overlay 传输。这是语义硬切，不是兼容转发壳。
2. 中立产物负责 Android Dialog、PopupWindow、Toast、锚点观察、坐标定位、嵌套渲染容器适配及
   Root/Session 清理。
3. Snackbar 与 Modal Bottom Sheet 是窄 Presenter 插槽；缺失时显式报告 `Unsupported`，中立 Host
   不替换为 Material。
4. `viewcompose-overlay-material3-android` 仅拥有 Material Snackbar、`BottomSheetDialog` Presenter
   及把它们装配到中立 Host 的薄 Adapter。
5. 中立 `setUiContent` 与 Navigation Root 显式构造中立 Host；`setMaterial3UiContent` 显式构造
   Material Adapter。Classpath 顺序不参与设计选择。
6. Host Android 仅为自定义底层 Host 保留发现单个中立 Provider 的 `ServiceLoader`。零个时回退
   no-op，多个时确定性失败。
7. 设计系统快照用 `UiIntegrationAttribution` 报告 Capability、Transport、Presenter、Conformance
   与 Fallback；延迟 Overlay 内容捕获这份不可变快照。
8. 只有必须在 View 构造前解析不同 Android Context 的设计系统才新增 Activity/Fragment 入口模块。
   仅 Token/Recipe 的设计系统继续使用中立 Root。

## 影响

- 中立与 One UI 的默认 Overlay 依赖图不包含 Material Components。
- Material 应用仍可通过 `viewcompose-material3-android` 保持单依赖、单 Host 调用路径。
- 自定义 Material Host 必须显式构造 Material Adapter；旧 Material Service Provider 被移除。
- 使用 `viewcompose-overlay-android:0.1.0-alpha03` 的 Consumer 必须迁移公开包引用，不能再假设该
  坐标提供 Material Snackbar 或 Bottom Sheet。
- One UI Snackbar 与 Modal Bottom Sheet 在拥有并验证 Recipe 前保持显式 Unsupported，禁止静默
  Material Fallback。
- 即使 Maven 坐标重新启用，历史 Tag 与生成式文档仍保持不可变。

## 否决方案

### 保留 Material 完整 Host Provider

否决，因为 Provider 顺序是进程级的，无法证明哪个设计系统快照拥有 Root 或延迟 Overlay。

### 每套设计系统发布一套 Activity/Fragment Extension

否决，因为仅 Token 的系统不需要不同 Android Context；复制 Lifecycle、Saved State 与 Render
Session 入口只会增加漂移。

### 把全部 Overlay Presenter 放入 UI Foundation

否决，因为 Android Window 与 Material Widget 属于平台/集成细节；UI Foundation 只负责请求、
队列、Session 与捕获内容。

### 保持旧坐标退役并新增另一个中立坐标

按项目决策否决。只要明确声明并版本化不兼容语义，恢复清晰的通用坐标优于继续增加 `platform`
或 `host` 限定词。
