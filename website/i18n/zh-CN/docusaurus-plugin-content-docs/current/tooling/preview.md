---
translation_source: tooling/preview.md
translation_source_hash: be71e061a064189ee1c1554514c24159f60d9374551e6e1567e8139dac9c0ee9
translation_status: current
---

# ViewCompose 预览

`viewcompose-preview` 模块提供两类开发期预览能力：

- Android Studio Compose Preview 通过 `ViewComposePreviewHost` 桥接渲染 ViewCompose DSL。
- Paparazzi 快照回归消费同一份 `PreviewCatalog`，避免分别维护 Preview 和截图定义。

## 业务侧接入（推荐）

面向实际业务的预览应写在使用方模块中，直接调用 `:viewcompose-preview` 的公开 API：

- `com.viewcompose.preview.ViewComposePreview`
- `com.viewcompose.preview.ViewComposePreviewWithRoot`（页面构建需要根 `ViewGroup` 时使用）
- `com.viewcompose.preview.ViewComposePreviewOptions`
- `com.viewcompose.preview.ViewComposePreviewTheme`

使用方模块（不是 `:viewcompose-preview`）需要自行启用 Compose：

```kotlin
plugins {
    alias(libs.plugins.android.library) // or android.application
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":viewcompose-preview"))
}
```

业务预览示例：

```kotlin
@Preview(name = "Biz Light", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizLightPreview() {
    ViewComposePreview(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Light),
    ) {
        // 在这里构建业务 DSL。
    }
}

@Preview(name = "Biz Root-Aware", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun BizRootAwarePreview() {
    ViewComposePreviewWithRoot(
        options = ViewComposePreviewOptions(theme = ViewComposePreviewTheme.Dark),
    ) { root ->
        // 页面 DSL 需要宿主 ViewGroup 时使用 root。
    }
}
```

## Studio Preview 使用

1. 在 Android Studio 中打开 `viewcompose-preview` 模块中的任一入口：
   - `com.viewcompose.preview.shell.PreviewShellsKt`
   - `com.viewcompose.preview.catalog.ui.CatalogPreviewsKt`
2. 使用 IDE Preview 面板检查浅色/深色、手机/平板和不同组件领域的变体。

## 定位真机当前 DSL

ViewCompose Android Studio 插件提供独立的 **Locate Device DSL** 工具栏动作与 Tools 菜单入口，
不与 Preview 工具窗口共用图标。要打开设备当前可见页面的 DSL：

1. 安装并打开使用当前 `viewcompose-host-android` Runtime 的可调试应用。
2. 在设备上进入目标 ViewCompose 页面。
3. 在 Android Studio 中选择 **Locate Device DSL**。

只有一台在线设备时会直接使用它。连接多台真机或模拟器时，插件会先弹出设备选择框，显示设备
类型、Android 版本和序列号。当同一窗口存在多个同样可见且嵌套最深的 ViewCompose 会话（例如
双栏布局）时，还会显示第二个选择框列出候选源码位置。

该动作通过 Android Studio 的 ADB 连接读取前台应用及其私有 Debug 报告，确认报告属于仍在运行
的进程，再把有界 JVM 源码候选解析到当前项目。当共享 Scaffold 先于 content 发出工具栏或容器
节点时，插件会移除在其他候选中重复出现的外层调用方，优先进入 content DSL；仍有多个独立
content 来源时会显示源码选择框。它不依赖 Preview 面板、外部存储、网络服务，也不会传输源码
文本。非调试构建不会暴露报告。如果没有可用报告，请让目标应用保持在前台，并确认其 Debug
构建使用当前 Host 构件。

## 快照回归

运行模块级快照验证：

```bash
./gradlew :viewcompose-preview:verifyPaparazziDebug
```

已提交的快照基准位于：

`viewcompose-preview/src/test/snapshots/images/`

审阅并确认视觉变更符合预期后，使用以下命令录制新基准：

```bash
./gradlew :viewcompose-preview:recordPaparazziDebug
```

提交前必须审阅每一张变更图片。原因不明的差异必须修复，不能直接录制。验证报告和差异图片输出到
`viewcompose-preview/build/reports/paparazzi/`；仓库 CI 会把 `qaPreview` 作为独立的必需门禁运行。
CI 失败时，Paparazzi 差异图片和测试报告会保存在 `qa-preview-failure-<attempt>` 产物中 7 天。

目录快照测试最多允许 `0.15%` 的整图差异，此容差仅用于吸收受支持的 macOS 与 Linux 主机之间
已知的 Layoutlib 原生可编辑文本字形栅格化差异。不得为了接受原因不明的布局、颜色或内容变化而
提高该阈值；应修复回归，或在人工审阅后为有意变更重新录制基准。

## 浮层预览策略

预览场景使用静态内容模拟浮层，不创建真正的窗口层。Dialog、Popup 和 BottomSheet 的
真实行为由 instrumentation 测试覆盖。
