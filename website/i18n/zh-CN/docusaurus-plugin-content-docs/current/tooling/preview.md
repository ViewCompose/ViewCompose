---
translation_source: tooling/preview.md
translation_source_hash: c68e8ca43a49bd898e9a42750a21a3015b3fba3ad921bf71d1b381aff2e712f5
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
